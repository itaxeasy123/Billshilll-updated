# 25. Python Backend API Architecture

Implemented in Phase 6 (`server/`) - superseding this doc's earlier aspirational draft (Celery/
Redis/WebSockets were never built; not needed for what Phase 6 actually required).

## Framework & Technology Stack
- **API Server**: Python (FastAPI, ASGI, async throughout).
- **ORM / Database Engine**: SQLAlchemy 2.0 async - `asyncpg` for PostgreSQL in production, `aiosqlite`
  for this development environment (no Docker/Postgres available here - see `docs/33_POSTGRESQL.md`).
  Same models/migrations, only the connection string differs.
- **Migrations**: Alembic (`server/migrations/`, `0001_initial` creates the full schema).
- **Protocol**: JSON over HTTP, versioned under `/api/v1/`.

## Layering (`server/app/`)
`api/` (routes + auth/tenant dependencies) -> `application/` (command handlers per `SyncEvent`
operation + read-only report queries) -> `domain/` (independent Python re-implementations of
double-entry/Contra/over-allocation principles + the structured error hierarchy) -> `infrastructure/`
(SQLAlchemy models, JWT/password hashing, idempotency store). See `docs/32_SERVER_ARCHITECTURE.md`.

## Core Endpoints
- `POST /api/v1/auth/{register,token,refresh,logout}` - JWT access + revocable refresh tokens.
- `POST /api/v1/sync/outbox/batch` - the sole mutation ingestion path; Android's Outbox is the only
  caller. See `docs/27_SYNC_PROTOCOL.md`.
- `GET /api/v1/{sales-invoices,purchase-bills,receipts,payments,credit-notes,debit-notes,contra,journals}` -
  business-level read resources, each a filter over `vouchers` by type, not a table per endpoint.
- `GET /api/v1/reports/{trial-balance,gst}` - computed server-side, never from a client-submitted total.

Full request/response contracts: `docs/34_API_CONTRACTS.md`.
