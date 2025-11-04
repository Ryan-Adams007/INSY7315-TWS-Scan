# app/routers/packing.py
from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, Query
from app.db import exec_sp
from app.deps import require_key

router = APIRouter(prefix="/packing", tags=["packing"])


@router.get("/health")
def health(_=Depends(require_key)):
    return {"ok": True, "feature": "packing"}


# 1) Start a new package OR set current to an existing one
@router.post("/start-or-set")
def start_or_set(
    packageNumber: Optional[str] = Query(default=None),
    _=Depends(require_key),
):
    rows = exec_sp("dbo.usp_Pack_StartOrSet", [packageNumber])
    if not rows:
        raise HTTPException(status_code=400, detail="Could not start or set package")
    return rows[0]


# 2) Add item(s) to a package by barcode or serial
@router.post("/add-item")
def add_item(
    packingId: int = Query(...),
    barcodeOrSerial: str = Query(...),
    qty: int = Query(1),
    _=Depends(require_key),
):
    try:
        rows = exec_sp("dbo.usp_Pack_AddItem", [packingId, barcodeOrSerial, qty])
    except Exception as e:
        msg = str(e)
        # Friendly error for unknown scans
        if "52012" in msg or "No product found for barcode/serial" in msg:
            raise HTTPException(status_code=400, detail="Unknown barcode/serial")
        # Bubble up anything else
        raise
    if not rows:
        raise HTTPException(status_code=400, detail="Add item failed")
    return rows[0]


# 3) Get all items currently in a package (path style)
@router.get("/{packingId}/items")
def get_items_path(
    packingId: int,
    _=Depends(require_key),
):
    rows = exec_sp("dbo.usp_Pack_GetItems", [packingId])
    return rows  # plain array


# 3a) Alias for mobile client (query style): /packing/items?packingId=12
@router.get("/items")
def get_items_query(
    packingId: int = Query(..., alias="packingId"),
    _=Depends(require_key),
):
    rows = exec_sp("dbo.usp_Pack_GetItems", [packingId])
    return rows  # plain array


# 4) Undo the most recent added line (path style)
@router.post("/{packingId}/undo-last")
def undo_last_path(
    packingId: int,
    _=Depends(require_key),
):
    rows = exec_sp("dbo.usp_Pack_UndoLast", [packingId])
    return rows[0] if rows else {"Removed": 0}


# 4a) Alias (query style): /packing/undo-last?packingId=12
@router.post("/undo-last")
def undo_last_query(
    packingId: int = Query(...),
    _=Depends(require_key),
):
    rows = exec_sp("dbo.usp_Pack_UndoLast", [packingId])
    return rows[0] if rows else {"Removed": 0}


# 5) Clear all items (path style)
@router.post("/{packingId}/clear")
def clear_package_path(
    packingId: int,
    _=Depends(require_key),
):
    rows = exec_sp("dbo.usp_Pack_Clear", [packingId])
    return rows[0] if rows else {"Cleared": 0}


# 5a) Alias (query style): /packing/clear?packingId=12
@router.post("/clear")
def clear_package_query(
    packingId: int = Query(...),
    _=Depends(require_key),
):
    rows = exec_sp("dbo.usp_Pack_Clear", [packingId])
    return rows[0] if rows else {"Cleared": 0}


# --- NEW: validate packed vs staged before sealing (re-usable helper) ---

def _validate_against_staging(packingId: int) -> List[Dict[str, Any]]:
    """
    Calls dbo.usp_Pack_ValidateAgainstStaging.
    Returns:
      - [] if perfect match (we normalize 'Ok' row to empty for simplicity)
      - list of issue rows with fields:
          Issue ('Missing'|'Over'|'Extra'), ProductId, Sku, Name, Required, Packed, Delta
    """
    rows = exec_sp("dbo.usp_Pack_ValidateAgainstStaging", [packingId]) or []
    if rows and "Ok" in rows[0]:
        # DB returned a single OK row; normalize to empty issues list
        return []
    return rows


# --- NEW: expose a GET endpoint to preview validation issues on the client ---

@router.get("/{packingId}/validate")
def validate_path(
    packingId: int,
    _=Depends(require_key),
):
    issues = _validate_against_staging(packingId)
    if issues:
        return {"ok": False, "issues": issues}
    return {"ok": True, "issues": []}


@router.get("/validate")
def validate_query(
    packingId: int = Query(...),
    _=Depends(require_key),
):
    issues = _validate_against_staging(packingId)
    if issues:
        return {"ok": False, "issues": issues}
    return {"ok": True, "issues": []}


# 6) Seal the package (path style) — now with validation guard
@router.post("/{packingId}/seal")
def seal_path(
    packingId: int,
    _=Depends(require_key),
):
    issues = _validate_against_staging(packingId)
    if issues:
        # 409 Conflict: not ready to seal
        raise HTTPException(
            status_code=409,
            detail={"message": "Staged requirements not satisfied.", "issues": issues},
        )
    rows = exec_sp("dbo.usp_Pack_Seal", [packingId])
    if not rows:
        raise HTTPException(status_code=400, detail="Seal failed")
    return rows[0]


# 6a) Alias (query style): /packing/seal?packingId=12 — also guarded
@router.post("/seal")
def seal_query(
    packingId: int = Query(...),
    _=Depends(require_key),
):
    issues = _validate_against_staging(packingId)
    if issues:
        raise HTTPException(
            status_code=409,
            detail={"message": "Staged requirements not satisfied.", "issues": issues},
        )
    rows = exec_sp("dbo.usp_Pack_Seal", [packingId])
    if not rows:
        raise HTTPException(status_code=400, detail="Seal failed")
    return rows[0]


# 7) Header summary chip
@router.get("/{packingId}/summary")
def summary(
    packingId: int,
    _=Depends(require_key),
):
    rows = exec_sp("dbo.usp_Pack_Summary", [packingId])
    return rows[0] if rows else {}