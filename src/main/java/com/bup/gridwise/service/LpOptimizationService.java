package com.bup.gridwise.service;

import com.bup.gridwise.dto.request.BatteryConfig;
import com.bup.gridwise.dto.request.HourEntry;
import com.bup.gridwise.dto.response.DirectiveInterpretation;
import com.bup.gridwise.dto.response.HourlyPlanEntry;
import com.bup.gridwise.model.BatteryAction;
import com.bup.gridwise.model.DirectiveType;
import com.bup.gridwise.model.StructuredAdjustment;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class LpOptimizationService {

    public List<HourlyPlanEntry> optimizeSchedule(List<HourEntry> hours, BatteryConfig battery,
                                                  List<DirectiveInterpretation> directives) {
        int N = 24;

        // Base arrays
        double[] demand = new double[N];
        double[] solar = new double[N];
        double[] tariff = new double[N];

        for (HourEntry h : hours) {
            int idx = h.getHour();
            demand[idx] = h.getDemand_kwh();
            solar[idx] = h.getSolar_kwh();
            tariff[idx] = h.getTariff_bdt_per_kwh();
        }

        double cap = battery.getCapacity_kwh();
        double initialE = battery.getInitial_energy_kwh();
        double baseMinE = battery.getMinimum_energy_kwh();
        double maxCharge = battery.getMax_charge_kwh_per_hour();
        double maxDischarge = battery.getMax_discharge_kwh_per_hour();

        double[] effectiveSolar = Arrays.copyOf(solar, N);
        double[] minReserve = new double[N];
        Arrays.fill(minReserve, baseMinE);

        double[] maxChargeLimit = new double[N];
        Arrays.fill(maxChargeLimit, maxCharge);

        double[] maxDischargeLimit = new double[N];
        Arrays.fill(maxDischargeLimit, maxDischarge);

        double[] maxGridLimit = new double[N];
        Arrays.fill(maxGridLimit, 1e9);

        // Apply validated directives
        if (directives != null) {
            for (DirectiveInterpretation dire : directives) {
                if (!dire.isApplies() || dire.getDirective_type() == DirectiveType.no_op) {
                    continue;
                }
                DirectiveType type = dire.getDirective_type();
                StructuredAdjustment adj = dire.getStructured_adjustment();
                if (adj == null || adj.getHours() == null) continue;

                List<Integer> hrs = adj.getHours();
                if (type == DirectiveType.solar_reduction && adj.getFactor() != null) {
                    double factor = adj.getFactor();
                    for (int h : hrs) {
                        if (h >= 0 && h < N) {
                            effectiveSolar[h] = solar[h] * factor;
                        }
                    }
                } else if (type == DirectiveType.minimum_battery_reserve && adj.getMinimum_energy_kwh() != null) {
                    double reqMin = adj.getMinimum_energy_kwh();
                    for (int h : hrs) {
                        if (h >= 0 && h < N) {
                            minReserve[h] = Math.max(minReserve[h], reqMin);
                        }
                    }
                } else if (type == DirectiveType.no_charge_window) {
                    for (int h : hrs) {
                        if (h >= 0 && h < N) {
                            maxChargeLimit[h] = 0.0;
                        }
                    }
                } else if (type == DirectiveType.no_discharge_window) {
                    for (int h : hrs) {
                        if (h >= 0 && h < N) {
                            maxDischargeLimit[h] = 0.0;
                        }
                    }
                } else if (type == DirectiveType.max_grid_window && adj.getMax_grid_kwh() != null) {
                    double mg = adj.getMax_grid_kwh();
                    for (int h : hrs) {
                        if (h >= 0 && h < N) {
                            maxGridLimit[h] = Math.min(maxGridLimit[h], mg);
                        }
                    }
                }
            }
        }

        // Variables per hour h (Total = 5 * 24 = 120):
        // 0..23: grid_kwh[h]
        // 24..47: solar_used_kwh[h]
        // 48..71: battery_charge_kwh[h]
        // 72..95: battery_discharge_kwh[h]
        // 96..119: battery_energy_after_kwh[h]
        int totalVars = 120;
        double[] objCoeffs = new double[totalVars];
        for (int h = 0; h < N; h++) {
            objCoeffs[h] = tariff[h]; // grid_kwh cost
            objCoeffs[48 + h] = 1e-4; // small penalty to prevent wasteful battery charge cycling
            objCoeffs[72 + h] = 1e-4; // small penalty to prevent wasteful battery discharge cycling
        }

        LinearObjectiveFunction obj = new LinearObjectiveFunction(objCoeffs, 0);
        List<LinearConstraint> constraints = new ArrayList<>();

        for (int h = 0; h < N; h++) {
            // 1. Energy balance: grid[h] + solar_used[h] + discharge[h] - charge[h] = demand[h]
            double[] cBalance = new double[totalVars];
            cBalance[h] = 1.0;                  // grid
            cBalance[24 + h] = 1.0;             // solar_used
            cBalance[72 + h] = 1.0;             // discharge
            cBalance[48 + h] = -1.0;            // charge
            constraints.add(new LinearConstraint(cBalance, Relationship.EQ, demand[h]));

            // 2. Battery transition:
            // h=0: E_0 - charge_0 + discharge_0 = initialE
            // h>0: E_h - E_{h-1} - charge_h + discharge_h = 0
            double[] cBattery = new double[totalVars];
            cBattery[96 + h] = 1.0;             // E_h
            cBattery[48 + h] = -1.0;            // charge_h
            cBattery[72 + h] = 1.0;             // discharge_h
            if (h == 0) {
                constraints.add(new LinearConstraint(cBattery, Relationship.EQ, initialE));
            } else {
                cBattery[96 + h - 1] = -1.0;     // E_{h-1}
                constraints.add(new LinearConstraint(cBattery, Relationship.EQ, 0.0));
            }

            // Bounds as linear constraints
            // grid[h] <= maxGridLimit[h]
            double[] cGridMax = new double[totalVars];
            cGridMax[h] = 1.0;
            constraints.add(new LinearConstraint(cGridMax, Relationship.LEQ, maxGridLimit[h]));

            // solar_used[h] <= effectiveSolar[h]
            double[] cSolarMax = new double[totalVars];
            cSolarMax[24 + h] = 1.0;
            constraints.add(new LinearConstraint(cSolarMax, Relationship.LEQ, effectiveSolar[h]));

            // charge[h] <= maxChargeLimit[h]
            double[] cChargeMax = new double[totalVars];
            cChargeMax[48 + h] = 1.0;
            constraints.add(new LinearConstraint(cChargeMax, Relationship.LEQ, maxChargeLimit[h]));

            // discharge[h] <= maxDischargeLimit[h]
            double[] cDischargeMax = new double[totalVars];
            cDischargeMax[72 + h] = 1.0;
            constraints.add(new LinearConstraint(cDischargeMax, Relationship.LEQ, maxDischargeLimit[h]));

            // battery_energy_after[h] >= minReserve[h]
            double[] cEnergyMin = new double[totalVars];
            cEnergyMin[96 + h] = 1.0;
            constraints.add(new LinearConstraint(cEnergyMin, Relationship.GEQ, minReserve[h]));

            // battery_energy_after[h] <= cap
            double[] cEnergyMax = new double[totalVars];
            cEnergyMax[96 + h] = 1.0;
            constraints.add(new LinearConstraint(cEnergyMax, Relationship.LEQ, cap));
        }

        // 3. End-of-day battery neutrality: E_23 = initialE
        double[] cEod = new double[totalVars];
        cEod[96 + 23] = 1.0;
        constraints.add(new LinearConstraint(cEod, Relationship.EQ, initialE));

        SimplexSolver solver = new SimplexSolver();
        PointValuePair solution = solver.optimize(
                obj,
                new LinearConstraintSet(constraints),
                GoalType.MINIMIZE,
                new NonNegativeConstraint(true)
        );

        double[] x = solution.getPoint();
        List<HourlyPlanEntry> hourlyPlan = new ArrayList<>();

        for (int h = 0; h < N; h++) {
            double gridVal = Math.max(0.0, roundTwoDecimals(x[h]));
            double solarVal = Math.max(0.0, roundTwoDecimals(x[24 + h]));
            double chargeVal = Math.max(0.0, roundTwoDecimals(x[48 + h]));
            double dischargeVal = Math.max(0.0, roundTwoDecimals(x[72 + h]));
            double energyAfterVal = roundTwoDecimals(x[96 + h]);

            BatteryAction action;
            double batteryKwh;
            if (chargeVal > 0.001) {
                action = BatteryAction.charge;
                batteryKwh = chargeVal;
            } else if (dischargeVal > 0.001) {
                action = BatteryAction.discharge;
                batteryKwh = dischargeVal;
            } else {
                action = BatteryAction.idle;
                batteryKwh = 0.0;
            }

            HourlyPlanEntry entry = new HourlyPlanEntry(
                    h,
                    gridVal,
                    solarVal,
                    action,
                    batteryKwh,
                    energyAfterVal
            );
            hourlyPlan.add(entry);
        }

        return hourlyPlan;
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
