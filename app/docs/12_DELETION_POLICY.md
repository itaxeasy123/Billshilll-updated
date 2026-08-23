# 12. Deletion Policy

## WHAT
Rules governing the removal or cancellation of vouchers and master records.

## POLICY
1. **Ledgers**:
   - Ledgers with $\ge 1$ journal transactions **cannot be deleted**.
   - System ledgers (Suspense, Retained Earnings, Rounding) can **never** be deleted.
   - Zero-entry ledgers may be deleted.
2. **Vouchers**:
   - Historical vouchers cannot be deleted by raw SQL row drops without balance compensation.
   - Deleting a voucher reverses the debit/credit effects on ledger balances atomically and generates a corresponding audit log.
   - Deletions are forbidden in locked accounting periods.
