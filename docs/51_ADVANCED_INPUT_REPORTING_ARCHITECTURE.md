# 51. Advanced Input & Reporting Architecture (Phase 7I)

## Status

Architecture and contracts only, per the explicit principle adopted for all remaining structural
phases: *"First create the correct architecture, folders, interfaces, contracts and documentation.
Implement the actual business capability only when its dedicated phase arrives."* No HTTP client,
no OCR/ML library, no Android dependency, no implementation of any of the three interfaces below,
no UI.

## Where this scope comes from

Not a rediscovery of a single written "7I = X" line - no such line exists anywhere in the prior
docs, which only ever gated 7J on "7A-7I complete" without ever defining 7I's content. This phase
synthesizes three already-named-but-homeless signals into one phase, mirroring Phase 7H's own
precedent of bundling several loosely-related external-facing concerns under one phase letter:

1. The user's own words while scoping Phase 7H: *"prevent us from building half-finished
   GST/ITR/OCR/CMA functionality too early."* GST and ITR are 7H's `gst/`/`income_tax/`
   sub-modules; **OCR** and **CMA** were named but never assigned anywhere.
2. `docs/46_EXPORT_ARCHITECTURE.md`'s Phase 7E master spec already pre-agreed an OCR boundary rule
   ("OCR... never becomes direct accounting posting") for a capability that had never been given a
   contract shape.
3. Phase 7F's own bank-reconciliation "quarantine" diagram (`docs/50_AUTOMATION_ARCHITECTURE.md`)
   already specified the correct future architecture for bank-statement-import reconciliation
   *suggestions*, explicitly deferred, never built.

All three share the identical safety shape this project has used everywhere: **external/
unstructured input -> a suggestion or derived structure -> human review or an existing engine ->
never a parallel computation or auto-mutation path.**

## OCR Ingestion (`domain/ocr/`)

`OcrIngestionAdapter.extractFromDocument` - given an already-uploaded
`domain.rendering.DocumentAsset` (referenced by id, never duplicated), returns an
`OcrExtractionResult`: a best-effort guess (`confidenceScore` makes that explicit) at document
type, vendor name/GSTIN, date, total, and line items. Every field is a suggestion meant to pre-fill
the existing voucher-entry flow for a human to review and submit - nothing here ever creates,
edits, or posts a `Voucher`, or touches a `Ledger` balance.

## Bank Reconciliation Suggestions (`domain/reconciliation/`)

`ReconciliationAdapter.suggestMatches` takes a list of parsed `BankStatementLine`s and a list of
`candidateVouchers: List<Voucher>` (an explicit parameter, not a DAO query - this interface has
zero database access) and returns proposed `SuggestedVoucherMatch`es with a confidence level and a
reason, plus which lines matched nothing. This is the concrete contract for the architecture Phase
7F's safety diagram already promised:

```
Bank Data -> Imported Transaction -> Suggested Match -> Human Review -> Existing Posting Engine

Never: Bank Feed -> Auto-match -> Direct Ledger Posting
```

`BankLineDirection` (`DEPOSIT`/`WITHDRAWAL`) is deliberately its own type, not a reuse of
`core.common.DrCr` - a bank statement's "credit" and this app's ledger `DrCr.CREDIT` describe the
same event from opposite perspectives, and conflating them would be a real correctness bug waiting
to happen in whatever phase implements this.

## CMA Report (`domain/cma/`)

`CmaReportGenerator.generate` - unlike the two adapters above, this isn't an external-provider
boundary at all; a CMA (Credit Monitoring Arrangement, the standard Indian bank-loan financial
summary format) statement is entirely derived from the company's own books. It's a sibling of
Phase 7C's Report Architecture and follows that phase's exact rule - **reports consume engine
output, they never recalculate** - by taking the already-computed `TrialBalanceReport`/
`ProfitAndLossReport`/`BalanceSheetReport` as its only inputs (besides the tenant-context
`BusinessProfile`). `CmaReport` here is a deliberately minimal, illustrative subset - the full
multi-year projected-figures CMA format is out of scope for this architecture-only phase.

## What all three do NOT include

No HTTP/Retrofit/OkHttp usage, no Android dependency, no `AccountingDao`/`AccountingRepository`
reference on any interface (confirmed structurally via reflection tests, not just by convention),
no fake/stub implementation, no UI, no wiring into any existing screen or repository function.

## Testing

`Phase7ITestSuite.kt` (pure JVM, structural-contract only, matching
`SandboxIntegrationTestSuite.kt`'s pattern): each interface is confirmed to be a genuine interface
with exactly one operation and zero concrete implementations; a reflection-based check confirms no
method's parameter or return type touches `AccountingDao`/`AccountingRepository`/`PostingEngine`/
`DoubleEntryValidator`; `BankLineDirection` is confirmed to share no vocabulary with `DrCr`; and
`CmaReportGenerator.generate` is confirmed to take only already-computed report types, never raw
ledger data.
