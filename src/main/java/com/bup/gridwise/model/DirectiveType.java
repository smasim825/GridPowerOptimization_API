package com.bup.gridwise.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DirectiveType {
    solar_reduction("solar_reduction"),
    minimum_battery_reserve("minimum_battery_reserve"),
    no_charge_window("no_charge_window"),
    no_discharge_window("no_discharge_window"),
    max_grid_window("max_grid_window"),
    no_op("no_op");

    private final String value;

    DirectiveType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static DirectiveType fromString(String text) {
        if (text == null) return no_op;
        for (DirectiveType b : DirectiveType.values()) {
            if (b.value.equalsIgnoreCase(text.trim())) {
                return b;
            }
        }
        return no_op;
    }
}
