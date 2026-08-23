# CMA Report Architecture (Phase 7I)

## Status: architecture and contracts only

`CmaReportGenerator.kt` defines the contract - a pure Kotlin interface (`generate`) plus a
minimal, illustrative `CmaReport` shape. No implementation, no UI, no wiring into
`AccountingRepository`.

## Why this exists now, but isn't built

CMA (Credit Monitoring Arrangement) is the standard Indian bank-loan financial-statement format.
Named explicitly (alongside GST/ITR/OCR) as functionality to scope correctly rather than
half-build too early. Unlike OCR/bank-reconciliation, this isn't an external-provider boundary at
all - it's a sibling of Phase 7C's Report Architecture (`docs/37_REPORT_ARCHITECTURE.md` through
`docs/41_RATIO_ANALYSIS.md`), and follows that phase's exact rule.

## Boundary

**Reports consume engine output, they never recalculate.** `generate` takes the already-computed
`TrialBalanceReport`/`ProfitAndLossReport`/`BalanceSheetReport` as input - by construction, an
implementation of this interface has no way to derive a figure independently; it can only re-shape
numbers those three existing, frozen report functions already produced. A future implementation
belongs in `AccountingRepository`, alongside `generateRatioAnalysis`, which already follows this
same "compute from already-generated reports" pattern.

`CmaReport` here is a deliberately minimal subset - the full multi-year CMA format (with
projected/estimated figures across several years) is out of scope for this architecture-only
phase.
