# app/routers/delivery.py
from fastapi import APIRouter, Depends, HTTPException, Query, Body
from typing import Any, Dict, List, Optional

from app.deps import require_key
from app.db import get_conn, exec_sp  # we'll reuse exec_sp and also use get_conn for multi-sets

router = APIRouter(prefix="/delivery", tags=["delivery"])

@router.get("/health")
def health(_=Depends(require_key)):
    return {"ok": True, "feature": "delivery"}

# --- small helper for multi-result-set stored procs (pyodbc) ---
def _exec_sp_multi(sp_name: str, params: list) -> List[List[Dict[str, Any]]]:
    """
    Execute a stored procedure that returns multiple result sets.
    Returns: [ [rows of set 1], [rows of set 2], ... ]
    """
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
            # move to next result set; break when none
            if not cur.nextset():
                break
    return sets

# 1) List packages + chip counts
@router.get("/list")
def list_packages(
    _=Depends(require_key),
    search: Optional[str] = Query(None, description="e.g. 'PKG-10'"),
    status: Optional[str] = Query(None, regex="^(To Load|Loaded)$"),
    top: int = Query(100, ge=1, le=500),
):
    result_sets = _exec_sp_multi("dbo.usp_Delivery_ListPackages", [search, status, top])
    if not result_sets:
        return {"items": [], "counts": {"Total": 0, "ToLoad": 0, "Loaded": 0}}

    items = result_sets[0]
    counts = (result_sets[1][0] if len(result_sets) > 1 and result_sets[1] else
              {"Total": len(items), "ToLoad": None, "Loaded": None})

    return {"items": items, "counts": counts}

# 2) Get single package details (for bottom sheet)
@router.get("/{packageNumber}")
def get_package_details(packageNumber: str, _=Depends(require_key)):
    rows = exec_sp("dbo.usp_Delivery_GetPackageDetails", [packageNumber])
    if not rows:
        raise HTTPException(status_code=404, detail="Package not found")
    return rows[0]

# 3) Mark as Loaded
@router.post("/{packageNumber}/mark-loaded")
def mark_loaded(packageNumber: str, _=Depends(require_key)):
    rows = exec_sp("dbo.usp_Delivery_MarkLoaded", [packageNumber])
    if not rows:
        raise HTTPException(status_code=400, detail="Could not mark loaded")
    return rows[0]

# 4) Revert to 'To Load'
@router.post("/{packageNumber}/mark-to-load")
def mark_to_load(packageNumber: str, _=Depends(require_key)):
    rows = exec_sp("dbo.usp_Delivery_MarkToLoad", [packageNumber])
    if not rows:
        raise HTTPException(status_code=400, detail="Could not revert to 'To Load'")
    return rows[0]

# 5) Quick scan handler → loads immediately
@router.post("/scan-to-load")
def scan_to_load(
    _=Depends(require_key),
    scannedNumber: str = Body(..., embed=True)   # expects {"scannedNumber": "PKG-10023"}
):
    rows = exec_sp("dbo.usp_Delivery_ScanToLoad", [scannedNumber])
    if not rows:
        raise HTTPException(status_code=400, detail="Scan failed")
    return rows[0]