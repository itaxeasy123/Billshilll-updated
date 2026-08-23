# 03. Financial Year Accounting Boundary

## Overview
A `FinancialYear` defines the macro accounting timeframe (typically April 1 to March 31 in India).

## Invariants
1. **Date Containment**: Every posting transaction must have its voucher date strictly within `startDate` and `endDate`.
2. **Closing Invariant**: When a Financial Year is locked (`isLocked = true`), no new transactions, edits, or reversals are accepted.
3. **Carry Forward**: Closing ledger balances are rolled forward into the subsequent year's opening balances via an automated Year-End Closing Task.
