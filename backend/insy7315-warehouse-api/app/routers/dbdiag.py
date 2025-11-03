# app/routers/dbdiag.py

from fastapi import APIRouter, HTTPException, Depends
from app.deps import require_key
from app.db import get_conn

router = APIRouter(prefix="/diag", tags=["diagnostics"])

@router.get("/db-ping")
def db_ping(_=Depends(require_key)):
    try:
        with get_conn() as c:
            cur = c.cursor()
            cur.execute("SELECT TOP 1 name FROM sys.databases")
            row = cur.fetchone()
            return {"ok": True, "sample_db": row[0] if row else None}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))