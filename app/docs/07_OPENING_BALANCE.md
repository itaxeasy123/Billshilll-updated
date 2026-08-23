# 07. Opening Balance

## WHAT
The initial financial position loaded into the chart of accounts at the start of a financial year or upon migration.

## WHY
Enables continuity between accounting periods or historical migration from legacy ERP systems.

## RULES
- Opening balance total debits must equal total credits.
- If there is a disparity between total opening debits and total opening credits during initialization, the unallocated difference is explicitly assigned to the **Suspense Account**.
- Once all opening adjustments are finalized, the Suspense Account balance returns to zero.

## WHAT MUST NOT CHANGE
- The mandate that unbalanced opening balances must be explicitly reflected in the Suspense A/c rather than hidden.
