# 14. Trial Balance Specification

## Source of Truth
The Trial Balance is a **read model** computed from posted journal entries + ledger opening balances for a `companyId` + `financialYearId`. It never mutates data (`AccountingRepository.generateTrialBalance`).

## Mathematical Definition
$$\sum \text{Closing Debit Balances} = \sum \text{Closing Credit Balances}$$

If this invariant fails, `generateTrialBalance` throws `AccountingTransactionException(AppError.TrialBalanceNotBalanced)` instead of returning a silently-unbalanced report.

## Group Classification (corrected in Phase 3)
Ledger rows are classified purely via `Ledger -> groupId -> Group -> PrimaryGroup`, walking the real `parentGroupId` hierarchy with exact ID comparisons (`GroupAggregationEngine`, `domain/reports/GroupAggregationEngine.kt`). Group **name** text is never matched/`contains()`'d for classification. `TrialBalanceRow.groupId` (added in Phase 3) carries the exact group ID for this purpose; `groupName` remains display-only.

## Group Hierarchy Aggregation (Section 21/22)
`TrialBalanceReport.groupHierarchy` holds the fully aggregated group tree: each node's `totalDebitPaise`/`totalCreditPaise` already includes every descendant, computed bottom-up so a ledger is counted exactly once. Cyclic `parentGroupId` relationships are detected and rejected as `AppError.GroupHierarchyInvalid` rather than recursed infinitely.

## Parameters
- `dateRange: ClosedRange<LocalDate>? = null` - optional custom period; must lie entirely inside the financial year and satisfy `start <= end`, else `AppError.InvalidDateRange`. Default `null` = full financial year.
- `includeZeroBalance: Boolean = true` - when `false`, zero-closing-balance ledger rows are omitted from `rows` (totals are identical either way, since a zero-balance ledger contributes zero regardless).

## Cancelled Vouchers
Cancellation is a compensating reversal (Phase 2), never a physical delete. Original and reversal journal lines both remain in the same ledger's transaction total, netting to the ledger's pre-posting balance automatically - no special-case code is needed in the Trial Balance for this.
