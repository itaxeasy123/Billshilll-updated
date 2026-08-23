# Sandbox TDS/TCS Services (future)

## Status: not implemented

`Form26AsEntry` (defined in the parent `sandbox/` package, alongside `Form26AsResult`) already
models one TDS/TCS line item (deductor name, TAN, section, amount paid, tax deducted) purely as
part of the 26AS statement shape - that is the only TDS-related type that exists today. No
dedicated TDS module, adapter method, or return-filing (Form 26Q/27Q) contract exists yet.

## Future scope

- Deductor/TAN verification lookups.
- TDS return (26Q/27Q) filing-status checks.
- Section-code (194C, 194J, 194Q, etc.) reference lookups to assist a user selecting the right rate
  when recording a TDS-applicable voucher - assistive/informational only.

## Boundary

TDS on an actual voucher (e.g. a payment with tax deducted at source) must always be recorded
through the existing, unmodified posting path - a TDS ledger line is an ordinary journal line like
any other, entered and confirmed by a human, validated by `DoubleEntryValidator`, posted through
`postVoucher`. Nothing built in this folder may ever create or infer a TDS ledger entry
automatically from an external lookup.
