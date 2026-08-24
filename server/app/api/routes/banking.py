"""
Business-level routes for Phase 7J-B Bank/UPI Profile ("Cash/Bank" settlement-metadata scope) - thin
pass-throughs to `app/application/services/banking_service.py`, never a raw database mutation.
Direct routes (not the Outbox/SyncEvent path), mirroring the 7D Business-Profile precedent.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.tenant import require_company_access
from app.application.services import banking_service
from app.infrastructure.database.base import get_db
from app.infrastructure.database.models import BankUpiProfile

router = APIRouter(tags=["banking"])


def _bank_upi_profile_dict(p: BankUpiProfile) -> dict:
    return {
        "bankUpiProfileId": p.bank_upi_profile_id, "companyId": p.company_id, "partyId": p.party_id,
        "bankName": p.bank_name, "accountHolderName": p.account_holder_name, "accountNumber": p.account_number,
        "ifscCode": p.ifsc_code, "branchName": p.branch_name, "upiId": p.upi_id,
        "upiPayeeName": p.upi_payee_name, "upiIsVerified": p.upi_is_verified,
        "createdAt": p.created_at, "updatedAt": p.updated_at,
    }


@router.get("/bank-upi-profiles")
async def list_bank_upi_profiles_route(
    companyId: str, partyId: str | None = None,
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    profiles = await banking_service.list_bank_upi_profiles(db, companyId, partyId)
    return [_bank_upi_profile_dict(p) for p in profiles]


@router.get("/bank-upi-profiles/{bank_upi_profile_id}")
async def get_bank_upi_profile_route(
    bank_upi_profile_id: str, companyId: str,
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    profile = await banking_service.get_bank_upi_profile(db, companyId, bank_upi_profile_id)
    if profile is None:
        from app.domain.errors import ValidationError
        raise ValidationError(f"Bank/UPI profile '{bank_upi_profile_id}' was not found.")
    return _bank_upi_profile_dict(profile)


class CreateBankUpiProfileRequest(BaseModel):
    companyId: str
    partyId: str | None = None
    bankName: str = ""
    accountHolderName: str = ""
    accountNumber: str = ""
    ifscCode: str = ""
    branchName: str = ""
    upiId: str | None = None
    upiPayeeName: str = ""
    upiIsVerified: bool = False


@router.post("/bank-upi-profiles")
async def create_bank_upi_profile_route(
    body: CreateBankUpiProfileRequest,
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, body.companyId)
    profile = await banking_service.create_bank_upi_profile(
        db, body.companyId, body.companyId, party_id=body.partyId, bank_name=body.bankName,
        account_holder_name=body.accountHolderName, account_number=body.accountNumber, ifsc_code=body.ifscCode,
        branch_name=body.branchName, upi_id=body.upiId, upi_payee_name=body.upiPayeeName, upi_is_verified=body.upiIsVerified,
    )
    await db.commit()
    return _bank_upi_profile_dict(profile)


class UpdateBankUpiProfileRequest(BaseModel):
    companyId: str
    bankName: str | None = None
    accountHolderName: str | None = None
    accountNumber: str | None = None
    ifscCode: str | None = None
    branchName: str | None = None
    upiId: str | None = None
    upiPayeeName: str | None = None
    upiIsVerified: bool | None = None


@router.put("/bank-upi-profiles/{bank_upi_profile_id}")
async def update_bank_upi_profile_route(
    bank_upi_profile_id: str, body: UpdateBankUpiProfileRequest,
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, body.companyId)
    fields = {
        to_snake: value for to_snake, value in {
            "bank_name": body.bankName, "account_holder_name": body.accountHolderName, "account_number": body.accountNumber,
            "ifsc_code": body.ifscCode, "branch_name": body.branchName, "upi_id": body.upiId,
            "upi_payee_name": body.upiPayeeName, "upi_is_verified": body.upiIsVerified,
        }.items() if value is not None
    }
    profile = await banking_service.update_bank_upi_profile(db, body.companyId, bank_upi_profile_id, **fields)
    await db.commit()
    return _bank_upi_profile_dict(profile)


@router.delete("/bank-upi-profiles/{bank_upi_profile_id}")
async def delete_bank_upi_profile_route(
    bank_upi_profile_id: str, companyId: str,
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    await banking_service.delete_bank_upi_profile(db, companyId, bank_upi_profile_id)
    await db.commit()
    return {"deleted": True}
