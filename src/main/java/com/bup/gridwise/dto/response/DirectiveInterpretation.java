package com.bup.gridwise.dto.response;

import com.bup.gridwise.model.DirectiveType;
import com.bup.gridwise.model.StructuredAdjustment;

public class DirectiveInterpretation {
    private int note_index;
    private boolean applies;
    private DirectiveType directive_type;
    private StructuredAdjustment structured_adjustment;
    private String explanation;

    public DirectiveInterpretation() {}

    public DirectiveInterpretation(int note_index, boolean applies, DirectiveType directive_type,
                                   StructuredAdjustment structured_adjustment, String explanation) {
        this.note_index = note_index;
        this.applies = applies;
        this.directive_type = directive_type;
        this.structured_adjustment = structured_adjustment;
        this.explanation = explanation;
    }

    public int getNote_index() {
        return note_index;
    }

    public void setNote_index(int note_index) {
        this.note_index = note_index;
    }

    public boolean isApplies() {
        return applies;
    }

    public void setApplies(boolean applies) {
        this.applies = applies;
    }

    public DirectiveType getDirective_type() {
        return directive_type;
    }

    public void setDirective_type(DirectiveType directive_type) {
        this.directive_type = directive_type;
    }

    public StructuredAdjustment getStructured_adjustment() {
        return structured_adjustment;
    }

    public void setStructured_adjustment(StructuredAdjustment structured_adjustment) {
        this.structured_adjustment = structured_adjustment;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
}
