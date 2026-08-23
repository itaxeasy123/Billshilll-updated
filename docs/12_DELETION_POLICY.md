# 12. Deletion and Cancellation Policy

## Master Ledger Deletion Rules
- **Permitted**: If and only if `accountingEntryCount == 0` and `isSystem == false`.
- **Blocked**: If `accountingEntryCount > 0`, deletion is rejected with `AppError.BusinessRuleViolation`. Historical audit references cannot be orphaned.

## Voucher Cancellation Rules
- Physical SQL `DELETE` is prohibited on posted vouchers.
- Cancellations execute as compensating accounting reversals:
  1. Ledger running balances are credited/debited with the opposite signs.
  2. Voucher status is updated to `CANCELLED`.
  3. Linked journal items are marked as cancelled.
  4. An audit log entry (`CANCEL_VOUCHER`) is written.
  5. An outbox mutation record is queued.
