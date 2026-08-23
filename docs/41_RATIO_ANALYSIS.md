# 41. Ratio Analysis (Phase 7C)

## Status

New on both platforms. The cleanest possible instance of "consume engine output, never recompute":
a pure function over two already-generated reports, with no DAO access of its own.

## Design

`RatioAnalysisEngine` (`domain/reports/RatioAnalysisEngine.kt`) is a Room-independent `object`, the
same pattern as `RoundOffEngine`/`GroupAggregationEngine` - testable with hand-built
`BalanceSheetReport`/`ProfitAndLossReport` values, no Room, no coroutines. `compute(balanceSheet,
profitAndLoss): RatioAnalysisReport` is the entire surface. `generateRatioAnalysis(companyId, fyId,
dateRange)` is a thin repository wrapper that fetches the two inputs via the existing, unmodified
`generateBalanceSheet`/`generateProfitAndLoss` and delegates. Python mirrors this as a single
`ratio_analysis()` function in `application/queries/reports.py` (no separate "pure engine" module,
since Python has no Room-independence constraint to satisfy - the calculation itself is identical).

All divisions are safe (`safeDivide`/`safePercent`, `_safe_divide`/`_safe_percent`): division by
zero returns `0.0`, never throws and never crashes a report.

## Ratios computed

- **Current Ratio** = Total Current Assets / Total Current Liabilities
- **Quick Ratio** = (Total Current Assets − Stock in Hand) / Total Current Liabilities
- **Debt-Equity Ratio** = Long-Term Loans / (Capital + Reserves & Surplus + Net Profit for Year)
- **Gross Profit Ratio %** = Gross Profit / Net Sales × 100
- **Net Profit Ratio %** = Net Profit / Net Sales × 100
- **Operating Ratio %** = (COGS-or-Purchases + Direct Expenses + Indirect Expenses) / Net Sales × 100
- **Return on Capital Employed %** = Net Profit / (Equity + Long-Term Loans) × 100

Net Sales = Sales Revenue + Direct Incomes. Operating cost uses COGS when the company is
inventory-aware (`ProfitAndLossReport.isInventoryAware`), otherwise raw Purchases - the same
Phase 4 rule `generateProfitAndLoss` itself already applies, not re-decided here.

## Real bug found and fixed: residual-vs-total confusion

Identical root cause to the Cash Flow bug in `docs/40_CASH_FLOW.md`: `BalanceSheetReport.
currentAssets`/`currentLiabilities` are **residual** buckets (the named line items - Sundry
Debtors, Bank, Cash, Stock; Duties & Taxes - are subtracted out for separate display), not true
totals. The first implementation of both `RatioAnalysisEngine.compute` and Python's
`ratio_analysis()` used `currentAssets`/`currentLiabilities` directly as if they were the totals a
Current/Quick Ratio needs - silently producing an understated ratio with no error, caught by a
hand-computed test (`testRatioAnalysisEngine_exactFormulas`) expecting `2.5`/`2.0` and initially
getting a wrong value back.

**Fix** (both sides, identical): reconstruct the true totals before dividing -

- `totalCurrentAssets = currentAssets + sundryDebtors + bankAccounts + cashInHand + stockInHand`
- `totalCurrentLiabilities = currentLiabilities + dutiesAndTaxesLiability`

`quickAssets = totalCurrentAssets − stockInHand`. The test's hand-computed expected values were
updated accordingly (Current Ratio `2.0 → 2.5`, Quick Ratio `1.5 → 2.0`) with an explanatory comment
- not weakened, corrected to match the now-verified-right formula.

**Any future consumer of `BalanceSheetReport` must apply this same reconstruction** - reading
`currentAssets`/`currentLiabilities` directly as a total is the specific mistake both real bugs in
this phase made independently.

## Actual vs. Projected/Estimated (CMA boundary)

`RatioAnalysisReport` represents **Actual** ratios only, computed from finalized, already-posted
report data. A future CMA (Credit Monitoring Arrangement) report - which projects/estimates future
ratios for bank submission - is an acknowledged future consumer of this same engine's *shape*, not
built in this phase: CMA would supply projected `BalanceSheetReport`/`ProfitAndLossReport`-shaped
inputs (estimated, not ledger-derived) to the same `compute()` function, and the caller would be
responsible for labeling the result as Projected/Estimated rather than Actual. No CMA-specific code
exists yet; this is a documented extension point, not an implementation.

## API

```
GET /api/v1/reports/ratios?companyId=&financialYearId=&startDate=&endDate=
```

Thin, `require_company_access`-gated, read-only.
