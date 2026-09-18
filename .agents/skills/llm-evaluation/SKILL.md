---
name: llm-evaluation
description: Automated evaluation harness, replay verification, and benchmark validation for LLM energy scheduling.
---

# LLM Evaluation Skill

## Evaluation Workflow
1. **Benchmark Datasets**: Test against standard evaluation datasets (e.g., 10 public sample scenarios).
2. **Deterministic Checking**: Validate that calculated 24-hour total grid electricity cost ($BDT$) matches reference targets within tolerance ($\le 1.0$ BDT).
3. **Energy Balance Verification**: Every single hour must satisfy:
   `grid_kwh + solar_used_kwh + battery_discharge_kwh == demand_kwh + battery_charge_kwh`
4. **Battery Neutrality Verification**: End-of-day state of charge `SoC[23]` must equal `initial_energy_kwh`.
