"""
Phase 7D - Document Template & Rendering Architecture (application service layer).

Mirrors Android's `AccountingRepository` Phase 7D section. Templates/Business-Individual Profiles/
Document Assets are NOT synced via the Outbox in this phase (a documented scope cut - see
`docs/42_DOCUMENT_TEMPLATE_ARCHITECTURE.md`): Android creates/edits them locally; this module gives
the server its own independent persistence + read/write surface for a future non-Android caller and
for company-isolation testing at the API boundary, matching the "business capability, never raw DB
mutation" rule already established in `trade_document_commands.py`/`party_commands.py`.

IMPORTANT BOUNDARY (Section 29: "do not duplicate Android accounting calculations in Python"):
unlike Android, this server has no GST calculation engine of its own - GST math is Android-only,
the server only ever receives and stores already-computed cgst/sgst/igst/cess figures via the
Outbox (see `voucher_commands.py`, which only ever assigns `gst_line.cgstPaise` etc. straight
through, never computes them). `assemble_document_data` therefore only supports POSTED invoices
(whose tax figures already exist as `GstTransaction` rows) - a draft Invoice or any non-posting
TradeDocument raises `DocumentPreviewNotAvailable` rather than inventing/duplicating GST math
server-side. See `docs/43_DOCUMENT_RENDERING.md`.
"""
from __future__ import annotations

import time
import uuid

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.domain.errors import AppError, ValidationError
from app.infrastructure.database.models import (
    BusinessProfile,
    Company,
    DocumentAsset,
    DocumentTemplate,
    GstTransaction,
    IndividualProfile,
    Invoice,
    InvoiceLine,
    Ledger,
    Party,
    RenderedDocumentRecord,
    TradeDocument,
    Voucher,
)

_POSTING_DOCUMENT_TYPES = {"SALES_INVOICE", "PURCHASE_BILL", "CREDIT_NOTE", "DEBIT_NOTE"}
_SALES_DIRECTION_TYPES = {"SALES_INVOICE", "CREDIT_NOTE", "QUOTATION", "PROFORMA_INVOICE", "SALES_ORDER", "DELIVERY_NOTE"}


class DocumentPreviewNotAvailable(AppError):
    code = "DOCUMENT_PREVIEW_NOT_AVAILABLE"
    status_code = 409


def _now_ms() -> int:
    return int(time.time() * 1000)


# ==================== Document Templates ====================

async def _get_active_template_version(db: AsyncSession, company_id: str, template_id: str) -> DocumentTemplate | None:
    result = await db.execute(
        select(DocumentTemplate).where(
            DocumentTemplate.company_id == company_id, DocumentTemplate.template_id == template_id, DocumentTemplate.status == "ACTIVE"
        )
    )
    return result.scalar_one_or_none()


async def _clear_default_template_flag(db: AsyncSession, company_id: str, document_type: str) -> None:
    result = await db.execute(
        select(DocumentTemplate).where(
            DocumentTemplate.company_id == company_id, DocumentTemplate.document_type == document_type, DocumentTemplate.is_default == True  # noqa: E712
        )
    )
    for row in result.scalars().all():
        row.is_default = False


async def create_document_template(
    db: AsyncSession, company_id: str, document_type: str, template_name: str, config_json: str, is_default: bool = False
) -> DocumentTemplate:
    if not template_name.strip():
        raise ValidationError("Template name must not be blank.")
    template_id = f"TPL_{uuid.uuid4().hex[:8]}_{company_id}"
    now = _now_ms()
    if is_default:
        await _clear_default_template_flag(db, company_id, document_type)
    row = DocumentTemplate(
        id=f"{template_id}_v1", template_id=template_id, company_id=company_id, document_type=document_type,
        template_name=template_name, version=1, status="ACTIVE", is_default=is_default,
        config_json=config_json, created_at=now, updated_at=now,
    )
    db.add(row)
    await db.flush()
    return row


async def update_document_template(
    db: AsyncSession, company_id: str, template_id: str, template_name: str | None = None, config_json: str | None = None
) -> DocumentTemplate:
    """Creates a new version, archiving the previous one - never mutates an existing version's row
    (an already-rendered document must stay reproducible under the version it used)."""
    current = await _get_active_template_version(db, company_id, template_id)
    if current is None:
        raise ValidationError(f"Template '{template_id}' was not found.")
    max_version_result = await db.execute(
        select(func.max(DocumentTemplate.version)).where(DocumentTemplate.company_id == company_id, DocumentTemplate.template_id == template_id)
    )
    next_version = (max_version_result.scalar_one() or current.version) + 1
    current.status = "ARCHIVED"
    now = _now_ms()
    new_row = DocumentTemplate(
        id=f"{template_id}_v{next_version}", template_id=template_id, company_id=company_id, document_type=current.document_type,
        template_name=template_name or current.template_name, version=next_version, status="ACTIVE",
        is_default=current.is_default, config_json=config_json or current.config_json,
        created_at=current.created_at, updated_at=now,
    )
    db.add(new_row)
    await db.flush()
    return new_row


async def set_default_document_template(db: AsyncSession, company_id: str, document_type: str, template_id: str) -> DocumentTemplate:
    current = await _get_active_template_version(db, company_id, template_id)
    if current is None:
        raise ValidationError(f"Template '{template_id}' was not found.")
    await _clear_default_template_flag(db, company_id, document_type)
    current.is_default = True
    await db.flush()
    return current


async def list_active_templates(db: AsyncSession, company_id: str, document_type: str) -> list[DocumentTemplate]:
    result = await db.execute(
        select(DocumentTemplate)
        .where(DocumentTemplate.company_id == company_id, DocumentTemplate.document_type == document_type, DocumentTemplate.status == "ACTIVE")
        .order_by(DocumentTemplate.template_name)
    )
    return list(result.scalars().all())


async def get_template_version(db: AsyncSession, company_id: str, template_id: str, version: int) -> DocumentTemplate | None:
    result = await db.execute(
        select(DocumentTemplate).where(
            DocumentTemplate.company_id == company_id, DocumentTemplate.template_id == template_id, DocumentTemplate.version == version
        )
    )
    return result.scalar_one_or_none()


_BUILTIN_DEFAULT_CONFIG_JSON = "{\"layout\":{},\"typography\":{},\"colors\":{}}"


async def resolve_template_for_render(db: AsyncSession, company_id: str, document_type: str, template_id: str | None = None) -> DocumentTemplate:
    """Resolution order: an explicitly-picked template's current version, else the company's
    configured default, else a transient (never persisted) built-in default - a document is
    always renderable, never blocked on template setup."""
    if template_id is not None:
        explicit = await _get_active_template_version(db, company_id, template_id)
        if explicit is not None:
            return explicit
    result = await db.execute(
        select(DocumentTemplate).where(
            DocumentTemplate.company_id == company_id, DocumentTemplate.document_type == document_type,
            DocumentTemplate.is_default == True, DocumentTemplate.status == "ACTIVE"  # noqa: E712
        )
    )
    default = result.scalar_one_or_none()
    if default is not None:
        return default
    return DocumentTemplate(
        id="BUILTIN_DEFAULT_v0", template_id="BUILTIN_DEFAULT", company_id=company_id, document_type=document_type,
        template_name="Default", version=0, status="ACTIVE", is_default=True,
        config_json=_BUILTIN_DEFAULT_CONFIG_JSON, created_at=0, updated_at=0,
    )


# ==================== Business / Individual Profiles ====================

async def get_business_profile(db: AsyncSession, company_id: str) -> BusinessProfile | None:
    result = await db.execute(select(BusinessProfile).where(BusinessProfile.company_id == company_id))
    return result.scalar_one_or_none()


async def upsert_business_profile(db: AsyncSession, company_id: str, business_name: str, **fields) -> BusinessProfile:
    if not business_name.strip():
        raise ValidationError("Business name must not be blank.")
    existing = await get_business_profile(db, company_id)
    now = _now_ms()
    if existing is not None:
        existing.business_name = business_name
        for key, value in fields.items():
            setattr(existing, key, value)
        existing.updated_at = now
        await db.flush()
        return existing
    row = BusinessProfile(
        business_profile_id=f"BIZ_{uuid.uuid4().hex[:8]}_{company_id}", company_id=company_id,
        business_name=business_name, created_at=now, updated_at=now, **fields,
    )
    db.add(row)
    await db.flush()
    return row


async def get_individual_profile(db: AsyncSession, company_id: str) -> IndividualProfile | None:
    result = await db.execute(select(IndividualProfile).where(IndividualProfile.company_id == company_id))
    return result.scalar_one_or_none()


async def upsert_individual_profile(db: AsyncSession, company_id: str, name: str, **fields) -> IndividualProfile:
    if not name.strip():
        raise ValidationError("Individual name must not be blank.")
    existing = await get_individual_profile(db, company_id)
    now = _now_ms()
    if existing is not None:
        existing.name = name
        for key, value in fields.items():
            setattr(existing, key, value)
        existing.updated_at = now
        await db.flush()
        return existing
    row = IndividualProfile(
        individual_profile_id=f"IND_{uuid.uuid4().hex[:8]}_{company_id}", company_id=company_id,
        name=name, created_at=now, updated_at=now, **fields,
    )
    db.add(row)
    await db.flush()
    return row


# ==================== Document Assets ====================

async def create_document_asset(
    db: AsyncSession, company_id: str, asset_type: str, storage_reference: str, checksum: str = "", mime_type: str = "", size_bytes: int = 0
) -> DocumentAsset:
    if not storage_reference.strip():
        raise ValidationError("Asset storage reference must not be blank.")
    row = DocumentAsset(
        asset_id=f"AST_{uuid.uuid4().hex[:8]}_{company_id}", company_id=company_id, type=asset_type,
        storage_reference=storage_reference, checksum=checksum, mime_type=mime_type, size_bytes=size_bytes,
        created_at=_now_ms(),
    )
    db.add(row)
    await db.flush()
    return row


async def get_document_asset(db: AsyncSession, company_id: str, asset_id: str) -> DocumentAsset | None:
    result = await db.execute(select(DocumentAsset).where(DocumentAsset.company_id == company_id, DocumentAsset.asset_id == asset_id))
    return result.scalar_one_or_none()


async def list_document_assets(db: AsyncSession, company_id: str) -> list[DocumentAsset]:
    result = await db.execute(select(DocumentAsset).where(DocumentAsset.company_id == company_id).order_by(DocumentAsset.created_at.desc()))
    return list(result.scalars().all())


# ==================== Rendered Document Records (reproducibility log) ====================

async def log_document_render(
    db: AsyncSession, company_id: str, document_id: str, document_type: str, template_id: str, template_version: int,
    format_: str, storage_reference: str | None = None
) -> RenderedDocumentRecord:
    row = RenderedDocumentRecord(
        record_id=f"RDR_{uuid.uuid4().hex}", company_id=company_id, document_id=document_id, document_type=document_type,
        template_id=template_id, template_version=template_version, format=format_, storage_reference=storage_reference,
        generated_at=_now_ms(),
    )
    db.add(row)
    await db.flush()
    return row


async def get_rendered_document_records(db: AsyncSession, company_id: str, document_id: str) -> list[RenderedDocumentRecord]:
    result = await db.execute(
        select(RenderedDocumentRecord)
        .where(RenderedDocumentRecord.company_id == company_id, RenderedDocumentRecord.document_id == document_id)
        .order_by(RenderedDocumentRecord.generated_at.desc())
    )
    return list(result.scalars().all())


# ==================== Document Data Assembly ====================

def _is_sales_direction(document_type: str) -> bool:
    return document_type in _SALES_DIRECTION_TYPES


async def _party_snapshot(db: AsyncSession, company_id: str, party_id: str) -> dict | None:
    party_result = await db.execute(select(Party).where(Party.company_id == company_id, Party.party_id == party_id))
    party = party_result.scalar_one_or_none()
    if party is None:
        return None
    ledger_result = await db.execute(select(Ledger).where(Ledger.company_id == company_id, Ledger.ledger_id == party.ledger_id))
    ledger = ledger_result.scalar_one_or_none()
    return {
        "name": party.display_name,
        "address": ledger.address if ledger else "",
        "gstin": ledger.gstin if ledger else "",
        "pan": ledger.pan if ledger else "",
        "phone": ledger.phone if ledger else "",
        "email": ledger.email if ledger else "",
    }


async def _seller_snapshot(db: AsyncSession, company: Company) -> dict:
    profile = await get_business_profile(db, company.company_id)
    return {
        "name": profile.business_name if profile and profile.business_name else company.name,
        "address": profile.address if profile and profile.address else company.address,
        "gstin": profile.gstin if profile and profile.gstin else company.gstin,
        "pan": profile.pan if profile and profile.pan else company.pan,
        "phone": profile.phone if profile and profile.phone else company.phone,
        "email": profile.email if profile and profile.email else company.email,
    }


async def _branding_snapshot(db: AsyncSession, company_id: str) -> dict:
    profile = await get_business_profile(db, company_id)
    if profile is None:
        return {"logoStorageReference": None, "signatureStorageReference": None, "qrCodeStorageReference": None}

    async def _asset_ref(asset_id: str | None) -> str | None:
        if asset_id is None:
            return None
        asset = await get_document_asset(db, company_id, asset_id)
        return asset.storage_reference if asset else None

    return {
        "logoStorageReference": await _asset_ref(profile.logo_asset_id),
        "signatureStorageReference": await _asset_ref(profile.signature_asset_id),
        "qrCodeStorageReference": await _asset_ref(profile.qr_code_asset_id),
    }


async def _payment_info_snapshot(db: AsyncSession, company_id: str) -> dict:
    profile = await get_business_profile(db, company_id)
    if profile is None:
        return {"bankName": "", "bankAccountNumber": "", "bankIfsc": "", "bankBranch": "", "upiId": ""}
    return {
        "bankName": profile.bank_name, "bankAccountNumber": profile.bank_account_number,
        "bankIfsc": profile.bank_ifsc, "bankBranch": profile.bank_branch, "upiId": profile.upi_id,
    }


async def _reference_info_for_invoice(db: AsyncSession, company_id: str, invoice: Invoice) -> dict:
    if invoice.reference_invoice_id is not None:
        ref_result = await db.execute(select(Invoice).where(Invoice.company_id == company_id, Invoice.invoice_id == invoice.reference_invoice_id))
        ref = ref_result.scalar_one_or_none()
        return {
            "referenceDocumentId": ref.invoice_id if ref else None,
            "referenceDocumentNumber": ref.invoice_number if ref else None,
            "referenceDocumentDate": ref.date if ref else None,
        }
    if invoice.source_trade_document_id is not None:
        ref_result = await db.execute(select(TradeDocument).where(TradeDocument.company_id == company_id, TradeDocument.trade_document_id == invoice.source_trade_document_id))
        ref = ref_result.scalar_one_or_none()
        return {
            "referenceDocumentId": ref.trade_document_id if ref else None,
            "referenceDocumentNumber": ref.document_number if ref else None,
            "referenceDocumentDate": ref.date if ref else None,
        }
    return {"referenceDocumentId": None, "referenceDocumentNumber": None, "referenceDocumentDate": None}


async def assemble_document_data(db: AsyncSession, company_id: str, document_type: str, document_id: str) -> dict:
    """The ONLY function that reads Invoice/GstTransaction/Party/Ledger/Company/BusinessProfile
    data for rendering purposes. Only supports POSTED invoices (see module docstring for why draft/
    non-posting documents are explicitly out of scope server-side, not silently faked). Read-only."""
    company_result = await db.execute(select(Company).where(Company.company_id == company_id))
    company = company_result.scalar_one_or_none()
    if company is None:
        raise ValidationError(f"Company '{company_id}' was not found.")

    if document_type not in _POSTING_DOCUMENT_TYPES:
        raise DocumentPreviewNotAvailable(
            "Non-posting document rendering is not available server-side - GST calculation is "
            "Android-only. Render this document on the device."
        )

    invoice_result = await db.execute(select(Invoice).where(Invoice.company_id == company_id, Invoice.invoice_id == document_id))
    invoice = invoice_result.scalar_one_or_none()
    if invoice is None:
        raise ValidationError(f"Invoice '{document_id}' was not found.")
    if invoice.voucher_id is None:
        raise DocumentPreviewNotAvailable(
            "Draft invoice tax preview is not available server-side - GST calculation is "
            "Android-only. Render this document on the device, or post it first."
        )

    voucher_result = await db.execute(select(Voucher).where(Voucher.company_id == company_id, Voucher.voucher_id == invoice.voucher_id))
    voucher = voucher_result.scalar_one_or_none()

    lines_result = await db.execute(select(InvoiceLine).where(InvoiceLine.invoice_id == invoice.invoice_id).order_by(InvoiceLine.line_order))
    lines = list(lines_result.scalars().all())
    gst_result = await db.execute(select(GstTransaction).where(GstTransaction.voucher_id == invoice.voucher_id))
    gst_by_line_order = {g.line_order: g for g in gst_result.scalars().all()}

    buyer = await _party_snapshot(db, company_id, invoice.party_id)
    if buyer is None:
        raise ValidationError(f"Party '{invoice.party_id}' was not found.")

    items = []
    for line in lines:
        gst = gst_by_line_order.get(line.line_order)
        if gst is None:
            raise DocumentPreviewNotAvailable(f"No GstTransaction found for line order {line.line_order} on a posted invoice.")
        line_total = gst.taxable_amount_paise + gst.cgst_paise + gst.sgst_paise + gst.igst_paise + gst.cess_paise
        items.append({
            "itemId": line.item_id, "description": line.item_name, "hsnSacCode": line.hsn_sac_code,
            "quantity": (line.quantity_raw / 1000.0) if line.quantity_raw else None, "unit": "",
            "ratePaise": line.rate_paise, "discountPaise": 0,
            "taxableAmountPaise": gst.taxable_amount_paise, "gstRatePercent": gst.gst_rate_percent,
            "cgstPaise": gst.cgst_paise, "sgstPaise": gst.sgst_paise, "igstPaise": gst.igst_paise, "cessPaise": gst.cess_paise,
            "lineTotalPaise": line_total,
        })

    taxable_sum = sum(i["taxableAmountPaise"] for i in items)
    cgst_sum = sum(i["cgstPaise"] for i in items)
    sgst_sum = sum(i["sgstPaise"] for i in items)
    igst_sum = sum(i["igstPaise"] for i in items)
    cess_sum = sum(i["cessPaise"] for i in items)
    line_total_sum = sum(i["lineTotalPaise"] for i in items)
    grand_total = voucher.total_amount_paise if voucher else line_total_sum

    seller = await _seller_snapshot(db, company)
    profile = await get_business_profile(db, company_id)

    return {
        "schemaVersion": 1,
        "documentType": document_type,
        "documentNumber": invoice.invoice_number or invoice.invoice_id,
        "date": invoice.date,
        "dueDate": invoice.due_date,
        "isPosted": True,
        "accountingVoucherNumber": voucher.voucher_number if voucher else None,
        "seller": seller if _is_sales_direction(document_type) else buyer,
        "buyer": buyer if _is_sales_direction(document_type) else seller,
        "items": items,
        "tax": {"taxableAmountPaise": taxable_sum, "cgstPaise": cgst_sum, "sgstPaise": sgst_sum, "igstPaise": igst_sum, "cessPaise": cess_sum},
        "totals": {"taxableAmountPaise": taxable_sum, "roundOffPaise": grand_total - line_total_sum, "grandTotalPaise": grand_total},
        "paymentInformation": await _payment_info_snapshot(db, company_id),
        "references": await _reference_info_for_invoice(db, company_id, invoice),
        "terms": profile.terms_and_conditions if profile else "",
        "branding": await _branding_snapshot(db, company_id),
    }


async def render_document_as_json(db: AsyncSession, company_id: str, document_type: str, document_id: str, template_id: str | None = None) -> dict:
    data = await assemble_document_data(db, company_id, document_type, document_id)
    template = await resolve_template_for_render(db, company_id, document_type, template_id)
    await log_document_render(db, company_id, document_id, document_type, template.template_id, template.version, "JSON")
    return data
