# Data Import Architecture (Phase 7J)

## Status: architecture and contracts only

`DataImportAdapter.kt` defines the contract - a pure Kotlin interface (`parseFile`) plus the
minimal typed models a CSV/JSON/Excel import pass needs (`ImportResult`,
`ImportRowSuggestion`, `ImportFileFormat`, `ImportSuggestionType`). No CSV/Excel-parsing library,
no HTTP client, no Android dependency, no implementation, no UI.

See `docs/52_MANAGEMENT_ARCHITECTURE.md` for the full Phase 7J audit this contract came out of -
this file only covers this one package's own boundary, not repeated here.

## Boundary

Every parsed row becomes a *suggestion*, never a created record - the same "external input ->
suggestion -> human review -> existing creation path" shape this codebase already uses for OCR
(`domain.ocr`) and bank reconciliation (`domain.reconciliation`). A future implementation may only
ever lead to a human calling the existing, unmodified `AccountingRepository.createParty`/
`createLedger`/`createStockItem` - there is no bulk-import posting path here, and Voucher/
transaction import is explicitly out of scope for this contract entirely (see
`ImportSuggestionType`'s doc comment) - bulk-importing double-entry transactions is a materially
bigger safety question left for a future phase to decide, not assumed here.
