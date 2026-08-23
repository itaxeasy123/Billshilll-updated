"""
Read-only report endpoints (Phase 6, Priority 6.8/6.14; extended Phase 7C) - GET only, computed
server-side via `app/application/queries/reports.py`. Never accepts a client-submitted total.
Thin by design: every route below is a direct pass-through to one query function - no accounting
calculation happens in this file.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.tenant import require_company_access
from app.application.queries import reports as report_queries
from app.infrastructure.database.base import get_db

router = APIRouter(prefix="/reports", tags=["reports"])


@router.get("/trial-balance")
async def trial_balance(
    companyId: str,
    financialYearId: str,
    startDate: str | None = None,
    endDate: str | None = None,
    includeZeroBalance: bool = True,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    return await report_queries.trial_balance(db, companyId, financialYearId, startDate, endDate, includeZeroBalance)


@router.get("/gst")
async def gst_summary(
    companyId: str,
    financialYearId: str,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    return await report_queries.gst_summary(db, companyId, financialYearId)


@router.get("/profit-loss")
async def profit_and_loss(
    companyId: str,
    financialYearId: str,
    startDate: str | None = None,
    endDate: str | None = None,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    return await report_queries.profit_and_loss(db, companyId, financialYearId, startDate, endDate)


@router.get("/balance-sheet")
async def balance_sheet(
    companyId: str,
    financialYearId: str,
    startDate: str | None = None,
    endDate: str | None = None,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    return await report_queries.balance_sheet(db, companyId, financialYearId, startDate, endDate)


@router.get("/ledger")
async def ledger_statement(
    companyId: str,
    ledgerId: str,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    return await report_queries.ledger_statement(db, companyId, ledgerId)


@router.get("/day-book")
async def day_book(
    companyId: str,
    startDate: str,
    endDate: str,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    return await report_queries.day_book(db, companyId, startDate, endDate)


@router.get("/outstanding")
async def outstanding(
    companyId: str,
    today: str | None = None,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    return await report_queries.outstanding_report(db, companyId, role=None, today=today)


@router.get("/receivables")
async def receivables(
    companyId: str,
    today: str | None = None,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    return await report_queries.outstanding_report(db, companyId, role="CUSTOMER", today=today)


@router.get("/payables")
async def payables(
    companyId: str,
    today: str | None = None,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    return await report_queries.outstanding_report(db, companyId, role="SUPPLIER", today=today)


@router.get("/cash-flow")
async def cash_flow(
    companyId: str,
    financialYearId: str,
    startDate: str,
    endDate: str,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    return await report_queries.cash_flow(db, companyId, financialYearId, startDate, endDate)


@router.get("/ratios")
async def ratio_analysis(
    companyId: str,
    financialYearId: str,
    startDate: str | None = None,
    endDate: str | None = None,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    return await report_queries.ratio_analysis(db, companyId, financialYearId, startDate, endDate)
