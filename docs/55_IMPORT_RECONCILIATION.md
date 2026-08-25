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
