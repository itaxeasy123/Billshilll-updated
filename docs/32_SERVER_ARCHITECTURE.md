# 32. Server Architecture

```
server/app/
  api/
    routes/          auth, sync, reports, vouchers (business-level read resources), health
    dependencies/     get_current_user_id (JWT), require_company_access (tenant check)
  domain/
    errors.py         structured AppError hierarchy - one class per docs/26's error code list
    accounting/        double_entry.py, contra.py, allocation.py - independent Python
                        re-implementations of the same principles Android's DoubleEntryValidator/
                        VoucherPostingEngine enforce, not a port and not a redesign of the Kotlin
  application/
    commands/          one handler group per SyncEvent operation family (voucher_commands.py
                        covers every POST_*/CANCEL_VOUCHER operation - they share the same
                        validate -> persist -> update-balances shape; ledger_commands.py covers
                        CREATE/UPDATE/DELETE_LEDGER)
    queries/            read-only report computation (reports.py) - never trusts a client total
    services/           outbox_dispatcher.py (routes a decoded SyncEvent by `operation` to its
                        command handler, wrapped in the idempotency check), idempotency.py
  infrastructure/
    database/           SQLAlchemy async engine/session (base.py) + ORM models (models.py)
    security/           JWT issuance/verification, bcrypt password hashing
  schemas/              Pydantic DTOs mirroring the Kotlin SyncEvent envelope field-for-field
```

## Why one `voucher_commands.py`, not ten files
Every `POST_*`/`CANCEL_VOUCHER` operation persists the same shape (voucher header + journal lines +
optional stock lines + optional GST transactions + optional settlement allocations) and differs
only in which extra domain check applies (Contra's ledger restriction, a settlement's
over-allocation guard). Splitting that into one near-empty file per voucher type would be
premature abstraction without behavioral difference - the dispatcher
(`outbox_dispatcher.py`) still routes by exact `operation` string, so adding a genuinely different
operation later is additive, not a refactor of existing handlers.

## Request flow (a single Outbox item)
```
POST /api/v1/sync/outbox/batch
  -> require_company_access(user, companyId)          [403 TENANT_MISMATCH if not a member]
  -> for each item:
       decode payloadJson -> SyncEvent                 [malformed payload rejected, batch continues]
       idempotency check                                [replayed key returns the stored result]
       dispatch_event() -> apply_voucher_event() / apply_ledger_event()
         -> period-lock check, double-entry balance, Contra/allocation checks
         -> persist rows, update ledger balances
       store response, commit this item's transaction   [one bad item never rolls back the rest]
```

## What this server does NOT do
No PDF/print/document rendering, no CSV/JSON statutory export, no GSTR filing integration, no
Invoice-status (Draft/Partially Paid) modeling - all Phase 7. No direct "create a voucher" HTTP
call exists for the UI to use; the Outbox batch endpoint is the only mutation path Android's
client code is wired to (see `docs/27_SYNC_PROTOCOL.md`).
