# 11. Posting Engine

## WHAT
The transactional execution pipeline that validates, posts, updates running ledger balances, records immutable audit trails, and enqueues outbox sync items.

## TRANSACTION BOUNDARY
All operations within a post must succeed or fail as a single unit via `DatabaseTransaction`:
```
BEGIN TRANSACTION
  1. Validate Double Entry & Period Lock
  2. Insert Voucher Header
  3. Insert Journal Items
  4. Update Running Balances on affected Ledgers
  5. Insert Audit Log
  6. Enqueue Outbox Sync Entity
COMMIT
```

## WHAT MUST NOT CHANGE
- The all-or-nothing atomicity of voucher postings and ledger balance updates.
