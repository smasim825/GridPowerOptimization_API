package com.bup.gridwise.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class BatteryConfig {
    @NotNull
    @Min(0)
    private Double capacity_kwh;

    @NotNull
    @Min(0)
    private Double initial_energy_kwh;

    @NotNull
    @Min(0)
    private Double minimum_energy_kwh;

    @NotNull
    @Min(0)
    private Double max_charge_kwh_per_hour;

    @NotNull
    @Min(0)
    private Double max_discharge_kwh_per_hour;

    public BatteryConfig() {}

    public BatteryConfig(Double capacity_kwh, Double initial_energy_kwh, Double minimum_energy_kwh,
                         Double max_charge_kwh_per_hour, Double max_discharge_kwh_per_hour) {
        this.capacity_kwh = capacity_kwh;
        this.initial_energy_kwh = initial_energy_kwh;
        this.minimum_energy_kwh = minimum_energy_kwh;
        this.max_charge_kwh_per_hour = max_charge_kwh_per_hour;
        this.max_discharge_kwh_per_hour = max_discharge_kwh_per_hour;
    }

    public Double getCapacity_kwh() {
        return capacity_kwh;
    }

    public void setCapacity_kwh(Double capacity_kwh) {
        this.capacity_kwh = capacity_kwh;
    }

    public Double getInitial_energy_kwh() {
        return initial_energy_kwh;
    }

    public void setInitial_energy_kwh(Double initial_energy_kwh) {
        this.initial_energy_kwh = initial_energy_kwh;
    }

    public Double getMinimum_energy_kwh() {
        return minimum_energy_kwh;
    }

    public void setMinimum_energy_kwh(Double minimum_energy_kwh) {
        this.minimum_energy_kwh = minimum_energy_kwh;
    }

    public Double getMax_charge_kwh_per_hour() {
        return max_charge_kwh_per_hour;
    }

    public void setMax_charge_kwh_per_hour(Double max_charge_kwh_per_hour) {
        this.max_charge_kwh_per_hour = max_charge_kwh_per_hour;
    }

    public Double getMax_discharge_kwh_per_hour() {
        return max_discharge_kwh_per_hour;
    }

    public void setMax_discharge_kwh_per_hour(Double max_discharge_kwh_per_hour) {
        this.max_discharge_kwh_per_hour = max_discharge_kwh_per_hour;
    }
}
