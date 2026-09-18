import json
import urllib.request
import urllib.error
import sys
import time

def run_tests(base_url):
    print(f"Connecting to service at {base_url}...")
    
    # 1. Test /health
    try:
        health_req = urllib.request.Request(f"{base_url}/health", headers={"User-Agent": "Verifier/1.0"})
        with urllib.request.urlopen(health_req, timeout=5) as response:
            status_code = response.getcode()
            body = json.loads(response.read().decode('utf-8'))
            if status_code == 200 and body.get("status") == "ok":
                print("✅ /health check PASSED: status = ok")
            else:
                print(f"❌ /health check FAILED: status_code={status_code}, body={body}")
                return False
    except Exception as e:
        print(f"❌ /health check FAILED with exception: {e}")
        return False

    # 2. Test 10 Public Sample Cases
    sample_file = "BUP_CSE_FEST_2026_Preli_Public_Sample_Cases.json"
    try:
        with open(sample_file, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print(f"❌ Failed to load {sample_file}: {e}")
        return False

    cases = data.get("cases", [])
    print(f"\nEvaluating {len(cases)} public sample cases against POST {base_url}/optimize-energy ...\n")

    passed_count = 0

    for idx, case in enumerate(cases):
        scenario_id = case["input"]["scenario_id"]
        req_payload = json.dumps(case["input"]).encode('utf-8')
        
        req = urllib.request.Request(
            f"{base_url}/optimize-energy",
            data=req_payload,
            headers={"Content-Type": "application/json", "User-Agent": "Verifier/1.0"},
            method="POST"
        )
        
        start_time = time.time()
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                elapsed_ms = (time.time() - start_time) * 1000
                res_code = resp.getcode()
                res_body = json.loads(resp.read().decode('utf-8'))
                
                # Validation checks
                if res_code != 200:
                    print(f"[{idx+1}/{len(cases)}] Case {scenario_id}: ❌ HTTP {res_code}")
                    continue
                    
                calc_cost = res_body.get("total_cost_bdt", -1)
                ref_cost = case["expected_output"]["total_cost_bdt"]
                diff = abs(calc_cost - ref_cost)
                
                directives = res_body.get("directive_interpretation", [])
                hourly_plan = res_body.get("hourly_plan", [])
                
                if len(hourly_plan) != 24:
                    print(f"[{idx+1}/{len(cases)}] Case {scenario_id}: ❌ hourly_plan length != 24 ({len(hourly_plan)})")
                    continue
                    
                if diff <= 1.0: # Allow small numeric tolerance
                    print(f"[{idx+1}/{len(cases)}] Case {scenario_id}: ✅ PASSED in {elapsed_ms:.1f}ms | Calc Cost: {calc_cost:.2f} BDT | Ref Cost: {ref_cost:.2f} BDT (Diff: {diff:.2f})")
                    passed_count += 1
                else:
                    print(f"[{idx+1}/{len(cases)}] Case {scenario_id}: ⚠️ Cost Discrepancy in {elapsed_ms:.1f}ms | Calc Cost: {calc_cost:.2f} BDT | Ref Cost: {ref_cost:.2f} BDT (Diff: {diff:.2f})")
        except Exception as e:
            print(f"[{idx+1}/{len(cases)}] Case {scenario_id}: ❌ Exception: {e}")

    print(f"\n==========================================")
    print(f"SUMMARY: {passed_count}/{len(cases)} public sample cases passed.")
    print(f"==========================================")
    return passed_count == len(cases)

if __name__ == "__main__":
    url = "http://localhost:8080"
    if len(sys.argv) > 2 and sys.argv[1] == "--url":
        url = sys.argv[2]
    run_tests(url)
