import os
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, Optional

from passlib.context import CryptContext
import jwt
from dotenv import load_dotenv

load_dotenv()

# ----- Password hashing (Argon2 via passlib) -----
_pwd_ctx = CryptContext(schemes=["argon2"], deprecated="auto")

def hash_password(password: str) -> str:
    return _pwd_ctx.hash(password)

def verify_password(password: str, password_hash: str) -> bool:
    try:
        return _pwd_ctx.verify(password, password_hash)
    except Exception:
        return False

# ----- JWT settings -----
JWT_SECRET = os.getenv("JWT_SECRET", "dev-secret-change-me")
JWT_ISS = os.getenv("JWT_ISS", "insy7315-warehouse")
JWT_AUD = os.getenv("JWT_AUD", "insy7315-mobile")
JWT_EXPIRE_MIN = int(os.getenv("JWT_EXPIRE_MIN", "1440"))  # minutes (default 24h)

def create_token(
    user_id: str | int,
    email: str,
    name: str,
    role: str = "User",
    expires_minutes: Optional[int] = None,
    extra: Optional[Dict[str, Any]] = None,
) -> str:
    now = datetime.now(tz=timezone.utc)
    exp = now + timedelta(minutes=expires_minutes or JWT_EXPIRE_MIN)

    payload: Dict[str, Any] = {
        "sub": str(user_id),
        "email": email,
        "name": name,
        "role": role,
        "iss": JWT_ISS,
        "aud": JWT_AUD,
        "iat": int(now.timestamp()),
        "exp": int(exp.timestamp()),
    }
    if extra:
        payload.update(extra)

    return jwt.encode(payload, JWT_SECRET, algorithm="HS256")

def decode_token(token: str) -> Dict[str, Any]:
    return jwt.decode(
        token,
        JWT_SECRET,
        algorithms=["HS256"],
        audience=JWT_AUD,
        issuer=JWT_ISS,
    )