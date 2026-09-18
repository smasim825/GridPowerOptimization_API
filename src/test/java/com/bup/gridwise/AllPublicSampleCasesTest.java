package com.bup.gridwise;

import com.bup.gridwise.controller.EnergyOptimizationController;
import com.bup.gridwise.dto.request.ScenarioRequest;
import com.bup.gridwise.dto.response.OptimizationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AllPublicSampleCasesTest {

    @Autowired
    private EnergyOptimizationController controller;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Verify all 10 public sample cases against the Energy Optimization Controller")
    void testAllTenPublicSampleCases() throws Exception {
        String[] possiblePaths = {
                "pdfs/BUP_CSE_FEST_2026_Preli_Public_Sample_Cases.json",
                "../pdfs/BUP_CSE_FEST_2026_Preli_Public_Sample_Cases.json",
                "BUP_CSE_FEST_2026_Preli_Public_Sample_Cases.json"
        };

        File sampleFile = null;
        for (String path : possiblePaths) {
            File f = new File(path);
            if (f.exists()) {
                sampleFile = f;
                break;
            }
        }

        assertNotNull(sampleFile, "Sample cases JSON file should exist");

        JsonNode root = objectMapper.readTree(sampleFile);
        JsonNode cases = root.get("cases");
        assertNotNull(cases, "Cases array must not be null");
        assertTrue(cases.size() >= 10, "Should have 10 sample cases");

        int passedCount = 0;

        for (int i = 0; i < cases.size(); i++) {
            JsonNode caseNode = cases.get(i);
            String scenarioId = caseNode.get("id").asText();
            JsonNode inputNode = caseNode.get("input");
            JsonNode expectedOutputNode = caseNode.get("expected_output");

            ScenarioRequest request = objectMapper.treeToValue(inputNode, ScenarioRequest.class);
            ResponseEntity<OptimizationResponse> responseEntity = controller.optimizeEnergy(request);

            assertEquals(200, responseEntity.getStatusCode().value(), "HTTP status must be 200 for " + scenarioId);
            OptimizationResponse response = responseEntity.getBody();
            assertNotNull(response, "Response body must not be null for " + scenarioId);

            assertEquals(scenarioId, response.getScenario_id(), "Scenario ID must match");
            assertEquals(24, response.getHourly_plan().size(), "Hourly plan must contain exactly 24 entries");
            assertEquals(request.getOperator_notes().size(), response.getDirective_interpretation().size(),
                    "Directive interpretations count must match operator notes count");

            double expectedCost = expectedOutputNode.get("total_cost_bdt").asDouble();
            double actualCost = response.getTotal_cost_bdt();
            double diff = Math.abs(actualCost - expectedCost);

            System.out.printf("[%d/%d] Case %s: actualCost=%.2f, expectedCost=%.2f, diff=%.2f%n",
                    i + 1, cases.size(), scenarioId, actualCost, expectedCost, diff);

            // Verify cost within tolerance (allowing standard numerical tolerance)
            assertTrue(diff <= 5.0, String.format("Case %s cost mismatch: actual=%.2f, expected=%.2f, diff=%.2f",
                    scenarioId, actualCost, expectedCost, diff));

            // Verify energy balance for all 24 hours
            for (int h = 0; h < 24; h++) {
                var planHour = response.getHourly_plan().get(h);
                var reqHour = request.getHours().get(h);
                double grid = planHour.getGrid_kwh();
                double solarUsed = planHour.getSolar_used_kwh();
                double batKwh = planHour.getBattery_kwh();
                double discharge = (planHour.getBattery_action() == com.bup.gridwise.model.BatteryAction.discharge) ? batKwh : 0.0;
                double charge = (planHour.getBattery_action() == com.bup.gridwise.model.BatteryAction.charge) ? batKwh : 0.0;

                double lhs = grid + solarUsed + discharge;
                double rhs = reqHour.getDemand_kwh() + charge;
                assertEquals(rhs, lhs, 0.1, String.format("Case %s Hour %d energy balance mismatch: lhs=%.2f, rhs=%.2f",
                        scenarioId, h, lhs, rhs));
            }

            // Verify battery neutrality at hour 23
            double initialBat = request.getBattery().getInitial_energy_kwh();
            double finalBat = response.getHourly_plan().get(23).getBattery_energy_after_kwh();
            assertEquals(initialBat, finalBat, 0.1, String.format("Case %s End-of-day battery neutrality mismatch: initial=%.2f, final=%.2f",
                    scenarioId, initialBat, finalBat));

            passedCount++;
        }

        assertEquals(cases.size(), passedCount, "All public sample cases must pass verification");
    }
}
