# LedgerPrime API Server (Phase 6)

FastAPI + SQLAlchemy (async) + PostgreSQL backend for LedgerPrime's optional cloud sync. The
Android app is fully offline-capable without this server; this only exists to receive what
Android's Outbox pushes once a device is online and the user has opted into Cloud Sync.

## Layout

```
app/
  api/            routes + FastAPI dependencies (auth, tenant)
  domain/         pure business-rule re-implementations (double-entry, Contra, over-allocation,
                   structured error codes) - independent of Android's Kotlin code, same principles
  application/     command handlers (one per SyncEvent operation) + read-only report queries
  infrastructure/ SQLAlchemy models/engine, JWT/password hashing, idempotency store
  schemas/        Pydantic DTOs mirroring the Kotlin SyncEvent envelope field-for-field
tests/            pytest, runs against SQLite in this environment (no Docker/Postgres available -
                   see docs/33_POSTGRESQL.md); schema is Postgres-portable via SQLAlchemy dialects
migrations/       Alembic - 0001_initial creates the full schema
```

## Local development

This environment has no Docker and no local PostgreSQL - `DATABASE_URL` defaults to SQLite
(`sqlite+aiosqlite:///./ledgerprime_dev.db`) so `uvicorn`/`pytest` both work without either. A real
deployment sets `DATABASE_URL` to a `postgresql+asyncpg://...` DSN (see `.env.example`) and runs
`alembic upgrade head` against it - no code changes needed, only the connection string.

```
py -m venv .venv
.venv\Scripts\pip install -r requirements\dev.txt
.venv\Scripts\python -m pytest
.venv\Scripts\uvicorn app.main:app --reload --port 8000
```

The Android emulator reaches this at `http://10.0.2.2:8000/api/v1/` by default (see
`SecureStorage.DEFAULT_API_BASE_URL`); a real device/deployment overrides this via
`SecureStorage.setApiBaseUrl()`.

## What this server does NOT do

No PDF/print/document generation, no CSV/JSON statutory export, no GSTR filing integration - all
deferred to Phase 7 (see `docs/30_CHANGELOG.md`). This server's job is exactly: receive Outbox
events, validate them independently (never trusting a client's totals), persist them, and serve
read-only reports computed from its own data.
