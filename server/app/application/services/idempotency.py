"""
Server-side idempotency (Phase 6, Priority 6.5): NEW KEY -> process, store result, return result.
SAME KEY AGAIN -> do not reprocess, return the stored result. Protects against disconnection,
timeout, retry, app restart, duplicate HTTP request, and "server response lost after successful
posting" - exactly the five scenarios 6.5 lists.
"""
from __future__ import annotations

import time
import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.infrastructure.database.models import IdempotencyKeyRecord


async def get_stored_response(db: AsyncSession, company_id: str, idempotency_key: str) -> str | None:
    result = await db.execute(
        select(IdempotencyKeyRecord).where(
            IdempotencyKeyRecord.company_id == company_id,
            IdempotencyKeyRecord.idempotency_key == idempotency_key,
        )
    )
    record = result.scalar_one_or_none()
    return record.response_json if record else None


async def store_response(db: AsyncSession, company_id: str, idempotency_key: str, response_json: str) -> None:
    db.add(
        IdempotencyKeyRecord(
            id=uuid.uuid4().hex,
            company_id=company_id,
            idempotency_key=idempotency_key,
            response_json=response_json,
            created_at=int(time.time() * 1000),
        )
    )
