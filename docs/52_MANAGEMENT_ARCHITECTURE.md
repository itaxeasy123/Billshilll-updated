# 52. Management + Subscription Architecture (Phase 7J — Architecture)

## Status

Structure only - no UI, no real implementation. This is Phase 7J's own first installment, the
same "architecture and contracts before implementation" discipline already used for 7H and 7I,
now applied one more time before any actual screen gets built. **"Phase 7J = UI, explicitly
last"** (every prior doc's stated gate) still holds - this document does not authorize starting
UI work; it is the audit and structural prep that has to exist first.

## What this phase actually is

A read-only audit of Phase 0-7I against the business areas Phase 7J's eventual UI will need to
call into (Invoice, Voucher, Receipt, Payment, Cash/Bank, Party/Ledger/Item, Settlement/
Outstanding, Reports, PDF/Excel/CSV/JSON Import/Export, Preview/Print/Share, OCR extraction, QR/
Barcode, Business/Profession master, HSN/SAC/GST item structure, Subscription/Entitlement),
followed by adding **only** whatever was genuinely missing. The audit found nearly everything
already exists as real, frozen, tested implementations - adding a second "management" interface
on top of them would itself be the "duplicate engine/API layer" this phase was explicitly told to
avoid. Five genuine gaps were found: **data import** (CSV/JSON/Excel -> suggestion), **QR/barcode
generation and scanning**, **Business/Profession master data**, **HSN/SAC classification**, and
**Subscription/Entitlement**. Everything else below is a map, not new code.

## Audit: what already exists (reused, nothing duplicated)

| Area | Already covered by | Status |
|---|---|---|
| Voucher (incl. Receipt/Payment/Contra/Journal) | `AccountingRepository.postVoucher`, `VoucherPostingEngine` (inside `core/database/DatabaseTransaction.kt`), `DoubleEntryValidator` | Frozen, Phase 0-2 |
| Invoice | `domain.invoice.Invoice`, `AccountingRepository.createDraftInvoice`/`postInvoice` | Frozen, Phase 7A/7B |
| Cash/Bank | Ordinary `Ledger` rows under the Cash/Bank system groups - no separate model | Frozen, Phase 0 |
| Party/Ledger/Item | `AccountingRepository.createParty` (:2571), `createLedger` (:1183), `createStockItem` (:717) | Frozen, Phase 0/4/7A |
| Settlement/Outstanding | `AccountingRepository.allocateSettlement` (:799), `generateOutstandingReport`/`generateReceivablesReport`/`generatePayablesReport` | Frozen, Phase 5/7C |
| Reports (Trial Balance/P&L/Balance Sheet/GST/Ledger Statement/Day Book/Outstanding/Cash Flow/Ratio Analysis) | `domain.reports.*`, `AccountingRepository.generate*` | Frozen, Phase 3/4/7C |
| PDF/CSV/JSON Export | `domain.export.*` (7E), `data/rendering/PrintAdapter.kt` + `domain.rendering.DocumentRenderer`/`JsonDocumentRenderer` (7D) | Frozen, Phase 7D/7E |
| Preview/Print/Share | `domain.rendering.DocumentData`/`DocumentRenderer<T>` assembly + `data/rendering/PrintAdapter.kt` (Android Print framework + Share intent) | Frozen, Phase 7D |
| OCR extraction contract | `domain.ocr.OcrIngestionAdapter` (`extractFromDocument`) | Frozen, Phase 7I - **not rebuilt here** |
| Bank reconciliation suggestions | `domain.reconciliation.ReconciliationAdapter` | Frozen, Phase 7I |
| Excel export | **Not built.** `domain.export.ExportFormat` (7E) is a frozen enum whose own docs already call out a future format as "an additive case, never a rewrite" - Excel is exactly that pre-planned extension point, deliberately left untouched here per the "never touch a frozen file" discipline this session has held everywhere else. Deferred to whichever future phase implements it. |

## The five genuine gaps

### Data Import (`domain/dataimport/`)

No import capability - CSV, JSON, or Excel - existed anywhere before this phase. `DataImportAdapter.parseFile`
follows the exact shape `OcrIngestionAdapter`/`ReconciliationAdapter` already established:
references an already-uploaded `DocumentAsset` by id, returns `ImportResult` - a set of
`ImportRowSuggestion`s, never created records. See `domain/dataimport/README.md` for its specific
boundary. Voucher/transaction bulk-import is explicitly excluded from this contract's scope
entirely (`ImportSuggestionType` covers only `PARTY`/`LEDGER`/`STOCK_ITEM`) - bulk-importing
double-entry transactions is a materially bigger safety question left for a future phase to decide
explicitly, not assumed here.

### QR/Barcode (`domain/qrbarcode/`)

No barcode/QR generation or scanning capability existed anywhere before this phase (7H's
e-Invoice `signedQrCode` is just a data field Sandbox returns, not a generation/scanning
capability of our own). `QrBarcodeAdapter` mirrors `OcrIngestionAdapter`'s exact shape:
`generateForStockItem` takes an already-fetched `StockItem` (never an id it would look up itself),
`scanImage` takes a caller-supplied `candidateStockItems` list and returns a nullable
`matchedStockItemId` suggestion, never a created item. See `domain/qrbarcode/README.md`.

### Business/Profession master (`domain/profession/`)

No profession/business-type classification finer than the existing `BusinessType`
(TRADING/SERVICE, Phase 4) existed. `BusinessProfession` is deliberately a plain, open data
record - not a closed enum - so a new profession is just a new value, never a code change; that's
what "must be extensible" means structurally. Carries no GST rate field anywhere - a profession is
classification context for a future GST/ITR module, never a source of tax truth. How this
eventually attaches to `Company`/`BusinessProfile` is an open question left for a future phase,
the same way `docs/45_DOCUMENT_BRANDING.md` left its own "7D vs 7G" boundary open. See
`domain/profession/README.md`.

### HSN/SAC/GST item structure (`domain/itemclassification/`)

Before this phase, HSN/SAC only ever existed as a free-text `hsnSacCode: String` field scattered
across `StockItem`/`InvoiceLine`/`TradeDocumentLine`/`GstTransaction`, with no structured backing -
exactly why the Phase 7E export audit found `GSTTransactionExportDto.isService` could only ever be
`null`. `HsnSacCode` (code, description, `isService`) finally gives that fact a real type - though
wiring it back into those frozen entities is left for a future phase, not done here. No GST rate
field here either, for the same reason as `BusinessProfession`: a code's real-world rate changes by
government notification, and the existing, frozen `GstCalculationEngine`/`GstTransaction.gstRatePercent`
already correctly treats rate as a per-transaction fact, never a lookup. See
`domain/itemclassification/README.md`.

### Subscription/Entitlement (`domain/subscription/`)

The most safety-sensitive of the five. `CompanySubscription` (one row per company per financial
year - `financialYearId`, never a raw date range, matching every other FY-scoped concept in this
codebase) + `SubscriptionPlanType` (FREE/PAID) + `EntitlementFeature` (Accounting, GSTR,
E-Invoice, ITR, Audit Report, CMA, OCR, Inventory, Advanced Reports, API Access) +
`SubscriptionEntitlementChecker` - a **pure, non-suspend** object (deliberately not an adapter
interface like the others; there's no external I/O here, only a boolean decision over already-known
data) with one function, `hasEntitlement`, returning a plain `Boolean`. Zero
`AccountingDao`/`AccountingRepository`/`VoucherPostingEngine`/`DoubleEntryValidator` reference
anywhere - structurally incapable of altering, deleting, or recalculating any accounting data
regardless of subscription state; a denied entitlement is entirely a future UI-layer concern (a
screen simply doesn't offer a gated action). `EntitlementFeature.ACCOUNTING` is included because
the phase scope named it, but whether core double-entry posting - offline-first and login-free
since this project's very first line in `README.md` - is ever actually gated behind it is an open
product question left for whichever future phase wires real checks into real feature code, not
decided here. See `domain/subscription/README.md`.

### Recurring Invoice generation (`domain/recurringinvoice/`)

Added after a scoping round-trip: "Invoice Management, dynamic" was initially read as document-
template customization (already fully covered by Phase 7D - see below) before the user clarified
they also meant scheduled/recurring invoice generation specifically. `RecurringInvoiceSchedule` +
`RecurringInvoicePeriod` + `RecurringInvoiceGenerationOutcome` mirror
`domain.recurring.RecurringVoucherSchedule`'s draft-first shape, with one structural
simplification: unlike `Voucher` (which needed a whole new `RecurringVoucherDraftEntity` table in
Phase 7F because Voucher has no draft concept), `Invoice` **already is** its own draft - zero
accounting effect until `postInvoice` sets its `voucherId` (Phase 7A). So a future implementation
needs no new draft type at all, only a thin `generateInvoiceIfDue`-style function reusing the
existing `createDraftInvoice`, plus a small idempotency table. `RecurringInvoicePeriod.periodKeyFor`
delegates directly to the existing, frozen `RecurringVoucherPeriod.periodKeyFor` (verified
byte-identical by a test) rather than duplicating it; only `isDue` is a small, deliberate parallel,
since it must read this type's own fields. See `domain/recurringinvoice/README.md`.

### Document architecture confirmed already covering "dynamic" template customization

Researched, not built - Phase 7D's `domain.rendering.TemplateModels.kt`/`DocumentData.kt` already
covers nearly everything raised: `TemplateColors`/`TemplateTypography`/`TemplateLayout` (font,
color, logo position, show/hide signature/header/footer/bank-details/terms, visible line-item
columns), `DocumentPartySnapshot` (seller/buyer), `RenderedDocument.Pdf` + the existing
`PrintAdapter` (PDF/print/A4), and the full GST line-item breakdown
(`DocumentLineData`/`DocumentTotals`: CGST/SGST/IGST/CESS, sourced from the frozen
`GSTRules.determineSupplyType`/`GstCalculationEngine` - the same 50:50-same-state-else-IGST rule
already correctly implemented, never re-derived by a renderer). Nothing was duplicated.

**One genuine, narrow gap found and added additively**: neither `DocumentData` nor
`DocumentReferenceInfo` had a ship/dispatch date field. Added `shipDate`/`dispatchDate: LocalDate? = null`
directly to `DocumentData` (both new, both default `null`, zero change to either existing
`DocumentData(...)` construction call site in `assembleDocumentData` - both already use named
arguments). Always `null` today - neither `Invoice` nor `TradeDocument` has an underlying source
field for this yet - the same "documented extension point, never a fabricated value" pattern
`docs/40_CASH_FLOW.md` already used for `netCashFromInvestingActivities`. A future phase that adds
the real field (and a migration) to those frozen types is what would make this non-null.

**Scanning a barcode to add an invoice line and auto-calculate tax/total** is not a new domain
model - it's wiring three already-existing pieces together (`domain.qrbarcode.QrBarcodeAdapter`,
the matched `StockItem`, and the frozen `GstCalculationEngine`). That's a workflow/UI concern for
the eventual Phase 7J UI implementation, not architecture to build now.

## Non-goals (explicit, matching every prior architecture-only phase)

- No UI, no Compose screen, no ViewModel.
- No accounting calculation performed outside the existing frozen engines - nothing in this phase
  or its future implementation may recompute a balance, tax figure, or report value itself.
- No auto-posting, no auto-ledger-creation, no auto-party-creation - every import/OCR suggestion
  requires a human review step before reaching `createParty`/`createLedger`/`createStockItem`/
  `postVoucher`.
- No OCR implementation (still a contract only, from 7I), no Sandbox.co.in HTTP calls (still a
  contract only, from 7H), no ITR/GST return filing, no automatic bank reconciliation posting.
- No duplicate posting engine, sync engine, or API client - every new contract in this phase has
  zero `AccountingDao`/`AccountingRepository` reference, same structural guarantee every 7H/7I
  contract already carries.
- No hardcoded GST rate on `BusinessProfession` or `HsnSacCode` - both are classification only.
- No accounting-data mutation reachable from subscription state - `SubscriptionEntitlementChecker`
  only ever answers a yes/no question.

## Requirement recorded for future implementation: inventory features must be AccountingMode-gated

Not implemented here (no UI/API code touched this phase). Researched while auditing Invoice/Cash
Flow's existing APIs: `domain.company.AccountingMode` (`ACCOUNT_ONLY`/`ACCOUNT_WITH_INVENTORY`,
Phase 4) already exists and already gates which figures appear in `generateProfitAndLoss`/
`generateBalanceSheet` (COGS/Stock-in-Hand only computed when inventory-aware) - but nothing gates
the *feature surface* itself. As of this audit, `ChartOfAccountsScreen.kt`'s Items tab and stock-
item creation (`MainAppScreen.kt`'s `onOpenCreateStockItem`) are shown unconditionally, and
`server/app/api/routes/` has zero `AccountingMode`/`accounting_mode` reference anywhere - an
`ACCOUNT_ONLY` company can see and call inventory features that shouldn't apply to it on either
platform today.

**Requirement for whichever future phase touches this UI/API surface**: inventory-related screens,
dialogs, and API endpoints (`StockItem` creation/listing, stock movements, and - once built - the
7I/7J `QrBarcodeAdapter`/inventory-relevant `DataImportAdapter` suggestions) must only activate
when the requesting company's `AccountingMode == ACCOUNT_WITH_INVENTORY`. This is a UI/API-surface
concern, not an engine one - `generateProfitAndLoss`/`generateBalanceSheet` already handle their
own half of this correctly; the gap is purely in what's *shown*/*callable*, not what's
*calculated*.

## UI rule recorded for the future UI phase (not acted on now - no UI touched this phase)

**No Toast/Toastify notifications.** When Phase 7J's actual UI work eventually begins, it must use
the project's existing UI feedback pattern only - `AccountingViewModel.snackbarEvents`
(`MutableSharedFlow<String>` + `emitMessage`), already wired through the app's Snackbar host - and
must not introduce any new Toast library or a second, competing notification system. Recorded here
now, before any screen exists, so it isn't rediscovered or violated once UI work starts.

## Forward-looking: Play Store publishing compliance for OCR/gallery/contact-reading

Not implemented here (no manifest change, no permission request, no consent UI - adding an unused
Android permission is itself a Play Store "sensitive permissions" policy risk before the feature
it justifies actually exists). Documented now because it's a direct, foreseeable consequence of
`domain.ocr.OcrIngestionAdapter` and `domain.dataimport.DataImportAdapter` eventually needing to
read a device image (camera or gallery) and, potentially, a device contact - so a future
implementation phase has this checklist ready rather than discovering it during a Play Console
submission.

**Android manifest permissions a future implementation will need** (only at the point each
feature is actually built, requested contextually at time of use per Android's runtime-permission
model, never all upfront at first launch):
- `android.permission.CAMERA` - only if OCR ever captures a photo directly, rather than only
  accepting an already-existing image.
- Reading an existing gallery image: `READ_MEDIA_IMAGES` (API 33+) / `READ_EXTERNAL_STORAGE` (API
  32 and below) - or, better, the **Android Photo Picker** (`ACTION_PICK_IMAGES`), which needs
  **no runtime permission at all** and is Google's explicitly recommended replacement for broad
  media-library access - worth defaulting to when this is actually implemented, to avoid the
  sensitive-permission declaration below entirely for the common case.
- `android.permission.READ_CONTACTS` - only if `DataImportAdapter` (or a future feature) ever
  offers "import a Party from your Contacts" - this is a **Play Console "Restricted" permission**
  requiring a separate declaration form and a narrow, justified use case; the Photo Picker
  precedent above (avoiding the permission entirely) does not have an equivalent for Contacts, so
  this one specifically needs the full policy review if ever built.

**Google Play Store policy requirements** (apply once any of the above ships, not before):
- A **Privacy Policy URL** is mandatory for any app requesting Camera, Photos/Media, or Contacts
  access - must be set in Play Console and linked in-app, and must specifically describe what's
  read, why, and whether/how it's transmitted (relevant here: OCR/import data should be described
  as processed on-device or sent only to a disclosed provider, never silently uploaded).
- The **Data Safety form** in Play Console must accurately declare every data type collected
  (Photos, Contacts) and its purpose (app functionality) and retention/sharing behavior - this is
  a Play Console submission step, not application code, but the app's actual behavior must match
  what's declared or the listing can be rejected/removed.
- **Restricted Permissions declaration**: `READ_CONTACTS` (and, if ever added, broad media access
  instead of the Photo Picker) falls under Play's Sensitive/Restricted permissions policy -
  requires an explicit Play Console justification form per release, approved before publishing.
- **Runtime disclosure**: Android (and Play policy) requires requesting each permission only when
  the corresponding feature is actually used, with an in-context explanation of why - never a
  blanket "grant everything" prompt at first launch.
- A **Terms of Service** is not a hard Play Store technical requirement the way a Privacy Policy
  is, but is standard practice for a financial-data app and worth having before publishing
  regardless of OCR/Contacts specifically - a separate, non-blocking recommendation, not a Play
  Store gate.

## Phase 7J-A: application-service contracts (Invoice, Voucher, numbering)

A further sub-step, explicitly labelled 7J-A, adding **application-service layer contracts** -
the clean interfaces a future UI's ViewModels would depend on, sitting between UI and
`AccountingRepository`/domain engines. Same discipline: interface only, no implementation.

### `InvoiceManagementService` (`application/invoice/`)

`createDraft`/`updateDraft`/`duplicateInvoice`/`cancelInvoice`/`search` (with `InvoiceFilter`:
query, `InvoiceStatus`, `ClosedRange<LocalDate>`, partyId - all reusing existing, frozen types,
`ClosedRange<LocalDate>` matching `generateDayBook`'s own convention). Unlike everything else in
7J, this is **not filling a gap** - `createDraftInvoice`/`postInvoice`/`cancelInvoice` already
exist and work; this is a facade contract for a future implementation to delegate to, not a new
capability. `updateDraft`/`duplicateInvoice` are genuinely new operations with no existing
equivalent. The absolute rule: this layer must never calculate an accounting fact itself - any
future implementation needing GST/stock/journal facts must go through the existing, unmodified
`TradingWorkflowEngine`, then the existing, unmodified `AccountingRepository` functions.

### `VoucherManagementService` (`application/voucher/`)

`createDraft`/`editDraft`/`postDraft`/`attachDocumentReference`. **Flagged explicitly, not
glossed over**: unlike `Invoice` (already draft-capable since Phase 7A), a generic `Voucher` has
**no draft concept today** - `VoucherPostingEngine` posts atomically and immediately, which is
exactly why Phase 7F had to build `RecurringVoucherDraftEntity` from scratch rather than reuse
anything Voucher itself provided. A future implementation of `createDraft`/`editDraft` needs its
own new draft entity, mirroring that same pattern - not built here. `postDraft` mirrors
`AccountingRepository.postVoucher`'s exact parameter shape (Voucher, idempotency key, stock lines,
GST transactions) so a future implementation is a direct delegation to the existing
`VoucherPostingEngine`, never a second posting mechanism. `attachDocumentReference` links an
already-uploaded `DocumentAsset` (by id, never duplicated) to a voucher as supporting evidence -
metadata only, zero accounting effect.

### `DocumentNumberConfig` (`application/numbering/`)

Strictly read-only domain logic, as requested - a data holder plus one pure `formatted()` method,
no generation/mutation anywhere. Real gap confirmed by re-reading the code: today,
`generateNextVoucherNumber`/`generateNextDocumentNumber` both hardcode their format
(`"${VoucherType.prefix}$yearPart-${4-digit-count}"`, e.g. `"SAL-2026-0001"`) - no company can
customize prefix, suffix, or padding. `DocumentNumberConfig` is the shape a future, per-company-
configurable scheme would need, without changing either existing function's current behavior.

### Architecture coverage check (asked directly, verified before building)

Confirmed, not assumed: Sundry Debtors/Creditors are ordinary `Ledger` rows under existing system
groups (Phase 0); Sale/Purchase, Sales Return (Credit Note)/Purchase Return (Debit Note) are all
built and frozen via `TradingWorkflowEngine.buildSale`/`buildPurchase`/`buildNote` (Phase 5);
Opening/Closing Stock already appears in `generateBalanceSheet`/`generateProfitAndLoss` when
inventory-aware (Phase 4). **GSTR-1/3B/4/9/9C return filing itself is NOT built** - only GSTIN
verification exists as a contract (`SandboxProviderAdapter.verifyGstin`); actual return generation/
submission remains exactly where `domain/sandbox/gst/README.md` already documented it: deferred,
not architecture built yet, its own future scope/safety decision.

## Testing

`Phase7JArchitectureTestSuite.kt` (pure JVM, structural-contract only, same pattern as
`Phase7ITestSuite.kt`/`SandboxIntegrationTestSuite.kt`, 39 tests total): confirms `DataImportAdapter`
and `QrBarcodeAdapter` are genuine interfaces with zero implementations and zero path to
`AccountingDao`/`AccountingRepository`/`PostingEngine`/`DoubleEntryValidator`; confirms
`ImportSuggestionType` deliberately excludes any Voucher/transaction suggestion type; confirms
`BusinessProfession`/`HsnSacCode` carry no rate/tax/percent-named field and that a brand-new
profession can be constructed without modifying the type; confirms `SubscriptionEntitlementChecker`
is pure/non-suspend with zero accounting-mutation path, and exercises `hasEntitlement`'s active/
inactive behavior directly; confirms `CompanySubscription` is keyed by `financialYearId` with no
raw date-range field; confirms `RecurringInvoicePeriod.isDue`'s due/not-due/inactive/out-of-window/short-month-clamping/
YEARLY-anniversary behavior and that `periodKeyFor` produces byte-identical output to the
existing, frozen `RecurringVoucherPeriod.periodKeyFor`; confirms `DocumentData.shipDate`/
`dispatchDate` exist as `LocalDate`-typed fields; confirms `InvoiceManagementService`/
`VoucherManagementService` are genuine interfaces with zero accounting-mutation path and zero
implementations, `cancelInvoice`/`postDraft` mirror their existing repository counterparts'
parameter shapes, `attachDocumentReference` never takes `DocumentAsset` directly (id-reference
only), and `DocumentNumberConfig` has no method beyond the one pure `formatted()` - no generation
or mutation logic anywhere on the type.

## Gap-analysis re-audit: one orphaned entitlement found and closed

The user explicitly asked for a fresh check on whether anything named across this phase's whole
history had been forgotten - not a re-confirmation of what was already audited. That check
cross-referenced every one of `EntitlementFeature`'s 10 values against an actual capability in the
codebase: `ACCOUNTING`->core engine, `GSTR`->`domain/sandbox/gst/`, `E_INVOICE`->
`SandboxProviderAdapter.requestEInvoiceIrn` + `domain/sandbox/einvoice/`, `ITR`->
`domain/sandbox/income_tax/` + `fetchForm26As`, `CMA`->`domain/cma/`, `OCR`->`domain/ocr/`,
`INVENTORY`->Phase 4's `AccountingMode`, `ADVANCED_REPORTS`/`API_ACCESS`->the existing Phase 7C
reports / existing server API. One value, **`AUDIT_REPORT`, had zero corresponding capability
anywhere** - an entitlement that gated nothing.

**Fixed**: `domain/sandbox/audit_report/README.md` - a sixth placeholder folder, matching the
other five exactly (status: not implemented, future scope, non-mutation boundary). A statutory/tax
Audit Report (e.g. Form 3CA/3CB/3CD under Section 44AB) is a government-compliance document in the
same family as GSTR/ITR, not a new architectural shape - and explicitly distinct from
`domain.audit.AuditLog` (Phase 0's internal edit-history trail, a different, pre-existing concept
this folder's name deliberately avoids colliding with). No code change - documentation only, since
this was a documentation gap (an unmapped entitlement), not a missing contract shape.
