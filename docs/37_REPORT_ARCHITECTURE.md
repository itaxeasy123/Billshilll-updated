# 37. Report Management Architecture (Phase 7C)

## Status

Phase 7C of the amended Phase 7 scope. Builds the full Report Management layer on both platforms
- Trial Balance, Profit & Loss, Balance Sheet, GST Summary, Ledger Statement (already existed on
Android, ported to Python for the first time), Day Book, Outstanding/Receivables/Payables, Cash
Flow, and Ratio Analysis - with **zero UI** and **zero changes** to any posting/GST/inventory/
settlement engine, exactly as in 7A/7B.

## Core rule: reports consume engine output, they never recalculate

Every report function reads `Ledger`/`JournalItem`/`GstTransaction`/`Invoice`/`Party` data (or, for
Cash Flow/Ratio Analysis, the *output* of other report functions) and derives figures via the
existing, unmodified engines:

- `GroupAggregationEngine`/`group_aggregation.py` - the sole mechanism for classifying ledgers into
  Sales/Purchase/Direct/Indirect Income-Expense/Current Assets-Liabilities/etc. Never string or
  name matching.
- `InvoiceStatusEngine.deriveStatus` / `domain/invoice/status.py` - the sole source of invoice
  status (DRAFT/POSTED/PARTIALLY_PAID/PAID/OVERDUE/CANCELLED).
- `computeOutstandingPaise` / `compute_outstanding_paise` - the sole source of an invoice's
  outstanding amount, shared (not duplicated) between the Outstanding report and the invoice API.
- `RoundOffEngine`, GST calculation, Settlement allocation - untouched, read through their existing
  persisted results (`JournalItemEntity`/`GstTransaction`/`settlement_allocations`), never
  re-derived.

No report function ever walks raw `Voucher` rows to compute a figure that an existing report
function already provides (e.g. Cash Flow's net profit comes from `generateProfitAndLoss`, never
from re-summing income/expense journal items itself).

## Report Model -> Adapter separation

Domain report models (`domain/reports/ReportModels.kt`; Python: plain `dict`s built by
`application/queries/reports.py`) are immutable, presentation-agnostic data - `Money`/paise
integers, `LocalDate`, plain enums. They carry no PDF/CSV/JSON/Compose-specific shape. A future
PDF/Print/Share/Export consumer (Phase 7D/7E) adapts a report model into its output format; the
report model itself never imports or knows about any such consumer. Money stays a `Long` paise
value throughout - no currency symbol, no locale formatting, embedded in any domain model.

## API boundary

All new routes are `GET`-only (`server/app/api/routes/reports.py`), gated by
`require_company_access` exactly like every other authenticated route:

```
GET /api/v1/reports/trial-balance   GET /api/v1/reports/profit-loss   GET /api/v1/reports/balance-sheet
GET /api/v1/reports/gst             GET /api/v1/reports/ledger        GET /api/v1/reports/day-book
GET /api/v1/reports/outstanding     GET /api/v1/reports/receivables   GET /api/v1/reports/payables
GET /api/v1/reports/cash-flow       GET /api/v1/reports/ratios
```

Reports carry no mutation risk (pure computed data), so there is no offline-first tension to reason
about here, unlike the Party/Invoice/TradeDocument mutation paths - these routes exist for a future
non-Android caller (e.g. a web dashboard), matching the same rationale as 7B's trade-document
routes. Routes are thin: they parse query params and call straight into
`application/queries/reports.py`; no business logic lives in `routes/reports.py` itself.

## Date handling

Every report accepts an optional `startDate`/`endDate` (`dateRange: ClosedRange<LocalDate>?` on
Android). A financial-year label alone (e.g. "FY 2026-27") is insufficient for a real reporting
system, so all new and extended reports carry real ISO dates, preparing for future custom-date
reports without a later breaking change. Where a range is supplied, it must fall entirely within
the named financial year (`AppError.InvalidDateRange` otherwise) - this validation already existed
in `generateTrialBalance` and is reused, never re-implemented, by every function that calls it.

## Company + Financial-Year isolation

Every report function requires and filters by `companyId` (and, where relevant, `financialYearId`)
exactly like every other Phase 0-7B function - tested explicitly at the Repository layer (Android
JVM tests) and the API layer (`test_new_report_endpoints_require_authentication` plus the project's
pre-existing `test_tenant_isolation.py`, which is the canonical cross-tenant test and was not
duplicated here).

## Zero-balance and cancellation policy

`includeZeroBalance` (Trial Balance) is consistent with the project's frozen zero-balance-hide-
not-delete rule: a zero-balance ledger is filterable from a report's *display*, never deleted or
excluded from the underlying chart of accounts. A cancelled voucher's journal items are already
neutralized at posting time (the frozen `VoucherPostingEngine.cancel`/`apply_voucher_event`
behavior) - every report consumes that already-neutralized state as-is; Day Book is the one report
that additionally *surfaces* a voucher's Posted/Cancelled status explicitly (see
`docs/38_REPORTS.md`), rather than silently omitting cancelled entries.

## No report mutation

Every report-generating function is `suspend fun generate...(): ReportModel` with no side effect -
verified explicitly in Android JVM tests by comparing every ledger's `currentBalancePaise` before
and after report generation (see `Phase7CTestSuite.kt`'s cash-flow test). Python's query functions
never call `db.commit()` or any command-layer mutation function.

## Explicit extension points (documented, not fabricated)

Per the project's standing rule - "if a requirement cannot be implemented safely from the current
domain, create an explicit extension point and document it instead of inventing accounting
behavior" - two places in this phase deliberately return `null` rather than a guessed value:

- **Cash Flow's Investing/Financing sections** (`CashFlowReport.netCashFromInvestingActivities`/
  `netCashFromFinancingActivities`) - see `docs/40_CASH_FLOW.md` for why.
- **CMA (Credit Monitoring Arrangement) reporting** - acknowledged in `docs/41_RATIO_ANALYSIS.md`
  as a distinct future consumer of Ratio Analysis's *Actual* output, not built in this phase.

## One dormant, pre-existing issue - documented, not fixed

The Phase 7 structural audit found `TrialBalanceReport.groupHierarchy[].groupId` values are
company-suffixed internal IDs (e.g. `"GRP_SALES_<companyId>"`) - a Phase 3 read-model shape,
currently dormant since no UI/API renders `groupHierarchy` directly today. Modifying a frozen
Phase 3 type for a cosmetic, currently-inert concern was out of scope for this phase; it remains a
known item for whichever future UI/API layer first renders group hierarchy directly. None of this
phase's *new* report models repeat the pattern - their identifiers (`partyId`/`invoiceId`/
`voucherId`/`ledgerId`) are ordinary foreign keys, not internal system-group IDs.

## Real bugs found and fixed during testing

Testing surfaced two genuine calculation bugs (not test-infrastructure gaps) before freeze - both
stemmed from the same misunderstanding of `BalanceSheetReport`'s residual bucket shape. Full detail
in `docs/40_CASH_FLOW.md` and `docs/41_RATIO_ANALYSIS.md`; summarized here because it is the
single most important correctness lesson from this phase:

`BalanceSheetReport.currentAssets`/`currentLiabilities` (and the Python `balance_sheet()` dict's
`currentAssetsPaise`/`currentLiabilitiesPaise`) are **residual** figures -
`generateBalanceSheet`/`balance_sheet()` subtract the named buckets (Sundry Debtors, Bank, Cash,
Stock; Duties & Taxes) out of the primary-group total specifically so the Balance Sheet can display
them as separate line items. Any *new* consumer that needs a true total (as Ratio Analysis's
Current/Quick Ratio and Cash Flow's working-capital-change both do) must add those named buckets
back in - treating the residual field as if it were already the total silently produces a wrong
number with no error. Both `RatioAnalysisEngine`/`ratio_analysis()` and `generateCashFlow`/
`cash_flow()` were fixed to reconstruct the true totals explicitly, with an in-code comment
explaining why. Any future report consuming `BalanceSheetReport` must apply the same reconstruction
rather than reading `currentAssets`/`currentLiabilities` directly as a total.

## What Phase 7C deliberately does not include

- Any UI - `ReportsScreen.kt`/`DayBookScreen.kt` are not touched; new report-generation capability
  is added without being wired into any screen, per the Phase 7 gate (UI is Phase 7J).
- Document Template Engine, PDF/Print/Share, Export/GSTR JSON, Automation wiring, Business/
  Individual Profile branding - Phases 7D/7E/7F/7G respectively.
- Any change to GST calculation, inventory valuation, Round-Off/Suspense protection, Contra
  restriction, or settlement allocation - all frozen and independently re-verified unchanged as
  part of this phase's freeze audit.
