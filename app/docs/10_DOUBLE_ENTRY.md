# 10. Double Entry Validator

## WHAT
The core validation engine enforcing mathematical and accounting consistency before any voucher is posted to the database or outbox.

## RULES
1. $\sum_{i=1}^n \text{Debit}_i == \sum_{i=1}^n \text{Credit}_i$.
2. Every item must have either `debit > 0` or `credit > 0`, never both.
3. Every voucher must contain at least 2 distinct journal items.
4. Total transaction amount must be greater than zero.
5. All referenced ledger accounts must exist within the active `companyId`.

## ERROR BEHAVIOR
- Rejects unbalanced vouchers immediately with `DoubleEntryNotBalancedException` or `AppError.ValidationError`.
