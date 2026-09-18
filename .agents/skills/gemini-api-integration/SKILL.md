---
name: gemini-api-integration
description: Google Gemini API integration, prompt engineering, structured JSON extraction, and fallback handling.
---

# Gemini API Integration Skill

## Key Design Patterns
1. **Model Selection**: Use `gemini-2.5-flash` for high-speed, cost-effective structured JSON interpretation.
2. **Structured Outputs**: Pass `response_mime_type: "application/json"` in `generationConfig` to guarantee machine-readable JSON output.
3. **Deterministic Guardrails**: Always pass raw LLM outputs through a strict validation/guardrail layer to catch edge case hallucinations or out-of-range parameters.
4. **Resilient Fallback**: Fallback to rule-based/regex pattern extractors or safe default directives (`no_op`) if API timeouts, network errors, or rate limits occur.
