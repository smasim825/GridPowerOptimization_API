package com.bup.gridwise.service;

import com.bup.gridwise.dto.request.BatteryConfig;
import com.bup.gridwise.dto.request.HourEntry;
import com.bup.gridwise.dto.response.HourlyPlanEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LpOptimizationServiceTest {

    private LpOptimizationService lpOptimizationService;

    @BeforeEach
    void setUp() {
        lpOptimizationService = new LpOptimizationService();
    }

    @Test
    void test24HourScheduleGenerationAndEnergyBalance() {
        List<HourEntry> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hours.add(new HourEntry(h, 200.0, 50.0, 10.0));
        }

        BatteryConfig battery = new BatteryConfig(500.0, 200.0, 50.0, 100.0, 100.0);

        List<HourlyPlanEntry> plan = lpOptimizationService.optimizeSchedule(hours, battery, List.of());

        assertEquals(24, plan.size());

        // Verify hourly balance: grid + solar_used + discharge = demand + charge
        for (int h = 0; h < 24; h++) {
            HourlyPlanEntry entry = plan.get(h);
            double grid = entry.getGrid_kwh();
            double solarUsed = entry.getSolar_used_kwh();
            double batteryKwh = entry.getBattery_kwh();
            double discharge = (entry.getBattery_action() == com.bup.gridwise.model.BatteryAction.discharge) ? batteryKwh : 0.0;
            double charge = (entry.getBattery_action() == com.bup.gridwise.model.BatteryAction.charge) ? batteryKwh : 0.0;

            double lhs = grid + solarUsed + discharge;
            double rhs = 200.0 + charge;

            assertEquals(rhs, lhs, 0.1, String.format("Hour %d balance error: grid=%.2f, solar=%.2f, action=%s, batteryKwh=%.2f",
                    h, grid, solarUsed, entry.getBattery_action(), batteryKwh));
        }

        // Verify End-of-day neutrality: SoC[23] = initial energy
        HourlyPlanEntry finalHour = plan.get(23);
        assertEquals(200.0, finalHour.getBattery_energy_after_kwh(), 0.1, "End of day battery neutrality failed");
    }
}
