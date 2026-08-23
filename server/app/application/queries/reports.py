"""
Read-only report queries (Phase 6, Priority 6.8/6.14; extended Phase 7C) - computed entirely from
the server's own Postgres data, using the same domain principles the Android reports use (Trial
Balance = ledger opening balance + journal-item movement; P&L/Balance Sheet classify via the real
group hierarchy - `domain/reports/group_aggregation.py` - never `ledgerName.contains(...)`). Never
accepts a client-submitted total as authoritative - there is no code path here that reads a number
out of the request and treats it as a report figure.

Phase 7C additions are additive only: `trial_balance`/`gst_summary`'s existing response fields
(`rows`, `debitPaise`, `creditPaise`, `totalDebitPaise`, `totalCreditPaise`, `isBalanced`,
`outward`, `inward`) are unchanged in meaning - new fields are appended alongside them.

Known, documented scope limitation: `profit_and_loss`/`balance_sheet` do not compute COGS/stock
valuation (unlike the Android side's `computeCogsIfInventoryAware`) - no inventory-valuation engine
has been ported to Python yet. `isInventoryAware` is always `False` and `stockInHandPaise` is
always `0` here; this mirrors exactly what Android itself returns for an ACCOUNT_ONLY company, so
it is a real, correct (if partial) answer, not a wrong one - and is called out explicitly rather
than silently guessed at.
"""
from __future__ import annotations

from datetime import date as date_cls, timedelta

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.domain.accounting import standard_groups as sg
from app.domain.reports.group_aggregation import LedgerContribution, aggregate, find_node
from app.infrastructure.database.models import (
    Company,
    FinancialYear,
    Group,
    Invoice,
    JournalItem,
    Ledger,
    Party,
    SettlementAllocation,
    Voucher,
)


def _date_in_range(voucher_date: str | None, start_date: str | None, end_date: str | None) -> bool:
    if voucher_date is None:
        return True
    if start_date is not None and voucher_date < start_date:
        return False
    if end_date is not None and voucher_date > end_date:
        return False
    return True


async def trial_balance(
    db: AsyncSession,
    company_id: str,
    financial_year_id: str,
    start_date: str | None = None,
    end_date: str | None = None,
    include_zero_balance: bool = True,
) -> dict:
    ledgers = (await db.execute(select(Ledger).where(Ledger.company_id == company_id))).scalars().all()
    groups = (await db.execute(select(Group).where(Group.company_id == company_id))).scalars().all()

    voucher_dates: dict[str, str] = {}
    if start_date is not None or end_date is not None:
        v_result = await db.execute(
            select(Voucher.voucher_id, Voucher.date).where(Voucher.company_id == company_id, Voucher.financial_year_id == financial_year_id)
        )
        voucher_dates = dict(v_result.all())

    rows: list[dict] = []
    contributions: list[LedgerContribution] = []
    total_opening_debit = total_opening_credit = 0
    total_debit = total_credit = 0
    total_closing_debit = total_closing_credit = 0

    for ledger in ledgers:
        items_result = await db.execute(
            select(JournalItem).where(
                JournalItem.company_id == company_id,
                JournalItem.financial_year_id == financial_year_id,
                JournalItem.ledger_id == ledger.ledger_id,
            )
        )
        items = items_result.scalars().all()
        if start_date is not None or end_date is not None:
            items = [i for i in items if _date_in_range(voucher_dates.get(i.voucher_id), start_date, end_date)]

        debit_total = sum(i.amount_paise for i in items if i.type == "DEBIT")
        credit_total = sum(i.amount_paise for i in items if i.type == "CREDIT")

        opening_debit = ledger.opening_balance_paise if ledger.opening_balance_type == "DEBIT" else 0
        opening_credit = ledger.opening_balance_paise if ledger.opening_balance_type == "CREDIT" else 0

        net_signed = (opening_debit - opening_credit) + (debit_total - credit_total)
        closing_debit = net_signed if net_signed >= 0 else 0
        closing_credit = -net_signed if net_signed < 0 else 0

        contributions.append(LedgerContribution(ledger.group_id, closing_debit, closing_credit))

        if not include_zero_balance and closing_debit == 0 and closing_credit == 0:
            continue

        rows.append({
            "ledgerId": ledger.ledger_id,
            "ledgerName": ledger.name,
            "groupId": ledger.group_id,
            "debitPaise": debit_total,
            "creditPaise": credit_total,
            "openingDebitPaise": opening_debit,
            "openingCreditPaise": opening_credit,
            "closingDebitPaise": closing_debit,
            "closingCreditPaise": closing_credit,
        })
        total_opening_debit += opening_debit
        total_opening_credit += opening_credit
        total_debit += debit_total
        total_credit += credit_total
        total_closing_debit += closing_debit
        total_closing_credit += closing_credit

    hierarchy = aggregate(groups, contributions)

    def node_to_dict(node) -> dict:
        return {
            "groupId": node.group_id,
            "groupName": node.group_name,
            "primaryGroup": node.primary_group,
            "parentGroupId": node.parent_group_id,
            "totalDebitPaise": node.total_debit_paise,
            "totalCreditPaise": node.total_credit_paise,
            "netBalancePaise": node.net_balance_paise,
            "balanceType": node.balance_type,
            "children": [node_to_dict(c) for c in node.children],
        }

    return {
        "rows": rows,
        "totalDebitPaise": total_debit,
        "totalCreditPaise": total_credit,
        "isBalanced": total_closing_debit == total_closing_credit,
        "totalOpeningDebitPaise": total_opening_debit,
        "totalOpeningCreditPaise": total_opening_credit,
        "totalClosingDebitPaise": total_closing_debit,
        "totalClosingCreditPaise": total_closing_credit,
        "groupHierarchy": [node_to_dict(n) for n in hierarchy],
    }


async def gst_summary(db: AsyncSession, company_id: str, financial_year_id: str) -> dict:
    from app.infrastructure.database.models import GstTransaction

    result = await db.execute(
        select(GstTransaction).where(
            GstTransaction.company_id == company_id, GstTransaction.financial_year_id == financial_year_id
        )
    )
    transactions = result.scalars().all()
    outward = [t for t in transactions if t.direction == "OUTPUT"]
    inward = [t for t in transactions if t.direction == "INPUT"]

    def sums(rows):
        return {
            "taxablePaise": sum(r.taxable_amount_paise for r in rows),
            "cgstPaise": sum(r.cgst_paise for r in rows),
            "sgstPaise": sum(r.sgst_paise for r in rows),
            "igstPaise": sum(r.igst_paise for r in rows),
            "cessPaise": sum(r.cess_paise for r in rows),
        }

    outward_sums = sums(outward)
    inward_sums = sums(inward)
    net_tax_payable = (
        (outward_sums["cgstPaise"] + outward_sums["sgstPaise"] + outward_sums["igstPaise"])
        - (inward_sums["cgstPaise"] + inward_sums["sgstPaise"] + inward_sums["igstPaise"])
    )
    net_cess_payable = outward_sums["cessPaise"] - inward_sums["cessPaise"]

    return {
        "outward": outward_sums,
        "inward": inward_sums,
        "netTaxPayablePaise": net_tax_payable,
        "netCessPayablePaise": net_cess_payable,
    }


async def profit_and_loss(
    db: AsyncSession,
    company_id: str,
    financial_year_id: str,
    start_date: str | None = None,
    end_date: str | None = None,
) -> dict:
    company = (await db.execute(select(Company).where(Company.company_id == company_id))).scalar_one_or_none()
    fy = (await db.execute(select(FinancialYear).where(FinancialYear.financial_year_id == financial_year_id))).scalar_one_or_none()
    groups = (await db.execute(select(Group).where(Group.company_id == company_id))).scalars().all()

    tb = await trial_balance(db, company_id, financial_year_id, start_date, end_date)
    period_contributions = [LedgerContribution(r["groupId"], r["debitPaise"], r["creditPaise"]) for r in tb["rows"]]
    hierarchy = aggregate(groups, period_contributions)

    def named_node_net(bare_group_id: str) -> int:
        node = find_node(hierarchy, f"{bare_group_id}_{company_id}")
        if node is None:
            return 0
        if node.primary_group == "INCOME":
            return node.total_credit_paise - node.total_debit_paise
        return node.total_debit_paise - node.total_credit_paise

    total_income = sum(n.total_credit_paise - n.total_debit_paise for n in hierarchy if n.primary_group == "INCOME")
    total_expense = sum(n.total_debit_paise - n.total_credit_paise for n in hierarchy if n.primary_group == "EXPENSES")

    sales = named_node_net(sg.SALES_GROUP_ID)
    direct_income = named_node_net(sg.DIRECT_INCOME_GROUP_ID)
    purchases = named_node_net(sg.PURCHASE_GROUP_ID)
    direct_expense = named_node_net(sg.DIRECT_EXPENSE_GROUP_ID)

    indirect_income = total_income - sales - direct_income
    indirect_expense = total_expense - purchases - direct_expense

    # No COGS/inventory-valuation engine ported server-side yet - see module docstring.
    trading_expense = purchases + direct_expense
    total_trading_income = sales + direct_income
    gross_profit = total_trading_income - trading_expense
    net_profit = gross_profit + indirect_income - indirect_expense

    return {
        "companyName": company.name if company else "Apex Industrial Technologies Ltd.",
        "financialYearCode": fy.fy_code if fy else "FY 2026-27",
        "salesRevenuePaise": sales,
        "directIncomesPaise": direct_income,
        "purchasesPaise": purchases,
        "directExpensesPaise": direct_expense,
        "grossProfitPaise": gross_profit,
        "indirectIncomesPaise": indirect_income,
        "indirectExpensesPaise": indirect_expense,
        "netProfitPaise": net_profit,
        "cogsPaise": 0,
        "openingStockPaise": 0,
        "closingStockPaise": 0,
        "isInventoryAware": False,
    }


async def balance_sheet(
    db: AsyncSession,
    company_id: str,
    financial_year_id: str,
    start_date: str | None = None,
    end_date: str | None = None,
) -> dict:
    company = (await db.execute(select(Company).where(Company.company_id == company_id))).scalar_one_or_none()
    fy = (await db.execute(select(FinancialYear).where(FinancialYear.financial_year_id == financial_year_id))).scalar_one_or_none()
    groups = (await db.execute(select(Group).where(Group.company_id == company_id))).scalars().all()

    tb = await trial_balance(db, company_id, financial_year_id, start_date, end_date)
    pnl = await profit_and_loss(db, company_id, financial_year_id, start_date, end_date)
    contributions = [LedgerContribution(r["groupId"], r["closingDebitPaise"], r["closingCreditPaise"]) for r in tb["rows"]]
    hierarchy = aggregate(groups, contributions)

    def net_debit(bare_id: str) -> int:
        node = find_node(hierarchy, f"{bare_id}_{company_id}")
        return (node.total_debit_paise - node.total_credit_paise) if node else 0

    def net_credit(bare_id: str) -> int:
        node = find_node(hierarchy, f"{bare_id}_{company_id}")
        return (node.total_credit_paise - node.total_debit_paise) if node else 0

    suspense_node = find_node(hierarchy, f"{sg.SUSPENSE_GROUP_ID}_{company_id}")
    suspense_net_signed = (suspense_node.total_debit_paise - suspense_node.total_credit_paise) if suspense_node else 0
    suspense_debit = suspense_net_signed if suspense_net_signed > 0 else 0
    suspense_credit = -suspense_net_signed if suspense_net_signed < 0 else 0

    capital = net_credit(sg.CAPITAL_GROUP_ID)
    reserves = net_credit(sg.RESERVES_GROUP_ID)

    # LIABILITIES - named buckets subtracted from the primary-group-wide total, so nested
    # subgroups (e.g. GRP_DUTIES lives under GRP_CURRENT_LIAB) are attributed once, correctly -
    # mirrors Android's generateBalanceSheet exactly.
    loans = net_credit(sg.LOANS_GROUP_ID)
    duties_taxes = net_credit(sg.DUTIES_GROUP_ID)
    branch_div = net_credit(sg.BRANCH_DIVISIONS_GROUP_ID)
    total_liabilities_primary = sum(n.total_credit_paise - n.total_debit_paise for n in hierarchy if n.primary_group == "LIABILITIES")
    current_liabilities = total_liabilities_primary - loans - duties_taxes - branch_div

    # ASSETS - same pattern.
    fixed_assets = net_debit(sg.FIXED_ASSETS_GROUP_ID)
    investments = net_debit(sg.INVESTMENTS_GROUP_ID)
    misc_exp = net_debit(sg.MISC_EXPENSES_GROUP_ID)
    debtors = net_debit(sg.DEBTORS_GROUP_ID)
    bank = net_debit(sg.BANK_GROUP_ID)
    cash = net_debit(sg.CASH_GROUP_ID)
    total_assets_primary = sum(n.total_debit_paise - n.total_credit_paise for n in hierarchy if n.primary_group == "ASSETS")
    current_assets = total_assets_primary - fixed_assets - investments - misc_exp - debtors - bank - cash

    # Stock-in-Hand: always 0 here - see module docstring (no COGS/inventory-valuation engine
    # ported server-side yet).
    stock_in_hand = pnl["closingStockPaise"]

    total_liabilities = capital + reserves + pnl["netProfitPaise"] + loans + current_liabilities + duties_taxes + branch_div + suspense_credit
    total_assets = fixed_assets + investments + current_assets + debtors + bank + cash + misc_exp + suspense_debit + stock_in_hand

    return {
        "companyName": company.name if company else "Apex Industrial Technologies Ltd.",
        "financialYearCode": fy.fy_code if fy else "FY 2026-27",
        "asOfDate": end_date or (fy.end_date if fy else None),
        "capitalAccountsPaise": capital,
        "reservesAndSurplusPaise": reserves,
        "netProfitForYearPaise": pnl["netProfitPaise"],
        "loansLiabilitiesPaise": loans,
        "currentLiabilitiesPaise": current_liabilities,
        "dutiesAndTaxesLiabilityPaise": duties_taxes,
        "branchDivisionsPaise": branch_div,
        "suspenseCreditPaise": suspense_credit,
        "totalLiabilitiesPaise": total_liabilities,
        "fixedAssetsPaise": fixed_assets,
        "investmentsPaise": investments,
        "currentAssetsPaise": current_assets,
        "sundryDebtorsPaise": debtors,
        "bankAccountsPaise": bank,
        "cashInHandPaise": cash,
        "miscExpensesAssetPaise": misc_exp,
        "stockInHandPaise": stock_in_hand,
        "suspenseDebitPaise": suspense_debit,
        "totalAssetsPaise": total_assets,
        "isBalanced": total_liabilities == total_assets,
    }


async def ledger_statement(db: AsyncSession, company_id: str, ledger_id: str) -> dict:
    from app.domain.errors import ValidationError

    ledger = (await db.execute(select(Ledger).where(Ledger.company_id == company_id, Ledger.ledger_id == ledger_id))).scalar_one_or_none()
    if ledger is None:
        raise ValidationError(f"Ledger '{ledger_id}' not found.")

    items = (await db.execute(
        select(JournalItem).where(JournalItem.company_id == company_id, JournalItem.ledger_id == ledger_id)
    )).scalars().all()
    voucher_ids = {i.voucher_id for i in items}
    vouchers: dict[str, Voucher] = {}
    if voucher_ids:
        v_result = await db.execute(select(Voucher).where(Voucher.voucher_id.in_(voucher_ids)))
        vouchers = {v.voucher_id: v for v in v_result.scalars().all()}

    def sort_key(item):
        v = vouchers.get(item.voucher_id)
        return (v.date if v else "", item.line_order)

    sorted_items = sorted(items, key=sort_key)

    running_signed = ledger.opening_balance_paise if ledger.opening_balance_type == "DEBIT" else -ledger.opening_balance_paise
    rows = []
    total_debit = total_credit = 0
    for item in sorted_items:
        v = vouchers.get(item.voucher_id)
        if item.type == "DEBIT":
            running_signed += item.amount_paise
            total_debit += item.amount_paise
        else:
            running_signed -= item.amount_paise
            total_credit += item.amount_paise
        rows.append({
            "voucherId": item.voucher_id,
            "voucherNumber": v.voucher_number if v else "",
            "voucherType": v.voucher_type if v else "",
            "date": v.date if v else "",
            "particulars": item.narration,
            "debitPaise": item.amount_paise if item.type == "DEBIT" else 0,
            "creditPaise": item.amount_paise if item.type == "CREDIT" else 0,
            "runningBalancePaise": abs(running_signed),
            "balanceType": "DEBIT" if running_signed >= 0 else "CREDIT",
        })

    return {
        "ledgerId": ledger.ledger_id,
        "ledgerName": ledger.name,
        "openingBalancePaise": ledger.opening_balance_paise,
        "openingType": ledger.opening_balance_type,
        "rows": rows,
        "totalDebitPaise": total_debit,
        "totalCreditPaise": total_credit,
        "closingBalancePaise": abs(running_signed),
        "closingType": "DEBIT" if running_signed >= 0 else "CREDIT",
    }


async def day_book(db: AsyncSession, company_id: str, start_date: str, end_date: str) -> dict:
    vouchers = (await db.execute(
        select(Voucher).where(Voucher.company_id == company_id, Voucher.date >= start_date, Voucher.date <= end_date)
    )).scalars().all()
    vouchers_sorted = sorted(vouchers, key=lambda v: v.date)

    voucher_ids = [v.voucher_id for v in vouchers_sorted]
    invoices_by_voucher: dict[str, Invoice] = {}
    if voucher_ids:
        inv_result = await db.execute(select(Invoice).where(Invoice.voucher_id.in_(voucher_ids)))
        invoices_by_voucher = {i.voucher_id: i for i in inv_result.scalars().all()}
    party_ids = {i.party_id for i in invoices_by_voucher.values()}
    parties_by_id: dict[str, Party] = {}
    if party_ids:
        p_result = await db.execute(select(Party).where(Party.party_id.in_(party_ids)))
        parties_by_id = {p.party_id: p for p in p_result.scalars().all()}

    rows = []
    total = 0
    for v in vouchers_sorted:
        party_name = None
        inv = invoices_by_voucher.get(v.voucher_id)
        if inv is not None:
            party = parties_by_id.get(inv.party_id)
            party_name = party.display_name if party else None
        status = "CANCELLED" if v.is_cancelled else "POSTED"
        rows.append({
            "voucherId": v.voucher_id,
            "voucherNumber": v.voucher_number,
            "voucherType": v.voucher_type,
            "date": v.date,
            "partyName": party_name,
            "narration": v.narration,
            "totalAmountPaise": v.total_amount_paise,
            "status": status,
        })
        if not v.is_cancelled:
            total += v.total_amount_paise

    return {"dateRangeLabel": f"{start_date} to {end_date}", "rows": rows, "totalAmountPaise": total}


async def compute_outstanding_paise(db: AsyncSession, invoice_voucher_id: str, total_amount_paise: int) -> int:
    """Shared read-query (Phase 7C) - the single source both `outstanding_report` and
    `api/routes/invoices.py` use, replacing what used to be two independent duplicates of the same
    computation. Still deliberately independent of `voucher_commands.py`'s own
    `_compute_outstanding_paise` (frozen, never imported from here)."""
    alloc_result = await db.execute(select(SettlementAllocation).where(SettlementAllocation.invoice_voucher_id == invoice_voucher_id))
    allocated = sum(a.allocated_amount_paise for a in alloc_result.scalars().all())

    note_result = await db.execute(
        select(Voucher).where(Voucher.reference_voucher_id == invoice_voucher_id, Voucher.is_cancelled == False)  # noqa: E712
    )
    note_adjustment = sum(v.total_amount_paise for v in note_result.scalars().all())

    return total_amount_paise - allocated - note_adjustment


def _aging_bucket(days_outstanding: int) -> str:
    if days_outstanding <= 0:
        return "CURRENT"
    if days_outstanding <= 30:
        return "DAYS_1_30"
    if days_outstanding <= 60:
        return "DAYS_31_60"
    if days_outstanding <= 90:
        return "DAYS_61_90"
    return "DAYS_90_PLUS"


async def outstanding_report(db: AsyncSession, company_id: str, role: str | None = None, today: str | None = None) -> dict:
    """Outstanding/Receivables/Payables (Phase 7C) - one shared, Party-aware report, mirroring
    Android's `generateOutstandingReport` exactly: every amount comes from the existing
    `compute_outstanding_paise`, every status from the existing `derive_status` (Phase 7A) - this
    function only joins Invoice/Party/Voucher and buckets by age."""
    from app.domain.invoice.status import derive_status

    today_str = today or date_cls.today().isoformat()
    invoice_types = {"CUSTOMER": {"SALES_INVOICE"}, "SUPPLIER": {"PURCHASE_BILL"}}.get(role, {"SALES_INVOICE", "PURCHASE_BILL"})

    invoices = (await db.execute(
        select(Invoice).where(Invoice.company_id == company_id, Invoice.invoice_type.in_(invoice_types), Invoice.voucher_id.isnot(None))
    )).scalars().all()

    rows = []
    for inv in invoices:
        voucher = (await db.execute(select(Voucher).where(Voucher.company_id == company_id, Voucher.voucher_id == inv.voucher_id))).scalar_one_or_none()
        if voucher is None or voucher.is_cancelled:
            continue
        party = (await db.execute(select(Party).where(Party.company_id == company_id, Party.party_id == inv.party_id))).scalar_one_or_none()
        if party is None:
            continue
        if role is not None and party.role != role:
            continue

        outstanding = await compute_outstanding_paise(db, inv.voucher_id, voucher.total_amount_paise)
        if outstanding <= 0:
            continue

        status = derive_status(
            voucher_id=inv.voucher_id, is_cancelled=voucher.is_cancelled, total_amount_paise=voucher.total_amount_paise,
            outstanding_paise=outstanding, due_date=inv.due_date, today=today_str,
        )
        days_outstanding = 0
        if inv.due_date:
            days_outstanding = max(0, (date_cls.fromisoformat(today_str) - date_cls.fromisoformat(inv.due_date)).days)
        bucket = _aging_bucket(days_outstanding)

        rows.append({
            "invoiceId": inv.invoice_id, "invoiceNumber": inv.invoice_number, "invoiceType": inv.invoice_type,
            "partyId": party.party_id, "partyName": party.display_name, "voucherId": inv.voucher_id,
            "voucherNumber": voucher.voucher_number, "date": inv.date, "dueDate": inv.due_date,
            "totalAmountPaise": voucher.total_amount_paise, "outstandingAmountPaise": outstanding,
            "status": status, "daysOutstanding": days_outstanding, "agingBucket": bucket,
        })

    rows.sort(key=lambda r: r["dueDate"] or r["date"])
    aging_summary = []
    for bucket in ("CURRENT", "DAYS_1_30", "DAYS_31_60", "DAYS_61_90", "DAYS_90_PLUS"):
        bucket_rows = [r for r in rows if r["agingBucket"] == bucket]
        aging_summary.append({
            "bucket": bucket,
            "totalOutstandingPaise": sum(r["outstandingAmountPaise"] for r in bucket_rows),
            "invoiceCount": len(bucket_rows),
        })

    return {
        "rows": rows,
        "totalOutstandingPaise": sum(r["outstandingAmountPaise"] for r in rows),
        "agingSummary": aging_summary,
    }


async def _opening_current_assets_and_liabilities_paise(db: AsyncSession, company_id: str) -> tuple[int, int]:
    """Opening Current-Assets(excl. Cash/Bank)/Current-Liabilities using ONLY each ledger's own
    stored opening balance (zero transactions) - needed only when a Cash Flow period starts
    exactly at the financial year's own start date. Mirrors Android's identically-named/-purposed
    helper in `AccountingRepository.kt` exactly."""
    groups = (await db.execute(select(Group).where(Group.company_id == company_id))).scalars().all()
    ledgers = (await db.execute(select(Ledger).where(Ledger.company_id == company_id))).scalars().all()
    contributions = [
        LedgerContribution(
            l.group_id,
            l.opening_balance_paise if l.opening_balance_type == "DEBIT" else 0,
            l.opening_balance_paise if l.opening_balance_type == "CREDIT" else 0,
        )
        for l in ledgers
    ]
    hierarchy = aggregate(groups, contributions)

    def net_debit(bare_id: str) -> int:
        node = find_node(hierarchy, f"{bare_id}_{company_id}")
        return (node.total_debit_paise - node.total_credit_paise) if node else 0

    def net_credit(bare_id: str) -> int:
        node = find_node(hierarchy, f"{bare_id}_{company_id}")
        return (node.total_credit_paise - node.total_debit_paise) if node else 0

    current_assets_excl_cash = net_debit(sg.CURRENT_ASSETS_GROUP_ID) - net_debit(sg.BANK_GROUP_ID) - net_debit(sg.CASH_GROUP_ID)
    current_liabilities = net_credit(sg.CURRENT_LIABILITIES_GROUP_ID)
    return current_assets_excl_cash, current_liabilities


async def cash_flow(db: AsyncSession, company_id: str, financial_year_id: str, start_date: str, end_date: str) -> dict:
    """Cash Flow (Phase 7C) - Operating Activities only, via the standard indirect method, sourced
    entirely from the existing `profit_and_loss`/`balance_sheet`. Investing/Financing are explicit,
    documented extension points (always `None`) - never fabricated. Mirrors Android's
    `generateCashFlow` exactly."""
    company = (await db.execute(select(Company).where(Company.company_id == company_id))).scalar_one_or_none()
    fy = (await db.execute(select(FinancialYear).where(FinancialYear.financial_year_id == financial_year_id))).scalar_one_or_none()
    fy_start = fy.start_date if fy else start_date

    # balance_sheet()'s currentAssetsPaise/currentLiabilitiesPaise are RESIDUAL buckets (Debtors/
    # Bank/Cash/Stock and Duties&Taxes are subtracted out into their own named fields) - "current
    # assets excluding cash/bank" must add Debtors/Stock back in (only Bank/Cash stay excluded);
    # "current liabilities" must add Duties&Taxes back in (only Loans/BranchDiv, long-term, stay
    # excluded). Mirrors the Android `generateCashFlow` fix exactly.
    if start_date <= fy_start:
        opening_assets_excl_cash, opening_liabilities = await _opening_current_assets_and_liabilities_paise(db, company_id)
        opening_cash = 0
    else:
        opening_day_before = (date_cls.fromisoformat(start_date) - timedelta(days=1)).isoformat()
        opening_bs = await balance_sheet(db, company_id, financial_year_id, fy_start, opening_day_before)
        opening_assets_excl_cash = opening_bs["currentAssetsPaise"] + opening_bs["sundryDebtorsPaise"] + opening_bs["stockInHandPaise"]
        opening_liabilities = opening_bs["currentLiabilitiesPaise"] + opening_bs["dutiesAndTaxesLiabilityPaise"]
        opening_cash = opening_bs["bankAccountsPaise"] + opening_bs["cashInHandPaise"]

    closing_bs = await balance_sheet(db, company_id, financial_year_id, fy_start, end_date)
    closing_assets_excl_cash = closing_bs["currentAssetsPaise"] + closing_bs["sundryDebtorsPaise"] + closing_bs["stockInHandPaise"]
    closing_liabilities = closing_bs["currentLiabilitiesPaise"] + closing_bs["dutiesAndTaxesLiabilityPaise"]
    closing_cash = closing_bs["bankAccountsPaise"] + closing_bs["cashInHandPaise"]

    pnl = await profit_and_loss(db, company_id, financial_year_id, start_date, end_date)
    net_profit = pnl["netProfitPaise"]

    change_assets = closing_assets_excl_cash - opening_assets_excl_cash
    change_liabilities = closing_liabilities - opening_liabilities
    operating = net_profit - change_assets + change_liabilities

    return {
        "companyName": company.name if company else "Apex Industrial Technologies Ltd.",
        "financialYearCode": fy.fy_code if fy else "FY 2026-27",
        "dateRangeLabel": f"{start_date} to {end_date}",
        "netProfitPaise": net_profit,
        "changeInCurrentAssetsExcludingCashPaise": change_assets,
        "changeInCurrentLiabilitiesPaise": change_liabilities,
        "netCashFromOperatingActivitiesPaise": operating,
        "netCashFromInvestingActivitiesPaise": None,
        "netCashFromFinancingActivitiesPaise": None,
        "openingCashAndBankPaise": opening_cash,
        "closingCashAndBankPaise": closing_cash,
        "netChangeInCashAndBankPaise": closing_cash - opening_cash,
    }


def _safe_divide(numerator: int, denominator: int) -> float:
    return 0.0 if denominator == 0 else numerator / denominator


def _safe_percent(numerator: int, denominator: int) -> float:
    return _safe_divide(numerator, denominator) * 100.0


async def ratio_analysis(
    db: AsyncSession,
    company_id: str,
    financial_year_id: str,
    start_date: str | None = None,
    end_date: str | None = None,
) -> dict:
    """ACTUAL ratios only (Phase 7C), derived purely from `balance_sheet`/`profit_and_loss` -
    never recalculated from raw ledgers. Mirrors the pure Android `RatioAnalysisEngine.compute`
    exactly, including its zero-denominator-returns-0.0 (never crashes) behavior."""
    bs = await balance_sheet(db, company_id, financial_year_id, start_date, end_date)
    pnl = await profit_and_loss(db, company_id, financial_year_id, start_date, end_date)

    # bs["currentAssetsPaise"]/["currentLiabilitiesPaise"] are RESIDUAL buckets (balance_sheet
    # subtracts Debtors/Bank/Cash/Stock and Duties&Taxes out into their own named fields) - the
    # true totals a Current/Quick Ratio needs must add those named buckets back in. Mirrors the
    # Android `RatioAnalysisEngine.compute` fix exactly.
    total_current_assets = bs["currentAssetsPaise"] + bs["sundryDebtorsPaise"] + bs["bankAccountsPaise"] + bs["cashInHandPaise"] + bs["stockInHandPaise"]
    total_current_liabilities = bs["currentLiabilitiesPaise"] + bs["dutiesAndTaxesLiabilityPaise"]
    quick_assets = total_current_assets - bs["stockInHandPaise"]
    equity = bs["capitalAccountsPaise"] + bs["reservesAndSurplusPaise"] + bs["netProfitForYearPaise"]
    capital_employed = equity + bs["loansLiabilitiesPaise"]
    net_sales = pnl["salesRevenuePaise"] + pnl["directIncomesPaise"]
    operating_cost = (
        (pnl["cogsPaise"] if pnl["isInventoryAware"] else pnl["purchasesPaise"])
        + pnl["directExpensesPaise"] + pnl["indirectExpensesPaise"]
    )

    return {
        "companyName": bs["companyName"],
        "financialYearCode": bs["financialYearCode"],
        "asOfDate": bs["asOfDate"],
        "currentRatio": _safe_divide(total_current_assets, total_current_liabilities),
        "quickRatio": _safe_divide(quick_assets, total_current_liabilities),
        "debtEquityRatio": _safe_divide(bs["loansLiabilitiesPaise"], equity),
        "grossProfitRatioPercent": _safe_percent(pnl["grossProfitPaise"], net_sales),
        "netProfitRatioPercent": _safe_percent(pnl["netProfitPaise"], net_sales),
        "operatingRatioPercent": _safe_percent(operating_cost, net_sales),
        "returnOnCapitalEmployedPercent": _safe_percent(pnl["netProfitPaise"], capital_employed),
    }
