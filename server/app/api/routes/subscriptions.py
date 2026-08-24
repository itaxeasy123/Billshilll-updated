"""
Business-level routes for Phase 7J-B Subscription/Entitlements - thin pass-throughs to
`app/application/services/subscription_service.py`, never a raw database mutation. Direct routes
(not the Outbox/SyncEvent path), mirroring the 7D Business-Profile precedent - see
`subscription_service.py`'s module docstring for why.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.tenant import require_company_access
from app.application.services import subscription_service
from app.infrastructure.database.base import get_db
from app.infrastructure.database.models import CompanySubscription

router = APIRouter(tags=["subscriptions"])


def _subscription_dict(s: CompanySubscription) -> dict:
    return {
        "subscriptionId": s.subscription_id, "companyId": s.company_id, "financialYearId": s.financial_year_id,
        "planType": s.plan_type, "planName": s.plan_name,
        "entitlements": [e for e in s.entitlements_csv.split(",") if e],
        "isActive": s.is_active, "createdAt": s.created_at, "updatedAt": s.updated_at,
    }


@router.get("/subscriptions/current")
async def get_current_subscription_route(
    companyId: str, financialYearId: str,
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    subscription = await subscription_service.get_current_subscription(db, companyId, financialYearId)
    return _subscription_dict(subscription) if subscription else None


class CreateOrRenewSubscriptionRequest(BaseModel):
    companyId: str
    financialYearId: str
    planType: str
    planName: str
    entitlements: list[str] = []


@router.post("/subscriptions")
async def create_or_renew_subscription_route(
    body: CreateOrRenewSubscriptionRequest,
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, body.companyId)
    subscription = await subscription_service.create_or_renew_subscription(
        db, body.companyId, body.financialYearId, body.planType, body.planName, body.entitlements
    )
    await db.commit()
    return _subscription_dict(subscription)


@router.get("/subscriptions/entitlement")
async def check_entitlement_route(
    companyId: str, financialYearId: str, feature: str,
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    has_it = await subscription_service.check_entitlement(db, companyId, financialYearId, feature)
    return {"hasEntitlement": has_it}


@router.post("/subscriptions/{subscription_id}/deactivate")
async def deactivate_subscription_route(
    subscription_id: str, companyId: str,
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    subscription = await subscription_service.deactivate_subscription(db, companyId, subscription_id)
    await db.commit()
    return _subscription_dict(subscription)
