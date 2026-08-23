"""
Authentication routes (Phase 6, Priority 6.9). `/auth/register` exists because this system has no
separate provisioning flow - a user creates their own account, then the first company they sync
auto-grants them OWNER (see `require_company_access`'s bootstrap rule). Passwords are hashed with
bcrypt before ever touching the database; refresh tokens are stored only as a SHA-256 hash.
"""
from __future__ import annotations

import time
import uuid

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.domain.errors import AuthenticationRequired, ValidationError
from app.infrastructure.database.base import get_db
from app.infrastructure.database.models import RefreshToken, User
from app.infrastructure.security.jwt import (
    create_access_token,
    create_refresh_token_plaintext,
    hash_refresh_token,
)
from app.infrastructure.security.passwords import hash_password, verify_password
from app.schemas.auth import AuthTokenResponse, LoginRequest, RefreshRequest

router = APIRouter(prefix="/auth", tags=["auth"])


async def _issue_tokens(db: AsyncSession, user_id: str) -> AuthTokenResponse:
    access_token, expires_in = create_access_token(user_id)
    refresh_token = create_refresh_token_plaintext()
    now = int(time.time())
    from app.config import get_settings

    settings = get_settings()
    db.add(
        RefreshToken(
            id=uuid.uuid4().hex,
            user_id=user_id,
            token_hash=hash_refresh_token(refresh_token),
            expires_at=now + settings.jwt_refresh_token_expire_days * 86400,
            revoked=False,
            created_at=now * 1000,
        )
    )
    return AuthTokenResponse(accessToken=access_token, refreshToken=refresh_token, expiresInSeconds=expires_in)


@router.post("/register", response_model=AuthTokenResponse)
async def register(request: LoginRequest, db: AsyncSession = Depends(get_db)):
    existing = await db.execute(select(User).where(User.email == request.email))
    if existing.scalar_one_or_none() is not None:
        raise ValidationError("An account with this email already exists.")

    user = User(user_id=uuid.uuid4().hex, email=request.email, hashed_password=hash_password(request.password), created_at=int(time.time() * 1000))
    db.add(user)
    await db.flush()
    tokens = await _issue_tokens(db, user.user_id)
    await db.commit()
    return tokens


@router.post("/token", response_model=AuthTokenResponse)
async def login(request: LoginRequest, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(User).where(User.email == request.email))
    user = result.scalar_one_or_none()
    if user is None or not verify_password(request.password, user.hashed_password):
        raise AuthenticationRequired("Invalid email or password.")

    tokens = await _issue_tokens(db, user.user_id)
    await db.commit()
    return tokens


@router.post("/refresh", response_model=AuthTokenResponse)
async def refresh(request: RefreshRequest, db: AsyncSession = Depends(get_db)):
    token_hash = hash_refresh_token(request.refreshToken)
    result = await db.execute(select(RefreshToken).where(RefreshToken.token_hash == token_hash))
    stored = result.scalar_one_or_none()
    if stored is None or stored.revoked or stored.expires_at < int(time.time()):
        raise AuthenticationRequired("Refresh token is invalid, revoked, or expired.")

    # Rotate: revoke the old refresh token, issue a brand new pair.
    stored.revoked = True
    tokens = await _issue_tokens(db, stored.user_id)
    await db.commit()
    return tokens


@router.post("/logout")
async def logout(request: RefreshRequest, db: AsyncSession = Depends(get_db)):
    token_hash = hash_refresh_token(request.refreshToken)
    result = await db.execute(select(RefreshToken).where(RefreshToken.token_hash == token_hash))
    stored = result.scalar_one_or_none()
    if stored is not None:
        stored.revoked = True
        await db.commit()
    return {}
