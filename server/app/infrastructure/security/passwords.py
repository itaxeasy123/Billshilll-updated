"""Password hashing (Phase 6, Priority 6.10) - bcrypt directly (not via passlib, whose bcrypt
backend is incompatible with modern bcrypt>=4.1 releases), never plaintext, never a reversible
scheme."""
from __future__ import annotations

import bcrypt


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(password: str, hashed: str) -> bool:
    return bcrypt.checkpw(password.encode("utf-8"), hashed.encode("utf-8"))
