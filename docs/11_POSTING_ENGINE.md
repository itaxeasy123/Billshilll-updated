# 11. Transaction Posting Engine

## Atomic Posting Steps (`DatabaseTransaction`)
Every financial posting executes as a single, atomic SQLite Room transaction:
1. **Pre-validation**: Invoke `DoubleEntryValidator.validate(...)`.
2. **Voucher Header Insert**: Persist `VoucherEntity` with unique `voucherId`.
3. **Journal Items Insert**: Persist `JournalItemEntity` rows referencing `voucherId`.
4. **Ledger Balances Update**: Calculate net debit/credit delta and increment/decrement `currentBalancePaise` on each touched ledger.
5. **Audit Trail Logging**: Append an immutable `AuditLogEntity` with action `CREATE_VOUCHER`.
6. **Outbox Queuing**: Insert `OutboxSyncEntity` with a unique `idempotencyKey` and `syncState = PENDING`.

If any step fails, SQLite automatically rolls back all changes, leaving the ledger in its previous state.
