import json
import os
import sys
import requests

def test_api(base_url="http://localhost:8080"):
    print(f"Testing GridWise Optimization API at {base_url} ...\n")
    
    # 1. Health check
    try:
        health_resp = requests.get(f"{base_url}/health", timeout=5)
        if health_resp.status_code == 200 and health_resp.json().get("status") == "ok":
            print(f"✅ GET {base_url}/health: Status OK")
        else:
            print(f"❌ Health check failed: status {health_resp.status_code}, response: {health_resp.text}")
            return False
    except Exception as e:
        print(f"❌ Could not connect to {base_url}/health: {e}")
        return False

    # 2. Test 10 Public Sample Cases
    sample_files = [
        "pdfs/BUP_CSE_FEST_2026_Preli_Public_Sample_Cases.json",
        "BUP_CSE_FEST_2026_Preli_Public_Sample_Cases.json",
        "../pdfs/BUP_CSE_FEST_2026_Preli_Public_Sample_Cases.json"
    ]
    sample_file = None
    for p in sample_files:
        if os.path.exists(p):
            sample_file = p
            break

    if not sample_file:
        print("❌ Sample cases JSON not found!")
        return False

    with open(sample_file, "r") as f:
        data = json.load(f)

    cases = data.get("cases", [])
    print(f"\nEvaluating {len(cases)} public sample cases against POST {base_url}/optimize-energy ...\n")

    passed = 0
    for idx, case in enumerate(cases):
        scenario_id = case["input"]["scenario_id"]
        resp = requests.post(f"{base_url}/optimize-energy", json=case["input"], timeout=30)
        
        if resp.status_code != 200:
            print(f"[{idx+1}/{len(cases)}] {scenario_id}: ❌ HTTP {resp.status_code} - {resp.text}")
            continue

        result = resp.json()
        calc_cost = result.get("total_cost_bdt", -1)
        ref_cost = case["expected_output"]["total_cost_bdt"]
        diff = abs(calc_cost - ref_cost)

        # Validate hourly plan
        plan = result.get("hourly_plan", [])
        if len(plan) != 24:
            print(f"[{idx+1}/{len(cases)}] {scenario_id}: ❌ Invalid hourly_plan length: {len(plan)}")
            continue

        # Validate energy balance
        balance_ok = True
        for h in range(24):
            req_h = case["input"]["hours"][h]
            res_h = plan[h]
            grid = res_h["grid_kwh"]
            solar = res_h["solar_used_kwh"]
            action = res_h["battery_action"]
            bat_kwh = res_h["battery_kwh"]
            discharge = bat_kwh if action == "discharge" else 0.0
            charge = bat_kwh if action == "charge" else 0.0
            
            lhs = grid + solar + discharge
            rhs = req_h["demand_kwh"] + charge
            if abs(lhs - rhs) > 0.1:
                balance_ok = False
                break

        if not balance_ok:
            print(f"[{idx+1}/{len(cases)}] {scenario_id}: ❌ Energy balance constraint violated in hourly_plan")
            continue

        # Validate battery neutrality
        initial_bat = case["input"]["battery"]["initial_energy_kwh"]
        final_bat = plan[23]["battery_energy_after_kwh"]
        if abs(initial_bat - final_bat) > 0.1:
            print(f"[{idx+1}/{len(cases)}] {scenario_id}: ❌ End-of-day battery neutrality failed (initial={initial_bat}, final={final_bat})")
            continue

        if diff <= 1.0:
            print(f"[{idx+1}/{len(cases)}] {scenario_id}: ✅ PASSED | Cost: {calc_cost:.2f} BDT | Ref: {ref_cost:.2f} BDT (Diff: {diff:.2f})")
            passed += 1
        else:
            print(f"[{idx+1}/{len(cases)}] {scenario_id}: ⚠️ Cost difference | Cost: {calc_cost:.2f} BDT | Ref: {ref_cost:.2f} BDT (Diff: {diff:.2f})")

    print("\n" + "="*45)
    print(f"SUMMARY: {passed}/{len(cases)} sample cases passed successfully.")
    print("="*45)
    return passed == len(cases)

if __name__ == "__main__":
    url = "http://localhost:8080"
    if len(sys.argv) > 1:
        url = sys.argv[1]
    test_api(url)
