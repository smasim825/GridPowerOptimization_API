from fastapi import FastAPI, HTTPException
from models import OptimizeRequest, OptimizeResponse
from llm_interpreter import interpret_operator_notes
from optimizer import optimize_schedule

app = FastAPI(title="GridWise Optimization API")

@app.get("/health")
def health_check():
    return {"status": "ok"}

@app.post("/optimize-energy", response_model=OptimizeResponse)
def optimize_energy(request: OptimizeRequest):
    try:
        # 1. Interpret Notes using LLM
        directives = interpret_operator_notes(request.operator_notes)
        
        # 2. Optimize Schedule using PuLP
        hourly_plan, total_grid, total_cost, peak_grid = optimize_schedule(request, directives)
        
        return OptimizeResponse(
            scenario_id=request.scenario_id,
            directive_interpretation=directives,
            hourly_plan=hourly_plan,
            total_grid_kwh=round(total_grid, 2),
            total_cost_bdt=round(total_cost, 2),
            peak_grid_kwh=round(peak_grid, 2),
            plan_summary="Successfully applied operator notes and optimized the grid cost using PuLP."
        )
    except Exception as e:
        # Log the exception stacktrace internally, return 500
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail="Internal Server Error")
