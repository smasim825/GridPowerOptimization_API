package com.bup.gridwise.service;

import com.bup.gridwise.dto.response.DirectiveInterpretation;
import com.bup.gridwise.model.DirectiveType;
import com.bup.gridwise.model.StructuredAdjustment;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DirectiveGuardrailService {

    public List<DirectiveInterpretation> validateDirectives(List<DirectiveInterpretation> rawDirectives, int expectedNoteCount) {
        if (rawDirectives == null) {
            rawDirectives = new ArrayList<>();
        }

        List<DirectiveInterpretation> validated = new ArrayList<>();
        Set<Integer> seenIndices = new HashSet<>();

        for (int i = 0; i < expectedNoteCount; i++) {
            DirectiveInterpretation matched = null;
            for (DirectiveInterpretation raw : rawDirectives) {
                if (raw != null && raw.getNote_index() == i && !seenIndices.contains(i)) {
                    matched = raw;
                    seenIndices.add(i);
                    break;
                }
            }

            if (matched == null) {
                // Default missing note to no_op
                matched = createNoOpDirective(i, "Defaulted to no_op due to missing or invalid interpretation.");
            } else {
                matched = cleanAndValidateDirective(matched, i);
            }
            validated.add(matched);
        }

        return validated;
    }

    private DirectiveInterpretation cleanAndValidateDirective(DirectiveInterpretation raw, int index) {
        raw.setNote_index(index);
        DirectiveType type = raw.getDirective_type();

        if (type == null || type == DirectiveType.no_op) {
            return createNoOpDirective(index, raw.getExplanation() != null ? raw.getExplanation() : "This note does not affect the energy schedule.");
        }

        StructuredAdjustment adj = raw.getStructured_adjustment();
        if (adj == null || adj.getHours() == null || adj.getHours().isEmpty()) {
            return createNoOpDirective(index, "Invalid structured adjustment hours; defaulted to no_op.");
        }

        // Clean and sort hours
        List<Integer> sortedHours = new ArrayList<>();
        for (Integer h : adj.getHours()) {
            if (h != null && h >= 0 && h <= 23 && !sortedHours.contains(h)) {
                sortedHours.add(h);
            }
        }
        Collections.sort(sortedHours);

        if (sortedHours.isEmpty()) {
            return createNoOpDirective(index, "No valid hours [0..23] specified; defaulted to no_op.");
        }
        adj.setHours(sortedHours);

        // Validate directive-specific fields
        if (type == DirectiveType.solar_reduction) {
            Double factor = adj.getFactor();
            if (factor == null || factor < 0.0 || factor > 1.0) {
                return createNoOpDirective(index, "Invalid solar reduction factor; defaulted to no_op.");
            }
            adj.setMinimum_energy_kwh(null);
            adj.setMax_grid_kwh(null);
        } else if (type == DirectiveType.minimum_battery_reserve) {
            Double minEnergy = adj.getMinimum_energy_kwh();
            if (minEnergy == null || minEnergy < 0.0 || Double.isNaN(minEnergy) || Double.isInfinite(minEnergy)) {
                return createNoOpDirective(index, "Invalid minimum battery reserve value; defaulted to no_op.");
            }
            adj.setFactor(null);
            adj.setMax_grid_kwh(null);
        } else if (type == DirectiveType.no_charge_window || type == DirectiveType.no_discharge_window) {
            adj.setFactor(null);
            adj.setMinimum_energy_kwh(null);
            adj.setMax_grid_kwh(null);
        } else if (type == DirectiveType.max_grid_window) {
            Double maxGrid = adj.getMax_grid_kwh();
            if (maxGrid == null || maxGrid < 0.0 || Double.isNaN(maxGrid) || Double.isInfinite(maxGrid)) {
                return createNoOpDirective(index, "Invalid max grid window limit; defaulted to no_op.");
            }
            adj.setFactor(null);
            adj.setMinimum_energy_kwh(null);
        }

        raw.setApplies(true);
        raw.setDirective_type(type);
        raw.setStructured_adjustment(adj);
        if (raw.getExplanation() == null || raw.getExplanation().isBlank()) {
            raw.setExplanation("Extracted directive: " + type.getValue());
        }

        return raw;
    }

    private DirectiveInterpretation createNoOpDirective(int index, String explanation) {
        DirectiveInterpretation noOp = new DirectiveInterpretation();
        noOp.setNote_index(index);
        noOp.setApplies(false);
        noOp.setDirective_type(DirectiveType.no_op);
        noOp.setStructured_adjustment(null);
        noOp.setExplanation(explanation);
        return noOp;
    }
}
