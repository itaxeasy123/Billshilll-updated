# 55. Import Reconciliation Summary (Phase 7K — Implementation)

## Status

Real, compiled, tested implementation. One new pure domain type
(`domain/dataimport/ImportReconciliationSummary.kt`) plus one thin delegating method on the
existing `application/imports/DataImportManagementService`. No UI, no new persistence, no schema
change, no accounting engine touched.

## Why this, and why now

A read-only audit of Phase 0 -> 7J-B (this project's own stated checkpoint) found that the
dependency order the project is committed to -

```
Accounting Foundation -> Transactions -> Reconciliation -> Import/Export -> GST-ready -> UI
```

- had a real gap sitting exactly where "Reconciliation" is supposed to come before further
Import/Export work: the CSV/JSON import path (`data/dataimport/CsvJsonDataImportAdapter.kt` ->
`DataImportManagementService.reviewAndCreate`, Phase 7J-B) is real and already produces real
`ImportResult`/`ImportRowSuggestion` data, but nothing anywhere answered the basic reconciliation
questions a data-interchange step must be able to answer before it's trusted: what was imported,
what was accepted, what was rejected, what remains unresolved, and whether every parsed row is
actually accounted for.

This phase closes exactly that gap, and nothing else. It does not touch the bank-statement
reconciliation contract (`domain/reconciliation/ReconciliationAdapter`, Phase 7I) - that is a
separate, already-scoped concern (statement lines vs. vouchers) and stays exactly as it was,
still contract-only.

## What was added

### `ImportRowOutcome` (`domain/dataimport/ImportReconciliationSummary.kt`)

A small, structured enum - `CREATED` / `FAILED` / `SKIPPED` - standing in for the raw, free-text
outcome string the presentation layer (`AccountingViewModel.reviewAndCreateImportRow`) currently
keeps for itself (`"Created"` / `"Failed: <message>"`). `ImportReconciliationSummary.from` needs a
structured shape to count on; a raw string was never going to be that shape, and this phase does
not touch the ViewModel or its `lastImportRowOutcomes: Map<Int, String>` state at all - that
remains exactly as it was. A future phase that wires this summary into the UI is what would map
the ViewModel's own string outcomes onto `ImportRowOutcome` (or store `ImportRowOutcome` directly),
not decided here.

### `ImportReconciliationSummary` (same file)

Pure aggregation only, over an already-produced `ImportResult` and a caller-supplied
`Map<Int, ImportRowOutcome>` keyed by `ImportRowSuggestion.rowNumber`. Answers:

| Field | Meaning |
|---|---|
| `totalRowsParsed` | Direct passthrough of `ImportResult.totalRowsParsed`. |
| `unparsedRowCount` | Rows the parser itself could not turn into a suggestion at all. |
| `suggestedRowCount` | Rows that did produce a suggestion. |
| `createdCount` / `failedCount` / `skippedCount` | Reviewed suggestions, by outcome. |
| `unresolvedCount` / `unresolvedRowNumbers` | Suggestions with no entry in the caller's outcome map yet - never silently dropped. |
| `isFullyReconciled` | `true` only when `unresolvedCount == 0 AND unparsedRowCount == 0`. |

A row number present in the outcome map but absent from `result.suggestions` (a caller passing
stale or unrelated data) is ignored - this type only ever reports on rows the given `ImportResult`
itself actually produced.

**Deliberately excluded**: a "duplicate" or "mapped" count. Section 14 of this project's own
dependency-order rules names both, but neither `CsvJsonDataImportAdapter` nor
`DataImportManagementService` detects duplicates or records a field-mapping decision anywhere
today - fabricating those categories here would be a guessed fact, not a derived one, breaking the
same rule `docs/52_MANAGEMENT_ARCHITECTURE.md` already applied to `DocumentData.shipDate`
("documented extension point, never a fabricated value"). A future phase that adds real duplicate
detection to the import pipeline is what would make that category meaningful here.

### `DataImportManagementService.summarize` (`application/imports/DataImportManagementService.kt`)

One new method, `summarize(result: ImportResult, rowOutcomes: Map<Int, ImportRowOutcome>):
ImportReconciliationSummary`, a direct one-line delegation to `ImportReconciliationSummary.from`.
The service itself still tracks no outcomes of its own - the same pass-through discipline every
other method on this service already follows (`parseFile` passes straight to the adapter;
`reviewAndCreate` is the only method with any side effect at all).

## Non-goals

- No UI, no ViewModel change, no Compose screen.
- No change to `CsvJsonDataImportAdapter`, `DataImportManagementService.reviewAndCreate`, or any
  frozen accounting engine.
- No duplicate-detection logic, no field-mapping persistence.
- No bank-statement reconciliation work (`domain/reconciliation/ReconciliationAdapter` is
  untouched, still contract-only, Phase 7I's own separate scope).
- No Excel import, no GST/HSN structural wiring - both remain the next real gaps after this one,
  per the same audit's ordering.

## Testing

`ImportReconciliationSummaryTestSuite.kt` (pure JVM, no DAO/Room/coroutine needed for the
aggregation tests themselves): all-rows-reviewed mixed-outcome counting, unreviewed rows reported
as unresolved rather than silently ignored, unparsed rows counted separately from unresolved
suggestions and correctly blocking `isFullyReconciled`, the empty-result case, a stale/unrelated
row-number entry in the outcome map being ignored, and one integration-style test proving
`DataImportManagementService.summarize` is a genuine delegation (identical output to calling
`ImportReconciliationSummary.from` directly).

**Verification**: `compileDebugKotlin`/`compileDebugUnitTestKotlin` clean; `testDebugUnitTest`
matches the pre-existing baseline (440/445, the same 5 Robolectric `DefaultSdkProvider`
environment failures unrelated to this change) plus this phase's own new, passing tests.

## Next step after this one

Per the audit's own ordering, the next genuine gap is still **Reconciliation** in the fuller
sense (a real `ReconciliationAdapter` implementation) or **GST/HSN structural wiring**
(`HsnSacCode` into `StockItem`/`InvoiceLine`), not further Import/Export breadth (Excel, more
formats) - those stay explicitly deferred until the smaller pieces immediately ahead of them are
done, one at a time.

## Addendum: Ledger Opening Balance Import

A follow-up read-only audit of this same LEDGER import path found one concrete gap: it always
created a ledger with a zero opening balance, regardless of what a source file actually contained,
because `DataImportManagementService.reviewAndCreate`'s `LEDGER` branch never read an
opening-balance column at all. This addition closes that gap - `Ledger`, `AccountingRepository`,
`VoucherPostingEngine`, and every report remain untouched; only the import-time field mapping and
validation changed.

### Supported columns (LEDGER rows only)

Column matching reuses `firstNonBlank`'s existing normalization (lowercase, strip spaces and
underscores) - so any of a header's listed spellings resolve to the same field:

| Field | Recognized headers |
|---|---|
| Opening balance amount | `openingbalance`, `opening_balance`, `opening balance`, `balance` |
| Opening balance type | `openingbalancetype`, `opening_balance_type`, `opening balance type`, `drcr`, `type` |

### Supported balance-type values

Case-insensitive, whitespace-trimmed: `DEBIT`, `DR` -> `DrCr.DEBIT`; `CREDIT`, `CR` ->
`DrCr.CREDIT`. Anything else is rejected (see Invalid values below) - never passed to raw
`DrCr.valueOf(...)`, which throws on anything other than the exact strings `"DEBIT"`/`"CREDIT"`
and would crash the whole import batch over one bad row.

### Partial-column rule

|  | Type present | Type missing |
|---|---|---|
| **Balance present** | Parsed and imported normally. | `AppError.ValidationError` - rejected, no ledger created. |
| **Balance missing** | `AppError.ValidationError` - rejected, no ledger created. | Both absent: the pre-existing default (`Ledger`'s own `Money.ZERO`/`DrCr.DEBIT`) - unchanged, not a new policy. |

A column present in the header but blank for a given row is indistinguishable from the column
being absent entirely - `firstNonBlank` already treats the two identically, so this is a reuse of
an existing behavior, not a new blank-value rule.

### Invalid-value behavior

- **Invalid amount** (e.g. non-numeric text): a dedicated parser rejects it with
  `AppError.ValidationError` naming the row and the offending value. Deliberately never
  `Money.parse(...)`, which silently returns `Money.ZERO` for any unparseable input - correct for a
  live UI field mid-typing, wrong for an import row, where a bad value must be rejected, never
  guessed into a plausible-looking zero.
- **Invalid balance type** (anything other than the values listed above): also a row-numbered
  `AppError.ValidationError`, never a thrown exception - one bad row is rejected on its own; the
  rest of the batch is unaffected, matching how a malformed CSV row already degrades to
  `ImportResult.unparsedRowNumbers` rather than crashing the whole parse.
- In every rejection case: no `Ledger` is created, no partial accounting data is written (the
  check runs entirely before `AccountingRepository.createLedger` is ever called), and the row's
  outcome (once reviewed through the existing `reviewAndCreateImportRow` flow) is recorded as a
  failure like any other rejected suggestion - it is not silently dropped from
  `ImportReconciliationSummary`.

### Ledger creation

A successfully parsed opening balance/type is passed straight into the existing `Ledger` domain
model's `openingBalance`/`openingBalanceType` fields, then through the existing, unmodified
`AccountingRepository.createLedger` - which already initializes `currentBalance`/
`currentBalanceType` equal to the given opening values. `VoucherPostingEngine` remains the only
place that ever moves a ledger's balance after creation; this feature only supplies the starting
value.

### Testing

`LedgerOpeningBalanceImportTestSuite.kt`: valid debit/credit/decimal amounts import correctly;
balance-without-type and type-without-balance are both rejected with no ledger created; an invalid
amount and an invalid type are each rejected with no ledger created; both fields absent preserves
the pre-existing zero-balance default; the imported balance is actually persisted; and the
ledger's `currentBalance`/`currentBalanceType` initially equal the imported opening values.
`LedgerImportGroupValidationTestSuite.kt` and `Phase7JBDataImportTestSuite.kt` were re-run
unchanged to confirm no regression.
