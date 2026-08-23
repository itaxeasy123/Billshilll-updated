# 38. Financial Statements & Day Book (Phase 7C)

Covers Trial Balance, Profit & Loss, Balance Sheet, GST Summary, Ledger Statement, and Day Book.
See `docs/37_REPORT_ARCHITECTURE.md` for the rules shared by every report in this phase, and
`docs/39_OUTSTANDING_REPORTS.md`/`docs/40_CASH_FLOW.md`/`docs/41_RATIO_ANALYSIS.md` for the
remaining four.

## Trial Balance

Already existed on Android (`generateTrialBalance`) from Phase 3; Python's `trial_balance()`
existed only as a thin `debitPaise`/`creditPaise`-per-ledger shape. This phase extends it
**additively only** - every pre-existing field keeps its exact prior meaning (the existing
`test_idempotency.py` assertion on `row["debitPaise"]` is untouched and still passes):

- Per row, new: `groupId`, `openingDebitPaise`/`openingCreditPaise`,
  `closingDebitPaise`/`closingCreditPaise`.
- Top-level, new: `totalOpeningDebitPaise`/`totalOpeningCreditPaise`,
  `totalClosingDebitPaise`/`totalClosingCreditPaise`, `groupHierarchy` (via the new
  `group_aggregation.py` port - see `docs/37_REPORT_ARCHITECTURE.md`'s note on the dormant
  `groupId` cosmetic issue in this field).

Supports an optional `startDate`/`endDate` (validated to fall within the named financial year) and
`includeZeroBalance` (default `true`, consistent with the zero-balance-hide-not-delete rule).
Ledger/group filtering is done by the caller post-hoc on `rows` - the report itself always returns
the full chart, matching the Android behavior this mirrors.

## Profit & Loss

Unchanged calculation logic (already correct per the Phase 7 structural audit); newly available on
Python for the first time in this phase, since P&L structurally *requires* group-hierarchy
classification (Sales vs. Direct vs. Indirect Income/Expense cannot be separated without walking
the group tree) - this is what made porting `group_aggregation.py` mandatory rather than optional.
Classification never does `ledgerName`/`groupName` string matching; it walks the same
`GroupAggregationEngine`/`group_aggregation.aggregate()` hierarchy Trial Balance already built.
Gross Profit correctly switches to COGS (Opening Stock + Purchases - Purchase Returns - Closing
Stock) for inventory-aware companies, otherwise uses raw Purchases - both sides unchanged from
Phase 4.

## Balance Sheet

Unchanged calculation logic, newly available on Python. Full Assets/Liabilities/Equity taxonomy
built from **closing** balances (opening + every transaction ever, not just the report's date
window) via the same recursive group hierarchy. Suspense uses its own dedicated system group/ledger
identity (never name matching, never folded into ordinary Current Assets/Liabilities) and is
presented on whichever side its net balance falls - frozen behavior, re-verified unchanged by this
phase's audit. Round-Off is similarly frozen and untouched.

**Important for any future consumer**: `currentAssets`/`currentLiabilities` on this report are
*residual* buckets, not totals - see `docs/37_REPORT_ARCHITECTURE.md`'s "Real bugs found" section
and `docs/40_CASH_FLOW.md`/`docs/41_RATIO_ANALYSIS.md` for the two places this phase got that wrong
before fixing it.

## GST Summary

Extended additively: existing `outward`/`inward` tax-bucket sums are untouched; new top-level
`netTaxPayablePaise` (sum of outward CGST+SGST+IGST minus inward CGST+SGST+IGST) and
`netCessPayablePaise` (outward Cess minus inward Cess) are added to prepare for a future GSTR
export (Phase 7E) without needing a second calculation pass over the same `GstTransaction` rows.

## Ledger Statement

Unchanged calculation logic (running-balance-per-ledger), newly available on Python. Reads only
`JournalItemEntity`/`Ledger` opening balance - never recomputes from raw vouchers.

## Day Book

Previously existed only as ad-hoc client-side filtering inside `DayBookScreen.kt` - never a real,
reusable domain report. This phase extracts it as `DayBookReport`/`DayBookRow`
(`generateDayBook`/`day_book()`), reading the exact same data source (`Voucher` rows in a date
range) the screen already filtered, without touching the screen itself.

Each row carries an explicit `status` (`POSTED`/`CANCELLED`) - Day Book is deliberately the one
report in this phase that *surfaces* cancellation rather than silently omitting it, since a day's
activity log is exactly the place a user needs to see "this entry existed and was later cancelled,"
not have it vanish. `totalAmountPaise` only sums non-cancelled rows. `partyName` is resolved by
looking up the row's linked `Invoice` (if any) and then its `Party` - `null` for any voucher with no
linked invoice (e.g. a plain Journal or Contra entry), never a fabricated placeholder.

## API

```
GET /api/v1/reports/trial-balance?companyId=&financialYearId=&startDate=&endDate=&includeZeroBalance=
GET /api/v1/reports/profit-loss?companyId=&financialYearId=&startDate=&endDate=
GET /api/v1/reports/balance-sheet?companyId=&financialYearId=&startDate=&endDate=
GET /api/v1/reports/gst?companyId=&financialYearId=
GET /api/v1/reports/ledger?companyId=&ledgerId=
GET /api/v1/reports/day-book?companyId=&startDate=&endDate=
```

All thin, all `require_company_access`-gated, all read-only.
