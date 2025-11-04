# app/deps.py
import os
from fastapi import HTTPException, Security
from fastapi.security.api_key import APIKeyHeader

API_KEY = os.getenv("API_KEY", "dev-key")
API_KEY_NAME = "X-API-Key"

_api_key_header = APIKeyHeader(name=API_KEY_NAME, auto_error=False)

def require_key(x_api_key: str | None = Security(_api_key_header)):
    if not API_KEY or x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="Unauthorized")