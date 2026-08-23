# 39. Outstanding / Receivables / Payables (Phase 7C)

## Status

The cleanest case in this phase: 7A already built every number this needs (Party, Invoice,
`InvoiceStatusEngine`, `computeOutstandingPaise`) - nobody had assembled them into a standalone
report yet. Previously, "outstanding" existed only as correct math reachable from inside the
Receipt/Payment dialog's data flow, pre-dating Phase 7A's Party model entirely.

## Shape

One shared, Party-aware report function backs all three: `generateOutstandingReport(companyId,
role: PartyRole? = null, today = LocalDate.now())` / `outstanding_report(db, company_id, role=None,
today=None)`. `generateReceivablesReport`/`generatePayablesReport` (and the `/receivables`/
`/payables` routes) are one-line wrappers passing `PartyRole.CUSTOMER`/`SUPPLIER`.

**Deliberately not scoped by financial year.** An unpaid invoice from a prior financial year is
still outstanding today - a customer's unsettled balance doesn't reset at a FY boundary the way a
P&L period does. Both platforms pool every non-cancelled, not-fully-settled invoice across all
financial years for the company; neither signature takes a `financialYearId` parameter. (An earlier
draft of this phase had a Kotlin-only `financialYearId` parameter that was never read by the
function body - a leftover from an earlier design, not an intentional scoping - it has been
removed so the two platforms' signatures match.)

Each `OutstandingReportRow` carries: `invoiceId`, `invoiceNumber`, `invoiceType`, `partyId`,
`partyName`, `voucherId`, `voucherNumber`, `date`, `dueDate`, `totalAmount`, `outstandingAmount`,
`status`, `daysOutstanding`, `agingBucket` - i.e. exactly Party + Invoice + Invoice Number/Date +
Due Date + Invoice Amount + Outstanding + Status + Days Outstanding, matching the report's original
specification.

## Zero new ledger math

The report filters `Invoice` rows by type (`SALES_INVOICE` for `CUSTOMER`, `PURCHASE_BILL` for
`SUPPLIER`, both when `role` is `null`), excludes `CANCELLED` and fully-settled invoices, and for
every remaining row:

- **Status** comes from the existing, unmodified `InvoiceStatusEngine.deriveStatus` /
  `domain/invoice/status.py` - never re-implemented here.
- **Outstanding amount** comes from the existing, unmodified `computeOutstandingPaise` /
  `compute_outstanding_paise` (moved to a single shared location in
  `application/queries/reports.py` in this phase - see below - but the computation itself is
  byte-for-byte unchanged).

No report code walks `SettlementAllocationEntity`/`settlement_allocations` directly to re-derive
what's been paid; it goes through the one shared function every other consumer already trusts.

## Aging

`AgingBucket`: `CURRENT`, `DAYS_1_30`, `DAYS_31_60`, `DAYS_61_90`, `DAYS_90_PLUS` - computed once,
in the domain/query layer (`agingBucketFor`/`_aging_bucket`). Never computed in Compose/UI.

`daysOutstanding = today − dueDate` when the invoice has a due date. **When an invoice has no due
date, `daysOutstanding` is `0` and the row is always bucketed `CURRENT`** - this is a deliberate
choice, not an oversight: without a due date there is no contractually meaningful basis to measure
lateness against, so the report does not guess one (e.g. falling back to the invoice date would
silently imply every un-due-dated invoice becomes "overdue" the moment it ages, which is not a
claim this report makes). A party that wants aging to be meaningful for these invoices should set a
due date on them; this is not enforced or defaulted by the report layer.

The report's `agingSummary: List<AgingBucketTotal>` (bucket, total outstanding, invoice count)
aggregates every row exactly once - no double counting between the per-row list and the summary.

## One shared `compute_outstanding_paise` (Python DRY cleanup)

`invoices.py` previously carried a locally-duplicated `_compute_outstanding_paise` (added in 7A
specifically to avoid importing from `voucher_commands.py`). Now that `reports.py` needs the
identical computation for `outstanding_report`, the local copy was removed in favor of one shared
`compute_outstanding_paise` living in `application/queries/reports.py` - the correct home for
read-side query logic. This is a behavior-preserving import change only; it does not touch
`voucher_commands.py` or any posting path. `invoices.py`'s `_invoice_dict` also now additionally
returns `sourceTradeDocumentId`, `totalAmountPaise`, and `outstandingAmountPaise` alongside its
existing fields (additive only).

## API

```
GET /api/v1/reports/outstanding?companyId=&today=
GET /api/v1/reports/receivables?companyId=&today=
GET /api/v1/reports/payables?companyId=&today=
```

All thin, all `require_company_access`-gated, all read-only.
