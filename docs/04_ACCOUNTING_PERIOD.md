# 04. Accounting Period & Period Locking

## Overview
Financial years are partitioned into monthly `AccountingPeriod` intervals.

## Period States
- `OPEN`: Regular daily voucher posting and modifications allowed.
- `LOCKED`: Month-end reconciliation finalized; normal users cannot post or edit.
- `AUDIT_LOCKED`: Auditor review complete; strict read-only mode enforced for all roles.

## Invariant Enforcement
- Attempting to post, cancel, or edit transactions with a date falling into a locked period throws `AppError.PeriodLocked`.
