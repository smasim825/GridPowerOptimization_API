import json
import requests

def test_api():
    # 1. Check health
    health_resp = requests.get("http://localhost:8000/health")
    print("Health Status:", health_resp.json())
    
    # 2. Test public cases
    # We will pick the first public case and send it to the API
    with open("..\\public_cases.json", "r") as f:
        data = json.load(f)
        
    case_1 = data["cases"][0]["input"]
    
    print(f"Testing Scenario ID: {case_1['scenario_id']}")
    print(f"Operator Notes: {case_1['operator_notes']}")
    
    resp = requests.post("http://localhost:8000/optimize-energy", json=case_1)
    
    if resp.status_code == 200:
        result = resp.json()
        print("--- API Response ---")
        print("Scenario ID:", result["scenario_id"])
        print("Directives:", json.dumps(result["directive_interpretation"], indent=2))
        print("Total Cost (BDT):", result["total_cost_bdt"])
        print("Total Grid kWh:", result["total_grid_kwh"])
        print("Peak Grid kWh:", result["peak_grid_kwh"])
        print("Summary:", result["plan_summary"])
    else:
        print("API Error:", resp.status_code)
        print(resp.text)

if __name__ == "__main__":
    test_api()
