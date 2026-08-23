# 21. Synchronization Architecture

## WHAT
The Outbox-pattern synchronization pipeline bridging Android client instances with the remote server.

## PIPELINE
1. **Mutation Capture**: Local mutations produce an `OutboxSyncEntity` record with `SyncState.PENDING`.
2. **Batch Dispatch**: `OutboxProcessor` queries pending items in FIFO order (`createdAt ASC`) and dispatches them via REST/JSON.
3. **Idempotent Ingestion**: Remote server validates the `idempotencyKey`; if previously processed, it returns the cached result without re-executing accounting writes.
4. **Conflict Resolution**: Last-write-wins with server version reconciliation for master records; voucher mutations are additive and conflict-free.
5. **State Transition**: Upon 200 OK, local state transitions to `SyncState.SYNCED`. On failure, exponential backoff is applied up to `maxRetries = 5`.
