# OCR Ingestion Architecture (Phase 7I)

## Status: architecture and contracts only

`OcrIngestionAdapter.kt` defines the contract - a pure Kotlin interface (`extractFromDocument`) plus
the minimal typed models a bill/receipt-scanning result needs (`OcrExtractionResult`,
`OcrLineItemSuggestion`, `OcrDocumentType`). No OCR/ML library, no HTTP client, no Android
dependency, no implementation, no UI. Mirrors `domain.sandbox.SandboxProviderAdapter`'s exact
shape.

## Why this exists now, but isn't built

`docs/46_EXPORT_ARCHITECTURE.md`'s Phase 7E master spec already pre-agreed an OCR boundary rule -
"OCR... never becomes direct accounting posting" - for a capability that had never been given a
contract shape. This phase gives it one, so a future dedicated OCR phase has an established
interface to implement against instead of inventing one from scratch under time pressure.

## Boundary

A generated `OcrExtractionResult` is a **suggestion**, never an accounting event. Every field -
vendor name, GSTIN, date, amounts, line items - is a guess with an explicit `confidenceScore`,
meant to pre-fill an existing voucher-entry flow for a human to review, correct, and submit.
Nothing implementing this interface may create, edit, or post a `Voucher`, or touch a `Ledger`
balance directly - the existing `DoubleEntryValidator`/`postVoucher` path, entered by a human
action, is the only way anything derived from OCR output ever reaches the books.

References an already-uploaded `domain.rendering.DocumentAsset` by `assetId` rather than
duplicating the scanned image/PDF itself - the same reference-by-id convention `BusinessProfile`'s
`logoAssetId`/`signatureAssetId` already use.
