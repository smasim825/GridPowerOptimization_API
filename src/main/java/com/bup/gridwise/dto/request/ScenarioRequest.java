package com.bup.gridwise.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class ScenarioRequest {
    @NotNull
    @NotEmpty
    private String scenario_id;

    @NotNull
    @Size(min = 1, max = 3)
    private List<String> operator_notes;

    @NotNull
    @Size(min = 24, max = 24)
    @Valid
    private List<HourEntry> hours;

    @NotNull
    @Valid
    private BatteryConfig battery;

    public ScenarioRequest() {}

    public ScenarioRequest(String scenario_id, List<String> operator_notes, List<HourEntry> hours, BatteryConfig battery) {
        this.scenario_id = scenario_id;
        this.operator_notes = operator_notes;
        this.hours = hours;
        this.battery = battery;
    }

    public String getScenario_id() {
        return scenario_id;
    }

    public void setScenario_id(String scenario_id) {
        this.scenario_id = scenario_id;
    }

    public List<String> getOperator_notes() {
        return operator_notes;
    }

    public void setOperator_notes(List<String> operator_notes) {
        this.operator_notes = operator_notes;
    }

    public List<HourEntry> getHours() {
        return hours;
    }

    public void setHours(List<HourEntry> hours) {
        this.hours = hours;
    }

    public BatteryConfig getBattery() {
        return battery;
    }

    public void setBattery(BatteryConfig battery) {
        this.battery = battery;
    }
}
