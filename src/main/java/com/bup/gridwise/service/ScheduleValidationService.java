package com.bup.gridwise.service;

import com.bup.gridwise.dto.request.HourEntry;
import com.bup.gridwise.dto.response.HourlyPlanEntry;
import com.bup.gridwise.dto.response.OptimizationResponse;
import com.bup.gridwise.dto.response.DirectiveInterpretation;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScheduleValidationService {

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
