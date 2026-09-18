package com.bup.gridwise.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class HourEntry {
    @NotNull
    @Min(0)
    @Max(23)
    private Integer hour;

    @NotNull
    @Min(0)
    private Double demand_kwh;

    @NotNull
    @Min(0)
    private Double solar_kwh;

    @NotNull
    @Min(0)
    private Double tariff_bdt_per_kwh;

    public HourEntry() {}

    public HourEntry(Integer hour, Double demand_kwh, Double solar_kwh, Double tariff_bdt_per_kwh) {
        this.hour = hour;
        this.demand_kwh = demand_kwh;
        this.solar_kwh = solar_kwh;
        this.tariff_bdt_per_kwh = tariff_bdt_per_kwh;
    }

    public Integer getHour() {
        return hour;
    }

    public void setHour(Integer hour) {
        this.hour = hour;
    }

    public Double getDemand_kwh() {
        return demand_kwh;
    }

    public void setDemand_kwh(Double demand_kwh) {
        this.demand_kwh = demand_kwh;
    }

    public Double getSolar_kwh() {
        return solar_kwh;
    }

    public void setSolar_kwh(Double solar_kwh) {
        this.solar_kwh = solar_kwh;
    }

    public Double getTariff_bdt_per_kwh() {
        return tariff_bdt_per_kwh;
    }

    public void setTariff_bdt_per_kwh(Double tariff_bdt_per_kwh) {
        this.tariff_bdt_per_kwh = tariff_bdt_per_kwh;
    }
}
