# QR/Barcode Architecture (Phase 7J)

## Status: architecture and contracts only

`QrBarcodeAdapter.kt` - a pure Kotlin interface (`generateForStockItem`, `scanImage`) plus minimal
typed models (`BarcodePayload`, `BarcodeGenerationResult`, `BarcodeScanSuggestion`,
`BarcodeSymbology`). No barcode/QR library (ZXing, ML Kit, etc.), no Android dependency (image
capture/decoding is camera/bitmap work, explicitly out of scope here), no implementation, no UI.

See `docs/52_MANAGEMENT_ARCHITECTURE.md` for the full Phase 7J audit this contract came out of.

## Boundary

`scanImage` only ever produces a *suggestion* (`matchedStockItemId` is nullable, with a
`confidenceScore`) from a caller-supplied `candidateStockItems` list - it has zero database
access, so it cannot look anything up itself, and it never creates a new `StockItem` on a scan
that doesn't match. `generateForStockItem` takes an already-fetched `StockItem`, not an id, for
the same reason - this interface has no path to `AccountingDao`/`AccountingRepository` anywhere.
