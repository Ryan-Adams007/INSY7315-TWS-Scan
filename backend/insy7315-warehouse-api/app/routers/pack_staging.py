# app/routers/pack_staging.py
from fastapi import APIRouter, Depends, HTTPException
from app.db import exec_sp
from app.deps import require_key

router = APIRouter(prefix="/staging", tags=["staging"])

@router.post("/from-pick/{sessionId}")
def stage_from_pick(sessionId: int, _=Depends(require_key)):
    rows = exec_sp("dbo.usp_Pick_StageForPack", [sessionId])
    if not rows:
        raise HTTPException(status_code=400, detail="Stage failed")
    return rows[0]

@router.post("/claim-next")
def claim_next(packedBy: int, packageNumber: str | None = None, _=Depends(require_key)):
    rows = exec_sp("dbo.usp_Pack_ClaimNext", [packedBy, packageNumber])
    if not rows:
        raise HTTPException(status_code=404, detail="No staged picks available")
    return rows[0]

@router.get("/{stagingId}/lines")
def get_lines(stagingId: int, _=Depends(require_key)):
    # ✅ call the correct SP name
    rows = exec_sp("dbo.usp_Pack_GetStagedLines", [stagingId])
    # return a plain array (Android expects a JSON array)
    return rows or []

@router.post("/consume")
def consume(stagingId: int, _=Depends(require_key)):
    rows = exec_sp("dbo.usp_Pack_ConsumeStaging", [stagingId])
    return {"items": rows}

@router.post("/release")
def release(stagingId: int, _=Depends(require_key)):
    rows = exec_sp("dbo.usp_Pack_ReleaseStaging", [stagingId])
    if not rows:
        raise HTTPException(status_code=400, detail="Release failed")
    return rows[0]

@router.get("/health")
def health(_=Depends(require_key)):
    return {"ok": True, "feature": "staging"}