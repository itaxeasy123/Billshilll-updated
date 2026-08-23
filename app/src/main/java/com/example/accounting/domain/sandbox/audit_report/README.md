# Sandbox Statutory Audit Report Services (future)

## Status: not implemented

No contract exists yet anywhere in the `sandbox/` package - unlike GST verification, e-Invoice,
and Form 26AS, this service has no interface method or model on `SandboxProviderAdapter` today.
This folder exists purely to hold that future scope's documentation until its own dedicated phase
begins.

## Why this exists

`domain.subscription.EntitlementFeature.AUDIT_REPORT` is a listed entitlement with no
corresponding capability anywhere in the codebase - every other entitlement value traces to a real
contract or engine (`GSTR`->`gst/`, `E_INVOICE`->`einvoice/`, `ITR`->`income_tax/`, `CMA`->
`domain/cma/`, `OCR`->`domain/ocr/`, `INVENTORY`->the existing `AccountingMode`, `ACCOUNTING`->the
core engine). This folder closes that gap the same way the other five did: a placeholder, not a
build.

## Future scope

A statutory/tax Audit Report - e.g. Form 3CA/3CB/3CD under Section 44AB of the Income Tax Act -
is a government-compliance document in the same family as GSTR/ITR, not a new architectural shape.
A future contract here would most likely mirror `CmaReportGenerator`'s pattern: consume the
already-computed Trial Balance/P&L/Balance Sheet (and possibly `domain.audit.AuditLog`, a
different, pre-existing concept - the internal edit-history trail, not this report) rather than
recompute anything.

## Boundary

Same as every sibling folder here: whatever is eventually built must never write directly to a
`Ledger`/`Voucher`/report table, and must never be confused with `domain.audit.AuditLog` (Phase 0's
internal edit-history/audit-trail record) - that is a different concept this folder's name
deliberately avoids colliding with.
