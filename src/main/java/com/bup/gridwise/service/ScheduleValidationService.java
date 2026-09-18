package com.bup.gridwise.service;

import com.bup.gridwise.dto.request.BatteryConfig;
import com.bup.gridwise.dto.request.HourEntry;
import com.bup.gridwise.dto.request.ScenarioRequest;
import com.bup.gridwise.dto.response.DirectiveInterpretation;
import com.bup.gridwise.dto.response.HourlyPlanEntry;
import com.bup.gridwise.dto.response.OptimizationResponse;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ScheduleValidationService {

    /**
     * Validates input scenario request for all structural & business logic edge cases.
     * Throws IllegalArgumentException (handled as HTTP 422) if validation fails.
     */
    public void validateScenarioRequest(ScenarioRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Semantically invalid request: payload cannot be null.");
        }
        if (request.getScenario_id() == null || request.getScenario_id().trim().isEmpty()) {
            throw new IllegalArgumentException("Semantically invalid request: scenario_id cannot be null or empty.");
        }
        if (request.getHours() == null || request.getHours().size() != 24) {
            throw new IllegalArgumentException("Semantically invalid request: hours list must contain exactly 24 entries.");
        }

        // Validate hourly coverage (must contain unique hours 0 through 23)
        Set<Integer> seenHours = new HashSet<>();
        for (HourEntry h : request.getHours()) {
            if (h == null) {
                throw new IllegalArgumentException("Semantically invalid request: hour entry cannot be null.");
            }
            if (h.getHour() == null || h.getHour() < 0 || h.getHour() > 23) {
                throw new IllegalArgumentException("Semantically invalid request: hour index must be between 0 and 23.");
            }
            if (!seenHours.add(h.getHour())) {
                throw new IllegalArgumentException("Semantically invalid request: duplicate hour entry for hour " + h.getHour());
            }
            if (h.getDemand_kwh() == null || h.getDemand_kwh() < 0) {
                throw new IllegalArgumentException("Semantically invalid request: demand_kwh cannot be negative for hour " + h.getHour());
            }
            if (h.getSolar_kwh() == null || h.getSolar_kwh() < 0) {
                throw new IllegalArgumentException("Semantically invalid request: solar_kwh cannot be negative for hour " + h.getHour());
            }
            if (h.getTariff_bdt_per_kwh() == null || h.getTariff_bdt_per_kwh() < 0) {
                throw new IllegalArgumentException("Semantically invalid request: tariff_bdt_per_kwh cannot be negative for hour " + h.getHour());
            }
        }
        if (seenHours.size() != 24) {
            throw new IllegalArgumentException("Semantically invalid request: hours must span all 24 hours (0 to 23).");
        }

        // Validate battery constraints
        BatteryConfig battery = request.getBattery();
        if (battery == null) {
            throw new IllegalArgumentException("Semantically invalid request: battery config cannot be null.");
        }
        if (battery.getCapacity_kwh() == null || battery.getCapacity_kwh() < 0) {
            throw new IllegalArgumentException("Semantically invalid request: battery capacity_kwh cannot be negative.");
        }
        if (battery.getMinimum_energy_kwh() == null || battery.getMinimum_energy_kwh() < 0) {
            throw new IllegalArgumentException("Semantically invalid request: battery minimum_energy_kwh cannot be negative.");
        }
        if (battery.getCapacity_kwh() < battery.getMinimum_energy_kwh()) {
            throw new IllegalArgumentException("Semantically invalid request: battery capacity cannot be less than minimum energy reserve.");
        }
        if (battery.getInitial_energy_kwh() == null || battery.getInitial_energy_kwh() < battery.getMinimum_energy_kwh() || battery.getInitial_energy_kwh() > battery.getCapacity_kwh()) {
            throw new IllegalArgumentException("Semantically invalid request: initial_energy_kwh must be between minimum_energy_kwh and capacity_kwh.");
        }
        if (battery.getMax_charge_kwh_per_hour() == null || battery.getMax_charge_kwh_per_hour() < 0) {
            throw new IllegalArgumentException("Semantically invalid request: max_charge_kwh_per_hour cannot be negative.");
        }
        if (battery.getMax_discharge_kwh_per_hour() == null || battery.getMax_discharge_kwh_per_hour() < 0) {
            throw new IllegalArgumentException("Semantically invalid request: max_discharge_kwh_per_hour cannot be negative.");
        }
    }

    public OptimizationResponse buildFinalResponse(String scenarioId, List<DirectiveInterpretation> directives,
                                                   List<HourlyPlanEntry> hourlyPlan, List<HourEntry> hours) {

        Map<Integer, Double> tariffMap = new HashMap<>();
        for (HourEntry h : hours) {
            tariffMap.put(h.getHour(), h.getTariff_bdt_per_kwh());
        }

        double totalGridKwh = 0.0;
        double totalCostBdt = 0.0;
        double peakGridKwh = 0.0;

        for (HourlyPlanEntry entry : hourlyPlan) {
            double grid = entry.getGrid_kwh();
            double tariff = tariffMap.getOrDefault(entry.getHour(), 0.0);

            totalGridKwh += grid;
            totalCostBdt += (grid * tariff);
            if (grid > peakGridKwh) {
                peakGridKwh = grid;
            }
        }

        totalGridKwh = roundTwoDecimals(totalGridKwh);
        totalCostBdt = roundTwoDecimals(totalCostBdt);
        peakGridKwh = roundTwoDecimals(peakGridKwh);

        String summary = String.format(
                "Optimal 24-hour schedule generated. Total grid electricity purchased: %.2f kWh, Total cost: %.2f BDT, Peak grid demand: %.2f kWh.",
                totalGridKwh, totalCostBdt, peakGridKwh
        );

        return new OptimizationResponse(
                scenarioId,
                directives,
                hourlyPlan,
                totalGridKwh,
                totalCostBdt,
                peakGridKwh,
                summary
        );
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
