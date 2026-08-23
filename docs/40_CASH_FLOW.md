# 40. Cash Flow (Phase 7C)

## Status

New on both platforms. Scoped honestly, not fabricated: the audit found no Fixed-Asset-purchase or
Loan-drawn/repaid transaction categorization exists anywhere in the ledger structure - a full
3-section (Operating/Investing/Financing) statement that claims to reconcile to actual cash
movement would require inventing an attribution scheme this system doesn't have data for. **This
phase builds Operating Activities only** - the well-defined indirect-method calculation - plus the
*factual* opening/closing Cash & Bank balances, without forcing an artificial Investing/Financing
reconciliation.

## Calculation (indirect method, Operating Activities only)

`generateCashFlow(companyId, fyId, dateRange)` / `cash_flow(db, company_id, financial_year_id,
start_date, end_date)`:

1. **Net Profit** for the period - from the existing, unmodified `generateProfitAndLoss`/
   `profit_and_loss()`. Never re-summed from raw journal items.
2. **Opening** Current-Assets-excluding-Cash/Bank and Current-Liabilities:
   - If the period starts exactly at the financial year's start, these come from the ledgers'
     **opening balances** (via `openingCurrentAssetsExclCashAndLiabilitiesPaise`/
     `_opening_current_assets_and_liabilities_paise`, group-hierarchy-driven, opening cash is `0`
     since nothing has happened yet).
   - Otherwise, from a `generateBalanceSheet`/`balance_sheet()` call as of the day before the
     period starts.
3. **Closing** Current-Assets-excluding-Cash/Bank, Current-Liabilities, and Cash & Bank - from a
   `generateBalanceSheet`/`balance_sheet()` call as of the period's end.
4. **Change in Current Assets (excl. cash)** = closing − opening; **Change in Current
   Liabilities** = closing − opening.
5. **Net Cash from Operating Activities** = Net Profit − Change in Current Assets + Change in
   Current Liabilities.

`netCashFromInvestingActivities`/`netCashFromFinancingActivities` are `Money? = null` - an explicit,
documented extension point (see below), never a fabricated zero or guessed figure.

## Real bug found and fixed: residual-vs-total confusion

`generateBalanceSheet`/`balance_sheet()`'s `currentAssets`/`currentLiabilities` fields are
**residual** buckets: the function subtracts the named line items (Sundry Debtors, Bank, Cash,
Stock; Duties & Taxes) out of the primary-group total specifically so the Balance Sheet can display
them separately (see `docs/38_REPORTS.md`). The first implementation of `generateCashFlow`/
`cash_flow()` computed `"current assets excluding cash"` as `currentAssets − (bankAccounts +
cashInHand)` - **double-subtracting** Bank/Cash from a figure that had already excluded them,
producing a large negative number instead of the correct one (caught by a failing test asserting
`changeInCurrentAssetsExcludingCashPaise == 0`, which instead returned `-2500000`).

**Fix** (both Kotlin `AccountingRepository.generateCashFlow` and Python
`reports.py::cash_flow`), applied identically to both the opening and closing figures, and to both
the "period starts exactly at FY start" branch and the normal branch:

- `currentAssetsExcludingCash = currentAssets + sundryDebtors + stockInHand` (Bank/Cash are the
  only pieces meant to stay excluded - they're the report's own opening/closing cash figures).
- `currentLiabilities' = currentLiabilities + dutiesAndTaxesLiability` (Loans/Branch-Divisions
  are long-term and correctly stay excluded).

The separate `openingCurrentAssetsExclCashAndLiabilitiesPaise`/
`_opening_current_assets_and_liabilities_paise` helper (used only for the FY-start edge case) was
checked and confirmed **already correct** - it reads the full group-subtree node total
(`netDebit(CURRENT_ASSETS_GROUP_ID)`) directly from `GroupAggregationEngine`/`group_aggregation.py`,
not the residual `BalanceSheetReport` field, so it never had this bug.

## Test-infrastructure gap found and fixed (Android)

Independently of the calculation bug above, `Phase7CTestSuite`'s Cash Flow test initially failed
with `netProfit == 0` (expected `50_000_00`). Root cause: the suite's `SettlementAwareDao` test
double added real backing for `settlement_allocations` but not for `Group` - and the base
`FakeAccountingDao`'s `getGroupsByCompany`/`insertGroups` are permanent no-op stubs (always-empty /
no-op), by design, so that each phase's test suite only pays for the entity backing it actually
needs (the same pattern `Phase3TestSuite.GroupAwareDao` established). Since `generateProfitAndLoss`/
`generateBalanceSheet` require a real group hierarchy, every group-dependent figure silently
resolved to `0`. Fixed by adding the same `GroupAwareDao`-style overrides
(`getGroupsByCompany`/`getGroupById`/`insertGroup`/`insertGroups`/`updateGroup`/`deleteGroup`,
backed by a real `LinkedHashMap`) directly into `SettlementAwareDao`. This is a test-only fix - no
production code changed as a result of this gap. The Python suite's equivalent tests always seeded
`Group` rows explicitly (`_seed_groups`), so this gap was Android-only.

## API

```
GET /api/v1/reports/cash-flow?companyId=&financialYearId=&startDate=&endDate=
```

Thin, `require_company_access`-gated, read-only.

## Explicit extension point

Investing and Financing Activities are **not computed** - they remain `null` on both platforms
until a future phase introduces Fixed-Asset-purchase and Loan-drawn/repaid transaction
categorization with enough structure to attribute cash movement correctly. This is a deliberate,
documented limitation, not an oversight: inventing a categorization scheme without real domain data
backing it would silently produce wrong numbers that look authoritative - the exact failure mode
this project's standing rule ("if a requirement cannot be implemented safely from the current
domain, create an explicit extension point and document it") exists to prevent.
