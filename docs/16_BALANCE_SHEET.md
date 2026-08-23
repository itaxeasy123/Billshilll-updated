# 16. Balance Sheet Specification

## Source of Truth
Read model reusing the same closing-balance group hierarchy the Trial Balance already built (Assets/Liabilities/Equity legitimately use closing balances, unlike P&L). Never mutates data.

## Fundamental Accounting Equation
$$\text{Total Assets} \equiv \text{Total Liabilities} + \text{Equity} + \text{Current-Year Net Profit/Loss}$$

`balanceSheetDifference = totalAssets - totalLiabilities` (the model's `totalLiabilities` field already includes Equity + current-year P&L, matching the pre-Phase-3 naming convention). If nonzero, `generateBalanceSheet` throws `AccountingTransactionException(AppError.BalanceSheetNotBalanced)` rather than returning a silently-unbalanced report - no rounding trick ever hides an imbalance.

## Group Classification (corrected in Phase 3)
Every line (Bank, Cash, Debtors, Fixed Assets, Investments, Misc. Expenses (Asset), Loans, Duties & Taxes, Branch/Divisions, Capital, Reserves & Surplus) is resolved by exact group-ID ancestry via `GroupAggregationEngine`, not group-name text matching. "Current Assets"/"Current Liabilities" are computed as the primary-group-wide total minus the named sub-buckets, so nested standard subgroups (e.g. `GRP_DUTIES` living under `GRP_CURRENT_LIAB`) are attributed exactly once.

## Suspense (Section 18/19)
Uses the dedicated `GRP_SYS_SUSPENSE` / `LED_SYS_SUSPENSE` system identity exclusively - never ledger-name matching, never folded into ordinary Current Liabilities/Assets:
- Net **Debit** balance -> presented on the Assets side ("Suspense A/c (Control Debit)").
- Net **Credit** balance -> presented on the Liabilities side ("Suspense A/c (Control Credit)").
- Zero balance -> no line displayed.
- A nonzero Suspense balance never blocks statement generation. Requiring Suspense = 0 is a future year-end-closing workflow concern, not Phase 3.

## Current-Year Profit/Loss
Flows into the Liabilities+Equity total as a calculated figure (`netProfitForYear`) only. Generating a Balance Sheet never posts a journal entry, updates a ledger, or creates an audit/outbox record to represent or "balance" this figure - verified by `Phase3TestSuite.bs6_CurrentYearProfit_FlowsIntoEquityWithoutPostingJournalEntry`.

## Read-Only Guarantee
`generateTrialBalance`/`generateProfitAndLoss`/`generateBalanceSheet` only call `dao.get*` - never `insert*/update*/delete*`, and never touch `DatabaseTransaction`/`VoucherPostingEngine`.
