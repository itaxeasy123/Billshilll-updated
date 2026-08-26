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
    val supplyNature: GstSupplyNature = GstSupplyNature.NORMAL
)

/** A resolved ledger reference (companyId-suffixed id + display name) - supplied by the caller
 * (which has DAO access) so this engine itself stays pure/Room-independent. */
data class LedgerRef(val ledgerId: String, val name: String)

data class TradingGstLedgers(
    val outputCgst: LedgerRef, val outputSgst: LedgerRef, val outputIgst: LedgerRef,
    val inputCgst: LedgerRef, val inputSgst: LedgerRef, val inputIgst: LedgerRef,
    val cess: LedgerRef
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

        val direction = if (isSale) GstDirection.OUTPUT else GstDirection.INPUT
        val taxLineType = if (isSale) DrCr.CREDIT else DrCr.DEBIT
        val tradeLineType = if (isSale) DrCr.CREDIT else DrCr.DEBIT
        val partyLineType = if (isSale) DrCr.DEBIT else DrCr.CREDIT
        val stockDirection = if (isSale) StockDirection.OUT else StockDirection.IN
        val voucherType = if (isSale) VoucherType.SALES else VoucherType.PURCHASE

        var taxableTotal = Money.ZERO
        var cgstTotal = Money.ZERO
        var sgstTotal = Money.ZERO
        var igstTotal = Money.ZERO
        var cessTotal = Money.ZERO
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
            cgstTotal += breakdown.cgstAmount
            sgstTotal += breakdown.sgstAmount
            igstTotal += breakdown.igstAmount
            cessTotal += breakdown.cessAmount

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
                direction = direction, lineOrder = index + 1
            )
        }

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

        fun taxLine(ref: LedgerRef, amount: Money, label: String) {
            if (amount.isPositive) {
                journalItems += JournalItem(
                    itemId = UUID.randomUUID().toString(), voucherId = voucherId, companyId = companyId,
                    financialYearId = financialYearId, ledgerId = ref.ledgerId, ledgerName = ref.name,
                    type = taxLineType, amount = amount, narration = label, lineOrder = order++
                )
            }
        }

        val directionLabel = if (isSale) "Output" else "Input"
        when (resolvedSupplyType) {
            SupplyType.INTRA_STATE -> {
                taxLine(if (isSale) gstLedgers.outputCgst else gstLedgers.inputCgst, cgstTotal, "$directionLabel CGST")
                taxLine(if (isSale) gstLedgers.outputSgst else gstLedgers.inputSgst, sgstTotal, "$directionLabel SGST")
            }
            SupplyType.INTER_STATE -> {
                taxLine(if (isSale) gstLedgers.outputIgst else gstLedgers.inputIgst, igstTotal, "$directionLabel IGST")
            }
            SupplyType.EXPORT, SupplyType.EXEMPT -> { /* zero-rated/exempt: nothing to post */ }
        }
        taxLine(gstLedgers.cess, cessTotal, "CESS")

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
