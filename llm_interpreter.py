import json
import os
from google import genai
from google.genai import types
from pydantic import BaseModel
from models import DirectiveInterpretation
from typing import List
from dotenv import load_dotenv

load_dotenv()

# Initialize Gemini Client
try:
    client = genai.Client()
except Exception as e:
    client = None

class InterpretationsList(BaseModel):
    directives: list[DirectiveInterpretation]

def interpret_operator_notes(notes: List[str]) -> List[DirectiveInterpretation]:
    """
    Given a list of operator notes (1 to 3), interpret each note into a structured directive.
    Returns a list of DirectiveInterpretation matching the order of the notes.
    """
    if not client:
        raise RuntimeError("Gemini Client not initialized. Is GEMINI_API_KEY set?")
        
    prompt = f"""
    You are an AI assistant for a smart campus energy management system.
    You will receive {len(notes)} operator notes. You must return exactly {len(notes)} directives in the exact same order.
    
    Notes:
    {json.dumps([{"note_index": i, "text": note} for i, note in enumerate(notes)], indent=2)}
    
    Rules for directives:
    - directive_type must be one of: "solar_reduction", "minimum_battery_reserve", "no_charge_window", "no_discharge_window", "max_grid_window", "no_op"
    - If a note is irrelevant to energy scheduling, use "no_op" with applies=False and structured_adjustment=None.
    - If applies=True, you must provide the structured_adjustment object.
    - hours array in structured_adjustment must be unique integers from 0 to 23 in ascending order. Time windows include start and exclude end. e.g. 1 PM to 3 PM is [13, 14].
    - For solar_reduction, factor is the usable fraction remaining. 80% reduction means factor=0.2.
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
    
    directives = []
    for d in directives_raw:
        directives.append(DirectiveInterpretation(**d))
        
    return directives
