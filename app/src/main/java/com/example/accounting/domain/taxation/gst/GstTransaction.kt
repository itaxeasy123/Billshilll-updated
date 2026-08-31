package com.example.accounting.domain.taxation.gst

import com.example.accounting.core.common.Money
import com.example.accounting.core.common.Quantity
import com.example.accounting.domain.accounting.GstRegistrationStatus
import com.example.accounting.domain.accounting.VoucherType
import java.time.LocalDate

/**
 * One taxable line's worth of GST facts (Phase 5, Priority 4) - the dedicated record that makes
 * GST reporting independent of ledger names entirely. Built by the trading workflow engines
 * ([com.example.accounting.domain.trading.TradingWorkflowEngine]) alongside the journal/stock
 * lines for the same voucher, and persisted 1:1 with [com.example.accounting.data.local.entity.GstTransactionEntity].
 */
data class GstTransaction(
    val gstTransactionId: String,
    val companyId: String,
    val financialYearId: String,
    /** UI-06/Architecture Checkpoint: nullable (was non-null) - a GST-only company's transaction
     * has no accounting Voucher at all. See [com.example.accounting.data.local.entity.GstTransactionEntity.voucherId]
     * for the persistence-side rationale. */
    val voucherId: String?,
    val voucherType: VoucherType,
    val partyLedgerId: String,
    val partyGstin: String,
    val placeOfSupply: String,
    val supplyType: SupplyType,
    val itemId: String?,
    val hsnSacCode: String,
    val quantity: Quantity?,
    val taxableAmount: Money,
    val gstRatePercent: Double,
    val cgst: Money,
    val sgst: Money,
    val igst: Money,
    val cess: Money,
    val direction: GstDirection,
    val lineOrder: Int,
    /** Rule 31 (Purchase/RCM Foundation) - who is liable to remit this line's tax. Defaults to
     * FORWARD_CHARGE so every pre-existing construction site (buildGstOnlySale, buildNote's
     * `.copy()`, tests) keeps compiling and behaving identically. */
    val chargeType: GstChargeType = GstChargeType.FORWARD_CHARGE,
    /** D1b (GST-Only Purchase + Sales/Purchase Return + GST Fact Hardening) - the raw, pre-collapse
     * tax-treatment the user actually chose (Taxable/Zero Rated/Exempt/Nil Rated). [supplyType] is
     * the geography+treatment-derived value [GstCalculationEngine] actually taxed against (and
     * deliberately collapses Exempt/Nil Rated into one zero-tax bucket - see its own KDoc) - this
     * field preserves the un-collapsed source fact for any future GSTR report that needs to tell
     * Exempt and Nil Rated apart. Defaults to NORMAL, matching every line's behavior before this
     * field existed (every pre-existing row really was Taxable when it resolved to INTRA_STATE/
     * INTER_STATE, and really was the corresponding nature when it resolved to EXPORT/EXEMPT - see
     * `MIGRATION_18_19`'s backfill for the exact, disclosed mapping). */
    val supplyNature: GstSupplyNature = GstSupplyNature.NORMAL,
    /** D1b - the single, explicit identifier that ties every line of ONE business transaction
     * together, for both the accounting-integrated path (where it is always the real [voucherId])
     * and the GST-only path (which has no [voucherId] at all, so this is the only correlation
     * available for future GSTR reporting to know which rows belong together). Never a second/
     * polymorphic [voucherId] - deliberately a distinct, always-non-null plain identifier with no
     * FK and no accounting meaning whatsoever. Defaults to "" only so existing call sites that
     * predate this field keep compiling; every real construction site (accounting or GST-only)
     * always supplies a real value. */
    val transactionGroupId: String = "",
    /** D1b - the real business/invoice date this GST fact belongs to. For the accounting-integrated
     * path this stays `null` - that path already has an unambiguous source of truth for the date
     * (the joined [com.example.accounting.domain.accounting.Voucher.date]), so duplicating it here
     * would just be a second value that could drift from the first. For the GST-only path there is
     * no Voucher to join to at all (the exact gap this field closes) - `postGstOnlySale`/
     * `postGstOnlyPurchase`/the GST-only note path always supply a real, explicit value; never
     * `createdAt` (a row-insert timestamp, not a business date) used as a substitute. */
    val transactionDate: LocalDate? = null,
    /** D1b - the party's [GstRegistrationStatus] AT THE TIME this transaction was recorded, copied
     * from the resolved party [com.example.accounting.domain.accounting.Ledger] at construction
     * time - deliberately never re-derived from the (mutable) Ledger master at report time, so a
     * later change to the party's registration status can never silently rewrite a historical GST
     * fact. `null` means the status was genuinely unknown at that time (the same "unknown, never
     * guessed" semantics [Ledger.gstRegistrationStatus] itself already uses) - never defaulted to
     * either real value. */
    val partyGstRegistrationStatus: GstRegistrationStatus? = null
)
