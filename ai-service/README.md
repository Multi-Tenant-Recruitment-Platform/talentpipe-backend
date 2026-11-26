# TalentPipe AI Service (`ai-service`)

Stateless Python 3.12 + FastAPI microservice powering CV text extraction and Gemini-backed AI screening over TalentPipe's internal Docker network.

## Endpoints

- `GET /health` — Docker readiness check.
- `POST /api/v1/extract` — Accepts `multipart/form-data` (`file` <= 5 MB) for Canva/LaTeX/standard PDFs (`pymupdf4llm`) and `.docx` (`python-docx`) with NFKC ligature repair and PUA icon cleanup.
- `POST /api/v1/screen` — Evaluates candidate CV text against job requirements and optional criterion weights at `temperature=0.0` via `google-genai`.

## Local Development

```bash
cd ai-service
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements-dev.txt
pytest --cov=app
ruff check app tests
uvicorn app.main:app --host 0.0.0.0 --port 8000
```
