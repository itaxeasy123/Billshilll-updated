# 53. Management Services (Phase 7J-B — Implementation)

## Status

Real, compiled, tested implementations - the first "real code" installment since Phase 7J's own
architecture-only run (7H/7I/7J/7J-A). **No UI, no new accounting engine.** Every method delegates
to the existing, frozen engines/repository functions; imports/OCR/QR only ever produce a Draft/
Suggestion for human review; Subscription gets real persistence with paid validity always derived
from the referenced `FinancialYear`'s own dates. "Phase 7J = UI, explicitly last" still holds - this
phase built the application-service layer a future UI will call into, not the UI itself.

## Scope recap and two deliberate consolidations

The user's 13 named areas (Invoice, Voucher, Receipt, Payment, Cash/Bank, Party/Ledger/Item,
Settlement/Outstanding, Reports, Import/Export, Preview/Print/Share, OCR suggestions, QR/Barcode,
Business/Profession, Subscription/Entitlements) map to concrete deliverables below. Two
consolidations, stated explicitly rather than silently decided:

- **Receipt/Payment has no separate service.** They are `VoucherType` values, not a distinct domain
  concept anywhere in this codebase - they ride on `VoucherManagementServiceImpl` (posting) and
  `SettlementManagementService` (allocation).
- **HSN/SAC gets no new facade.** The user's scope list named "Business/Profession," not "HSN/SAC" -
  `domain/itemclassification/HsnSacCode.kt` (Phase 7J) stays exactly as it was.

## Per-area implementation

### Invoice
`application/invoice/InvoiceManagementServiceImpl.kt` - real implementation of the frozen 7J-A
`InvoiceManagementService` interface. `createDraft`/`cancelInvoice` are direct delegations to
`AccountingRepository.createDraftInvoice`/`cancelInvoice`. `updateDraft` needed one new, narrow,
additive repository function - `AccountingRepository.updateDraftInvoice` - mirroring
`createDraftInvoice`'s exact persistence shape, rejecting an already-posted invoice with the same
`AppError.BusinessRuleViolation` `postInvoice` already uses for double-posting.
`duplicateInvoice`/`search` needed two more small, read-only repository additions:
`getInvoicesForCompany` (a company-wide `Flow<List<Invoice>>`, mirroring `getInvoicesForParty`'s
exact shape) and `getInvoiceLines` (a single invoice's lines, reusing the exact entity->domain
mapping `convertTradeDocumentToInvoice` already applies to `TradeDocumentLine`). `search`'s status
filter always re-derives via `getInvoiceStatus`/`InvoiceStatusEngine`, never a second computation.

### Voucher (+ new draft entity)
`application/voucher/VoucherDraft.kt` (new: `VoucherDraft`, `VoucherDraftLine`,
`VoucherDraftStatus`) mirrors `RecurringVoucherDraft`'s exact shape (Phase 7F) - deliberately not a
`Voucher` and never stored in `vouchers`/`journal_items`, so "no journal/ledger/balance/GST/
inventory effect until posted" stays structural. New Room tables `voucher_drafts`/
`voucher_draft_lines`/`voucher_document_references` (the last a thin metadata join table for
`attachDocumentReference`, distinct from `RenderedDocumentRecord`'s "rendered output" semantics).

`application/voucher/VoucherManagementServiceImpl.kt` implements the frozen 7J-A
`VoucherManagementService` interface. Its `createDraft`/`editDraft`/`postDraft` are typed as plain
`Voucher` (not `VoucherDraft`) by that frozen interface - this implementation treats the returned
`Voucher.voucherId` as the draft's own id while `PENDING_REVIEW` (a caller round-trips it: `createDraft`
hands back a `Voucher` whose `voucherId` IS the draft id, and `postDraft` is expected to receive that
same id back). `postDraft` is a **direct delegation to the existing, unmodified
`AccountingRepository.postVoucher`** - never a second posting mechanism.

### Receipt/Payment
See "Two deliberate consolidations" above - `VoucherManagementServiceImpl` (posting) +
`SettlementManagementService` (allocation).

### Cash/Bank
`application/banking/CashBankLedgerService.kt` - a read-only query facade. Cash/Bank is not a
separate domain model; it is ordinary `Ledger` rows under the Cash/Bank system groups (Phase 0,
frozen). Reuses the exact `groupId.startsWith(StandardSystemGroups.BANK_GROUP_ID)`/`CASH_GROUP_ID`
filter already independently duplicated in `CreateVoucherDialog.kt` and
`automation/reports/ScheduledReportTasks.kt` - a fourth call-site of the same rule, not new logic.

### Party/Ledger/Item
`application/party/PartyManagementService.kt`, `application/ledger/LedgerManagementService.kt`,
`application/inventory/StockItemManagementService.kt` - three thin, pure-delegation facades over
the existing, unmodified `AccountingRepository.createParty`/`createLedger`/`deleteLedgerSafely`/
`createStockItem`/`getParties`/`getLedgers`/`getStockItems`. No new capability.

### Settlement/Outstanding
`application/settlement/SettlementManagementService.kt` - a thin facade over
`AccountingRepository.allocateSettlement`/`getOutstandingInvoices`, giving a future non-UI caller a
stable entry point independent of the repository's much larger surface.

### Reports
`application/reports/ReportManagementService.kt` - one method per existing `generate*`/
`RatioAnalysisEngine.compute` (via the repository's own `generateRatioAnalysis` wrapper), each a
direct delegation - never a recalculation, matching the "reports consume engine output" rule
structurally enforced since Phase 7C.

### Import/Export
**Real CSV/JSON parsing** (the depth decision explicitly greenlit this): `data/dataimport/CsvJsonDataImportAdapter.kt`
implements the frozen `DataImportAdapter` interface with a hand-rolled RFC-4180-ish CSV parser and a
Moshi-backed JSON parser - no new library dependency. Lives in `data/`, not `domain/`, since it needs
real file I/O (the same layering reason `PdfDocumentRenderer.kt` lives in `data/`). Resolves the
referenced `DocumentAsset` via `AccountingDao` directly (a benign lookup read, matching the
interface's own `fileAssetId: String` shape, which already expects the implementation to resolve the
id itself) - **never `AccountingRepository`**, verified by a reflection scan in
`Phase7JBDataImportTestSuite`. `ImportFileFormat.EXCEL` stays unimplemented (only CSV/JSON were
greenlit), returning a structured failure rather than a silent no-op.

`application/imports/DataImportManagementService.kt` - `parseFile` is a thin pass-through to the
adapter; `reviewAndCreate` is the **only** function anywhere in this workflow that calls
`createParty`/`createLedger`/`createStockItem`, always in direct response to one already-reviewed
suggestion, never automatically for a whole file - kept on the service (not the adapter) specifically
to preserve the adapter's own zero-`AccountingRepository`-access guarantee.

`application/export/ExportManagementService.kt` (Android-only, per this phase's explicit scope
decision) - one method per existing `AccountingRepository.export*As` function (Phase 7E), each a
direct delegation. `server/app/api/routes/exports.py` is untouched - confirmed by `git status`, not
a runtime-testable claim.

### Preview/Print/Share
`application/document/DocumentPreviewService.kt` (Android-only) - direct delegations to
`AccountingRepository.assembleDocumentData`/`renderDocumentAsJson` plus the existing, unmodified
`PdfDocumentRenderer`/`PrintAdapter`/`ShareAdapter` (Phase 7D). PDF rendering itself needs an
Android `Context` and is not exercised by a pure-JVM test - the same, already-documented environment
limitation `PdfDocumentRenderer`'s own class doc records.

### OCR suggestions
`application/ocr/OcrSuggestionService.kt` - orchestration only. **`OcrIngestionAdapter` itself stays
deliberately unimplemented this phase** (no vision/ML library was in scope - a documented, explicit
boundary, not a silently-skipped gap). `requestExtraction` fails gracefully with a structured error
when no adapter is configured, never a crash. `reviewAndPrefillVoucherDraft` is the one piece of real
new logic: it builds a `PENDING_REVIEW` `voucher_drafts` row (narration/date pre-filled from the
extraction's own best-effort guesses) with **deliberately zero lines** - OCR never identifies a
ledger id, and fabricating one would be an accounting decision this service is not allowed to make.
A human adds real, ledger-mapped lines via `VoucherManagementServiceImpl.editDraft` before the draft
can ever be posted.

### QR/Barcode
**Real generation/decoding** (the depth decision explicitly greenlit this, as a pure non-accounting
utility): `data/qrbarcode/ZxingQrBarcodeAdapter.kt`, backed by `com.google.zxing:core` (new
dependency - `core` only, no camera/Android-embedded module, so no new manifest permission).
`generateForStockItem` builds a deterministic payload (`"SKU:<sku>|ID:<itemId>"`, falling back to
`"ID:<itemId>"`) and performs a real ZXing encode as a correctness check - `BarcodePayload` itself
still carries only the raw string, never an image, matching the frozen domain type. `scanImage`
decodes via `android.graphics.BitmapFactory` + ZXing's `RGBLuminanceSource`/`MultiFormatReader` -
Android-dependent, so its Bitmap-decode path is a documented environment limitation (see Testing,
below); its non-Bitmap early-exit paths (unknown asset) and `generateForStockItem`'s full encode
round-trip ARE exercised, since ZXing `core` has zero Android dependency.

`application/qrbarcode/QrBarcodeManagementService.kt` - resolves a `StockItem` by id (via
`AccountingRepository.getStockItems`) before delegating, since the frozen interface's
`generateForStockItem` takes a full `StockItem`, never an id.

### Business/Profession
`application/profession/BusinessProfessionService.kt` - a thin, read-only catalog facade over the
existing, frozen `StandardBusinessProfessions`. No persistence, no company attachment - an
explicitly open question this phase does not resolve, per `BusinessProfession`'s own doc comment.

### Subscription/Entitlements
**Real persistence, both platforms.** Android: new Room table `company_subscriptions`
(`CompanySubscriptionEntity`, unique `(companyId, financialYearId)` index). Python: new
`company_subscriptions` table (`CompanySubscription` model, same unique constraint) plus an
independent domain re-implementation (`domain/subscription/subscription.py` -
`SubscriptionPlanType`/`EntitlementFeature`/`has_entitlement`, "duplicate the principle, not the
code," the same discipline already applied to `domain/invoice/status.py`).

**Paid validity is always derived from the referenced `FinancialYear`'s own stored
`startDate`/`endDate` - never a hardcoded "1 Apr-31 Mar" literal anywhere in either
`SubscriptionManagementService` (Kotlin) or `subscription_service.py` (Python)**. Neither
`CompanySubscriptionEntity` nor the Python `CompanySubscription` table stores a date column at all -
`financialYearId`/`financial_year_id` is the only source of truth, matching every other FY-scoped
concept in this codebase. Both test suites deliberately use a non-1-Apr financial year fixture to
prove this.

**Mutation path: direct routes, not Outbox/SyncEvent** (a judgment call, documented per this
project's own discipline) - `POST/GET /api/v1/subscriptions`, mirroring the 7D Business-Profile
precedent. Administrative metadata, no offline-conflict risk, no billing integration exists yet to
make this sync-sensitive.

### (Cash/Bank settlement metadata) Bank/UPI Profile
Real persistence, both platforms, for `BankUpiProfile` (Phase 7G domain model, previously
unpersisted on either side) - `application/banking/BankUpiProfileService.kt` (Android, tenant-
asserted via the existing, reused `TenantMismatchException`) and `banking_service.py` +
`api/routes/banking.py` (Python), mirroring the 7D Profile precedent exactly (own table, own routes,
**not** Outbox-synced, same reasoning as Subscription). Pure settlement/contact metadata - no
Ledger/Voucher/JournalItem foreign key anywhere in either schema.

### Automation/Recurring
`application/automation/RecurringVoucherManagementService.kt` - a thin facade over the four
existing, unmodified Phase 7F functions (`getRecurringVoucherDrafts`/`updateRecurringVoucherDraft`/
`discardRecurringVoucherDraft`/`postRecurringVoucherDraft`).

## Explicit companyId/financialYearId scoping

Every new/implemented service takes `companyId` (and `financialYearId` where the underlying data is
FY-scoped) as an explicit parameter, or via a domain object that already carries it (`Invoice`/
`Voucher` already carry `companyId`) - never an implicit "current company" lookup. Verified
area-by-area; the one gap found and fixed during design: `OcrSuggestionService.reviewAndPrefillVoucherDraft`
takes an explicit `financialYearId` parameter (the `VoucherDraft` it produces requires one) rather
than inferring it.

## Database summary

| Side | File | Version | New tables |
|---|---|---|---|
| Android | `AppDatabase.kt` `MIGRATION_8_9` | 8 → 9 | `voucher_drafts`, `voucher_draft_lines`, `voucher_document_references`, `company_subscriptions`, `bank_upi_profiles` |
| Python | `server/migrations/versions/0005_management_layer.py` | `0004` → `0005` | `company_subscriptions`, `bank_upi_profiles` |

Two pre-existing Android migration-count assertions (`Phase0TestSuite.testRoomDatabaseCreation_Invariants`/
`testMigrationInfrastructure_ExplicitRegistry`) were updated for the 8→9 bump - the same precedent
every prior schema-version phase already set.

## New error codes

Python: `SUBSCRIPTION_ALREADY_EXISTS` (409) - the `(company_id, financial_year_id)` unique-constraint
violation case.

## Testing

Android (JUnit4, pure JVM unless noted): `Phase7JBInvoiceManagementTestSuite`,
`Phase7JBVoucherManagementTestSuite` (+ Automation facade), `Phase7JBPartyManagementTestSuite`
(+ Ledger/Item), `Phase7JBSettlementManagementTestSuite`, `Phase7JBReportManagementTestSuite`,
`Phase7JBDataImportTestSuite`, `Phase7JBQrBarcodeTestSuite`, `Phase7JBOcrSuggestionTestSuite`,
`Phase7JBSubscriptionTestSuite`, `Phase7JBBankingTestSuite` (+ Cash/Bank facade),
`Phase7JBDocumentAndExportFacadeTestSuite`, `Phase7JBCatalogFacadesTestSuite` - 70 new tests, all
passing. `Phase7JBTestSupport.kt` adds one new shared, public `Phase7JBAwareDao` decorator (real
in-memory backing for `account_groups`/`document_assets`/`stock_items`/`settlement_allocations` plus
all five new Phase 7J-B tables), composable with `Phase7BTestSuite.Phase7BAwareDao` exactly the way
`Phase7FTestSuite` already reuses it - the same public-decorator-composition precedent, not a new
pattern.

One new Robolectric test, `Phase7JBVoucherPostingTest.kt` (mirrors
`Phase7FRecurringVoucherPostingTest.kt` exactly), covers `VoucherManagementServiceImpl.postDraft`'s
real posting delegation - currently blocked by this environment's pre-existing Robolectric
`DefaultSdkProvider` infrastructure issue (confirmed still present by directly re-running it before
this phase began), not by anything in the code under test. `postDraft`'s *failure* propagation (no
real `AppDatabase` supplied) is fully covered by a pure-JVM test in `Phase7JBVoucherManagementTestSuite`.

Python (`pytest`): `test_subscriptions_7jb.py` (7 tests), `test_banking_7jb.py` (7 tests) - both
platforms' cross-tenant tests needed one specific fix: establishing the requesting user's bootstrap
`OWNER` membership requires a *committing* (POST) call first, since a read-only GET route's
`require_company_access` bootstrap-grant is only flushed, not committed, and would otherwise
silently roll back - the same precedent `test_documents_7d.py` already established (it uses a POST
for the identical reason).

**Verification**: Android - `compileDebugKotlin`/`compileDebugUnitTestKotlin` clean,
`testDebugUnitTest` 439 tests completed, 5 failed (the same pre-existing 4 Robolectric
`DefaultSdkProvider` failures - `ExampleRobolectricTest`, `GreetingScreenshotTest`,
`Phase7FRecurringVoucherPostingTest`, `SuspenseControlArchitectureTest` - plus this phase's own
1 new, identically-caused, documented failure). Python - `pytest`, 95/95 passing (81 existing + 14
new). `git status` confirms zero files under `presentation/` and zero lines in any frozen engine
file (`VoucherPostingEngine`, `DoubleEntryValidator`, `TradingWorkflowEngine`,
`GroupAggregationEngine`, `GstCalculationEngine`, `InvoiceStatusEngine`, `apply_voucher_event`,
`server/app/api/routes/exports.py`) were touched.

## Explicitly out of scope

Real OCR/ML implementation (contract-only, unchanged from Phase 7I), Excel import, GSTR/ITR filing,
any UI of any kind, any change to a frozen engine, Outbox/SyncEvent sync for Subscription or
Bank/UPI Profile (both are direct-route, documented future extension points exactly like 7D's own
Business-Profile scope cut), HSN/SAC facade (not named in this phase's scope).
