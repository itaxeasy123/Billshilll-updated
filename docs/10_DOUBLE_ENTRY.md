# 10. Double-Entry Validation Invariants

## Core Principles
The double-entry invariant is enforced as a strict pre-condition prior to any database write.

$$\sum_{i=1}^n \text{Debit}_i = \sum_{j=1}^m \text{Credit}_j$$

## Validation Checks in `DoubleEntryValidator`:
1. **Balance Check**: Absolute equality down to integer paise ($\Delta = 0$).
2. **Line Count**: Minimum 2 distinct lines ($n \ge 1, m \ge 1$).
3. **Non-Zero Amount**: Every item must have $\text{amount} > 0$.
4. **Single Direction**: A line cannot have both Debit and Credit amounts simultaneously.
5. **Financial Year Range**: Voucher date must be between `FY.startDate` and `FY.endDate`.
6. **Accounting Period**: Associated monthly period must have status `OPEN`.
7. **Tenant Isolation**: Every referenced `ledgerId` must exist in the caller's `companyId`.

## Dual-Validation Guarantee
- **Client-side**: Kotlin `DoubleEntryValidator` validates immediately upon user entry.
- **Server-side**: Python FastAPI endpoint re-validates the identical invariant before PostgreSQL ingestion.
