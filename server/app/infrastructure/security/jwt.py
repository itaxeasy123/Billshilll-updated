"""
Access + refresh token issuance/verification (Phase 6, Priority 6.9). Access tokens are short-lived
and stateless (never stored server-side); refresh tokens are long-lived and stored *hashed* in
`refresh_tokens` so they can be revoked/rotated on logout - never store a refresh token in
plaintext, mirroring the "never store credentials in plaintext" rule from 6.10/6.20.
"""
from __future__ import annotations

import hashlib
import time
import uuid

from jose import JWTError, jwt

from app.config import get_settings

TOKEN_TYPE_ACCESS = "access"
TOKEN_TYPE_REFRESH = "refresh"


def create_access_token(user_id: str) -> tuple[str, int]:
    settings = get_settings()
    expires_in = settings.jwt_access_token_expire_minutes * 60
    payload = {
        "sub": user_id,
        "type": TOKEN_TYPE_ACCESS,
        "iat": int(time.time()),
        "exp": int(time.time()) + expires_in,
    }
    token = jwt.encode(payload, settings.jwt_secret_key, algorithm=settings.jwt_algorithm)
    return token, expires_in


def create_refresh_token_plaintext() -> str:
    """A random opaque string, not a JWT - JWTs are stateless and can't be revoked individually,
    but a refresh token MUST be revocable (logout, compromise). Stored server-side only as a hash."""
    return uuid.uuid4().hex + uuid.uuid4().hex


def hash_refresh_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def decode_access_token(token: str) -> str:
    """Returns the user_id (`sub` claim). Raises AuthenticationRequired on any failure (expired,
    malformed, wrong signature) - the caller never needs to distinguish why, just that the
    request is unauthenticated."""
    from app.domain.errors import AuthenticationRequired

    settings = get_settings()
    try:
        payload = jwt.decode(token, settings.jwt_secret_key, algorithms=[settings.jwt_algorithm])
    except JWTError:
        raise AuthenticationRequired("Access token is invalid or expired.")

    if payload.get("type") != TOKEN_TYPE_ACCESS:
        raise AuthenticationRequired("Token is not an access token.")

    sub = payload.get("sub")
    if not sub:
        raise AuthenticationRequired("Access token is missing a subject.")
    return sub
