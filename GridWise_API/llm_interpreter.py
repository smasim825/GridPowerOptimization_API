import json
import os
import re
from typing import List, Optional
from dotenv import load_dotenv
from models import DirectiveInterpretation, StructuredAdjustment, BatteryLimits
from pydantic import BaseModel

load_dotenv()

# Optional Gemini Client initialization
client = None
try:
    from google import genai
    from google.genai import types
    if os.getenv("GEMINI_API_KEY"):
        client = genai.Client()
except Exception:
    client = None

class InterpretationsList(BaseModel):
    directives: list[DirectiveInterpretation]

def interpret_operator_notes(notes: List[str], battery: Optional[BatteryLimits] = None) -> List[DirectiveInterpretation]:
    """
    Given a list of operator notes (1 to 3), interpret each note into a structured directive.
    Returns a list of DirectiveInterpretation matching the order of the notes.
    """
    if not notes:
        return []

    if client:
        try:
            battery_ctx = ""
            if battery:
                battery_ctx = f"\nBattery Specs: capacity_kwh={battery.capacity_kwh}, initial={battery.initial_energy_kwh}, min={battery.minimum_energy_kwh}, max_charge={battery.max_charge_kwh_per_hour}, max_discharge={battery.max_discharge_kwh_per_hour}\n"

            prompt = f"""
            You are an AI assistant for a smart campus energy management system.
            You will receive {len(notes)} operator notes. You must return exactly {len(notes)} directives in the exact same order.
            {battery_ctx}
            Notes:
            {json.dumps([{"note_index": i, "text": note} for i, note in enumerate(notes)], indent=2)}
            
            Rules for directives:
            - directive_type must be one of: "solar_reduction", "minimum_battery_reserve", "no_charge_window", "no_discharge_window", "max_grid_window", "no_op"
            - If a note is irrelevant to energy scheduling (e.g. cafeteria, library notices, sports registration, meeting room changes), use "no_op" with applies=False and structured_adjustment=None.
            - If applies=True, you must provide the structured_adjustment object.
            - hours array in structured_adjustment must be unique integers from 0 to 23 in ascending order. Time windows include start and exclude end. e.g. noon until 2 PM is [12, 13], 1 PM to 3 PM is [13, 14], 6 PM to 9 PM is [18, 19, 20], 6 PM to 10 PM is [18, 19, 20, 21].
            - For solar_reduction, factor is the usable fraction remaining. 80% reduction means factor=0.2, roughly 25% means factor=0.25, half means factor=0.5.
            - For minimum_battery_reserve, if specified as a percentage (e.g. 50% of battery capacity), calculate minimum_energy_kwh = percentage * battery.capacity_kwh.
            """

            response = client.models.generate_content(
                model='gemini-2.5-flash',
                contents=prompt,
                config=types.GenerateContentConfig(
                    response_mime_type="application/json",
                    response_schema=InterpretationsList,
                    temperature=0.0
                ),
            )
            data = json.loads(response.text)
            directives_raw = data.get("directives", [])
            directives = [DirectiveInterpretation(**d) for d in directives_raw]
            if len(directives) == len(notes):
                return validate_and_sanitize_directives(directives, len(notes))
        except Exception as e:
            print(f"Gemini API call failed ({e}), falling back to deterministic extractor.")

    return fallback_interpret_operator_notes(notes, battery)

def validate_and_sanitize_directives(directives: List[DirectiveInterpretation], expected_count: int) -> List[DirectiveInterpretation]:
    sanitized = []
    for i in range(expected_count):
        d = directives[i] if i < len(directives) else None
        if not d or d.directive_type == "no_op" or not d.applies or not d.structured_adjustment:
            sanitized.append(DirectiveInterpretation(
                note_index=i,
                applies=False,
                directive_type="no_op",
                structured_adjustment=None,
                explanation=d.explanation if (d and d.explanation) else "This note does not affect today's energy schedule."
            ))
        else:
            adj = d.structured_adjustment
            hours = sorted(list(set([h for h in (adj.hours or []) if 0 <= h <= 23])))
            if not hours:
                sanitized.append(DirectiveInterpretation(
                    note_index=i, applies=False, directive_type="no_op", structured_adjustment=None, explanation="Invalid hours; defaulted to no_op."
                ))
                continue
            
            clean_adj = StructuredAdjustment(
                hours=hours,
                factor=adj.factor if d.directive_type == "solar_reduction" else None,
                minimum_energy_kwh=adj.minimum_energy_kwh if d.directive_type == "minimum_battery_reserve" else None,
                max_grid_kwh=adj.max_grid_kwh if d.directive_type == "max_grid_window" else None
            )
            sanitized.append(DirectiveInterpretation(
                note_index=i,
                applies=True,
                directive_type=d.directive_type,
                structured_adjustment=clean_adj,
                explanation=d.explanation or f"Extracted {d.directive_type}."
            ))
    return sanitized

def parse_hours(text: str) -> List[int]:
    normalized = text.lower().replace("noon", "12 pm").replace("midnight", "12 am")
    hrs = []
    
    # Check 24-hour style "14:00 to 16:00"
    m24 = re.search(r'(?:from|between)?\s*(\d{1,2}):00\s*(?:until|to|-)\s*(\d{1,2}):00', normalized)
    if m24:
        start, end = int(m24.group(1)), int(m24.group(2))
        return [h for h in range(start, end) if 0 <= h <= 23]
        
    # Check 12-hour style "noon until 2 PM", "6 PM until 9 PM", "11 AM and 2 PM"
    m12 = re.search(r'(?:from|between)?\s*(\d{1,2})(?::\d{2})?\s*(am|pm)?\s*(?:until|to|and|-)\s*(\d{1,2})(?::\d{2})?\s*(am|pm)?', normalized)
    if m12:
        start, s_ampm, end, e_ampm = int(m12.group(1)), m12.group(2), int(m12.group(3)), m12.group(4)
        if not e_ampm:
            e_ampm = s_ampm
        if e_ampm == "pm" and end < 12:
            end += 12
        elif e_ampm == "am" and end == 12:
            end = 0
            
        if s_ampm == "pm" and start < 12:
            start += 12
        elif s_ampm == "am" and start == 12:
            start = 0
            
        if not s_ampm and e_ampm == "pm" and start < 12 and start <= (end - 12):
            start += 12
            
        return [h for h in range(start, end) if 0 <= h <= 23]
        
    return hrs

def parse_factor(text: str) -> float:
    lower = text.lower()
    if "half" in lower:
        return 0.5
    if "one-fifth" in lower:
        return 0.2
    m_pct = re.search(r'(\d+)%', lower)
    if m_pct:
        val = float(m_pct.group(1))
        if any(w in lower for w in ["reduction", "reduced", "cut", "drop by", "fall by"]):
            return max(0.0, round((100.0 - val) / 100.0, 4))
        else:
            return round(val / 100.0, 4)
    return 0.2

def parse_reserve(text: str, battery: Optional[BatteryLimits]) -> float:
    lower = text.lower()
    m_pct = re.search(r'(\d+)%\s*(?:of\s*(?:the)?\s*battery\s*capacity)?', lower)
    if m_pct:
        pct = float(m_pct.group(1)) / 100.0
        cap = battery.capacity_kwh if (battery and battery.capacity_kwh > 0) else 200.0
        return round(pct * cap, 2)
    m_kwh = re.search(r'(?:at least|minimum|keep|requires|hold|reserve)\s+(\d+(?:\.\d+)?)\s*(?:kwh)?', lower)
    if m_kwh:
        return float(m_kwh.group(1))
    return 100.0

def parse_max_grid(text: str) -> float:
    lower = text.lower()
    m = re.search(r'(?:exceed|below|intake|limit is|at or below|stay at or below|cap of|cap at)\s+(\d+(?:\.\d+)?)\s*(?:kwh)?', lower)
    if m:
        return float(m.group(1))
    return 150.0

def is_distractor(text: str) -> bool:
    lower = text.lower()
    energy_keywords = [
        "solar", "pv", "panel", "battery", "reserve", "charg", "discharg",
        "grid", "kwh", "transformer", "substation", "feeder", "inverter"
    ]
    if any(kw in lower for kw in energy_keywords):
        return False
    return True

def fallback_interpret_operator_notes(notes: List[str], battery: Optional[BatteryLimits] = None) -> List[DirectiveInterpretation]:
    directives = []
    for i, note in enumerate(notes):
        lower = note.lower()
        if is_distractor(lower):
            directives.append(DirectiveInterpretation(
                note_index=i, applies=False, directive_type="no_op", structured_adjustment=None, explanation="This note does not affect today's energy schedule."
            ))
        elif any(k in lower for k in ["solar", "pv", "panel", "sun"]):
            hrs = parse_hours(lower)
            if hrs:
                factor = parse_factor(lower)
                directives.append(DirectiveInterpretation(
                    note_index=i, applies=True, directive_type="solar_reduction",
                    structured_adjustment=StructuredAdjustment(hours=hrs, factor=factor),
                    explanation="Solar reduction applied."
                ))
            else:
                directives.append(DirectiveInterpretation(note_index=i, applies=False, directive_type="no_op", structured_adjustment=None, explanation="Default no_op."))
        elif any(k in lower for k in ["reserve", "keep at least", "minimum battery", "remain in the battery", "hold at least", "capacity stored", "stored in the battery"]):
            hrs = parse_hours(lower)
            req_min = parse_reserve(lower, battery)
            if hrs:
                directives.append(DirectiveInterpretation(
                    note_index=i, applies=True, directive_type="minimum_battery_reserve",
                    structured_adjustment=StructuredAdjustment(hours=hrs, minimum_energy_kwh=req_min),
                    explanation="Minimum battery reserve applied."
                ))
            else:
                directives.append(DirectiveInterpretation(note_index=i, applies=False, directive_type="no_op", structured_adjustment=None, explanation="Default no_op."))
        elif any(k in lower for k in ["do not charge", "no charge", "charger will be isolated", "charging circuit", "charging is disabled", "disallow charge", "stop charging", "isolated", "charging is unavailable", "charger is unavailable", "unavailable"]) and not any(d in lower for d in ["discharge", "discharging"]):
            hrs = parse_hours(lower)
            if hrs:
                directives.append(DirectiveInterpretation(
                    note_index=i, applies=True, directive_type="no_charge_window",
                    structured_adjustment=StructuredAdjustment(hours=hrs),
                    explanation="No charging window applied."
                ))
            else:
                directives.append(DirectiveInterpretation(note_index=i, applies=False, directive_type="no_op", structured_adjustment=None, explanation="Default no_op."))
        elif any(k in lower for k in ["do not discharge", "no discharge", "must not discharge", "stop discharging", "discharging is prohibited", "discharging is disabled", "disallow discharge"]):
            hrs = parse_hours(lower)
            if hrs:
                directives.append(DirectiveInterpretation(
                    note_index=i, applies=True, directive_type="no_discharge_window",
                    structured_adjustment=StructuredAdjustment(hours=hrs),
                    explanation="No discharging window applied."
                ))
            else:
                directives.append(DirectiveInterpretation(note_index=i, applies=False, directive_type="no_op", structured_adjustment=None, explanation="Default no_op."))
        elif any(k in lower for k in ["grid import", "grid intake", "max grid", "grid limit", "grid cap", "transformer limit", "substation", "feeder", "must not exceed", "at or below", "stay at or below", "cap grid"]):
            hrs = parse_hours(lower)
            max_g = parse_max_grid(lower)
            if hrs:
                directives.append(DirectiveInterpretation(
                    note_index=i, applies=True, directive_type="max_grid_window",
                    structured_adjustment=StructuredAdjustment(hours=hrs, max_grid_kwh=max_g),
                    explanation="Max grid window cap applied."
                ))
            else:
                directives.append(DirectiveInterpretation(note_index=i, applies=False, directive_type="no_op", structured_adjustment=None, explanation="Default no_op."))
        else:
            directives.append(DirectiveInterpretation(
                note_index=i, applies=False, directive_type="no_op", structured_adjustment=None, explanation="This note does not affect today's energy schedule."
            ))
    return directives
