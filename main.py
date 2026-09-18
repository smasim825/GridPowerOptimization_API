from fastapi import FastAPI, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from models import OptimizeRequest, OptimizeResponse
from llm_interpreter import interpret_operator_notes
from optimizer import optimize_schedule
import json

app = FastAPI(title="GridWise Optimization API")

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    # Differentiate structural JSON issues (400) from semantic validation issues (422)
    errors = exc.errors()
    for error in errors:
        err_type = error.get("type", "")
        loc = error.get("loc", [])
        if "json_invalid" in err_type or "missing" in err_type or ("body" in loc and len(loc) <= 2):
            return JSONResponse(
                status_code=status.HTTP_400_BAD_REQUEST,
                content={"detail": "Malformed JSON or structurally invalid request."}
            )
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={"detail": "Semantically invalid but well-formed request.", "errors": errors}
    )

@app.get("/health")
def health_check():
    return {"status": "ok"}

@app.post("/optimize-energy", response_model=OptimizeResponse)
def optimize_energy(request: OptimizeRequest):
    # Semantic Validation Checks
    hour_indices = [h.hour for h in request.hours]
    if sorted(hour_indices) != list(range(24)):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Semantically invalid request: hours array must contain unique integers 0 through 23."
        )
    if request.battery.capacity_kwh < 0 or request.battery.initial_energy_kwh < 0 or request.battery.minimum_energy_kwh < 0:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Semantically invalid request: battery parameters must be non-negative."
        )

    try:
        # 1. Interpret Notes using LLM with battery context
        directives = interpret_operator_notes(request.operator_notes, request.battery)
        
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
    except HTTPException:
        raise
    except Exception as e:
        # Log stacktrace internally without exposing secrets or raw stack traces to client
        import traceback
        traceback.print_exc()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Controlled internal error. Do not expose secrets or raw stack traces."
        )
