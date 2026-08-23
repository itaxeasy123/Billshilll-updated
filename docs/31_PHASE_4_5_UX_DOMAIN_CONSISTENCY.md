# 31. Phase 4.5 — Accounting UX & Domain-Consistency Specification (FROZEN)

**Status: specification only. Nothing in this document has been implemented yet.** This consolidates the read-only Phase 4.5 audit findings with the mandatory rules agreed afterward, into one frozen reference. Phase 5 (GST) must not begin until this phase is implemented and its own gate (Section H) passes. Nothing here reopens or reinterprets the frozen Phase 0–4 accounting engine (`VoucherPostingEngine`, `DoubleEntryValidator`, `InventoryEngine`, `GroupAggregationEngine`, `CogsEngine`) — every rule below is either a UI-layer change, an additive engine capability (Round Off ledger, GST engine ledger split), or a presentation rule.

## Guiding principle

```
USER ACTION
    -> Guided Voucher Workflow
    -> Domain Validation
    -> Tax / Inventory Calculation
    -> Double Entry
    -> Voucher Posting Engine
    -> Journal
    -> Ledger Balance
    -> Reports
    -> Export / Print / API
```

Never `UI calculation -> direct ledger balance`. The UI only displays what the engine already calculated. Internal architecture (`companyId`, `periodId`, `ledgerId`, audit trail, outbox, idempotency, validators, invariants) stays fully enforced but never surfaces its raw form to the end user.

---

## A. Guided voucher workflows (confirmed gap — audit finding)

`CreateVoucherDialog.kt` currently only exposes 5 voucher-type chips (`PAYMENT, RECEIPT, SALES, CONTRA, JOURNAL`); PURCHASE, CREDIT_NOTE, and DEBIT_NOTE are not reachable from the UI at all, and PAYMENT/RECEIPT/CONTRA/JOURNAL all share one generic "Debit Account (Dr) / Credit Account (Cr)" ledger-picker form. Target design, frozen:

- **Sale**: Customer/Party -> Invoice No. -> Invoice Date -> Items (Qty/Rate/GST) -> Total. Engine generates `Customer Dr / Sales Cr / Output GST Cr`.
- **Purchase**: Supplier/Party -> Supplier Bill No. -> Bill Date -> Items (Qty/Rate/GST) -> Total. Engine generates `Purchase Dr / Input GST Dr / Supplier Cr`.
- **Receipt**: Customer -> Receipt No. -> Date -> Amount -> Reference/Invoice (allocation against outstanding). Engine generates `Bank/Cash Dr / Customer Cr`. No GST field.
- **Payment**: Supplier/Expense -> Cash/Bank -> Allocate -> Post. Engine generates `Supplier Dr / Bank/Cash Cr`. No GST field.
- **Credit Note** (sales return): Customer -> original Sale/Invoice -> returned items -> GST reversal -> post.
- **Debit Note** (purchase return): Supplier -> original Purchase -> returned items -> GST reversal -> post.
- **Contra**: restricted to Cash/Bank ledgers only (Bank->Bank, Cash->Bank, Bank->Cash, Cash->Cash) — the ledger picker must filter to `GRP_BANK`/`GRP_CASH` descendants (exact-ID group-hierarchy check, per the Phase 3 classification rule — never name matching), not list every ledger in the company as it does today.
- The user never manually constructs "Debit Account" / "Credit Account" journal lines for these six voucher types. A raw dual-ledger picker remains acceptable only for `JOURNAL` (genuine general-purpose adjustments) and inventory-only documents.

## B. GST architecture correction (confirmed gap — audit finding)

1. **Separate Input/Output ledgers per tax type**, not one ledger conflating both (today's seed literally creates ledgers named `"CGST Input/Output A/c"` etc. — confirmed in `AccountingRepository.kt`):
   ```
   Current Liabilities
   |- Duties & Taxes
      |- Output CGST      |- Input CGST
      |- Output SGST      |- Input SGST
      |- Output IGST      |- Input IGST
   ```
   "Duties & Taxes" is the **group**, never a single ledger. The GST engine (`GstCalculationEngine`, extended) determines which of the six ledgers a given transaction posts to, by supply type (intra/inter-state) and direction (outward/inward) — resolved by exact ledger ID, same discipline as `StandardSystemGroups`/`GstLedgerIds` already established, never by name matching.
2. **GST rate comes from the item's configured HSN/rate** (`StockItem.hsnCode`/`gstRatePercent`), never a fixed UI chip list. Today's Sales GST form (`CreateVoucherDialog.kt`, `listOf(0.0, 5.0, 12.0, 18.0, 28.0)` defaulting to 18.0) is disconnected from the Phase 4 item model entirely — it must be rebuilt as item-driven: `Item -> Qty x Rate -> Taxable Value -> Place of Supply -> Intra/Inter -> GST Engine -> CGST+SGST or IGST -> Posting Engine -> Duties & Taxes ledgers`.
3. **No GST on Receipt/Payment** — confirmed *not* currently violated (today's `isGstSales` flag is SALES-only), but must be frozen as an explicit rule so it isn't accidentally introduced later: a Receipt/Payment settles an existing receivable/payable; GST belongs to the originating supply/purchase transaction, never to its settlement.
4. **Accounting Period != GST filing period.** These are two independent governance dimensions:
   ```
   Financial Year / Accounting Period  -> Accounting governance (existing PeriodStatus: OPEN/LOCKED/AUDIT_LOCKED)
   GST return / filing period          -> GST compliance governance (new, separate status)
   ```
   A GST filing lock must never silently become (or require) an accounting-period lock, and vice versa. This means Phase 5 needs its own filing-period entity/status, not a reuse of `AccountingPeriodEntity`.

## C. Round Off — a second, distinct system control ledger

Freeze this as a new permanent system ledger, structurally parallel to Suspense but semantically distinct:

```
System Control Ledgers
|- Suspense A/c   - temporary/unresolved difference, must eventually be identified and cleared
|- Round Off A/c  - legitimate rounding adjustment from defined precision rules
```

They must never be combined into one ledger or one concept. Round Off rules (mirrors the existing Suspense protection pattern in `StandardSystemGroups`/`AccountingRepository.updateGroup`/`updateLedger`/`deleteLedgerSafely`):
- System-created, non-deletable, non-renamable, non-reparentable, company-scoped.
- Usable by the posting/calculation engine: `Exact calculated amount -> required accounting precision -> rounding difference -> Round Off ledger`. A rounding difference is posted, never silently dropped.
- The rounding **direction/method must reuse the project's already-frozen `Money`/`RoundingMode.HALF_EVEN` rules** (`Money.percentage`, `StockValuationEngine`'s BigDecimal division pattern) — no new rounding convention invented ad hoc.
- Visible in normal ledger views only when it actually has a nonzero balance/entries (see Section E) — same visibility rule as everything else, no special-casing to always show it.

## D. Presentation rules (frozen formatting/terminology)

1. **No currency symbol, no Indian digit grouping in accounting numeric displays.** `500000.15`, `1250000.50`, `0.00` — not `₹5,00,000.15`. This is a **display-layer-only** rule: the authoritative `Money`/paise `Long` representation and its existing `RoundingMode.HALF_EVEN` precision are unchanged; only a new plain-decimal formatting path is added alongside (or replacing, where these rules apply) `Money.format()`'s current `₹` + Indian-grouping output. Existing callers of `Money.format()` for other contexts are not assumed changed by this rule alone — this needs explicit scoping per screen when implemented.
2. **No permanent "Double Entry Balance: Balanced" banner.** Confirmed present today on the voucher creation form (`CreateVoucherDialog.kt:479`), `ReportsScreen`'s Trial Balance/Balance Sheet banners, and `VoucherDetailDialog.kt`'s `"Total (Balanced)"` label. The engine (`DoubleEntryValidator`, `TrialBalanceReport.isBalanced`, `AppError.TrialBalanceNotBalanced`/`BalanceSheetNotBalanced`) keeps enforcing the invariant unconditionally — the UI shows a plain Debit/Credit total table, and only surfaces an actionable warning if a genuine imbalance is ever reported (which today throws a structured error rather than rendering, per Phase 3 — so in practice this becomes an error state, not a banner state).
3. **No internal terminology in normal UI**: "Immutable Audit Log," audit event/mutation IDs, "System Group," "Ledger ID: LED_...," "Active Tenant: COMP_...," raw `companyId`. Confirmed present today: `ChartOfAccountsScreen.kt` shows a literal `"SYSTEM"` badge on ledgers and groups in the normal browsing list. Replace with business-meaningful equivalents: audit actions become "Created / Edited / Posted / Cancelled" history entries; the active company shows as its actual name (e.g. "Apex Enterprises"), never its ID. The underlying append-only audit trail, `companyId` scoping, idempotency, and outbox remain fully enforced and available to an authorized admin/compliance surface if one is ever built — they are hidden from normal users, not removed.
4. **Business-type-aware Capital naming.** An individual/proprietor should see `Capital -> <Proprietor Name>`, not the generic company-oriented `"Share Capital Account"`/`"Capital Account"` label seeded today regardless of entity type. Driven by `Company.businessType`/a future constitution field, applied at ledger-seeding time.
5. **FY vs. date context.** Within a screen already operating inside a clearly selected Financial Year, don't repeat the FY on every row; show a single context indicator (e.g. "Financial Year: FY 2026-27") and let transaction rows show only their date. The engine still validates the date falls within the selected FY regardless of what's displayed. (Noted in the prior audit as needing a closer per-screen pass before implementation — not yet confirmed against every screen.)

## E. Ledger/report visibility rules

1. **Zero-balance, no-entry ledgers hidden from normal operational views by default** — opening=0, debit=0, credit=0, closing=0, and no accounting entries ever posted. Applies especially to unused system/GST ledgers (e.g. `Output IGST` for a company that has never made an inter-state sale) and newly created empty ledgers. Exactly mirrors the `includeZeroBalance` flag `generateTrialBalance` already added in Phase 3 (default `true` today at the engine level) — Phase 4.5's job is to make the **UI's default** `false`-equivalent (hide by default, "show all" as an explicit user toggle), not to change the engine's default, which stays capable of returning everything.
2. **"Not displayed" is never "deleted."** An empty/unused ledger stays in the master; it is only ever removable through the existing, unchanged rule: `AccountingRepository.deleteLedgerSafely` — permitted only when `accountingEntryCount == 0` and not `isSystem`. This rule is not being touched.
3. **System ledgers (Suspense, Round Off) stay protected regardless of visibility state** — never auto-hidden into a state where a user could forget they exist, but also never force-displayed at a permanent zero balance; same visibility rule as any other ledger (Section E.1), just still non-deletable per Section C.

## F. Internal vs. user-facing architecture (organizing principle)

```
                 ACCOUNTING ENGINE
                       |
        +--------------+--------------+
        |              |              |
    companyId      audit trail     invariants
    periodId       outbox          validators
    ledgerId       IDs             controls
        |              |              |
        +--------------+--------------+
                       v
                  APPLICATION
                       v
                 USER-FACING UI
```

The UI expresses business meaning (Customer, Invoice, Supplier, Bill, Receipt, Payment, Cash, Bank, Sales, Purchase, Outstanding, Stock, Profit, Loss). The engine stays rigorous and explicit (Entity, Group ID, Ledger ID, Voucher, Journal, Debit, Credit, Validator, Posting Engine, Period, Company ID). They must not look identical, and the direction of dependency is one-way: UI reads engine output, never computes accounting values itself (Section G.15 in the checklist below is the load-bearing rule this whole document exists to protect).

## G. Consolidated freeze checklist

**From the workflow/GST/formatting audit:**
- [ ] Sale, Purchase, Receipt, Payment, Credit Note, Debit Note each have a dedicated guided workflow (not a generic Debit/Credit picker)
- [ ] Contra is restricted to Cash/Bank ledgers only
- [ ] Party (Customer/Supplier) is automatically mapped to its receivable/payable ledger
- [ ] Invoice/Bill number is captured and mapped to the voucher
- [ ] Receipt/Payment support outstanding/settlement allocation against an original invoice/bill
- [ ] GST uses six separate Output/Input x CGST/SGST/IGST ledgers under one Duties & Taxes group, never one conflated ledger
- [ ] GST rate is sourced from the item's HSN/tax configuration, never a hardcoded/manually-chosen UI rate
- [ ] Receipt/Payment never calculate or post GST
- [ ] Accounting Period governance remains accounting-specific; GST filing period is separately governed and never silently locks accounting periods or vice versa
- [ ] Individual vs. business capital ledger naming reflects entity constitution/business type
- [ ] System-group/ledger internal badges are not shown in normal browsing views
- [ ] No permanent "Double Entry Balance: Balanced" banner anywhere in normal UI; Trial Balance presents a plain Debit/Credit table; imbalance (if it ever occurs) is an actionable error, not routine chrome
- [ ] No `₹` symbol and no Indian digit grouping in accounting numeric displays; exact calculated values shown per the existing precision/rounding rules
- [ ] Permanent Round Off system ledger exists, structurally protected like Suspense but conceptually distinct from it
- [ ] Rounding differences are posted to Round Off by the engine, never silently dropped, using the project's existing `Money`/`HALF_EVEN` rounding rules
- [ ] Zero-balance, no-entry ledgers (including unused GST ledgers) are hidden from normal operational views by default, with an explicit "show all" escape hatch
- [ ] Hiding a ledger never deletes it; deletion still requires zero accounting entries, exactly as today
- [ ] System ledgers (Suspense, Round Off) remain non-deletable/non-renamable/non-reparentable regardless of visibility state
- [ ] Audit trail, tenant/company ID, idempotency, outbox, and all other engine internals remain fully enforced but are never shown in normal user-facing screens
- [ ] UI never calculates a ledger/report balance itself — it only renders what `AccountingRepository`'s engines already computed

## H. Gate before Phase 5

Phase 5 (GST/statutory) does not begin until:
1. This document is reviewed and any open questions in Sections D.1/D.5 (exact scope of the `₹`/formatting change, and the FY/date redundancy screen-by-screen pass) are resolved.
2. A controlled implementation pass addresses the checklist above as one planned, reviewed body of work — not incremental one-off edits.
3. The resulting engine/UI changes pass the same compile + full JVM test suite discipline already established (Phase 0–4 suites stay green; new tests cover the Round Off ledger, GST ledger split, and any new visibility/formatting logic that has engine-side behavior).

Building Phase 5's GST return/filing logic on top of the current generic voucher UI and conflated GST ledgers would inherit exactly the problems this document exists to prevent.
