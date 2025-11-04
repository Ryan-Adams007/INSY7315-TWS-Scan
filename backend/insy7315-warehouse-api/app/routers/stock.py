# app/routers/stock.py
from fastapi import APIRouter, Depends, HTTPException, Query, Body
from typing import Any, Dict, List, Optional

from app.deps import require_key
from app.db import exec_sp, get_conn

router = APIRouter(prefix="/stock", tags=["stock"])

@router.get("/health")
def health(_=Depends(require_key)):
    return {"ok": True, "feature": "stock"}

# --- small helper for procs that return multiple result sets ---
def _exec_sp_multi(sp_name: str, params: list) -> List[List[Dict[str, Any]]]:
    sets: List[List[Dict[str, Any]]] = []
    with get_conn() as conn:
        cur = conn.cursor()
        placeholders = ",".join(["?"] * len(params))
        cur.execute(f"EXEC {sp_name} {placeholders}", params)
        while True:
            if cur.description:
                cols = [d[0] for d in cur.description]
                rows = [dict(zip(cols, r)) for r in cur.fetchall()]
                sets.append(rows)
            if not cur.nextset():
                break
    return sets

# 1) Start a stock-take session
@router.post("/start")
def start_session(
    userId: int = Query(..., description="User starting the stock take"),
    name: Optional[str] = Query(None, description="Optional session name"),
    _=Depends(require_key),
):
    rows = exec_sp("dbo.usp_Stock_StartSession", [userId, name])
    if not rows:
        raise HTTPException(status_code=400, detail="Failed to start stock session")
    return rows[0]

# 2) List items within a stock-take (with optional search)
@router.get("/{stockTakeId}/items")
def list_items(
    stockTakeId: int,
    search: Optional[str] = Query(None, description="Filter by SKU or Name"),
    _=Depends(require_key),
):
    rows = exec_sp("dbo.usp_Stock_ListItems", [stockTakeId, search])
    return {"items": rows}

# 3) Add count (scan) to the stock-take
@router.post("/add")
def add_count(
    _=Depends(require_key),
    stockTakeId: int = Query(...),
    barcodeOrSku: str = Query(...),
    qty: int = Query(1, ge=1),
):
    rows = exec_sp("dbo.usp_Stock_AddCount", [stockTakeId, barcodeOrSku, qty])
    if not rows:
        raise HTTPException(status_code=400, detail="Add count failed")
    # returns the updated row for this product
    return rows[0]

# 4) Undo last scan in this stock-take
@router.post("/{stockTakeId}/undo-last")
def undo_last(stockTakeId: int, _=Depends(require_key)):
    rows = exec_sp("dbo.usp_Stock_UndoLast", [stockTakeId])
    if not rows:
        # proc throws when nothing to undo; if caught at DB layer, rows may be empty
        raise HTTPException(status_code=400, detail="Nothing to undo")
    return rows[0]

# 5) Finish / complete the stock-take (returns 3 result sets)
@router.post("/{stockTakeId}/finish")
def finish(stockTakeId: int, _=Depends(require_key)):
    result_sets = _exec_sp_multi("dbo.usp_Stock_Finish", [stockTakeId])
    # Expecting: [header], [totals], [discrepancies]
    header = result_sets[0][0] if len(result_sets) > 0 and result_sets[0] else {}
    totals = result_sets[1][0] if len(result_sets) > 1 and result_sets[1] else {}
    discrepancies = result_sets[2] if len(result_sets) > 2 else []
    return {
        "header": header,
        "totals": totals,
        "discrepancies": discrepancies
    }