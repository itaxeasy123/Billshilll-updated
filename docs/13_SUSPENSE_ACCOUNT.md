# 13. Permanent Suspense Account Specification

## Role & Invariants
1. **Control Group & System Ledger**:
   - `GRP_SYS_SUSPENSE` is a dedicated, protected `SPECIAL_CONTROL` group.
   - `LED_SYS_SUSPENSE` is seeded directly under `GRP_SYS_SUSPENSE` with company isolation (`LED_SYS_SUSPENSE_<companyId>`).
   - The Suspense ledger does NOT belong directly to `GRP_CURRENT_LIAB` or any operational liability group.
2. **Permanent Protection**:
   - Marked with `isSystem = true`.
   - Non-deletable, non-renamable, non-reparentable, non-reclassifiable.
   - Always available as a valid double-entry target for balancing adjustments.
3. **Flexible Balance Dynamics**:
   - Does NOT have a fixed CREDIT normal balance constraint; it supports both DEBIT (Dr) and CREDIT (Cr) balances dynamically.
4. **P&L and Balance Sheet Presentation**:
   - **Profit & Loss Exclusion**: Suspense accounts are strictly excluded from Profit & Loss calculations (never treated as Income or Expense).
   - **Dynamic Balance Sheet Placement**:
     - If Suspense carries a net **Debit** balance, it is presented dynamically on the **Assets** side under Suspense Control Debit.
     - If Suspense carries a net **Credit** balance, it is presented dynamically on the **Liabilities** side under Suspense Control Credit.
5. **Balancing Buffer & Period Locking**:
   - When opening balances entered for a company do not balance ($\sum \text{Opening Dr} \neq \sum \text{Opening Cr}$), the delta is absorbed by the Suspense A/c.
   - Having a non-zero Suspense balance does NOT block period locking, allowing operational continuity while tracking reconciliation.

