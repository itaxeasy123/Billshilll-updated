"""
Tenant authorization (Phase 6, Priority 6.7/6.9) - the server NEVER trusts a client-supplied
companyId alone. Every request that names a companyId (path/body) must have the authenticated
user's membership verified against `user_company_roles` here; a mismatch is a 403 TENANT_MISMATCH,
never a silent read/write into another company's data.

Example (exactly the scenario 6.9 describes): token says User -> Company A; request says
companyId = Company B -> 403 TENANT_MISMATCH, not "200 empty result" or "404" (which would leak
whether Company B exists at all).
"""
from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.domain.errors import TenantMismatch
from app.infrastructure.database.models import UserCompanyRole


async def require_company_access(db: AsyncSession, user_id: str, company_id: str) -> str:
    """Returns the user's role for this company, or raises TenantMismatch.

    Bootstrap rule: if NO user anywhere has a membership row for this companyId yet, the first
    caller to sync it is auto-granted OWNER (a brand-new company created on a device that has
    never synced before). Once a company has any owner, every other caller must have an explicit
    membership - a client can never claim a company that already belongs to someone else."""
    result = await db.execute(
        select(UserCompanyRole).where(
            UserCompanyRole.user_id == user_id,
            UserCompanyRole.company_id == company_id,
        )
    )
    membership = result.scalar_one_or_none()
    if membership is not None:
        return membership.role

    any_owner_result = await db.execute(select(UserCompanyRole).where(UserCompanyRole.company_id == company_id))
    if any_owner_result.scalar_one_or_none() is not None:
        raise TenantMismatch()

    new_membership = UserCompanyRole(id=uuid.uuid4().hex, user_id=user_id, company_id=company_id, role="OWNER")
    db.add(new_membership)
    await db.flush()
    return "OWNER"
