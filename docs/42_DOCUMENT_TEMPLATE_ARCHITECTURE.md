# 42. Document Template & Rendering Architecture (Phase 7D)

## Status

Phase 7D of the amended Phase 7 scope. Builds the foundation for invoice/document templates, PDF,
print, share, and branding - **zero UI**. No file under
`app/src/main/java/com/example/accounting/presentation/` was touched; no Compose screen, no
template-settings screen, no navigation change, no PDF/print/share button on any existing screen.
UI integration is deferred to Phase 7J.

## The one rule that governs this whole phase

**A renderer never becomes an accounting engine.** Every value a template/PDF/JSON/CSV consumer
sees comes from `AccountingRepository.assembleDocumentData` (Android) /
`document_service.assemble_document_data` (Python) - the *only* function in this phase that reads
`Invoice`/`TradeDocument`/`GstTransaction`/`Party`/`Ledger`/`Company`/`BusinessProfile` data. It
never recomputes GST, totals, or ledger balances; for a posted Invoice every tax/total figure comes
straight from the already-persisted `GstTransaction`/`Voucher` rows, and for a draft Invoice or any
non-posting `TradeDocument` (neither of which ever has a `GstTransaction`) it calls the existing,
unmodified `GstCalculationEngine.calculateDetailed` - it never reimplements GST math. See
`docs/43_DOCUMENT_RENDERING.md` for the full assembly rules and the Android/Python asymmetry this
creates.

```
Accounting Engine -> Invoice/TradeDocument Domain -> assembleDocumentData -> DocumentData
                                                                                  |
                                                                                  v
                                                                          Template Engine
                                                                                  |
                                                          +----------+-----------+----------+
                                                          v          v           v          v
                                                         PDF       Print       Share    JSON/CSV
```

## Document Data Model

`domain/rendering/DocumentData.kt` - a plain, immutable representation with no Compose/PDF-library
dependency: `DocumentData` (header + `seller`/`buyer` `DocumentPartySnapshot`s, `items:
List<DocumentLineData>`, `totals: DocumentTotals`, `paymentInformation`, `references`, `terms`,
`branding`, `isPosted`, `accountingVoucherNumber`). `DocumentLineData.quantity` is nullable - Section
4's "quantity may be optional for non-inventory/service documents" requirement - see the known
limitation below for how that's currently inferred. Money stays `Long` paise throughout,
`Quantity`/`LocalDate` otherwise - no currency symbol, no formatting baked in.

Python mirrors the same shape as a plain `dict` (matching this project's established
`application/queries/reports.py` convention of returning dicts rather than typed DTOs) rather than
introducing a new typed-model layer Python doesn't otherwise have.

## Party representation - reuses Phase 7A, never a second Customer/Supplier table

`DocumentPartySnapshot` is assembled fresh at render time from the existing
`Party`/`Ledger`/`Company`/`BusinessProfile` rows - never a stored, independently-maintained copy.
Sale-direction document types (`SALES_INVOICE`, `CREDIT_NOTE`, `QUOTATION`, `PROFORMA_INVOICE`,
`SALES_ORDER`, `DELIVERY_NOTE`) render the company as seller and the Party as buyer; purchase-
direction types (`PURCHASE_BILL`, `DEBIT_NOTE`, `PURCHASE_ORDER`, `RECEIPT_NOTE`) render the
reverse. This is presentation-only (`isSalesDirection`/`_is_sales_direction`) - it never affects
which ledger a posting actually debits/credits, that remains `TradingWorkflowEngine`'s frozen
classification.

## Business / Individual Profile - separate from Company, never duplicating statutory data

`BusinessProfile`/`IndividualProfile` (one each per companyId, same 1:1-with-Ledger convention
`Party` already established) hold document-branding preferences - business name, address, bank
details, UPI, logo/signature/QR asset references, terms & conditions. `Company`'s own
`gstin`/`pan`/`address`/etc. remain the sole authoritative statutory source; a profile's own copies
are independently editable *rendering* preferences (e.g. a shorter trading name on invoices), never
a correction to Company. See `docs/45_DOCUMENT_BRANDING.md` for the full branding design and the
open 7D-vs-7G boundary question the structural audit flagged.

## Template Model

`DocumentTemplate` - company-scoped, versioned (`templateId` groups every version of one lineage;
each edit creates a new row with `version + 1`, archiving the previous one rather than mutating
it). `TemplateVisualConfig` (layout/typography/colors) is persisted as one JSON blob
(`configJson`/`config_json`) - the same "structured value object as a JSON string column"
convention this project already uses for `OutboxSyncEntity.payloadJson`. At most one template per
companyId+documentType is `isDefault`. `DocumentTemplate.builtinDefault`/the Python fallback
guarantee a document is always renderable even before a company creates its own template - never
null, never a crash.

## Template Versioning & Historical Immutability (Section 10/21/22)

Once a `DocumentTemplate` version row is inserted, its `configJson`/`templateName`/`isDefault` are
never mutated - only its `status` flips `ACTIVE -> ARCHIVED` when superseded. A separate
`RenderedDocumentRecord` (Android) / `rendered_document_records` table (Python) logs, at render
time, exactly which `templateId` + `version` produced a given artifact - **without adding any field
to the frozen `Invoice`/`TradeDocument` entities**. This is what makes "an already-generated
document must remain reproducible using the template/version associated with it" checkable: editing
a template to v2 never changes what a `RenderedDocumentRecord` from before the edit points at, and
v1's own content is never rewritten. Both `Phase7DTestSuite.kt` and `test_documents_7d.py` verify
this directly (create v1 -> render/log -> edit to v2 -> confirm the old record still says v1 and
v1's content is untouched -> confirm no ledger balance changed as a side effect of any of this).

Draft documents remain freely editable until finalized/posted; posting continues to go exclusively
through the existing, unmodified `postInvoice`/`TradingWorkflowEngine`/`apply_voucher_event` path -
this phase creates no second posting mechanism anywhere.

## Branding / Color / Typography boundary (Section 12/13)

`TemplateColors`/`TemplateTypography`/`TemplateLayout` are purely presentational and are never read
by any accounting/GST/report calculation - confirmed by construction (nothing in
`domain/reports/`, `domain/taxation/`, or the posting engines imports `domain.rendering`). No
drag-and-drop template designer was built (Section 11 explicitly warns against this) - only the
configuration toggles a preset layout needs.

## Numbering (Section 24)

A rendered document displays `Invoice.invoiceNumber`/`TradeDocument.documentNumber` verbatim, and
(when posted) `Voucher.voucherNumber` separately as `accountingVoucherNumber` - the renderer never
re-derives, reformats, or recomputes a number from `DocumentType`'s prefix itself. Both identifiers
are already frozen, pre-computed values from Phase 7A/7B; this phase adds no new numbering logic.

## Document References (Section 25)

`DocumentReferenceInfo` surfaces a Credit/Debit Note's `referenceInvoiceId` or a converted
document's `sourceTradeDocumentId` - reusing Phase 7B's already-established relationships verbatim,
never inventing a new one.

## Known, documented limitation: inferred "no quantity" instead of a stored flag

`InvoiceLineEntity`/`TradeDocumentLineEntity` have no explicit "quantity not applicable" column (a
genuine domain gap the Phase 7D structural audit found) - `DocumentData` currently infers "no
quantity" from a stored `quantityRaw == 0`, rather than a real flag. This is a reasonable,
documented heuristic (a genuine inventory line always has a positive quantity), not a silent
behavior change - a future phase could add an explicit nullable-quantity column if exact
distinction ever matters.

## Database changes (both sides purely additive)

- Android: Room schema version 5 -> 6 (`MIGRATION_5_6`: five new tables - `document_templates`,
  `business_profiles`, `individual_profiles`, `document_assets`, `rendered_document_records` - no
  existing table/column altered, dropped, or renamed).
- Python: Alembic revision `0004` (`down_revision = "0003"`), mirrors the same five tables.

## What Phase 7D deliberately does not include

- Any UI - per the Phase 7 gate, UI is Phase 7J.
- Outbox/SyncEvent sync for templates/profiles/assets - Android creates/edits them locally in this
  phase; Python independently exposes its own persistence + routes (for company-isolation testing
  and a future non-Android caller), but the two platforms' template/profile data does not yet sync
  between devices. This is an explicit, documented extension point (see `docs/43_DOCUMENT_RENDERING.md`),
  not an oversight - full Outbox wiring can follow the same pattern 7A/7B/7C already established
  when a later phase needs it.
- GSTR JSON export (Phase 7E) - structurally distinct from this phase's document/PDF concern
  (different consumer, different schema); confirmed no existing code conflates the two.
- A visual template designer/editor - explicit extension points only (Section 11).
- Business/Individual Profile *general* identity data beyond what document rendering needs (Phase
  7G) - the boundary between this phase's rendering-scoped profile and 7G's broader profile model
  is an open question, documented rather than silently decided (`docs/45_DOCUMENT_BRANDING.md`).
