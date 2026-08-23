# 33. PostgreSQL

## Schema
`server/app/infrastructure/database/models.py` mirrors the Room schema table-for-table
(`companies`, `financial_years`, `accounting_periods`, `account_groups`, `ledgers`, `vouchers`,
`journal_items`, `stock_items`, `voucher_stock_lines`, `stock_movements`, `gst_transactions`,
`settlement_allocations`, `gst_filing_periods`, `audit_logs`) plus server-only tables: `users`,
`user_company_roles`, `refresh_tokens`, `idempotency_keys`. Column names are the snake_case
counterpart of the Android Entity's camelCase field names, 1:1 - no schema redesign, just a
different naming convention per language. Money is always integer paise (never a float); dates are
ISO-8601 strings, matching how Room stores them.

## Environment note
This build environment has no Docker and no locally running PostgreSQL. The schema/migrations are
written against SQLAlchemy's portable column types and Alembic (`server/migrations/`,
`0001_initial`), so they apply unchanged to a real `postgresql+asyncpg://` DSN - but automated
tests *in this environment* run against SQLite (`sqlite+aiosqlite:///:memory:`, `StaticPool` so
every connection in a test shares the same in-memory database) via the exact same models. This is
an environment limitation, not a design choice - documented the same way Android UI screenshots
have been throughout every phase of this project.

## Invariants preserved server-side
- **Company isolation**: every table that isn't itself a lookup-by-id carries `company_id`; every
  query filters by it; `require_company_access` gates every route that names a `companyId`.
- **Financial-year boundaries**: `vouchers`/`journal_items`/etc. carry `financial_year_id`
  alongside `company_id` - a voucher can never silently cross a financial year.
- **Voucher immutability**: `CANCEL_VOUCHER` updates the existing row's `is_cancelled` flag and
  appends compensating reversal `journal_items` - it never deletes or rewrites the original
  voucher/journal rows. There is no UPDATE path for a voucher's financial content at all.
- **Idempotency**: `idempotency_keys` unique on `(company_id, idempotency_key)`.
- **Auditability**: `audit_logs` mirrors the Android-side audit trail shape; the server records
  its own copy independently rather than trusting a client-submitted audit description as fact.
