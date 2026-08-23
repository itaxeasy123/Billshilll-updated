from __future__ import annotations

import time

from fastapi import APIRouter

router = APIRouter(tags=["health"])


@router.get("/health")
async def health():
    return {"status": "ok", "version": "6.0.0", "timestamp": int(time.time() * 1000)}
