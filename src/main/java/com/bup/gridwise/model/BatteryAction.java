package com.bup.gridwise.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum BatteryAction {
    charge("charge"),
    discharge("discharge"),
    idle("idle");

    private final String value;

    BatteryAction(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static BatteryAction fromString(String text) {
        if (text == null) return idle;
        for (BatteryAction b : BatteryAction.values()) {
            if (b.value.equalsIgnoreCase(text.trim())) {
                return b;
            }
        }
        return idle;
    }
}
