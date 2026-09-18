import pulp
from typing import List, Dict, Tuple
from models import OptimizeRequest, DirectiveInterpretation, HourlyPlanEntry

def optimize_schedule(request: OptimizeRequest, directives: List[DirectiveInterpretation]) -> Tuple[List[HourlyPlanEntry], float, float, float]:
    hours_data = request.hours
    battery = request.battery
    
    # 1. Pre-process effective solar and directive constraints
    effective_solar = {h.hour: h.solar_kwh for h in hours_data}
    min_reserves = {h.hour: battery.minimum_energy_kwh for h in hours_data}
    max_grids = {h.hour: None for h in hours_data}
    no_charge_hours = set()
    no_discharge_hours = set()
    
    for d in directives:
        if not d.applies or not d.structured_adjustment:
            continue
            
        adj = d.structured_adjustment
        affected_hours = adj.hours or []
        
        if d.directive_type == "solar_reduction":
            factor = adj.factor if adj.factor is not None else 1.0
            for h in affected_hours:
                effective_solar[h] = effective_solar[h] * factor
        elif d.directive_type == "minimum_battery_reserve":
            min_e = adj.minimum_energy_kwh if adj.minimum_energy_kwh is not None else 0.0
            for h in affected_hours:
                min_reserves[h] = max(min_reserves[h], min_e)
        elif d.directive_type == "no_charge_window":
            for h in affected_hours:
                no_charge_hours.add(h)
        elif d.directive_type == "no_discharge_window":
            for h in affected_hours:
                no_discharge_hours.add(h)
        elif d.directive_type == "max_grid_window":
            max_g = adj.max_grid_kwh if adj.max_grid_kwh is not None else 0.0
            for h in affected_hours:
                if max_grids[h] is None:
                    max_grids[h] = max_g
                else:
                    max_grids[h] = min(max_grids[h], max_g)

    # 2. Setup PuLP Problem
    prob = pulp.LpProblem("GridWise_Optimization", pulp.LpMinimize)
    
    # Decision Variables
    grid_kwh = pulp.LpVariable.dicts("grid_kwh", range(24), lowBound=0)
    solar_used = pulp.LpVariable.dicts("solar_used", range(24), lowBound=0)
    charge = pulp.LpVariable.dicts("charge", range(24), lowBound=0, upBound=battery.max_charge_kwh_per_hour)
    discharge = pulp.LpVariable.dicts("discharge", range(24), lowBound=0, upBound=battery.max_discharge_kwh_per_hour)
    energy_after = pulp.LpVariable.dicts("energy_after", range(24), lowBound=0, upBound=battery.capacity_kwh)
    
    # 3. Constraints
    for i, h_data in enumerate(hours_data):
        h = h_data.hour
        
        # Energy balance: grid + solar_used + discharge = demand + charge
        prob += grid_kwh[h] + solar_used[h] + discharge[h] == h_data.demand_kwh + charge[h], f"EnergyBalance_{h}"
        
        # Solar usage limits
        prob += solar_used[h] <= effective_solar[h], f"SolarLimit_{h}"
        
        # Battery state
        prev_energy = energy_after[h-1] if h > 0 else battery.initial_energy_kwh
        prob += energy_after[h] == prev_energy + charge[h] - discharge[h], f"BatteryState_{h}"
        
        # Minimum reserve
        prob += energy_after[h] >= min_reserves[h], f"MinReserve_{h}"
        
        # Directive explicit constraints
        if h in no_charge_hours:
            prob += charge[h] == 0, f"NoCharge_{h}"
        if h in no_discharge_hours:
            prob += discharge[h] == 0, f"NoDischarge_{h}"
        if max_grids[h] is not None:
            prob += grid_kwh[h] <= max_grids[h], f"MaxGrid_{h}"
            
    # End-of-day neutrality
    prob += energy_after[23] == battery.initial_energy_kwh, "EndDayNeutrality"
    
    # 4. Objective
    prob += pulp.lpSum([grid_kwh[h_data.hour] * h_data.tariff_bdt_per_kwh for h_data in hours_data]), "TotalCost"
    
    # 5. Solve
    prob.solve(pulp.PULP_CBC_CMD(msg=0))
    
    # 6. Extract Results
    hourly_plan = []
    total_grid = 0.0
    total_cost = 0.0
    peak_grid = 0.0
    
    for h_data in hours_data:
        h = h_data.hour
        g_val = grid_kwh[h].varValue or 0.0
        s_val = solar_used[h].varValue or 0.0
        c_val = charge[h].varValue or 0.0
        d_val = discharge[h].varValue or 0.0
        e_val = energy_after[h].varValue or 0.0
        
        # Determine action
        if c_val > 0.01:
            action = "charge"
            bat_kwh = c_val
        elif d_val > 0.01:
            action = "discharge"
            bat_kwh = d_val
        else:
            action = "idle"
            bat_kwh = 0.0
            
        hourly_plan.append(HourlyPlanEntry(
            hour=h,
            grid_kwh=g_val,
            solar_used_kwh=s_val,
            battery_action=action,
            battery_kwh=bat_kwh,
            battery_energy_after_kwh=e_val
        ))
        
        total_grid += g_val
        total_cost += (g_val * h_data.tariff_bdt_per_kwh)
        if g_val > peak_grid:
            peak_grid = g_val
            
    return hourly_plan, total_grid, total_cost, peak_grid
