package com.bup.gridwise.dto.response;

import java.util.List;

public class OptimizationResponse {
    private String scenario_id;
    private List<DirectiveInterpretation> directive_interpretation;
    private List<HourlyPlanEntry> hourly_plan;
    private double total_grid_kwh;
    private double total_cost_bdt;
    private double peak_grid_kwh;
    private String plan_summary;

    public OptimizationResponse() {}

    public OptimizationResponse(String scenario_id, List<DirectiveInterpretation> directive_interpretation,
                                List<HourlyPlanEntry> hourly_plan, double total_grid_kwh, double total_cost_bdt,
                                double peak_grid_kwh, String plan_summary) {
        this.scenario_id = scenario_id;
        this.directive_interpretation = directive_interpretation;
        this.hourly_plan = hourly_plan;
        this.total_grid_kwh = total_grid_kwh;
        this.total_cost_bdt = total_cost_bdt;
        this.peak_grid_kwh = peak_grid_kwh;
        this.plan_summary = plan_summary;
    }

    public String getScenario_id() {
        return scenario_id;
    }

    public void setScenario_id(String scenario_id) {
        this.scenario_id = scenario_id;
    }

    public List<DirectiveInterpretation> getDirective_interpretation() {
        return directive_interpretation;
    }

    public void setDirective_interpretation(List<DirectiveInterpretation> directive_interpretation) {
        this.directive_interpretation = directive_interpretation;
    }

    public List<HourlyPlanEntry> getHourly_plan() {
        return hourly_plan;
    }

    public void setHourly_plan(List<HourlyPlanEntry> hourly_plan) {
        this.hourly_plan = hourly_plan;
    }

    public double getTotal_grid_kwh() {
        return total_grid_kwh;
    }

    public void setTotal_grid_kwh(double total_grid_kwh) {
        this.total_grid_kwh = total_grid_kwh;
    }

    public double getTotal_cost_bdt() {
        return total_cost_bdt;
    }

    public void setTotal_cost_bdt(double total_cost_bdt) {
        this.total_cost_bdt = total_cost_bdt;
    }

    public double getPeak_grid_kwh() {
        return peak_grid_kwh;
    }

    public void setPeak_grid_kwh(double peak_grid_kwh) {
        this.peak_grid_kwh = peak_grid_kwh;
    }

    public String getPlan_summary() {
        return plan_summary;
    }

    public void setPlan_summary(String plan_summary) {
        this.plan_summary = plan_summary;
    }
}
