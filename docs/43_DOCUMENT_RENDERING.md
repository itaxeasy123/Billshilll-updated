# 43. Document Rendering (Phase 7D)

See `docs/42_DOCUMENT_TEMPLATE_ARCHITECTURE.md` for the shared architecture; this doc covers
`assembleDocumentData`/`assemble_document_data`'s exact rules and the deliberate Android/Python
asymmetry they create.

## Posted vs. draft/non-posting assembly (Android)

- **Posted Invoice** (`voucherId != null`): every line's `taxableAmount`/`cgst`/`sgst`/`igst`/`cess`
  comes straight from the matching `GstTransactionEntity` (joined by `lineOrder`); `grandTotal`
  comes straight from `Voucher.totalAmountPaise`; `roundOff` is the arithmetic difference between
  that grand total and the summed line totals (a presentational figure, not a recalculation of
  `RoundOffEngine`'s own logic, which already ran at posting time).
- **Draft Invoice / any non-posting TradeDocument** (never has a `GstTransaction`): each line's tax
  is computed by calling the existing, unmodified `GstCalculationEngine.calculateDetailed` -
  `assembleDocumentData` supplies quantity x rate as the taxable amount and the line's own
  `gstRatePercent`/`cessRatePercent`; the engine does the actual CGST/SGST/IGST/CESS split. This is
  calling the frozen engine, not reimplementing it - the same rule `generateProfitAndLoss` etc.
  already follow for report generation.

## Why Python's assembly is narrower - a documented architectural boundary, not a gap

Unlike Android, the Python server has **no GST calculation engine of its own**. Every existing
Python command handler (`voucher_commands.py`) only ever assigns an already-computed
`gst_line.cgstPaise`/`sgstPaise`/etc. straight through from the Outbox payload - it never calculates
a tax split itself, because GST math has always been Android-only in this project (Section 29:
"Do not duplicate Android accounting calculations in Python").

Given that constraint, `document_service.assemble_document_data` genuinely **cannot** support a
draft Invoice or a non-posting TradeDocument without either (a) building a second, independent GST
engine in Python - directly violating the "no duplication" rule and risking silent divergence from
Android's own math - or (b) faking a plausible-looking but unverified tax split. Neither is
acceptable, so:

- **Posted Invoice**: fully supported, identical rules to Android (reads the real `GstTransaction`
  rows, joined by `line_order`).
- **Draft Invoice / non-posting TradeDocument**: raises `DocumentPreviewNotAvailable`
  (`DOCUMENT_PREVIEW_NOT_AVAILABLE`, HTTP 409) with a message explaining the document should be
  rendered on the Android device instead, or posted first. This is an explicit, documented
  extension point - exactly the same discipline already used for Cash Flow's Investing/Financing
  sections (`null` rather than a guessed figure) - not a silently narrower implementation.

## Read-only, always

Every assembly function is a plain read: no `db.commit()`-worthy mutation happens anywhere except
the append-only `RenderedDocumentRecord`/`rendered_document_records` log row a render writes (never
a rewrite of Invoice/Voucher/JournalItem/GstTransaction data). Verified explicitly in both test
suites by comparing ledger balances (and, on the Python side, the row set itself) before and after
rendering.

## Deterministic output (Section 31)

`JsonDocumentRenderer.render(data, template)` is a pure function of its two inputs - the same
`DocumentData` + the same `DocumentTemplate` version always produces byte-identical JSON
(`testJsonDocumentRenderer_sameDocumentDataProducesDeterministicOutput` asserts this directly).
This matters for reprinting/resharing/audit support later: nothing here has hidden state,
randomness, or wall-clock dependence in the rendered content itself (only the separate
`RenderedDocumentRecord.generatedAt` log timestamp is time-dependent, and that's metadata about the
render event, not part of the rendered content).

## API

```
GET  /api/v1/documents/{id}?companyId=&documentType=            -> assembled DocumentData (no render-log side effect)
GET  /api/v1/documents/{id}/json?companyId=&documentType=&templateId=  -> same, resolves+logs a template render
GET  /api/v1/documents/{id}/pdf?companyId=&documentType=        -> 501 PDF_NOT_AVAILABLE_SERVER_SIDE (see docs/44_PDF_PRINT_SHARE.md)
```

All thin, all `require_company_access`-gated, all GET-only - documents carry no mutation risk to
read, matching the precedent every other report/document GET route in this project already set.
