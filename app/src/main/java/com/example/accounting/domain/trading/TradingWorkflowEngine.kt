package com.example.accounting.domain.trading

import com.example.accounting.core.common.DrCr
import com.example.accounting.core.common.Money
import com.example.accounting.core.common.Quantity
import com.example.accounting.domain.accounting.JournalItem
import com.example.accounting.domain.accounting.RoundOffEngine
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.domain.inventory.StockDirection
import com.example.accounting.domain.inventory.VoucherStockLine
import com.example.accounting.domain.taxation.gst.GstCalculationEngine
import com.example.accounting.domain.taxation.gst.GstChargeType
import com.example.accounting.domain.taxation.gst.GstDirection
import com.example.accounting.domain.taxation.gst.GstSupplyNature
import com.example.accounting.domain.taxation.gst.GstTransaction
import com.example.accounting.domain.taxation.gst.GstTransactionFacts
import com.example.accounting.domain.taxation.gst.SupplyType
import java.time.LocalDate
import java.util.UUID

/** A Sale/Purchase voucher with a computed, non-zero outstanding balance (Phase 5, Priority 2) -
 * `total - Σ allocations - Σ note adjustments`, never a stored balance. */
data class OutstandingInvoice(
    val voucherId: String,
    val voucherNumber: String,
    val voucherType: VoucherType,
    val date: LocalDate,
    val totalAmount: Money,
    val outstandingAmount: Money
)

/** One item line as entered on a Sale/Purchase document - GST rate/HSN come from the selected
 * stock item, never typed freely by the user (Phase 5, Priority 1/3). */
data class TradingLineInput(
    val itemId: String,
    val itemName: String,
    val hsnSacCode: String,
    val quantity: Quantity,
    val rate: Money,
    val gstRatePercent: Double,
    val cessRatePercent: Double = 0.0,
    /** Tax Treatment (UI-06) - defaults to NORMAL (Taxable), i.e. byte-identical behavior to
     * before this field existed: geography (company vs place-of-supply) still decides Intra/Inter.
     * EXPORT/EXEMPT/NIL_RATED bypass geography entirely via [GstCalculationEngine.calculateDetailed]. */
    val supplyNature: GstSupplyNature = GstSupplyNature.NORMAL,
    /**
     * Rule 31 (Purchase/RCM Foundation) - who is liable to remit this line's tax. Lives here, per
     * line, deliberately never on the supplier [com.example.accounting.domain.accounting.Ledger] -
     * RCM is a fact about a specific supply, not a permanent property of who you bought it from.
     * [com.example.accounting.domain.accounting.Ledger.gstRegistrationStatus] is never read to set
     * this; a user must explicitly choose REVERSE_CHARGE. Defaults to FORWARD_CHARGE, i.e.
     * byte-identical behavior to before this field existed. Only meaningful on a Purchase line
     * (`build()` rejects it on a Sale) and only combinable with [GstSupplyNature.NORMAL] - there is
     * no tax to reverse-charge on a zero-tax supply.
     */
    val chargeType: GstChargeType = GstChargeType.FORWARD_CHARGE
)

/** A resolved ledger reference (companyId-suffixed id + display name) - supplied by the caller
 * (which has DAO access) so this engine itself stays pure/Room-independent. */
data class LedgerRef(val ledgerId: String, val name: String)

data class TradingGstLedgers(
    val outputCgst: LedgerRef, val outputSgst: LedgerRef, val outputIgst: LedgerRef,
    val inputCgst: LedgerRef, val inputSgst: LedgerRef, val inputIgst: LedgerRef,
    val cess: LedgerRef,
    /** Rule 31 (Purchase/RCM Foundation) - deliberately separate ledgers from [inputCgst]/etc.
     * (ordinary forward-charge Input Tax Credit) and from the supplier payable ledger. See
     * [com.example.accounting.domain.taxation.gst.GstLedgerIds] for the full rationale. */
    val rcmLiabilityCgst: LedgerRef, val rcmLiabilitySgst: LedgerRef, val rcmLiabilityIgst: LedgerRef,
    val rcmInputCgst: LedgerRef, val rcmInputSgst: LedgerRef, val rcmInputIgst: LedgerRef
)

data class TradingWorkflowResult(
    val journalItems: List<JournalItem>,
    val stockLines: List<VoucherStockLine>,
    val gstTransactions: List<GstTransaction>,
    val totalAmount: Money
)

/**
 * Builds the journal lines, stock lines, and GST-transaction facts for Sale/Purchase documents
 * from item-level facts (Phase 5, Priority 1) - pure, no Room/Android, mirroring
 * [com.example.accounting.core.database.VoucherPostingEngine]'s design. The ViewModel assembles a
 * [com.example.accounting.domain.accounting.Voucher] from this output and calls
 * [com.example.accounting.data.repository.AccountingRepository.postVoucher] exactly as it already
 * does for every other voucher type - VoucherPostingEngine/InventoryEngine/DoubleEntryValidator are
 * not modified.
 *
 * Round Off (Priority 7) is computed here, never typed in by the UI: the raw taxable+tax total is
 * rounded to the nearest rupee, and any non-zero difference becomes one more journal line to the
 * Round Off ledger, keeping the voucher balanced without the receivable/payable line carrying a
 * fractional-rupee figure the customer/supplier didn't actually see on the rounded invoice total.
 */
object TradingWorkflowEngine {

    fun buildSale(
        voucherId: String, companyId: String, financialYearId: String,
        customerLedgerId: String, customerName: String, customerGstin: String,
        salesLedgerId: String, salesLedgerName: String,
        companyStateCode: String, placeOfSupply: String,
        lines: List<TradingLineInput>,
        gstLedgers: TradingGstLedgers,
        roundOffLedgerId: String, roundOffLedgerName: String
    ): TradingWorkflowResult = build(
        isSale = true, voucherId = voucherId, companyId = companyId, financialYearId = financialYearId,
        partyLedgerId = customerLedgerId, partyName = customerName, partyGstin = customerGstin,
        tradeLedgerId = salesLedgerId, tradeLedgerName = salesLedgerName,
        companyStateCode = companyStateCode, placeOfSupply = placeOfSupply, lines = lines,
        gstLedgers = gstLedgers, roundOffLedgerId = roundOffLedgerId, roundOffLedgerName = roundOffLedgerName
    )

    /**
     * GST-only Sale (Architecture Checkpoint follow-up, Option B continuation) - for a company
     * that does not maintain accounting at all. Computes exactly the same per-line GST facts
     * [buildSale] computes internally (same [GstCalculationEngine.calculateDetailed] call, same
     * [GstSupplyNature] handling) but produces ONLY [GstTransaction] rows - no [JournalItem]s, no
     * [com.example.accounting.domain.inventory.VoucherStockLine]s, no GST-duty-ledger references,
     * no Round Off. There is deliberately no `voucherId` parameter - every returned row's
     * `voucherId` is `null`, matching [GstTransaction.voucherId]'s Architecture-Checkpoint
     * nullability. This is intentionally a separate, smaller function rather than a refactor of
     * [build] - `build`'s loop also computes running totals for Round Off and constructs stock
     * lines, none of which apply here, and reshaping that already-tested function carries far more
     * regression risk than this ~15-line, self-contained addition that calls the same engine.
     */
    fun buildGstOnlySale(
        companyId: String,
        financialYearId: String,
        customerLedgerId: String,
        customerGstin: String,
        companyStateCode: String,
        placeOfSupply: String,
        lines: List<TradingLineInput>
    ): List<GstTransaction> {
        require(lines.isNotEmpty()) { "At least one line item is required." }
        return lines.mapIndexed { index, line ->
            val lineTaxable = VoucherStockLine.computeAmount(line.quantity, line.rate)
            val breakdown = GstCalculationEngine.calculateDetailed(
                GstTransactionFacts(
                    taxableAmount = lineTaxable,
                    gstRatePercent = line.gstRatePercent,
                    cessRatePercent = line.cessRatePercent,
                    supplierStateCode = companyStateCode,
                    placeOfSupply = placeOfSupply,
                    supplyNature = line.supplyNature
                )
            )
            GstTransaction(
                gstTransactionId = UUID.randomUUID().toString(),
                companyId = companyId,
                financialYearId = financialYearId,
                voucherId = null,
                voucherType = VoucherType.SALES,
                partyLedgerId = customerLedgerId,
                partyGstin = customerGstin,
                placeOfSupply = placeOfSupply,
                supplyType = breakdown.supplyType,
                itemId = line.itemId,
                hsnSacCode = line.hsnSacCode,
                quantity = line.quantity,
                taxableAmount = breakdown.taxableAmount,
                gstRatePercent = line.gstRatePercent,
                cgst = breakdown.cgstAmount,
                sgst = breakdown.sgstAmount,
                igst = breakdown.igstAmount,
                cess = breakdown.cessAmount,
                direction = GstDirection.OUTPUT,
                lineOrder = index + 1
            )
        }
    }

    fun buildPurchase(
        voucherId: String, companyId: String, financialYearId: String,
        supplierLedgerId: String, supplierName: String, supplierGstin: String,
        purchaseLedgerId: String, purchaseLedgerName: String,
        companyStateCode: String, placeOfSupply: String,
        lines: List<TradingLineInput>,
        gstLedgers: TradingGstLedgers,
        roundOffLedgerId: String, roundOffLedgerName: String
    ): TradingWorkflowResult = build(
        isSale = false, voucherId = voucherId, companyId = companyId, financialYearId = financialYearId,
        partyLedgerId = supplierLedgerId, partyName = supplierName, partyGstin = supplierGstin,
        tradeLedgerId = purchaseLedgerId, tradeLedgerName = purchaseLedgerName,
        companyStateCode = companyStateCode, placeOfSupply = placeOfSupply, lines = lines,
        gstLedgers = gstLedgers, roundOffLedgerId = roundOffLedgerId, roundOffLedgerName = roundOffLedgerName
    )

    private fun build(
        isSale: Boolean, voucherId: String, companyId: String, financialYearId: String,
        partyLedgerId: String, partyName: String, partyGstin: String,
        tradeLedgerId: String, tradeLedgerName: String,
        companyStateCode: String, placeOfSupply: String,
        lines: List<TradingLineInput>,
        gstLedgers: TradingGstLedgers,
        roundOffLedgerId: String, roundOffLedgerName: String
    ): TradingWorkflowResult {
        require(lines.isNotEmpty()) { "At least one line item is required." }
        // Rule 31 (Purchase/RCM Foundation): authoritative backstops, matching the
        // lines.isNotEmpty() convention above. The real, graceful, user-facing check is
        // AccountingViewModel.postTradingDocument's pre-check (never reached in normal use); these
        // exist so any other caller of this pure engine can never silently produce a wrong posting.
        if (isSale) {
            require(lines.none { it.chargeType == GstChargeType.REVERSE_CHARGE }) {
                "Reverse charge is not applicable to a Sale."
            }
        }
        require(lines.none { it.chargeType == GstChargeType.REVERSE_CHARGE && it.supplyNature != GstSupplyNature.NORMAL }) {
            "Reverse charge requires a Taxable line - it cannot be combined with Zero Rated/Exempt/Nil Rated."
        }

        val direction = if (isSale) GstDirection.OUTPUT else GstDirection.INPUT
        val taxLineType = if (isSale) DrCr.CREDIT else DrCr.DEBIT
        val tradeLineType = if (isSale) DrCr.CREDIT else DrCr.DEBIT
        val partyLineType = if (isSale) DrCr.DEBIT else DrCr.CREDIT
        val stockDirection = if (isSale) StockDirection.OUT else StockDirection.IN
        val voucherType = if (isSale) VoucherType.SALES else VoucherType.PURCHASE

        var taxableTotal = Money.ZERO
        // Forward-charge tax only - what the supplier actually bills, and what the ordinary
        // Input/Output duty ledgers receive, exactly as before Rule 31.
        var cgstTotal = Money.ZERO
        var sgstTotal = Money.ZERO
        var igstTotal = Money.ZERO
        var cessTotal = Money.ZERO
        // Rule 31 (Purchase/RCM Foundation): reverse-charge tax, self-assessed by the recipient -
        // never billed by the supplier, never mixed into the forward-charge totals above.
        var rcmCgstTotal = Money.ZERO
        var rcmSgstTotal = Money.ZERO
        var rcmIgstTotal = Money.ZERO
        var resolvedSupplyType = SupplyType.INTRA_STATE

        val stockLines = mutableListOf<VoucherStockLine>()
        val gstTransactions = mutableListOf<GstTransaction>()

        lines.forEachIndexed { index, line ->
            val lineTaxable = VoucherStockLine.computeAmount(line.quantity, line.rate)
            val breakdown = GstCalculationEngine.calculateDetailed(
                GstTransactionFacts(
                    taxableAmount = lineTaxable,
                    gstRatePercent = line.gstRatePercent,
                    cessRatePercent = line.cessRatePercent,
                    supplierStateCode = companyStateCode,
                    placeOfSupply = placeOfSupply,
                    supplyNature = line.supplyNature
                )
            )
            // UI-06: only a NORMAL-nature line may decide the invoice-level CGST/SGST-vs-IGST
            // routing below - EXPORT/EXEMPT/NIL_RATED lines always resolve to a zero-tax
            // SupplyType.EXPORT/EXEMPT and must never be allowed to overwrite this with a line
            // that comes later in the list. Before this field existed every line was always
            // NORMAL, so this condition was always true and this is a no-op for old behavior;
            // once a line can be non-NORMAL, letting it win here would silently drop the
            // CGST/SGST/IGST postings for every real taxable line already accumulated in this
            // same invoice (the party line's total already includes that tax - the ledger entries
            // for it would simply vanish, unbalancing the voucher).
            if (line.supplyNature == GstSupplyNature.NORMAL) resolvedSupplyType = breakdown.supplyType
            taxableTotal += breakdown.taxableAmount
            cessTotal += breakdown.cessAmount

            // Rule 31: route this line's tax into the forward-charge or RCM bucket - the tax
            // amount itself came from the exact same calculateDetailed() call either way.
            if (line.chargeType == GstChargeType.REVERSE_CHARGE) {
                rcmCgstTotal += breakdown.cgstAmount
                rcmSgstTotal += breakdown.sgstAmount
                rcmIgstTotal += breakdown.igstAmount
            } else {
                cgstTotal += breakdown.cgstAmount
                sgstTotal += breakdown.sgstAmount
                igstTotal += breakdown.igstAmount
            }

            stockLines += VoucherStockLine(
                lineId = UUID.randomUUID().toString(), voucherId = voucherId, companyId = companyId,
                financialYearId = financialYearId, itemId = line.itemId, itemName = line.itemName,
                direction = stockDirection, quantity = line.quantity, rate = line.rate,
                amount = lineTaxable, lineOrder = index + 1
            )

            gstTransactions += GstTransaction(
                gstTransactionId = UUID.randomUUID().toString(), companyId = companyId, financialYearId = financialYearId,
                voucherId = voucherId, voucherType = voucherType,
                partyLedgerId = partyLedgerId, partyGstin = partyGstin, placeOfSupply = placeOfSupply,
                supplyType = breakdown.supplyType, itemId = line.itemId, hsnSacCode = line.hsnSacCode,
                quantity = line.quantity, taxableAmount = breakdown.taxableAmount, gstRatePercent = line.gstRatePercent,
                cgst = breakdown.cgstAmount, sgst = breakdown.sgstAmount, igst = breakdown.igstAmount, cess = breakdown.cessAmount,
                direction = direction, lineOrder = index + 1, chargeType = line.chargeType
            )
        }

        // Rule 31: the amount owed to the party excludes reverse-charge tax entirely - the
        // supplier never billed it, so it is never part of what the recipient owes them. Only
        // taxable value + forward-charge tax + cess enters the invoice total/Round Off, exactly as
        // before RCM existed.
        val rawTotal = taxableTotal + cgstTotal + sgstTotal + igstTotal + cessTotal
        val roundOff = RoundOffEngine.roundInvoiceTotal(rawTotal)

        val journalItems = mutableListOf<JournalItem>()
        var order = 1

        journalItems += JournalItem(
            itemId = UUID.randomUUID().toString(), voucherId = voucherId, companyId = companyId,
            financialYearId = financialYearId, ledgerId = partyLedgerId, ledgerName = partyName,
            type = partyLineType, amount = roundOff.roundedTotal,
            narration = if (isSale) "Customer invoice debit" else "Supplier bill credit", lineOrder = order++
        )
        journalItems += JournalItem(
            itemId = UUID.randomUUID().toString(), voucherId = voucherId, companyId = companyId,
            financialYearId = financialYearId, ledgerId = tradeLedgerId, ledgerName = tradeLedgerName,
            type = tradeLineType, amount = taxableTotal,
            narration = if (isSale) "Taxable sales revenue" else "Taxable purchase value", lineOrder = order++
        )

        fun taxLine(ref: LedgerRef, amount: Money, type: DrCr, label: String) {
            if (amount.isPositive) {
                journalItems += JournalItem(
                    itemId = UUID.randomUUID().toString(), voucherId = voucherId, companyId = companyId,
                    financialYearId = financialYearId, ledgerId = ref.ledgerId, ledgerName = ref.name,
                    type = type, amount = amount, narration = label, lineOrder = order++
                )
            }
        }

        val directionLabel = if (isSale) "Output" else "Input"
        when (resolvedSupplyType) {
            SupplyType.INTRA_STATE -> {
                taxLine(if (isSale) gstLedgers.outputCgst else gstLedgers.inputCgst, cgstTotal, taxLineType, "$directionLabel CGST")
                taxLine(if (isSale) gstLedgers.outputSgst else gstLedgers.inputSgst, sgstTotal, taxLineType, "$directionLabel SGST")
            }
            SupplyType.INTER_STATE -> {
                taxLine(if (isSale) gstLedgers.outputIgst else gstLedgers.inputIgst, igstTotal, taxLineType, "$directionLabel IGST")
            }
            SupplyType.EXPORT, SupplyType.EXEMPT -> { /* zero-rated/exempt: nothing to post */ }
        }
        taxLine(gstLedgers.cess, cessTotal, taxLineType, "CESS")

        // Rule 31 (Purchase/RCM Foundation): the reverse-charge pair is fully self-balancing (the
        // exact same amount posted to both sides) - it never needs Round Off and never touches the
        // supplier-payable line. Uses the same resolvedSupplyType already resolved above, since RCM
        // is only ever valid on a NORMAL-nature line (enforced by the require() above), and a
        // NORMAL line's geography-derived supply type is shared across the whole invoice.
        when (resolvedSupplyType) {
            SupplyType.INTRA_STATE -> {
                taxLine(gstLedgers.rcmInputCgst, rcmCgstTotal, DrCr.DEBIT, "RCM Input CGST")
                taxLine(gstLedgers.rcmLiabilityCgst, rcmCgstTotal, DrCr.CREDIT, "RCM Liability CGST")
                taxLine(gstLedgers.rcmInputSgst, rcmSgstTotal, DrCr.DEBIT, "RCM Input SGST")
                taxLine(gstLedgers.rcmLiabilitySgst, rcmSgstTotal, DrCr.CREDIT, "RCM Liability SGST")
            }
            SupplyType.INTER_STATE -> {
                taxLine(gstLedgers.rcmInputIgst, rcmIgstTotal, DrCr.DEBIT, "RCM Input IGST")
                taxLine(gstLedgers.rcmLiabilityIgst, rcmIgstTotal, DrCr.CREDIT, "RCM Liability IGST")
            }
            SupplyType.EXPORT, SupplyType.EXEMPT -> { /* RCM never applies here - the rcm totals are zero (require() above enforces NORMAL nature) */ }
        }

        // Round Off (Priority 7): the party line already carries the rounded total while the
        // trade+tax lines sum to the raw total, so whichever side is now short needs the
        // difference posted to Round Off. Sale's party line is the Debit side; Purchase's is Credit
        // - the sign flips accordingly.
        if (roundOff.roundOffAmount.paise != 0L) {
            val type = if (isSale) {
                if (roundOff.roundOffAmount.isPositive) DrCr.CREDIT else DrCr.DEBIT
            } else {
                if (roundOff.roundOffAmount.isPositive) DrCr.DEBIT else DrCr.CREDIT
            }
            journalItems += JournalItem(
                itemId = UUID.randomUUID().toString(), voucherId = voucherId, companyId = companyId,
                financialYearId = financialYearId, ledgerId = roundOffLedgerId, ledgerName = roundOffLedgerName,
                type = type, amount = roundOff.roundOffAmount.abs(), narration = "Invoice total rounding adjustment", lineOrder = order++
            )
        }

        return TradingWorkflowResult(journalItems, stockLines, gstTransactions, roundOff.roundedTotal)
    }

    /**
     * Builds a Credit Note (against an original Sale) or Debit Note (against an original Purchase)
     * as a full reversal of the original document (Phase 5, Priority 1) - opposite-sign journal
     * lines on the same ledgers, opposite-direction stock lines at the SAME quantity/rate as the
     * original (so the stock value removed/added by the original posting is restored exactly), and
     * GST-transaction rows carrying NEGATED amounts at the SAME direction as the original (the
     * standard GST-return representation: a credit/debit note nets against the original outward/
     * inward supply figure, it does not become a new opposite-direction transaction). The original
     * voucher/journal items/stock lines/GST transactions are never read back INTO this function's
     * output - only used to derive it; nothing about the original row is ever modified.
     */
    fun buildNote(
        noteVoucherId: String,
        originalJournalItems: List<JournalItem>,
        originalStockLines: List<VoucherStockLine>,
        originalGstTransactions: List<GstTransaction>
    ): TradingWorkflowResult {
        val journalItems = originalJournalItems.mapIndexed { index, item ->
            item.copy(
                itemId = UUID.randomUUID().toString(),
                voucherId = noteVoucherId,
                type = if (item.type == DrCr.DEBIT) DrCr.CREDIT else DrCr.DEBIT,
                narration = "Reversal: ${item.narration}".trim(),
                lineOrder = index + 1
            )
        }
        val stockLines = originalStockLines.mapIndexed { index, line ->
            line.copy(
                lineId = UUID.randomUUID().toString(),
                voucherId = noteVoucherId,
                direction = if (line.direction == StockDirection.IN) StockDirection.OUT else StockDirection.IN,
                lineOrder = index + 1
            )
        }
        val gstTransactions = originalGstTransactions.mapIndexed { index, gt ->
            gt.copy(
                gstTransactionId = UUID.randomUUID().toString(),
                voucherId = noteVoucherId,
                taxableAmount = -gt.taxableAmount,
                cgst = -gt.cgst,
                sgst = -gt.sgst,
                igst = -gt.igst,
                cess = -gt.cess,
                lineOrder = index + 1
            )
        }
        val totalAmount = originalJournalItems.filter { it.type == DrCr.DEBIT }.fold(Money.ZERO) { acc, i -> acc + i.amount }
        return TradingWorkflowResult(journalItems, stockLines, gstTransactions, totalAmount)
    }
}
