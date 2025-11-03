# app/routers/packing.py
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, Query
from app.db import exec_sp
from app.deps import require_key

router = APIRouter(prefix="/packing", tags=["packing"])

@router.get("/health")
def health(_=Depends(require_key)):
    return {"ok": True, "feature": "packing"}

# 1) Start a new package OR set current to an existing one
#    - pass packageNumber to resume an existing package
#    - omit/null to auto-create PKG-000001 style number
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
    packingId: int,
    barcodeOrSerial: str,
    qty: int = 1,
    _=Depends(require_key),
):
    rows = exec_sp("dbo.usp_Pack_AddItem", [packingId, barcodeOrSerial, qty])
    if not rows:
        raise HTTPException(status_code=400, detail="Add item failed")
    return rows[0]

# 3) Get all items currently in a package (for list rendering)
@router.get("/{packingId}/items")
def get_items(
    packingId: int,
    _=Depends(require_key),
):
    rows = exec_sp("dbo.usp_Pack_GetItems", [packingId])
    return {"items": rows}

# 4) Undo the most recent added line
@router.post("/{packingId}/undo-last")
def undo_last(
    packingId: int,
    _=Depends(require_key),
):
    rows = exec_sp("dbo.usp_Pack_UndoLast", [packingId])
    # proc always returns a small payload; surface it as-is
    return rows[0] if rows else {"Removed": 0}

# 5) Clear all items from the package
@router.post("/{packingId}/clear")
def clear_package(
    packingId: int,
    _=Depends(require_key),
):
    rows = exec_sp("dbo.usp_Pack_Clear", [packingId])
    return rows[0] if rows else {"Cleared": 0}

# 6) Seal the package (used by FAB: “Seal and Print”)
@router.post("/{packingId}/seal")
def seal(
    packingId: int,
    _=Depends(require_key),
):
    rows = exec_sp("dbo.usp_Pack_Seal", [packingId])
    if not rows:
        raise HTTPException(status_code=400, detail="Seal failed")
    return rows[0]

# 7) Header summary chip (item lines and total Qty)
@router.get("/{packingId}/summary")
def summary(
    packingId: int,
    _=Depends(require_key),
):
    rows = exec_sp("dbo.usp_Pack_Summary", [packingId])
    return rows[0] if rows else {}