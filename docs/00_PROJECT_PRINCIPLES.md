# Project Principles & Non-Negotiable Invariants

## 1. Zero Floating-Point Arithmetic Invariant
- All monetary representations across the entire system (SQLite Room, Domain Models, Outbox Payloads, Python Backend, PostgreSQL) MUST be stored as 64-bit integer minor units (**paise for INR**).
- `Money` encapsulation wrapping `Long paise` is the **single authoritative source of truth**.
- `BigDecimal` is permitted solely for display formatting and intermediate tax calculations with explicit scale and `RoundingMode.HALF_EVEN`.
- IEEE 754 floating-point types (`Double`, `Float`, `real`) are strictly prohibited in database schemas and accounting entities.

## 2. Double-Entry Conservation Invariant
- In every financial transaction:
  $$\sum \text{Debits} \equiv \sum \text{Credits}$$
- Minimum 2 line items per voucher.
- No zero-value journal entries.
- Single-side debit or credit assignment per line (no line with simultaneous debit and credit).

## 3. Atomic Multi-Entity Persistence
- Every voucher posting executes within an atomic database transaction:
  1. Header insert/verification.
  2. Multi-line journal item generation.
  3. Real-time balance accumulation on touched ledgers.
  4. Immutable audit trail entry creation.
  5. FIFO outbox sync queue insertion.

## 4. Strict Accounting Period & Financial Year Immutability
- Financial Year boundary is absolute. Vouchers outside the active FY date range are rejected at the domain validation layer.
- Closed or audit-locked accounting periods reject all new postings, edits, and cancellations.

## 5. Permanent Suspense Ledger Protection
- System account `LED_SYS_SUSPENSE` is automatically provisioned during company creation.
- It is permanently protected: cannot be renamed, edited, or deleted under any circumstance.
- Automated compliance jobs continuously monitor and report non-zero Suspense balances.

## 6. Audit Trail Immutability
- Accounting data is append-only.
- Direct row deletions of posted transactions are strictly prohibited.
- Cancellations are recorded via atomic compensating reversals with full audit logging.
- Audit logs cannot be modified, truncated, or dropped.

## 7. Explicit Tenant Isolation
- `companyId` is a mandatory attribute on every entity, primary key index, database query, and API payload.
- Cross-company data mutations are rejected by domain validators and database integrity constraints.

## 8. Offline-First Synchronization & Idempotency
- Local SQLite is the local authoritative source of truth for UI responsiveness.
- All mutations generate unique, cryptographically random `idempotencyKey` strings.
- Mutations queue into the Outbox in strict FIFO order (`createdAt ASC`) and synchronize with exponential backoff.
