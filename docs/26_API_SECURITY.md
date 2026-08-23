# 26. API Security & Tenant Isolation

Implemented in Phase 6 (`server/app/infrastructure/security/`, `server/app/api/dependencies/`) -
superseding this doc's earlier aspirational draft.

## Authentication & Token Management
- JWT access tokens (`sub` = user id only; 15-minute expiration, `jwt_access_token_expire_minutes`).
- Refresh tokens are a separate opaque random string, NOT a JWT (a JWT can't be individually
  revoked) - stored server-side only as a SHA-256 hash (`refresh_tokens` table), rotated on every
  use, revoked on logout. Login is optional and sync-gated on the Android side - the app never
  requires a login to do local accounting.
- Passwords hashed with bcrypt (`passlib`); never stored or logged in plaintext.

## Authorization (Tenant, not yet full RBAC)
- `user_company_roles` links a user to a company with a role (`OWNER` today - `ADMIN`/`ACCOUNTANT`/
  `VIEWER` are reserved values, not yet enforced with different permissions per route; that
  differentiation is future work, not built speculatively in this pass).
- **Bootstrap rule**: the first user to sync a brand-new `companyId` is auto-granted `OWNER`; once a
  company has an owner, every other request for that `companyId` requires an explicit membership row
  or is rejected `403 TENANT_MISMATCH` - a client-supplied `companyId` is never trusted alone.

## Server-Side Accounting Validation
- Every event in an Outbox batch is independently re-validated server-side
  (`server/app/domain/accounting/`): double-entry balance, Contra Cash/Bank-only restriction,
  settlement over-allocation against the invoice's actual remaining outstanding, accounting-period
  lock, duplicate voucher number/id - a deliberate Python re-implementation of the same principles
  Android's `DoubleEntryValidator`/`VoucherPostingEngine` enforce, not a port of the Kotlin code and
  not a redesign of it. The server never trusts a client-submitted total as authoritative.
