package com.bup.gridwise.service;

import com.bup.gridwise.dto.response.DirectiveInterpretation;
import com.bup.gridwise.model.DirectiveType;
import com.bup.gridwise.model.StructuredAdjustment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DirectiveGuardrailServiceTest {

    private DirectiveGuardrailService guardrailService;

    @BeforeEach
    void setUp() {
        guardrailService = new DirectiveGuardrailService();
    }

    @Test
    void testValidSolarReductionDirective() {
        DirectiveInterpretation raw = new DirectiveInterpretation(
                0, true, DirectiveType.solar_reduction,
                new StructuredAdjustment(List.of(13, 14), 0.2, null, null),
                "Solar output drops to 20%"
        );

        List<DirectiveInterpretation> result = guardrailService.validateDirectives(List.of(raw), 1);
        assertEquals(1, result.size());
        DirectiveInterpretation validated = result.get(0);

        assertTrue(validated.isApplies());
        assertEquals(DirectiveType.solar_reduction, validated.getDirective_type());
        assertEquals(List.of(13, 14), validated.getStructured_adjustment().getHours());
        assertEquals(0.2, validated.getStructured_adjustment().getFactor());
    }

    @Test
    void testInvalidHoursDefaultToNoOp() {
        DirectiveInterpretation raw = new DirectiveInterpretation(
                0, true, DirectiveType.no_charge_window,
                new StructuredAdjustment(List.of(25, 30), null, null, null),
                "Invalid hours"
        );

        List<DirectiveInterpretation> result = guardrailService.validateDirectives(List.of(raw), 1);
        DirectiveInterpretation validated = result.get(0);

        assertFalse(validated.isApplies());
        assertEquals(DirectiveType.no_op, validated.getDirective_type());
        assertNull(validated.getStructured_adjustment());
    }

    @Test
    void testMissingNoteIndexDefaultedToNoOp() {
        List<DirectiveInterpretation> result = guardrailService.validateDirectives(new ArrayList<>(), 2);
        assertEquals(2, result.size());
        assertFalse(result.get(0).isApplies());
        assertFalse(result.get(1).isApplies());
        assertEquals(DirectiveType.no_op, result.get(0).getDirective_type());
        assertEquals(DirectiveType.no_op, result.get(1).getDirective_type());
    }
}
