package com.bup.gridwise.dto.response;

import com.bup.gridwise.model.BatteryAction;

public class HourlyPlanEntry {
    private int hour;
    private double grid_kwh;
    private double solar_used_kwh;
    private BatteryAction battery_action;
    private double battery_kwh;
    private double battery_energy_after_kwh;

    public HourlyPlanEntry() {}

    public HourlyPlanEntry(int hour, double grid_kwh, double solar_used_kwh, BatteryAction battery_action,
                           double battery_kwh, double battery_energy_after_kwh) {
        this.hour = hour;
        this.grid_kwh = grid_kwh;
        this.solar_used_kwh = solar_used_kwh;
        this.battery_action = battery_action;
        this.battery_kwh = battery_kwh;
        this.battery_energy_after_kwh = battery_energy_after_kwh;
    }

    public int getHour() {
        return hour;
    }

    public void setHour(int hour) {
        this.hour = hour;
    }

    public double getGrid_kwh() {
        return grid_kwh;
    }

    public void setGrid_kwh(double grid_kwh) {
        this.grid_kwh = grid_kwh;
    }

    public double getSolar_used_kwh() {
        return solar_used_kwh;
    }

    public void setSolar_used_kwh(double solar_used_kwh) {
        this.solar_used_kwh = solar_used_kwh;
    }

    public BatteryAction getBattery_action() {
        return battery_action;
    }

    public void setBattery_action(BatteryAction battery_action) {
        this.battery_action = battery_action;
    }

    public double getBattery_kwh() {
        return battery_kwh;
    }

    public void setBattery_kwh(double battery_kwh) {
        this.battery_kwh = battery_kwh;
    }

    public double getBattery_energy_after_kwh() {
        return battery_energy_after_kwh;
    }

    public void setBattery_energy_after_kwh(double battery_energy_after_kwh) {
        this.battery_energy_after_kwh = battery_energy_after_kwh;
    }
}
