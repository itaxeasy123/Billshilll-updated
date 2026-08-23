# Sandbox.co.in Integration (Phase 7H)

## Status

**Architecture and contracts only.** No HTTP client, no Retrofit/OkHttp usage, no JSON
(de)serialization, no live API call, and no UI anywhere in this package. Everything here is a
pure Kotlin interface or a pure Kotlin data/enum type - safe to depend on from any layer without
pulling in network or Android dependencies.

## Files in this package

- **`SandboxEnvironment.kt`** - `SandboxEnvironment` (`TEST`/`LIVE`). Passed explicitly on every
  adapter call; nothing in this package stores a "current" environment as implicit state.
- **`SandboxIntegrationConfiguration.kt`** - `PricingType`, `SandboxServiceStatus`,
  `SandboxIntegrationConfiguration`. A company's per-service enablement record. Deliberately
  carries **no credential field** - see the boundary section below.
- **`SandboxProviderAdapter.kt`** - the adapter interface itself (`verifyGstin`,
  `requestEInvoiceIrn`, `fetchForm26As`) plus the minimal typed request/response models each
  operation needs (`AssessmentYear`, `GstinVerificationResult`, `EInvoiceIrnResult`,
  `Form26AsResult`, ...). No implementation of this interface exists yet.

## Phase 7H Integration Boundary

Sandbox.co.in is the external provider for GST/e-Invoice/e-Way Bill/income-tax lookups.

This phase establishes only the architecture and contracts - the interface shape, the domain
models, and the documented boundaries below. It does not implement a working integration.

External tax information such as **26AS, AIS, and TIS does not affect accounting records.**
No external API may directly create or modify:

- Voucher
- Journal
- Ledger
- Trial Balance
- Profit & Loss
- Balance Sheet

Every operation on `SandboxProviderAdapter` is a read/fetch from an external, government-linked
service. None of them take an `AccountingDao`/`AccountingRepository`, and none of them return a
type that represents a ledger mutation - that boundary is structural, not just documented (see
`SandboxIntegrationTestSuite.kt`'s structural-contract tests).

Credentials are never modeled here. `SandboxEnvironment` selects TEST vs. LIVE, but the actual API
key for each is expected to live in the existing, Keystore-backed
`com.example.accounting.core.security.SecureStorage` (`getCompanySetting`/`setCompanySetting`,
company-scoped), never as a field on any type in this package.

## Service subpackages (documentation only, for now)

```
sandbox/
├── gst/
│   └── README.md
├── income_tax/
│   └── README.md
├── tds/
│   └── README.md
├── einvoice/
│   └── README.md
├── ewaybill/
│   └── README.md
├── audit_report/
│   └── README.md   (Phase 7J - added to give domain.subscription.EntitlementFeature.AUDIT_REPORT
│                     a real placeholder to gate, matching the other five)
└── README.md   (this file)
```

Each subpackage holds only a `README.md` recording that service's intended future scope and
boundary. **No Kotlin file exists in any of them yet, and none should be added until that
service's own dedicated phase begins.** This is deliberate - a placeholder folder with a fake
`GstServiceAdapter`/`EWayBillClient` etc. that does nothing real would be worse than no folder at
all, since it looks implemented from a directory listing but isn't.

## Form 26AS: explicitly not an accounting concept

```
26AS
 -> ITR / Tax Information
 -> NOT Accounting
 -> NOT GST Accounting
 -> NOT Ledger
 -> NOT Voucher
```

The `fetchForm26As` contract (interface method + `Form26AsResult`/`Form26AsEntry`/`AssessmentYear`
models) can exist now, because it is only a typed shape for "what a 26AS fetch would return" - it
establishes the boundary (income-tax data, keyed by PAN + Assessment Year, never Financial Year or
GST filing period) without building anything. The actual 26AS *processing* - fetching, storing,
reconciling against TDS ledgers, surfacing in any UI - belongs to a future, dedicated ITR/Tax
phase, not to Phase 7H.

## Architecture-first principle for the remaining structural phases

First create the correct architecture, folders, interfaces, contracts, and documentation. Implement
the actual business capability only when its dedicated phase arrives. This keeps LedgerPrime clean
and prevents half-finished GST/ITR/OCR/CMA functionality from accumulating before it's actually
needed - a folder existing is not a claim that the feature works.
