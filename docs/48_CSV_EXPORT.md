# 48. CSV Export (Phase 7E)

See `docs/46_EXPORT_ARCHITECTURE.md` for the shared architecture; this doc covers the CSV engine
specifically.

## A generic engine, never hand-rolled string concatenation

Android's `domain/export/CsvEngine.kt` and Python's `domain/export/csv_engine.py` are both
report/DTO-agnostic - `CsvEngine.write(headers, rows)` / `write_csv(headers, rows)` take plain
`List<String>`/`List<List<String?>>` and handle escaping, never a caller building a CSV line via
raw string concatenation. Python's implementation is a thin wrapper over the standard library's
`csv` module (RFC 4180 quoting is the stdlib's job, never reimplemented); Android's is a small,
explicit implementation of the same rules since no CSV library is part of this project's
dependencies (Kotlin/JVM's standard library has no built-in CSV writer).

**Distinct from, and never modifies, Phase 7D's `domain.rendering.CsvExporter`** - that class is
frozen and document-line-specific (built for one `DocumentData`'s items); this phase's engine is
new and generic, consumed by six different DTO shapes (Voucher/Party/Ledger/Trial Balance/
Outstanding/GST Summary/GST Transactions) with a single shared escaping implementation.

## RFC 4180 escaping rules

- A field containing a comma, double-quote, or CR/LF is wrapped in double quotes; any double-quote
  inside it is doubled (`"` -> `""`).
- A `null` or empty value becomes an empty field - never the literal string `"null"`.
- Unicode passes through untouched - no transliteration or encoding narrowing.
- Every row uses `\r\n` line endings (the RFC 4180 standard, not a bare `\n`).

Verified directly with adversarial test data - narrations containing commas, embedded quotes,
embedded newlines, and non-ASCII characters together in one field - in both test suites.

## Exact monetary precision in CSV too

Every monetary column is written as the exact paise integer, as a plain digit string (e.g.
`"11800000"`) - never `.toRupeesDouble()`/a floating-point representation. This is a real fix
relative to Phase 7D's own `CsvExporter`, which does call `.toRupeesDouble()` for its (document-
line-only) CSV output - the structural audit flagged this as disqualifying for direct reuse, which
is exactly why this phase built a new, separate engine rather than generalizing the 7D one in
place (leaving 7D's frozen exporter untouched, as required).

## Deterministic column and row order

Column order is fixed per DTO type (declared once, in one place per DTO - `toCsvHeaders()`/
`toVoucherCsvHeaders()` etc. on Android, the `_csv()` functions in `csv_engine.py`) and row order is
always the caller's own list order - CSV output is never independently re-sorted by the engine.

## Which export types support CSV

`ExportFormatSupport`/`supports_format` restricts CSV to genuinely tabular data: Voucher, Party,
Ledger, Trial Balance, Outstanding, GST Summary, GST Transactions. Profit & Loss, Balance Sheet, and
Invoice are JSON-only (Section 3's "the architecture must allow future formats without modifying
accounting engines" doesn't require every format for every type on day one) - requesting CSV for
one of these returns `EXPORT_FORMAT_UNSUPPORTED`, never a best-effort/wrong CSV shape.

## Empty datasets

An export with zero rows (e.g. Outstanding for a company with no unpaid invoices) still produces a
valid CSV: the header row alone, followed by nothing - never an empty string, never an error.
Verified directly in both test suites.

## Content-Type

The Python API returns CSV exports with `Content-Type: text/csv` (via a plain `Response`, not
`JSONResponse`) so a browser/HTTP client can distinguish it from the JSON routes without parsing
the body first.
