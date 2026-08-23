# 15. Profit and Loss (P&L) Statement Specification

## Source of Truth
Read model computed from the Trial Balance's **period transaction movement only** (`transactionDebit`/`transactionCredit`), for a `companyId` + `financialYearId` (+ optional custom date range). Never mutates data.

## Corrected in Phase 3 (real bug fix)
The pre-Phase-3 implementation summed `closingDebit`/`closingCredit` (opening balance + period movement) for Income/Expense rows. Opening balance is a Balance-Sheet-only concept - if any Income/Expense ledger ever carried a nonzero opening balance, it would have leaked into Net Profit. `generateProfitAndLoss` now sums transaction-period figures exclusively.

## Classification
Same ID-based group hierarchy walk as Trial Balance (`GroupAggregationEngine`) - no name/`contains()` matching. Purchase Accounts (`GRP_PURCHASE`) are tracked as their own line, distinct from Direct/Indirect Expenses, per the project's Trading Account convention.

## Structure & Calculation
1. **Trading Section**: $\text{Gross Profit} = (\text{Sales} + \text{Direct Incomes}) - (\text{Purchases} + \text{Direct Expenses})$
2. **Income & Expense Section**: $\text{Net Profit} = \text{Gross Profit} + \text{Indirect Incomes} - \text{Indirect Expenses}$
3. A negative result is a genuine **Net Loss** - the sign is preserved internally (`Money.paise` stays negative); it is never converted into a fake positive figure.

## Suspense
`PrimaryGroup.SPECIAL_CONTROL` (Suspense) is excluded from P&L by construction - it simply never matches the `INCOME`/`EXPENSES` primary-group filters used to build the statement. No special-case exclusion code is needed or present.

## Year-End Closing (deferred, not Phase 3)
Net Profit/Loss is a **calculated statement result**, not automatically posted as a journal entry or transferred into Capital/Reserves. Year-end closing transfer is a separate future workflow; generating a P&L never writes to the ledger.
