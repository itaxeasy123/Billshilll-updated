# 13. Suspense Account Policy

## WHAT
The policy governing the permanent system Suspense Account (`LED_SYS_SUSPENSE`).

## RULES
1. The Suspense Account is permanently registered in every company's chart of accounts under `GRP_SYS_SUSPENSE`.
2. It absorbs opening balance imbalances during system migration.
3. It absorbs unallocated bank statement receipts/payments during automated bank feeds.
4. Automated daily compliance jobs (`SuspenseBalanceChecker`) verify whether the balance is zero and generate high-priority notifications if an uncleared balance exists.
5. Under no circumstances can the Suspense Account be hidden or bypassed.
