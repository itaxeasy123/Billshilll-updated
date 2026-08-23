"""
The sole mutation ingestion route (Phase 6, Priority 6.2/6.3/6.13) - Android's Outbox is the only
thing that ever calls this. Each item in the batch is processed in its OWN transaction (one bad/
conflicting event never blocks the rest of the batch), decoded from its `payloadJson` string into
a `SyncEvent`, tenant-checked, then dispatched. `DUPLICATE_VOUCHER`/`DOUBLE_ENTRY_NOT_BALANCED`/etc.
come back as per-item rejections in the response, never as a whole-batch HTTP failure.
"""
from __future__ import annotations

import json
import time

from fastapi import APIRouter, Depends, Header
from pydantic import ValidationError as PydanticValidationError
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.tenant import require_company_access
from app.application.services.outbox_dispatcher import dispatch_event
from app.domain.errors import TenantMismatch
from app.infrastructure.database.base import get_db
from app.schemas.sync import SyncBatchRequest, SyncBatchResponse, SyncEvent, SyncRejectionDto

router = APIRouter(prefix="/sync", tags=["sync"])


@router.post("/outbox/batch", response_model=SyncBatchResponse)
async def sync_outbox_batch(
    request: SyncBatchRequest,
    idempotency_key: str = Header(alias="Idempotency-Key"),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    processed_ids: list[str] = []
    rejections: list[SyncRejectionDto] = []

    try:
        await require_company_access(db, user_id, request.companyId)
        await db.commit()
    except TenantMismatch:
        # The whole batch is for one company - a tenant mismatch rejects every item identically,
        # never a partial "some items belong to you" leak.
        return SyncBatchResponse(
            success=False,
            processedCount=0,
            processedSyncIds=[],
            rejections=[
                SyncRejectionDto(syncId=item.syncId, idempotencyKey=item.idempotencyKey, reason="Company does not belong to this account.", conflictCode="TENANT_MISMATCH")
                for item in request.items
            ],
            serverTimestamp=int(time.time() * 1000),
        )

    for item in request.items:
        try:
            event = SyncEvent.model_validate(json.loads(item.payloadJson))
        except (PydanticValidationError, json.JSONDecodeError) as e:
            rejections.append(SyncRejectionDto(syncId=item.syncId, idempotencyKey=item.idempotencyKey, reason=f"Malformed payload: {e}"))
            continue

        if event.companyId != request.companyId:
            rejections.append(
                SyncRejectionDto(syncId=item.syncId, idempotencyKey=item.idempotencyKey, reason="Event companyId does not match batch companyId.", conflictCode="TENANT_MISMATCH")
            )
            continue

        result = await dispatch_event(db, event)
        if result.success:
            await db.commit()
            processed_ids.append(item.syncId)
        else:
            await db.rollback()
            conflict_code = result.error.code if result.error else None
            rejections.append(
                SyncRejectionDto(
                    syncId=item.syncId, idempotencyKey=item.idempotencyKey,
                    reason=result.error.message if result.error else "Unknown error",
                    conflictCode=conflict_code,
                )
            )

    return SyncBatchResponse(
        success=len(rejections) == 0,
        processedCount=len(processed_ids),
        processedSyncIds=processed_ids,
        rejections=rejections,
        serverTimestamp=int(time.time() * 1000),
    )
