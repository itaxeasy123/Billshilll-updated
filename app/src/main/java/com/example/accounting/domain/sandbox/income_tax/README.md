# Sandbox Income Tax Services (future)

## Status: not implemented

`SandboxProviderAdapter.fetchForm26As` (in the parent `sandbox/` package) is a typed contract
shape only - it defines what a 26AS fetch would return (`Form26AsResult`/`Form26AsEntry`, keyed by
PAN + `AssessmentYear`), nothing more. No fetching, storage, reconciliation, or display of 26AS
data is implemented.

## Future scope

- Fetching and caching 26AS/AIS/TIS statements per PAN/Assessment Year.
- Reconciling 26AS TDS entries against the company's own TDS-related ledgers, as an informational
  comparison view - never an automatic ledger correction.
- ITR pre-fill assistance, if ever built.

This is explicitly deferred to a **future, dedicated ITR/Tax phase** - not Phase 7H, and not an
incidental add-on to any other phase.

## Boundary

```
26AS / AIS / TIS
 -> ITR / Tax Information
 -> NOT Accounting
 -> NOT GST Accounting
 -> NOT Ledger
 -> NOT Voucher
```

This data reflects what the Income Tax Department's systems have on record for a PAN - it is
external, third-party-reported information, structurally unrelated to this company's own
double-entry books. Nothing here may ever create, edit, or reconcile a Voucher/Ledger entry
automatically; at most, a future UI could show it side-by-side with the company's own figures for a
human to compare.
