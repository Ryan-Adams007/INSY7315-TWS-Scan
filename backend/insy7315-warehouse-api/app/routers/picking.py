# app/routers/picking.py

from datetime import datetime
from typing import Any, List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel

from app.db import exec_sp
from app.deps import require_key

router = APIRouter(prefix="/picking", tags=["picking"])

# ---------- Pydantic models (match Android DTOs) ----------

class PickingSession(BaseModel):
    SessionId: int
    UserId: int
    StartedAt: datetime | str
    Status: str

class ScanItem(BaseModel):
    ScanId: int
    BarcodeOrSerial: str
    Qty: int
    ScannedAt: datetime | str | None = None

class RecentScans(BaseModel):
    items: List[ScanItem]

class CompletePick(BaseModel):
    ok: bool = True
    summary: Optional[List[dict[str, Any]]] = None


# ---------- helpers to normalize DB rows ----------

def _val(row: dict, *names: str, default: Any = None) -> Any:
    """Return first present key from names (case-insensitive)."""
    if row is None:
        return default
    lower = {k.lower(): v for k, v in row.items()}
    for n in names:
        v = lower.get(n.lower())
        if v is not None:
            return v
    return default

def _to_iso(dt: Any) -> Any:
    if isinstance(dt, datetime):
        return dt.isoformat()
    return dt


# ---------- routes ----------

@router.get("/health")
def health():
    return {"ok": True, "feature": "picking"}

@router.post(
    "/start",
    response_model=PickingSession,
    dependencies=[Depends(require_key)],
)
def start_session(userId: int = Query(..., description="Current user ID")):
    rows = exec_sp("dbo.usp_Pick_StartSession", [userId])
    if not rows:
        raise HTTPException(status_code=400, detail="Failed to start session")

    r = rows[0]
    return {
        "SessionId": int(_val(r, "SessionId", "PickSessionId", "Id")),
        "UserId": int(_val(r, "UserId", "UserID", "UID", default=userId)),
        "StartedAt": _to_iso(_val(r, "StartedAt", "CreatedAt", "StartTime")),
        "Status": str(_val(r, "Status", "State", default="Active")),
    }

@router.post(
    "/add-scan",
    response_model=ScanItem,
    dependencies=[Depends(require_key)],
)
def add_scan(
    sessionId: int = Query(...),
    barcodeOrSerial: str = Query(...),
    qty: int = Query(1),
):
    rows = exec_sp("dbo.usp_Pick_AddScan", [sessionId, barcodeOrSerial, qty])
    if not rows:
        raise HTTPException(status_code=400, detail="Add scan failed")

    r = rows[0]
    return {
        "ScanId": int(_val(r, "ScanId", "Id")),
        "BarcodeOrSerial": str(
            _val(r, "BarcodeOrSerial", "Barcode", "Serial", default=barcodeOrSerial)
        ),
        "Qty": int(_val(r, "Qty", "Quantity", "QuantityPicked", default=qty)),
        "ScannedAt": _to_iso(_val(r, "ScannedAt", "CreatedAt", "Timestamp")),
    }

@router.get(
    "/{sessionId}/recent",
    response_model=RecentScans,
    dependencies=[Depends(require_key)],
)
def recent_scans(sessionId: int, top: int = 25):
    rows = exec_sp("dbo.usp_Pick_GetRecentScans", [sessionId, top]) or []
    items: list[ScanItem] = []
    for r in rows:
        items.append(
            ScanItem(
                ScanId=int(_val(r, "ScanId", "Id")),
                BarcodeOrSerial=str(_val(r, "BarcodeOrSerial", "Barcode", "Serial", default="")),
                Qty=int(_val(r, "Qty", "Quantity", "QuantityPicked", default=1)),
                ScannedAt=_to_iso(_val(r, "ScannedAt", "CreatedAt", "Timestamp")),
            )
        )
    return {"items": items}

@router.post(
    "/complete",
    response_model=CompletePick,
    dependencies=[Depends(require_key)],
)
def complete(sessionId: int):
    rows = exec_sp("dbo.usp_Pick_Complete", [sessionId]) or []
    # We pass through the summary so you can render it or hand off to the next stage later.
    return {"ok": True, "summary": rows}