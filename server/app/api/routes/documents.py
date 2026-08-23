"""
Business-level routes for Phase 7D (Document Template & Rendering Architecture) - thin pass-throughs
to `app/application/services/document_service.py`, never a raw database mutation. Mirrors the
`trade_documents.py` precedent (routes for a hypothetical non-Android caller) but does NOT go
through the Outbox/SyncEvent path - see `document_service.py`'s module docstring for why templates/
profiles/assets are not synced in this phase.

`GET /documents/{id}/pdf` and non-posting/draft document rendering are explicit, documented
extension points (Section 29/`docs/43_DOCUMENT_RENDERING.md`) - this server has no GST calculation
engine of its own, and no PDF-generation library, so both routes report a clear
`DOCUMENT_PREVIEW_NOT_AVAILABLE`/`PDF_NOT_AVAILABLE_SERVER_SIDE` error rather than fabricating a
result.
"""
from __future__ import annotations

import json

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.tenant import require_company_access
from app.application.services import document_service
from app.domain.errors import AppError
from app.infrastructure.database.base import get_db

router = APIRouter(tags=["documents"])


class PdfNotAvailableServerSide(AppError):
    code = "PDF_NOT_AVAILABLE_SERVER_SIDE"
    status_code = 501


def _template_dict(t) -> dict:
    return {
        "templateId": t.template_id, "documentType": t.document_type, "templateName": t.template_name,
        "version": t.version, "status": t.status, "isDefault": t.is_default,
        "config": json.loads(t.config_json), "createdAt": t.created_at, "updatedAt": t.updated_at,
    }


def _business_profile_dict(p) -> dict:
    return {
        "businessProfileId": p.business_profile_id, "businessName": p.business_name, "legalName": p.legal_name,
        "address": p.address, "phone": p.phone, "email": p.email, "website": p.website, "gstin": p.gstin, "pan": p.pan,
        "logoAssetId": p.logo_asset_id, "bankName": p.bank_name, "bankAccountNumber": p.bank_account_number,
        "bankIfsc": p.bank_ifsc, "bankBranch": p.bank_branch, "upiId": p.upi_id, "qrCodeAssetId": p.qr_code_asset_id,
        "signatureAssetId": p.signature_asset_id, "termsAndConditions": p.terms_and_conditions,
    }


def _individual_profile_dict(p) -> dict:
    return {
        "individualProfileId": p.individual_profile_id, "name": p.name, "address": p.address, "pan": p.pan,
        "phone": p.phone, "email": p.email, "signatureAssetId": p.signature_asset_id,
        "termsAndConditions": p.terms_and_conditions,
    }


def _asset_dict(a) -> dict:
    return {
        "assetId": a.asset_id, "type": a.type, "storageReference": a.storage_reference,
        "checksum": a.checksum, "mimeType": a.mime_type, "sizeBytes": a.size_bytes,
    }


# ==================== Documents ====================

@router.get("/documents/{document_id}")
async def get_document(
    document_id: str, companyId: str, documentType: str,
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    return await document_service.assemble_document_data(db, companyId, documentType, document_id)


@router.get("/documents/{document_id}/json")
async def get_document_json(
    document_id: str, companyId: str, documentType: str, templateId: str | None = None,
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    data = await document_service.render_document_as_json(db, companyId, documentType, document_id, templateId)
    await db.commit()
    return data


@router.get("/documents/{document_id}/pdf")
async def get_document_pdf(
    document_id: str, companyId: str, documentType: str,
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    raise PdfNotAvailableServerSide(
        "PDF generation is not available server-side in this phase - Android generates PDFs "
        "locally via PdfDocumentRenderer. This is a documented extension point for a future phase."
    )


# ==================== Templates ====================

@router.get("/templates")
async def list_templates(
    companyId: str, documentType: str,
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    templates = await document_service.list_active_templates(db, companyId, documentType)
    return [_template_dict(t) for t in templates]


@router.get("/templates/{template_id}")
async def get_template(
    template_id: str, companyId: str, version: int,
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    template = await document_service.get_template_version(db, companyId, template_id, version)
    if template is None:
        from app.domain.errors import ValidationError
        raise ValidationError(f"Template '{template_id}' version {version} was not found.")
    return _template_dict(template)


class CreateTemplateRequest(BaseModel):
    companyId: str
    documentType: str
    templateName: str
    config: dict = {}
    isDefault: bool = False


@router.post("/templates")
async def create_template(body: CreateTemplateRequest, user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)):
    await require_company_access(db, user_id, body.companyId)
    template = await document_service.create_document_template(
        db, body.companyId, body.documentType, body.templateName, json.dumps(body.config), body.isDefault
    )
    await db.commit()
    return _template_dict(template)


class UpdateTemplateRequest(BaseModel):
    companyId: str
    templateName: str | None = None
    config: dict | None = None


@router.put("/templates/{template_id}")
async def update_template(
    template_id: str, body: UpdateTemplateRequest,
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, body.companyId)
    template = await document_service.update_document_template(
        db, body.companyId, template_id, body.templateName, json.dumps(body.config) if body.config is not None else None
    )
    await db.commit()
    return _template_dict(template)


class SetDefaultTemplateRequest(BaseModel):
    companyId: str
    documentType: str


@router.post("/templates/{template_id}/default")
async def set_default_template(
    template_id: str, body: SetDefaultTemplateRequest,
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, body.companyId)
    template = await document_service.set_default_document_template(db, body.companyId, body.documentType, template_id)
    await db.commit()
    return _template_dict(template)


# ==================== Business / Individual Profiles ====================

@router.get("/business-profile")
async def get_business_profile_route(companyId: str, user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)):
    await require_company_access(db, user_id, companyId)
    profile = await document_service.get_business_profile(db, companyId)
    return _business_profile_dict(profile) if profile else None


class UpsertBusinessProfileRequest(BaseModel):
    companyId: str
    businessName: str
    legalName: str = ""
    address: str = ""
    phone: str = ""
    email: str = ""
    website: str = ""
    gstin: str = ""
    pan: str = ""
    logoAssetId: str | None = None
    bankName: str = ""
    bankAccountNumber: str = ""
    bankIfsc: str = ""
    bankBranch: str = ""
    upiId: str = ""
    qrCodeAssetId: str | None = None
    signatureAssetId: str | None = None
    termsAndConditions: str = ""


@router.put("/business-profile")
async def upsert_business_profile_route(body: UpsertBusinessProfileRequest, user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)):
    await require_company_access(db, user_id, body.companyId)
    profile = await document_service.upsert_business_profile(
        db, body.companyId, body.businessName, legal_name=body.legalName, address=body.address, phone=body.phone,
        email=body.email, website=body.website, gstin=body.gstin, pan=body.pan, logo_asset_id=body.logoAssetId,
        bank_name=body.bankName, bank_account_number=body.bankAccountNumber, bank_ifsc=body.bankIfsc,
        bank_branch=body.bankBranch, upi_id=body.upiId, qr_code_asset_id=body.qrCodeAssetId,
        signature_asset_id=body.signatureAssetId, terms_and_conditions=body.termsAndConditions,
    )
    await db.commit()
    return _business_profile_dict(profile)


@router.get("/individual-profile")
async def get_individual_profile_route(companyId: str, user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)):
    await require_company_access(db, user_id, companyId)
    profile = await document_service.get_individual_profile(db, companyId)
    return _individual_profile_dict(profile) if profile else None


class UpsertIndividualProfileRequest(BaseModel):
    companyId: str
    name: str
    address: str = ""
    pan: str = ""
    phone: str = ""
    email: str = ""
    signatureAssetId: str | None = None
    termsAndConditions: str = ""


@router.put("/individual-profile")
async def upsert_individual_profile_route(body: UpsertIndividualProfileRequest, user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)):
    await require_company_access(db, user_id, body.companyId)
    profile = await document_service.upsert_individual_profile(
        db, body.companyId, body.name, address=body.address, pan=body.pan, phone=body.phone, email=body.email,
        signature_asset_id=body.signatureAssetId, terms_and_conditions=body.termsAndConditions,
    )
    await db.commit()
    return _individual_profile_dict(profile)


# ==================== Document Assets ====================

class CreateDocumentAssetRequest(BaseModel):
    companyId: str
    type: str
    storageReference: str
    checksum: str = ""
    mimeType: str = ""
    sizeBytes: int = 0


@router.post("/document-assets")
async def create_document_asset_route(body: CreateDocumentAssetRequest, user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)):
    await require_company_access(db, user_id, body.companyId)
    asset = await document_service.create_document_asset(
        db, body.companyId, body.type, body.storageReference, body.checksum, body.mimeType, body.sizeBytes
    )
    await db.commit()
    return _asset_dict(asset)


@router.get("/document-assets/{asset_id}")
async def get_document_asset_route(asset_id: str, companyId: str, user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)):
    await require_company_access(db, user_id, companyId)
    asset = await document_service.get_document_asset(db, companyId, asset_id)
    if asset is None:
        from app.domain.errors import ValidationError
        raise ValidationError(f"Document asset '{asset_id}' was not found.")
    return _asset_dict(asset)
