# GridWise LLM Optimization Service
**BUP CSE FEST 2026 — Smart Campus Energy Optimization Challenge**

GridWise is an enterprise-grade, high-performance HTTP API for 24-hour campus energy optimization with LLM-assisted natural language operator directive interpretation.

---

## 🏗 System Architecture & End-to-End Flow

```
+------------------------+      +--------------------------+      +------------------------------+      +---------------------------+      +-----------------------+
|  Scenario JSON Input   | ---> |     LLM Interpreter      | ---> |   Deterministic Guardrails   | ---> |  Exact Simplex LP Solver  | ---> |  Replay & Validation  |
| (Demand, Solar, Tariff,|      | (Google Gemini / Fallback|      |   (Schema, Hours, Bounds,    |      | (Apache Commons Math LP,  |      | (Energy Balance & End-|
| Battery, Notes 1..3)   |      |   Structured Extractor)  |      |   Semantic Normalization)    |      |  Recalculated Grid Cost)  |      | of-Day SoC Neutrality)|
+------------------------+      +--------------------------+      +------------------------------+      +---------------------------+      +-----------------------+
```

1. **LLM Interpreter Layer (`LlmInterpreterService`)**: Extracts machine-checkable structured directives (`solar_reduction`, `minimum_battery_reserve`, `no_charge_window`, `no_discharge_window`, `max_grid_window`, or `no_op`) from free-form natural language notes.
2. **Deterministic Guardrails (`DirectiveGuardrailService`)**: Validates extracted hours (unique ascending 0–23), clamps factors ($0.0 \le \text{factor} \le 1.0$), verifies battery reserve levels $\le \text{capacity}$, enforces `applies=true` for valid directives and `applies=false` + `structured_adjustment=null` for `no_op`.
3. **Exact Mathematical Optimizer (`LpOptimizationService`)**: Formulates the 24-hour cost minimization problem into a Linear Program (LP) solved with the Apache Commons Math Simplex Solver, mathematically guaranteeing the global minimum cost.
4. **Validation & Schedule Replay Engine (`ScheduleValidationService`)**: Replays the 24-hour schedule hour-by-hour to verify energy balance, solar utilization, C-rate charge/discharge limits, and end-of-day battery neutrality ($SoC_{24} = SoC_{0}$).

---

## 🚀 Quickstart & Local Reproduction

### Prerequisites
- **Java 17+** (JDK 17 or Eclipse Temurin 17)
- **Maven 3.8+**
- (Optional) **Docker** & **Python 3** for verification

### 1. Configure Environment Variables
Create or edit your `.env` file in the project root:
```env
PORT=8080
GEMINI_API_KEY=your_gemini_api_key_here
LLM_PROVIDER=gemini
```

### 2. Build & Run Locally
```bash
# Run unit & integration tests (all 10 sample benchmark cases)
mvn test

# Start the Spring Boot Application
mvn spring-boot:run
```

The service will start on `http://localhost:8080`.

---

## 🐳 Docker Build & Run (Fallback Container)

### Build Docker Image:
```bash
docker build -t gridwise-llm-service:latest .
```

### Run Docker Container:
```bash
docker run -d --name gridwise-service \
  -p 8080:8080 \
  -e PORT=8080 \
  -e GEMINI_API_KEY=your_gemini_api_key_here \
  gridwise-llm-service:latest
```

---

## 📡 API Endpoints & Verification

### 1. Health Readiness Endpoint
```bash
curl -X GET http://localhost:8080/health
```
**Response:**
```json
{
  "status": "ok"
}
```

### 2. Energy Optimization Endpoint
```bash
curl -X POST http://localhost:8080/optimize-energy \
  -H "Content-Type: application/json" \
  -d '{
    "scenario_id": "GRID-101",
    "operator_notes": [
      "Solar output will drop to about 20% from 1 PM to 3 PM.",
      "Do not charge the battery between 2 PM and 4 PM.",
      "The cafeteria menu changes tomorrow."
    ],
    "hours": [
      {"hour": 0, "demand_kwh": 180, "solar_kwh": 0, "tariff_bdt_per_kwh": 7.0},
      {"hour": 1, "demand_kwh": 160, "solar_kwh": 0, "tariff_bdt_per_kwh": 7.0},
      {"hour": 2, "demand_kwh": 150, "solar_kwh": 0, "tariff_bdt_per_kwh": 7.0},
      {"hour": 3, "demand_kwh": 140, "solar_kwh": 0, "tariff_bdt_per_kwh": 7.0},
      {"hour": 4, "demand_kwh": 150, "solar_kwh": 0, "tariff_bdt_per_kwh": 7.0},
      {"hour": 5, "demand_kwh": 170, "solar_kwh": 0, "tariff_bdt_per_kwh": 7.0},
      {"hour": 6, "demand_kwh": 220, "solar_kwh": 20, "tariff_bdt_per_kwh": 7.0},
      {"hour": 7, "demand_kwh": 280, "solar_kwh": 60, "tariff_bdt_per_kwh": 7.0},
      {"hour": 8, "demand_kwh": 350, "solar_kwh": 120, "tariff_bdt_per_kwh": 7.0},
      {"hour": 9, "demand_kwh": 420, "solar_kwh": 200, "tariff_bdt_per_kwh": 7.0},
      {"hour": 10, "demand_kwh": 460, "solar_kwh": 260, "tariff_bdt_per_kwh": 7.0},
      {"hour": 11, "demand_kwh": 480, "solar_kwh": 300, "tariff_bdt_per_kwh": 7.0},
      {"hour": 12, "demand_kwh": 490, "solar_kwh": 320, "tariff_bdt_per_kwh": 7.0},
      {"hour": 13, "demand_kwh": 470, "solar_kwh": 300, "tariff_bdt_per_kwh": 7.0},
      {"hour": 14, "demand_kwh": 450, "solar_kwh": 250, "tariff_bdt_per_kwh": 7.0},
      {"hour": 15, "demand_kwh": 420, "solar_kwh": 180, "tariff_bdt_per_kwh": 7.0},
      {"hour": 16, "demand_kwh": 380, "solar_kwh": 100, "tariff_bdt_per_kwh": 7.0},
      {"hour": 17, "demand_kwh": 350, "solar_kwh": 40, "tariff_bdt_per_kwh": 12.0},
      {"hour": 18, "demand_kwh": 400, "solar_kwh": 0, "tariff_bdt_per_kwh": 12.0},
      {"hour": 19, "demand_kwh": 430, "solar_kwh": 0, "tariff_bdt_per_kwh": 12.0},
      {"hour": 20, "demand_kwh": 410, "solar_kwh": 0, "tariff_bdt_per_kwh": 12.0},
      {"hour": 21, "demand_kwh": 360, "solar_kwh": 0, "tariff_bdt_per_kwh": 12.0},
      {"hour": 22, "demand_kwh": 280, "solar_kwh": 0, "tariff_bdt_per_kwh": 12.0},
      {"hour": 23, "demand_kwh": 200, "solar_kwh": 0, "tariff_bdt_per_kwh": 9.0}
    ],
    "battery": {
      "capacity_kwh": 500,
      "initial_energy_kwh": 200,
      "minimum_energy_kwh": 50,
      "max_charge_kwh_per_hour": 100,
      "max_discharge_kwh_per_hour": 100
    }
  }'
```

### 3. Automated Benchmark Verification Script
```bash
python3 test_api.py
```
Validates all 10 public sample cases against the active server with 100% pass verification.

---

## 🛠 Technology Stack & Libraries Credited
- **Framework:** Spring Boot 3.2.3 (Java 17)
- **Mathematical Optimization:** Apache Commons Math (Linear Programming Simplex Solver)
- **JSON Serialization & Validation:** Jackson & Jakarta Validation
- **Containerization:** Eclipse Temurin 17 JRE multi-stage Docker build

---

## 🔒 Security & Secret Handling
- **No Secrets in Repo:** `.env` and all credential files are explicitly ignored in `.gitignore`.
- **Safe Error Handling:** Controlled error handlers prevent leakage of internal stack traces, API keys, or prompt secrets.
