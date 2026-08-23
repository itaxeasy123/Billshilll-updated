# 06. Ledger

## WHAT
The fundamental account book entity holding balances, opening balances, running transactions, and tax/party configurations.

## WHY
Every double-entry line in a voucher posts directly to a ledger account, affecting its running debit or credit balance.

## RULES
- `currentBalancePaise` is computed strictly from: `OpeningBalance + TotalDebits - TotalCredits` (for Debit nature) or `OpeningBalance + TotalCredits - TotalDebits` (for Credit nature).
- A ledger with existing journal entries **cannot be deleted**.
- A ledger without any entries may be deleted.
- System ledgers (such as Suspense Account, P&L Account, Retained Earnings) cannot be deleted under any circumstances.

## WHAT MUST NOT CHANGE
- The prevention of deleting ledgers that contain posted historical transactions.
