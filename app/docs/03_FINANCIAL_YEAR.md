# 03. Financial Year

## WHAT
The annual fiscal boundary for accounting transactions, typically running from April 1st to March 31st (or custom fiscal calendar).

## WHY
Financial reporting, tax calculations, and profit & loss determinations require strict annual isolation.

## RULES
- A voucher date must fall between `FinancialYear.startDate` and `FinancialYear.endDate`.
- Closing a financial year automatically transfers Net Profit/Loss to the Balance Sheet Reserves & Surplus ledger.
- Balance sheet accounts carry forward opening balances into the successor FY; P&L nominal accounts reset to zero.

## WHAT MUST NOT CHANGE
- The immutability of closed financial years once audited and locked.
