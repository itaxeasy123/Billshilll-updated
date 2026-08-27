package com.example.accounting.domain.taxation.gst

import com.example.accounting.core.common.Money
import com.example.accounting.core.common.Quantity
import com.example.accounting.domain.accounting.VoucherType

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
    /** Rule 33 (GST Reporting Foundation) - the legal nature of this line's supply, carried through
     * unchanged from [com.example.accounting.domain.trading.TradingLineInput.supplyNature] instead
     * of being discarded after [supplyType] is derived from it. Needed because [supplyType] alone
     * cannot distinguish [GstSupplyNature.EXEMPT] from [GstSupplyNature.NIL_RATED] - both resolve to
     * the same [SupplyType.EXEMPT] - and GST reporting must classify Taxable/Zero-Rated/Exempt/
     * Nil-Rated separately. Defaults to NORMAL so every pre-existing construction site (buildNote's
     * `.copy()`, tests) keeps compiling and behaving identically. */
    val supplyNature: GstSupplyNature = GstSupplyNature.NORMAL
)
