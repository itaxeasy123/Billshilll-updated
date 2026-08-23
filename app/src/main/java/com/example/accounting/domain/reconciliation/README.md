# Bank Reconciliation Suggestion Architecture (Phase 7I)

## Status: architecture and contracts only

`ReconciliationAdapter.kt` defines the contract - a pure Kotlin interface (`suggestMatches`) plus
the minimal typed models a bank-statement-import reconciliation pass needs
(`BankStatementLine`, `SuggestedVoucherMatch`, `ReconciliationSuggestionResult`,
`BankLineDirection`, `ReconciliationMatchConfidence`). No HTTP client, no Android dependency, no
`AccountingDao`/`AccountingRepository` reference anywhere in the file, no implementation, no UI.

## Why this exists now, but isn't built

Phase 7F's "bank reconciliation = QUARANTINED" safety decision (`docs/50_AUTOMATION_ARCHITECTURE.md`)
already specified the correct target architecture for this capability and explicitly did not build
it:

```
Bank Data -> Imported Transaction -> Suggested Match -> Human Review -> Existing Posting Engine

Never: Bank Feed -> Auto-match -> Direct Ledger Posting
```

This phase gives that diagram a real Kotlin contract shape, so a future dedicated phase implements
against an already-agreed interface instead of re-deriving the safety boundary from scratch.

## Boundary

`suggestMatches` takes `candidateVouchers: List<Voucher>` as an explicit parameter rather than
querying the database itself - this interface has zero DAO/repository access, so it is
structurally incapable of reading anything beyond what its caller hands it, and structurally
incapable of writing anything at all. Every match it proposes is exactly that - a proposal, with a
confidence level and a reason, for a human to confirm. Nothing implementing this interface may
create, edit, match, or post a `Voucher`, or touch a `Ledger` balance.

`BankLineDirection` is deliberately its own type, not a reuse of `core.common.DrCr` - a bank
statement's "credit" (money in) and this app's ledger `DrCr.CREDIT` are opposite-perspective
concepts for the same event, and conflating them would be a real, subtle correctness bug waiting
to happen in whatever phase implements this.
