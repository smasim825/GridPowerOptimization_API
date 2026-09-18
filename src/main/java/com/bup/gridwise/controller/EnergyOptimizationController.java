package com.bup.gridwise.controller;

import com.bup.gridwise.dto.request.ScenarioRequest;
import com.bup.gridwise.dto.response.DirectiveInterpretation;
import com.bup.gridwise.dto.response.HourlyPlanEntry;
import com.bup.gridwise.dto.response.OptimizationResponse;
import com.bup.gridwise.service.DirectiveGuardrailService;
import com.bup.gridwise.service.LlmInterpreterService;
import com.bup.gridwise.service.LpOptimizationService;
import com.bup.gridwise.service.ScheduleValidationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EnergyOptimizationController {

    private final LlmInterpreterService llmInterpreterService;
    private final DirectiveGuardrailService directiveGuardrailService;
    private final LpOptimizationService lpOptimizationService;
    private final ScheduleValidationService scheduleValidationService;

    public EnergyOptimizationController(LlmInterpreterService llmInterpreterService,
                                        DirectiveGuardrailService directiveGuardrailService,
                                        LpOptimizationService lpOptimizationService,
                                        ScheduleValidationService scheduleValidationService) {
        this.llmInterpreterService = llmInterpreterService;
        this.directiveGuardrailService = directiveGuardrailService;
        this.lpOptimizationService = lpOptimizationService;
        this.scheduleValidationService = scheduleValidationService;
    }

    @PostMapping("/optimize-energy")
    public ResponseEntity<OptimizationResponse> optimizeEnergy(@Valid @RequestBody ScenarioRequest request) {
        if (request.getHours() == null || request.getHours().size() != 24) {
            throw new IllegalArgumentException("Semantically invalid request: hours list must contain exactly 24 entries.");
        }
        if (request.getBattery() != null && request.getBattery().getCapacity_kwh() < request.getBattery().getMinimum_energy_kwh()) {
            throw new IllegalArgumentException("Semantically invalid request: battery capacity cannot be less than minimum energy reserve.");
        }

        // Step 1: Interpret operator notes via LLM
        List<DirectiveInterpretation> rawDirectives = llmInterpreterService.interpretNotes(request.getOperator_notes());

        // Step 2: Validate directives through deterministic guardrails
        List<DirectiveInterpretation> validatedDirectives = directiveGuardrailService.validateDirectives(
                rawDirectives, request.getOperator_notes().size()
        );

        // Step 3: Solve 24-hour Linear Programming optimization model
        List<HourlyPlanEntry> hourlyPlan = lpOptimizationService.optimizeSchedule(
                request.getHours(), request.getBattery(), validatedDirectives
        );

        // Step 4: Replay schedule, calculate totals, assemble final JSON response
        OptimizationResponse response = scheduleValidationService.buildFinalResponse(
                request.getScenario_id(), validatedDirectives, hourlyPlan, request.getHours()
        );

        return ResponseEntity.ok(response);
    }
}
