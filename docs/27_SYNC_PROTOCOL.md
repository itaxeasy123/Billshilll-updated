# 27. Sync Protocol

## The Outbox Event Envelope
Every mutation enqueued to `OutboxSyncEntity.payloadJson` is a versioned `SyncEvent`
(`app/src/main/java/com/example/accounting/domain/sync/SyncEvent.kt`), not the old bare
`{"voucherNumber":...,"amount":...}` string: `schemaVersion`, `eventId`, `idempotencyKey`,
`companyId`, `financialYearId`, `operation` (`POST_SALES_INVOICE`, `POST_PURCHASE_BILL`,
`POST_RECEIPT`, `POST_PAYMENT`, `POST_CONTRA`, `POST_JOURNAL`, `POST_CREDIT_NOTE`,
`POST_DEBIT_NOTE`, `POST_VOUCHER`, `CANCEL_VOUCHER`, `CREATE_LEDGER`, `UPDATE_LEDGER`,
`DELETE_LEDGER`), `aggregateType`, `aggregateId`, plus embedded `voucher`, `journalLines`,
`stockLines`, `gstTransactions`, `settlements`, `ledger`. Built directly from Room entities at the
posting/cancellation/ledger-mutation call sites in `VoucherPostingEngine`/`AccountingRepository`,
serialized via Moshi (`SyncEventSerializer`).

## Flow
```
Room commit (local, authoritative)
      |
Outbox insert (same atomic transaction, richer payload)
      |
OutboxProcessor (network-reconnect trigger, manual trigger, or timed backoff retry)
      |
POST /api/v1/sync/outbox/batch  (Idempotency-Key header on the batch itself)
      |
Server: tenant check -> per-item decode -> per-item dispatch to a command handler
      |
Response: processedSyncIds (mark SYNCED) + rejections (mark FAILED/CONFLICT, with a reason + code)
```
One bad/conflicting item in a batch never blocks the rest - each item is its own transaction
server-side and its own outcome in the response.

## Idempotency
Both layers: `OutboxSyncEntity.idempotencyKey` has a unique index locally; the server's
`idempotency_keys` table is keyed on `(company_id, idempotency_key)` - a replayed key returns the
stored prior result instead of reprocessing. See `docs/26_API_SECURITY.md`.

## Retry / Backoff
`OutboxProcessor` calls `SyncEngine.recordFailure()` (previously it duplicated the retry-increment
logic itself, uncapped) - `FAILED` after 5 attempts, `PENDING` otherwise. A real timed exponential
backoff (`2^retryCount x 1000ms`, capped at 60s) re-triggers `processPendingOutbox` even while
already online, not only on reconnect or a manual "Sync Now" tap.

## Conflict Strategy
- **Masters** (ledgers/groups/items): last-write-wins, with an audit trail of the overwrite.
- **Posted vouchers**: never silently overwritten. A resync of an already-processed voucher is
  caught by the idempotency-key check; a genuine correction is only ever a Credit/Debit Note or a
  compensating cancellation - never an in-place edit, client or server side.
