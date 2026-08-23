package com.example.accounting.domain.subscription

/** FREE is always available with zero login (this app's founding principle - see `README.md`:
 * "fully functional with zero internet connectivity and zero login"). PAID unlocks
 * [EntitlementFeature]s beyond FREE's set - what exactly FREE includes by default is a product
 * decision for whichever future phase implements this, not decided here. */
enum class SubscriptionPlanType { FREE, PAID }

/**
 * A gate-able capability area. Note on [ACCOUNTING]: this project's core double-entry posting/
 * ledger functionality has always been offline-first and login-free (see `README.md`'s opening
 * sentence). Including `ACCOUNTING` here as a listed entitlement (per this phase's exact scope)
 * does not itself decide whether core posting is ever actually gated behind it - that tension is
 * deliberately left open for whichever future phase wires entitlement checks into real feature
 * code, the same way `docs/45_DOCUMENT_BRANDING.md` left its own "7D vs 7G" boundary open rather
 * than silently deciding it here.
 */
enum class EntitlementFeature {
    ACCOUNTING, GSTR, E_INVOICE, ITR, AUDIT_REPORT, CMA, OCR, INVENTORY, ADVANCED_REPORTS, API_ACCESS
}

/**
 * A company's subscription for exactly one financial year (Phase 7J) - paid validity is always
 * FY-bound (1 Apr - 31 Mar), keyed by [financialYearId] rather than a raw date range, matching how
 * every other FY-scoped concept in this codebase
 * ([com.example.accounting.domain.financialyear.AccountingPeriod],
 * [com.example.accounting.domain.taxation.gst.GstFilingPeriod]) already keys off a
 * `financialYearId` reference into the existing, frozen
 * [com.example.accounting.domain.financialyear.FinancialYear] rather than reinventing a date
 * range. One row per company per financial year - a company renewing next year gets a new
 * [CompanySubscription], the same way `RecurringVoucherDraftEntity`'s one-row-per-`(scheduleId,
 * periodKey)` pattern already works.
 */
data class CompanySubscription(
    val subscriptionId: String,
    val companyId: String,
    val financialYearId: String,
    val planType: SubscriptionPlanType,
    val planName: String,
    val entitlements: Set<EntitlementFeature>,
    val isActive: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Pure, side-effect-free entitlement gate (Phase 7J) - the **only** thing a subscription is
 * allowed to do is answer "is this feature available right now." No
 * `AccountingDao`/`AccountingRepository`/`VoucherPostingEngine`/`DoubleEntryValidator` reference
 * anywhere in this file - structurally incapable of altering, deleting, or recalculating any
 * accounting data or figure regardless of subscription state. A denied entitlement is a UI-layer
 * concern (a future screen simply doesn't offer the gated action) - this object never blocks,
 * reverses, or modifies anything that already exists.
 */
object SubscriptionEntitlementChecker {
    fun hasEntitlement(subscription: CompanySubscription, feature: EntitlementFeature): Boolean =
        subscription.isActive && feature in subscription.entitlements
}
