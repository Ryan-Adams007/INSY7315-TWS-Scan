#app/routers/auth.py

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, EmailStr
from app.db import exec_sp
from app.deps import require_key
from app.security import hash_password, verify_password, create_token

router = APIRouter(prefix="/auth", tags=["auth"])

class RegisterRequest(BaseModel):
    name: str
    email: EmailStr
    password: str

class LoginRequest(BaseModel):
    email: EmailStr
    password: str

@router.post("/register")
def register(req: RegisterRequest, _=Depends(require_key)):
    pw_hash = hash_password(req.password)
    rows = exec_sp("dbo.usp_User_CreateByEmail", [req.name, req.email, pw_hash])
    if not rows:
        raise HTTPException(status_code=400, detail="Registration failed")
    return {"ok": True, "user": rows[0]}

@router.post("/login")
def login(req: LoginRequest, _=Depends(require_key)):
    rows = exec_sp("dbo.usp_User_GetByEmail", [req.email])
    if not rows:
        raise HTTPException(status_code=401, detail="Invalid credentials")

    user = rows[0]
    if not verify_password(req.password, user["PasswordHash"]):
        raise HTTPException(status_code=401, detail="Invalid credentials")

    # Default role if your Users table does not have Role
    role = user.get("Role", "User")

    token = create_token(
        user_id=str(user["UserId"]),
        email=user["Email"],
        name=user["Name"],
        role=role,
    )

    return {
        "ok": True,
        "access_token": token,
        "token_type": "bearer",
        "user": {
            "UserId": user["UserId"],
            "Name": user["Name"],
            "Email": user["Email"],
            "Role": role,
        },
    }


