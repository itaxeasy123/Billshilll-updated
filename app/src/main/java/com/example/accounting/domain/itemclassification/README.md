# HSN/SAC/GST Item Structure (Phase 7J)

## Status: architecture and models only

`HsnSacCode.kt` - `HsnSacCode` (code, description, `isService`) + a handful of illustrative
entries in `IllustrativeHsnSacCodes` (not a real government master list). No wiring into
`StockItem`/`InvoiceLine`/`GstTransaction`'s existing free-text `hsnSacCode` fields, no
persistence, no UI.

See `docs/52_MANAGEMENT_ARCHITECTURE.md` for the full Phase 7J audit this came out of.

## Boundary

No GST rate field - a code's real-world rate changes by government notification over time, and
this project's existing, frozen `GstCalculationEngine`/`GstTransaction.gstRatePercent` already
correctly treats the rate as a per-transaction fact, never a lookup. This type is classification
only: `isService` finally gives a real type to a goods-vs-service fact that
`GSTTransactionExportDto.isService` (Phase 7E) could only ever leave `null` for lack of one.
