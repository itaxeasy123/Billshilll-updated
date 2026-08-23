# Subscription/Entitlement Architecture (Phase 7J)

## Status: architecture and models only

`CompanySubscription.kt` - `SubscriptionPlanType` (FREE/PAID), `EntitlementFeature` (Accounting,
GSTR, E-Invoice, ITR, Audit Report, CMA, OCR, Inventory, Advanced Reports, API Access),
`CompanySubscription` (one row per company per financial year), and
`SubscriptionEntitlementChecker` - a pure, non-suspend object with one function
(`hasEntitlement`). No Room entity, no migration, no DAO, no persistence, no billing/payment
integration, no UI.

See `docs/52_MANAGEMENT_ARCHITECTURE.md` for the full Phase 7J audit this came out of.

## Boundary - the most safety-sensitive part of this phase

**Subscription controls feature access only - it can never alter, delete, or recalculate
accounting data.** `SubscriptionEntitlementChecker` has zero
`AccountingDao`/`AccountingRepository`/`VoucherPostingEngine`/`DoubleEntryValidator` reference -
structurally, not just by convention, it is incapable of touching a Voucher, Ledger, or report
value regardless of what a company's subscription state is. `hasEntitlement` returns a plain
`Boolean`; a denied entitlement is entirely a future UI-layer concern (a screen simply doesn't
offer a gated action) - this object never blocks a posting, reverses a voucher, or hides/deletes
existing data.

Paid validity is always exactly one financial year (1 Apr - 31 Mar), keyed by `financialYearId`
into the existing, frozen `FinancialYear` - never a raw date range reinvented here.

`EntitlementFeature.ACCOUNTING` is included because the phase's scope named it, but this project's
core double-entry posting has always been offline-first and login-free (see `README.md`'s opening
line). Whether core accounting is ever actually gated behind this entitlement is an open product
question left for whichever future phase wires real checks into real feature code - not decided
here.
