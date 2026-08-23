"""
Phase 7E - thin export routes. Each route: authorize (`require_company_access`) -> call the
existing application service/query function for the data -> map to an export DTO -> serialize per
`?format=`. No accounting/GST/report calculation happens in this file - see
`app/application/services/export_service.py` and `docs/46_EXPORT_ARCHITECTURE.md`.

`format` defaults to `json` and accepts `json`/`csv`/`gstr-json` (only `/exports/gst` supports
`gstr-json`; an unsupported combination raises `EXPORT_FORMAT_UNSUPPORTED`, never a silently wrong
serialization).
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, Response
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.tenant import require_company_access
from app.application.services import export_service
from app.domain.export import csv_engine
from app.domain.export.gstr_json import build_gstr_json
from app.domain.export.json_envelope import build_metadata, envelope
from app.infrastructure.database.base import get_db

router = APIRouter(prefix="/exports", tags=["exports"])


def _csv_response(csv_text: str) -> Response:
    return Response(content=csv_text, media_type="text/csv")


@router.get("/vouchers/{voucher_id}")
async def export_voucher(voucher_id: str, companyId: str, format: str = "json", user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)):
    await require_company_access(db, user_id, companyId)
    export_service.require_supported_format("VOUCHER", format)
    dto = await export_service.export_voucher(db, companyId, voucher_id)
    if format == "csv":
        return _csv_response(csv_engine.voucher_csv(dto))
    return envelope(build_metadata(companyId, "VOUCHER"), dto)


@router.get("/parties/{party_id}")
async def export_party(party_id: str, companyId: str, format: str = "json", user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)):
    await require_company_access(db, user_id, companyId)
    export_service.require_supported_format("PARTY", format)
    dto = await export_service.export_party(db, companyId, party_id)
    if format == "csv":
        return _csv_response(csv_engine.parties_csv([dto]))
    return envelope(build_metadata(companyId, "PARTY"), dto)


@router.get("/ledgers/{ledger_id}")
async def export_ledger(ledger_id: str, companyId: str, format: str = "json", user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)):
    await require_company_access(db, user_id, companyId)
    export_service.require_supported_format("LEDGER", format)
    dto = await export_service.export_ledger(db, companyId, ledger_id)
    if format == "csv":
        return _csv_response(csv_engine.ledgers_csv([dto]))
    return envelope(build_metadata(companyId, "LEDGER"), dto)


@router.get("/invoices/{document_id}")
async def export_invoice(
    document_id: str, companyId: str, documentType: str, format: str = "json",
    user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    export_service.require_supported_format("INVOICE", format)
    dto = await export_service.export_invoice(db, companyId, documentType, document_id)
    return envelope(build_metadata(companyId, "INVOICE"), dto)


@router.get("/reports/trial-balance")
async def export_trial_balance_route(companyId: str, financialYearId: str, format: str = "json", user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)):
    await require_company_access(db, user_id, companyId)
    export_service.require_supported_format("TRIAL_BALANCE", format)
    dto = await export_service.export_trial_balance(db, companyId, financialYearId)
    if format == "csv":
        return _csv_response(csv_engine.trial_balance_csv(dto))
    return envelope(build_metadata(companyId, "TRIAL_BALANCE", financialYearId), dto)


@router.get("/reports/profit-loss")
async def export_profit_loss_route(companyId: str, financialYearId: str, format: str = "json", user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)):
    await require_company_access(db, user_id, companyId)
    export_service.require_supported_format("PROFIT_AND_LOSS", format)
    dto = await export_service.export_profit_and_loss(db, companyId, financialYearId)
    return envelope(build_metadata(companyId, "PROFIT_AND_LOSS", financialYearId), dto)


@router.get("/reports/balance-sheet")
async def export_balance_sheet_route(companyId: str, financialYearId: str, format: str = "json", user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)):
    await require_company_access(db, user_id, companyId)
    export_service.require_supported_format("BALANCE_SHEET", format)
    dto = await export_service.export_balance_sheet(db, companyId, financialYearId)
    return envelope(build_metadata(companyId, "BALANCE_SHEET", financialYearId), dto)


@router.get("/reports/outstanding")
async def export_outstanding_route(companyId: str, role: str | None = None, today: str | None = None, format: str = "json", user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)):
    await require_company_access(db, user_id, companyId)
    export_service.require_supported_format("OUTSTANDING", format)
    dto = await export_service.export_outstanding(db, companyId, role, today)
    if format == "csv":
        return _csv_response(csv_engine.outstanding_csv(dto))
    return envelope(build_metadata(companyId, "OUTSTANDING"), dto)


@router.get("/gst")
async def export_gst_route(companyId: str, financialYearId: str, format: str = "json", user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)):
    await require_company_access(db, user_id, companyId)
    if format == "gstr-json":
        export_service.require_supported_format("GST_TRANSACTIONS", format)
        transactions = await export_service.export_gst_transactions(db, companyId, financialYearId)
        return build_gstr_json(build_metadata(companyId, "GST_TRANSACTIONS", financialYearId), transactions)

    export_service.require_supported_format("GST_SUMMARY", format)
    dto = await export_service.export_gst_summary(db, companyId, financialYearId)
    if format == "csv":
        return _csv_response(csv_engine.gst_summary_csv(dto))
    return envelope(build_metadata(companyId, "GST_SUMMARY", financialYearId), dto)


@router.get("/gst/transactions")
async def export_gst_transactions_route(companyId: str, financialYearId: str, format: str = "json", user_id: str = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)):
    await require_company_access(db, user_id, companyId)
    export_service.require_supported_format("GST_TRANSACTIONS", format)
    transactions = await export_service.export_gst_transactions(db, companyId, financialYearId)
    if format == "csv":
        return _csv_response(csv_engine.gst_transactions_csv(transactions))
    if format == "gstr-json":
        return build_gstr_json(build_metadata(companyId, "GST_TRANSACTIONS", financialYearId), transactions)
    return envelope(build_metadata(companyId, "GST_TRANSACTIONS", financialYearId), {"count": len(transactions), "transactions": transactions})
