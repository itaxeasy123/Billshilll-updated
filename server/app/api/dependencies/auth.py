"""
Authentication dependency (Phase 6, Priority 6.9) - resolves the authenticated user's id from the
`Authorization: Bearer <token>` header. Every mutating/reading route that touches company data
depends on this (directly or via `require_company_access` in tenant.py); routes that don't (health)
skip it entirely.
"""
from __future__ import annotations

from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.domain.errors import AuthenticationRequired
from app.infrastructure.security.jwt import decode_access_token

_bearer_scheme = HTTPBearer(auto_error=False)


async def get_current_user_id(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer_scheme),
) -> str:
    if credentials is None or not credentials.credentials:
        raise AuthenticationRequired()
    return decode_access_token(credentials.credentials)
