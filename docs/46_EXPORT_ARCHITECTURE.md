# 46. Export Architecture & Data Interchange (Phase 7E)

## Status

Phase 7E of the amended Phase 7 scope. Builds one reusable export layer producing JSON/CSV/GSTR-JSON
from the OUTPUT of existing engines (accounting, reports, GST, invoice/document, voucher) - **zero
UI**. No file under `app/src/main/java/com/example/accounting/presentation/` was touched.

## The one rule that governs this whole phase

**Export is READ -> MAP -> SERIALIZE, never calculate.** Every export function reads
already-authoritative data (a DAO entity, an existing `generate*`/`report_queries.*` report, or
Phase 7D's `assembleDocumentData`), maps it to a distinct Export DTO (`domain.export.*` /
`export_service.py`'s dict shapes - never a Room entity or SQLAlchemy model serialized directly),
and serializes. No export function performs accounting/GST/report calculation; no GST figure is
ever reconstructed from a ledger name (`ledgerName.contains("CGST")`-style string guessing appears
nowhere in this codebase, confirmed by the Phase 7E structural audit before implementation began).

```
Accounting/Report/GST/Document Engines -> Authoritative Models -> Export DTO -> Export Engine -> JSON/CSV/GSTR-JSON -> API -> (future UI)
```

## What was already substantially done by 7C/7D (reused, not rebuilt)

The Phase 7E structural audit found that Phase 7C's report models (`domain/reports/ReportModels.kt`
/ `application/queries/reports.py`'s dict-returning functions) and Phase 7D's `DocumentData` were
already close to export-DTO shape - plain paise `Long`/`int` fields, no ORM leakage, no UI types.
7E's DTOs (`TrialBalanceExportDto`, `ProfitAndLossExportDto`, `BalanceSheetExportDto`,
`OutstandingExportDto`, `GSTSummaryExportDto`, `InvoiceExportDto`) are therefore genuinely thin
wrappers/mappers over that existing output - `AccountingRepository.kt`'s `toExportDto()` extension
functions and `export_service.py`'s re-shaping functions never recompute a single figure; they only
select/rename fields for the stable, distinct public export contract (Section 4/19 - "this protects
the database schema from becoming the public export contract").

## What was genuinely new in this phase

- The versioned envelope (`ExportMetadata`/`ExportJsonSerializer.envelope` / Python's
  `json_envelope.py`) - nothing before this phase wrapped an export in
  `schemaVersion`/`exportType`/`generatedAt`/`companyId`/`financialYearId`/`applicationVersion`/`data`.
- `VoucherExportDto`/`PartyExportDto`/`LedgerExportDto`/`GSTTransactionExportDto` - none existed in
  typed form on either platform before this phase.
- A generic, report-model-agnostic CSV engine (`domain/export/CsvEngine.kt` / `csv_engine.py`) -
  distinct from and never modifying Phase 7D's `domain.rendering.CsvExporter` (document-line-
  specific, frozen).
- The GSTR JSON serializer (`domain/export/GstrJsonSerializer.kt` / `gstr_json.py`) - no code,
  schema, or route for this existed anywhere before this phase.
- New `AppError`/`errors.py` codes: `EXPORT_FORMAT_UNSUPPORTED`, `EXPORT_SCHEMA_UNSUPPORTED`,
  `RESOURCE_NOT_FOUND` (both platforms), `INVALID_FINANCIAL_YEAR` (Python only - Kotlin already had
  an equivalent `AppError.InvalidFinancialYear`).
- Thin `/exports/...` API routes (Python) with `?format=` negotiation.

## Export Domain

`ExportFormat` (JSON/CSV/GSTR_JSON), `ExportType` (one per exportable kind of data), and
`ExportFormatSupport`/`supports_format` - the single source of truth for which `(exportType,
format)` combinations are valid. An unsupported combination (e.g. CSV for an Invoice, or GSTR_JSON
for a Voucher) is rejected with `AppError.ExportFormatUnsupported`/`ExportFormatUnsupported` -
never silently serialized wrong. `ExportRequest`/`ExportResult` round out the abstraction Section 3
asked for; `ExportResult.content` is always the fully-serialized artifact in one pass (Section 23 -
export is never partial/streaming).

## Future formats are extension points, not built now

PDF, XML, Excel, Tally-compatible formats, bank formats, and CMA export are explicitly NOT built in
this phase (Section 3/28) - `ExportFormat` is a plain enum specifically so a future format is an
additive case, never a rewrite of the DTO/engine layers underneath it. `GET /exports/.../pdf` does
not exist; PDF stays Android-only per Phase 7D's own scope (`docs/44_PDF_PRINT_SHARE.md`).

## Read-only guarantee

Every export function is read-only - confirmed directly in both test suites by comparing ledger
balances (and, on the Python side, the underlying row set) before and after every export call,
across every format. No export function ever creates a voucher, modifies a ledger, posts GST,
changes inventory, changes invoice status, or creates an outbox event.

## Company / Financial-Year isolation

Every export function requires and filters by `companyId`; FY-specific report exports (Trial
Balance, P&L, Balance Sheet, GST Summary/Transactions) additionally require `financialYearId`.
Python enforces this at the API boundary via the same `require_company_access` every other route
uses; Android's isolation is the same company-scoped-query discipline every DAO method in this
project already follows (there is no separate multi-tenant membership concept on-device - a single
local user's data is inherently scoped to whatever companies exist in that Room database).
Outstanding/Receivables/Payables follow the already-established outstanding-domain rule (lifetime/
company-wide, not FY-scoped - see `docs/39_OUTSTANDING_REPORTS.md`) rather than inventing a new FY
interpretation for exports specifically.

## Bugs found and fixed during testing

**Moshi silently omits null-valued map entries.** `ExportJsonSerializer`/`GstrJsonSerializer`'s
generic `Map<String, Any?>` adapter, by default, drops a key entirely when its value is `null`
rather than writing `"key":null` - caught by a test asserting `GSTTransactionExportDto.isService`
(an explicit, deliberately-always-`null`-today extension point, see `docs/49_GSTR_JSON_EXPORT.md`)
appears in GSTR JSON output. Without `.serializeNulls()`, the field vanished instead of visibly
signaling "not available." Fixed on both serializers - every export DTO's JSON now carries a
stable, consistent key set regardless of which fields are null, matching Section 21's determinism
requirement.

## What Phase 7E deliberately does not include

- Any UI - export is a domain/API-layer concern only, per the Phase 7 gate.
- PDF/XML/Excel/Tally/bank/CMA export formats - documented extension points (Section 28).
- OCR of any kind - explicitly out of scope (Section 29); a future OCR pipeline would produce a
  Draft Document through the existing Document Engine, never post accounting data directly.
- Automation wiring (Phase 7F) or GSTR *filing* (submitting to the tax authority) - this phase only
  produces the GSTR-shaped JSON document; filing/submission is out of scope entirely.
- Any change to `VoucherPostingEngine`, `DoubleEntryValidator`, `GstCalculationEngine`,
  `GroupAggregationEngine`, or any report-generation function - all read through unmodified.
