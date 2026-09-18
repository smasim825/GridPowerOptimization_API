# GridWise API (BUP CSE Fest 2026)

This is an LLM-Assisted Smart Campus Energy Optimization API.

## Project Structure
- `main.py`: The FastAPI application and endpoints.
- `models.py`: Pydantic models for request/response validation.
- `llm_interpreter.py`: Logic to interpret operator notes using Google Gemini.
- `optimizer.py`: Mathematical optimization logic using PuLP to calculate the best battery schedule.
- `test_api.py`: A test script to verify the API using public cases.

## Setup Instructions for Teammates

1. **Clone the repository**
2. **Create a virtual environment**:
   ```bash
   python -m venv venv
   # On Windows
   .\venv\Scripts\activate
   # On Mac/Linux
   source venv/bin/activate
   ```
3. **Install dependencies**:
   ```bash
   pip install fastapi uvicorn pydantic pulp google-genai python-dotenv requests
   ```
4. **Environment Variables**:
   Create a `.env` file in the root directory and add the Gemini API key:
   ```env
   GEMINI_API_KEY=your_gemini_api_key_here
   ```
5. **Run the server**:
   ```bash
   uvicorn main:app --port 8000
   ```
6. **Test the API**:
   Open a new terminal, activate the environment, and run:
   ```bash
   python test_api.py
   ```
