"""
Phase 7J-B - Bank/UPI Profile application service. Real persistence for `BankUpiProfile` (Phase 7G
domain model, previously unpersisted on either platform). Pure settlement/contact metadata,
structurally outside the double-entry stream - nothing in this file touches a Ledger/Voucher/
JournalItem table. Not synced via the Outbox in this phase (mirrors 7D's own Business-Profile scope
decision, same reasoning as `subscription_service.py`).
"""
from __future__ import annotations

import time
import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.domain.errors import TenantMismatch, ValidationError
from app.infrastructure.database.models import BankUpiProfile


def _now_ms() -> int:
    return int(time.time() * 1000)


async def list_bank_upi_profiles(db: AsyncSession, company_id: str, party_id: str | None = None) -> list[BankUpiProfile]:
    query = select(BankUpiProfile).where(BankUpiProfile.company_id == company_id)
    if party_id is not None:
        query = query.where(BankUpiProfile.party_id == party_id)
    result = await db.execute(query)
    return list(result.scalars().all())


async def get_bank_upi_profile(db: AsyncSession, company_id: str, bank_upi_profile_id: str) -> BankUpiProfile | None:
    result = await db.execute(
        select(BankUpiProfile).where(
            BankUpiProfile.bank_upi_profile_id == bank_upi_profile_id, BankUpiProfile.company_id == company_id
        )
    )
    return result.scalar_one_or_none()


async def create_bank_upi_profile(db: AsyncSession, context_company_id: str, company_id: str, **fields) -> BankUpiProfile:
    if context_company_id != company_id:
        raise TenantMismatch(f"Company '{context_company_id}' attempted to create a bank/UPI profile for company '{company_id}'.")
    now = _now_ms()
    row = BankUpiProfile(
        bank_upi_profile_id=f"BUP_{uuid.uuid4().hex[:8]}_{company_id}", company_id=company_id,
        created_at=now, updated_at=now, **fields,
    )
    db.add(row)
    await db.flush()
    return row


async def update_bank_upi_profile(db: AsyncSession, context_company_id: str, bank_upi_profile_id: str, **fields) -> BankUpiProfile:
    existing = await get_bank_upi_profile(db, context_company_id, bank_upi_profile_id)
    if existing is None:
        raise ValidationError(f"Bank/UPI profile '{bank_upi_profile_id}' was not found.")
    for key, value in fields.items():
        setattr(existing, key, value)
    existing.updated_at = _now_ms()
    await db.flush()
    return existing


async def delete_bank_upi_profile(db: AsyncSession, context_company_id: str, bank_upi_profile_id: str) -> None:
    existing = await get_bank_upi_profile(db, context_company_id, bank_upi_profile_id)
    if existing is None:
        raise ValidationError(f"Bank/UPI profile '{bank_upi_profile_id}' was not found.")
    await db.delete(existing)
    await db.flush()
