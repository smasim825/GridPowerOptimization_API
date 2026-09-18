package com.bup.gridwise.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StructuredAdjustment {
    private List<Integer> hours;
    private Double factor;
    private Double minimum_energy_kwh;
    private Double max_grid_kwh;

    public StructuredAdjustment() {}

    public StructuredAdjustment(List<Integer> hours, Double factor, Double minimum_energy_kwh, Double max_grid_kwh) {
        this.hours = hours;
        this.factor = factor;
        this.minimum_energy_kwh = minimum_energy_kwh;
        this.max_grid_kwh = max_grid_kwh;
    }

    public List<Integer> getHours() {
        return hours;
    }

    public void setHours(List<Integer> hours) {
        this.hours = hours;
    }

    public Double getFactor() {
        return factor;
    }

    public void setFactor(Double factor) {
        this.factor = factor;
    }

    public Double getMinimum_energy_kwh() {
        return minimum_energy_kwh;
    }

    public void setMinimum_energy_kwh(Double minimum_energy_kwh) {
        this.minimum_energy_kwh = minimum_energy_kwh;
    }

    public Double getMax_grid_kwh() {
        return max_grid_kwh;
    }

    public void setMax_grid_kwh(Double max_grid_kwh) {
        this.max_grid_kwh = max_grid_kwh;
    }
}
