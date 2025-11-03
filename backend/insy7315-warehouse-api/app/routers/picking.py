# app/routers/picking.py

from fastapi import APIRouter, HTTPException, Depends
from app.db import exec_sp
from app.deps import require_key

router = APIRouter(prefix="/picking", tags=["picking"])

@router.get("/health")
def health():
    return {"ok": True, "feature": "picking"}

@router.post("/start")
def start_session(userId: int, _=Depends(require_key)):
    rows = exec_sp("dbo.usp_Pick_StartSession", [userId])
    if not rows:
        raise HTTPException(status_code=400, detail="Failed to start session")
    return rows[0]

@router.post("/add-scan")
def add_scan(sessionId: int, barcodeOrSerial: str, qty: int = 1, _=Depends(require_key)):
    rows = exec_sp("dbo.usp_Pick_AddScan", [sessionId, barcodeOrSerial, qty])
    if not rows:
        raise HTTPException(status_code=400, detail="Add scan failed")
    return rows[0]

@router.get("/{sessionId}/recent")
def recent_scans(sessionId: int, top: int = 25, _=Depends(require_key)):
    rows = exec_sp("dbo.usp_Pick_GetRecentScans", [sessionId, top])
    return {"items": rows}

@router.post("/complete")
def complete(sessionId: int, _=Depends(require_key)):
    rows = exec_sp("dbo.usp_Pick_Complete", [sessionId])
    return {"ok": True, "summary": rows}