# 19. Audit Trail Architecture

## Statutory Invariant (MCA India Mandate)
- Audit log is strictly append-only.
- Every create, update, cancellation, period lock, and sync event captures:
  - `logId`: UUID.
  - `companyId`: Tenant reference.
  - `action`: Specific `AuditAction` enum.
  - `entityType` & `entityId`.
  - `performedBy`: Authenticated user ID.
  - `timestamp`: Unix timestamp (milliseconds).
  - `payloadJson`: Full serialized state snapshot before/after mutation.
- Direct tampering or purging of audit logs is permanently prohibited.
