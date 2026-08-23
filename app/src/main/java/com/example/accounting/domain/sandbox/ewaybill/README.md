# Sandbox e-Way Bill Services (future)

## Status: not implemented

No e-Way Bill contract exists yet anywhere in the `sandbox/` package - unlike GST verification,
e-Invoice, and Form 26AS, this service has no interface method or model on `SandboxProviderAdapter`
today. This folder exists purely to hold that future scope's documentation until its own dedicated
phase begins.

## Future scope

- e-Way Bill generation for goods-movement vouchers above the statutory threshold.
- Part-B updates (vehicle number / transporter details) on an already-generated e-Way Bill.
- e-Way Bill cancellation and validity-extension lookups.

## Boundary

Matches Phase 7F's established automation-safety pattern exactly: e-Way Bill generation must never
be triggered automatically from voucher posting. Even once built, this stays an explicit,
human-initiated action - a stock/goods-movement voucher being posted is not, by itself, permission
to call an external government API on the user's behalf. No code in this folder may create or
modify a `Voucher`, `Ledger`, or `VoucherStockLine`.
