"""
Phase 7J-B - Subscription/Entitlements application service. Real persistence for
`CompanySubscription` (Phase 7J domain model, previously unpersisted). Mirrors Android's
`application.subscription.SubscriptionManagementService`: FREE/PAID, one row per company per
financial year, paid validity always derived from the referenced FinancialYear's own stored
`start_date`/`end_date` - **never a hardcoded "1 Apr-31 Mar" literal anywhere in this file**.

Not synced via the Outbox in this phase (a deliberate scope decision mirroring 7D's own
Business-Profile precedent - administrative metadata, no offline-conflict risk, no billing
integration exists yet to make this sync-sensitive). Android creates/renews independently; this
module gives the server its own independent persistence + read/write surface, matching the
"business capability, never raw DB mutation" rule `document_service.py` already established.
"""
from __future__ import annotations

import time
import uuid
from datetime import date

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.domain.errors import SubscriptionAlreadyExists, TenantMismatch, ValidationError
from app.domain.subscription.subscription import EntitlementFeature, has_entitlement
from app.infrastructure.database.models import CompanySubscription, FinancialYear


def _now_ms() -> int:
    return int(time.time() * 1000)


async def _get_financial_year(db: AsyncSession, financial_year_id: str) -> FinancialYear | None:
    result = await db.execute(select(FinancialYear).where(FinancialYear.financial_year_id == financial_year_id))
    return result.scalar_one_or_none()


async def get_current_subscription(db: AsyncSession, company_id: str, financial_year_id: str) -> CompanySubscription | None:
    result = await db.execute(
        select(CompanySubscription).where(
            CompanySubscription.company_id == company_id, CompanySubscription.financial_year_id == financial_year_id
        )
    )
    return result.scalar_one_or_none()


async def create_or_renew_subscription(
    db: AsyncSession, company_id: str, financial_year_id: str, plan_type: str, plan_name: str, entitlements: list[str]
) -> CompanySubscription:
    fy = await _get_financial_year(db, financial_year_id)
    if fy is None:
        raise ValidationError(f"Financial year '{financial_year_id}' was not found.")
    if fy.company_id != company_id:
        raise TenantMismatch(f"Financial year '{financial_year_id}' does not belong to company '{company_id}'.")

    existing = await get_current_subscription(db, company_id, financial_year_id)
    now = _now_ms()
    entitlements_csv = ",".join(entitlements)
    if existing is not None:
        existing.plan_type = plan_type
        existing.plan_name = plan_name
        existing.entitlements_csv = entitlements_csv
        existing.is_active = True
        existing.updated_at = now
        await db.flush()
        return existing

    row = CompanySubscription(
        subscription_id=f"SUB_{uuid.uuid4().hex[:8]}_{company_id}", company_id=company_id, financial_year_id=financial_year_id,
        plan_type=plan_type, plan_name=plan_name, entitlements_csv=entitlements_csv, is_active=True,
        created_at=now, updated_at=now,
    )
    db.add(row)
    try:
        await db.flush()
    except IntegrityError as exc:
        await db.rollback()
        raise SubscriptionAlreadyExists(f"Company '{company_id}' already has a subscription for financial year '{financial_year_id}'.") from exc
    return row


async def check_entitlement(db: AsyncSession, company_id: str, financial_year_id: str, feature: str) -> bool:
    """Resolves the referenced FinancialYear and reuses the existing, frozen `has_entitlement` -
    never a second entitlement rule. `fy.start_date`/`fy.end_date` are always read from the stored
    row, proving validity is never computed from a hardcoded calendar literal."""
    subscription = await get_current_subscription(db, company_id, financial_year_id)
    if subscription is None:
        return False
    fy = await _get_financial_year(db, financial_year_id)
    if fy is None:
        return False
    try:
        fy_start = date.fromisoformat(fy.start_date)
        fy_end = date.fromisoformat(fy.end_date)
    except ValueError:
        return False
    today = date.today()
    if not (fy_start <= today <= fy_end):
        return False

    entitlements = {EntitlementFeature(e) for e in subscription.entitlements_csv.split(",") if e}
    return has_entitlement(subscription.is_active, entitlements, EntitlementFeature(feature))


async def deactivate_subscription(db: AsyncSession, company_id: str, subscription_id: str) -> CompanySubscription:
    result = await db.execute(
        select(CompanySubscription).where(
            CompanySubscription.subscription_id == subscription_id, CompanySubscription.company_id == company_id
        )
    )
    subscription = result.scalar_one_or_none()
    if subscription is None:
        raise ValidationError(f"Subscription '{subscription_id}' was not found.")
    subscription.is_active = False
    subscription.updated_at = _now_ms()
    await db.flush()
    return subscription
