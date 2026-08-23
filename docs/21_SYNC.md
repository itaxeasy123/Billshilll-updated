# 21. Synchronization Engine & Conflict Resolution

## Architecture
- **Outbox Table (`OutboxSyncEntity`)**: Stores mutations with FIFO sequence (`createdAt ASC`).
- **Idempotency Keys**: Unique cryptographic UUIDs assigned to each transaction prevent duplicate execution on the server.
- **Exponential Backoff**: Failed sync requests retry with $2^n \times 1000\text{ms}$ delay (capped at 60s).

## Conflict Resolution Matrix
- **Master Entities (Ledgers, Groups, Items)**: Last-Write-Wins (LWW) based on highest `updatedAt` timestamp.
- **Vouchers / Financial Transactions**: Additive-Only. Financial postings are never overwritten. In case of concurrent numbering collisions, the server re-assigns the sequence number while preserving the immutable debit/credit journal entries.
- **Conflict Notifications**: Any server-side conflict or reconciliation discrepancy is recorded in the Audit Log and notified to the user.
