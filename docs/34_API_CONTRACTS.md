# 34. API Contracts

(Numbered 34, not 31 - `docs/31_PHASE_4_5_UX_DOMAIN_CONSISTENCY.md` already occupies slot 31.)

All endpoints are under `/api/v1/`. Structured errors (`docs/26_API_SECURITY.md`) always take the
shape `{"code": "TENANT_MISMATCH", "message": "..."}` at the matching HTTP status.

## Auth
| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/auth/register` | `{email, password}` | Creates a user, returns tokens immediately. |
| POST | `/auth/token` | `{email, password}` | Login. |
| POST | `/auth/refresh` | `{refreshToken}` | Rotates - old refresh token is revoked. |
| POST | `/auth/logout` | `{refreshToken}` | Revokes the refresh token. |

Response shape (`register`/`token`/`refresh`): `{accessToken, refreshToken, expiresInSeconds, tokenType}`.

## Sync (the only mutation path Android's client code calls)
| Method | Path | Header | Body |
|---|---|---|---|
| POST | `/sync/outbox/batch` | `Authorization: Bearer`, `Idempotency-Key` | `{companyId, deviceId, items: [OutboxSyncItemDto]}` |

`OutboxSyncItemDto`: `{syncId, entityType, entityId, operation, payloadJson, idempotencyKey, version, clientTimestamp}` -
`payloadJson` is the `SyncEvent` envelope (`docs/27_SYNC_PROTOCOL.md`), JSON-encoded as a string.

Response: `{success, processedCount, processedSyncIds, rejections: [{syncId, idempotencyKey, reason, conflictCode, serverVersion}], serverTimestamp}`.

## Business-level read resources
`GET /api/v1/{sales-invoices|purchase-bills|receipts|payments|credit-notes|debit-notes|contra|journals}?companyId=...&financialYearId=...`
- Each a filter over `vouchers` by `voucher_type`, not a table per endpoint. Read-only on this
  server - there is no POST variant, since Android never creates these directly (see 6.2/6.3: all
  mutation flows through Room -> Outbox -> `/sync/outbox/batch`).

## Reports (read-only, always server-computed)
`GET /api/v1/reports/trial-balance?companyId=...&financialYearId=...`
`GET /api/v1/reports/gst?companyId=...&financialYearId=...`

## Health
`GET /api/v1/health` -> `{status, version, timestamp}`, no auth required.
