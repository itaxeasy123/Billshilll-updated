"""
Business-level read resource for Parties (Phase 7A) - GET only, same precedent as `vouchers.py`:
mutation exclusively via the Outbox batch endpoint, never a direct POST here.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.tenant import require_company_access
from app.infrastructure.database.base import get_db
from app.infrastructure.database.models import Party

router = APIRouter(prefix="/parties", tags=["parties"])


def _party_dict(p: Party) -> dict:
    return {
        "partyId": p.party_id,
        "ledgerId": p.ledger_id,
        "role": p.role,
        "entityType": p.entity_type,
        "displayName": p.display_name,
        "contactName": p.contact_name,
        "creditLimitPaise": p.credit_limit_paise,
        "paymentTermsType": p.payment_terms_type,
        "paymentTermsCustomDays": p.payment_terms_custom_days,
        "isActive": p.is_active,
    }


@router.get("")
async def list_parties(
    companyId: str,
    role: str | None = None,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    query = select(Party).where(Party.company_id == companyId, Party.is_active == True)  # noqa: E712
    if role is not None:
        query = query.where(Party.role == role)
    result = await db.execute(query.order_by(Party.display_name))
    return [_party_dict(p) for p in result.scalars().all()]
