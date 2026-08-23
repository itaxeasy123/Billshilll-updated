from __future__ import annotations

from pydantic import BaseModel


class LoginRequest(BaseModel):
    email: str
    password: str


class RefreshRequest(BaseModel):
    refreshToken: str


class AuthTokenResponse(BaseModel):
    accessToken: str
    refreshToken: str
    expiresInSeconds: int
    tokenType: str = "Bearer"
