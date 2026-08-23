# Sandbox GST Services (future)

## Status: not implemented

`SandboxProviderAdapter.verifyGstin` (in the parent `sandbox/` package) is the only GST-related
contract that exists today - a read-only GSTIN verification lookup. Nothing else in this folder is
built.

## Future scope

- GSTR-1/GSTR-3B filing-status checks (read-only, mirrors `GstFilingPeriod`'s existing
  compliance-tracking role - never a second source of truth for a return's actual filed state).
- GST return filing itself, if ever built, is a write-side government action and would need its
  own explicit scope/safety decision (matching the discipline `docs/50_AUTOMATION_ARCHITECTURE.md`
  used for FY-closing and bank reconciliation) before any code is written.
- e-Invoice and e-Way Bill are deliberately their own sibling folders (`einvoice/`, `ewaybill/`),
  not folded into this one, even though all three sit under the GST umbrella in real life - keeping
  them separate avoids one oversized "GST" module later.

## Boundary

Whatever is eventually built here must never write directly to
`domain.taxation.gst.GstTransaction`/`GstFilingPeriod` or any `Ledger`/`Voucher`. GST figures in
this app are, and must remain, computed by the existing frozen `GstCalculationEngine`/`GSTRules`
from actual posted vouchers - never fetched from or overwritten by an external API call.
