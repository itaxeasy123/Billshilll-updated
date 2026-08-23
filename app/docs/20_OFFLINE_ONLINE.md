# 20. Offline-Online Architecture

## WHAT
The offline-first operating strategy ensuring complete functionality without internet access.

## WHY
Accounting activities (billing, point of sale, voucher entry, daybook review) cannot be paralyzed by network latency or outages.

## RULES
- All operations write immediately to local Room SQLite.
- Mutations are assigned a client-side UUID, a timestamp, and an idempotency key before being pushed to `outbox_sync`.
- When `NetworkMonitor` detects network availability, `OutboxProcessor` triggers background sync batches to the remote backend.
