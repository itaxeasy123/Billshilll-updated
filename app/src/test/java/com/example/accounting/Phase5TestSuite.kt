package com.example.accounting

import com.example.accounting.core.common.AppError
import com.example.accounting.core.common.DrCr
import com.example.accounting.core.common.Money
import com.example.accounting.core.common.Quantity
import com.example.accounting.core.database.AccountingTransactionException
import com.example.accounting.core.database.VoucherPostingEngine
import com.example.accounting.data.local.dao.AccountingDao
import com.example.accounting.data.local.entity.AccountingPeriodEntity
import com.example.accounting.data.local.entity.CompanyEntity
import com.example.accounting.data.local.entity.FinancialYearEntity
import com.example.accounting.data.local.entity.GroupEntity
import com.example.accounting.data.local.entity.GstFilingPeriodEntity
import com.example.accounting.data.local.entity.GstTransactionEntity
import com.example.accounting.data.local.entity.JournalItemEntity
import com.example.accounting.data.local.entity.LedgerEntity
import com.example.accounting.data.local.entity.SettlementAllocationEntity
import com.example.accounting.data.local.entity.StockItemEntity
import com.example.accounting.data.local.entity.VoucherEntity
import com.example.accounting.data.local.entity.VoucherStockLineEntity
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.JournalItem
import com.example.accounting.domain.accounting.StandardSystemGroups
import com.example.accounting.domain.accounting.SyncState
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.domain.company.AccountingMode
import com.example.accounting.domain.company.BusinessType
import com.example.accounting.domain.financialyear.PeriodStatus
import com.example.accounting.domain.inventory.StockDirection
import com.example.accounting.domain.inventory.VoucherStockLine
import com.example.accounting.domain.taxation.gst.GstCalculationEngine
import com.example.accounting.domain.taxation.gst.GstDirection
import com.example.accounting.domain.taxation.gst.GstLedgerIds
import com.example.accounting.domain.taxation.gst.GstSupplyNature
import com.example.accounting.domain.taxation.gst.GstTransaction
import com.example.accounting.domain.taxation.gst.GstTransactionFacts
import com.example.accounting.domain.taxation.gst.SupplyType
import com.example.accounting.domain.trading.LedgerRef
import com.example.accounting.domain.trading.TradingGstLedgers
import com.example.accounting.domain.trading.TradingLineInput
import com.example.accounting.domain.trading.TradingWorkflowEngine
import com.example.accounting.domain.trading.TradingWorkflowResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

/**
 * Phase 5 - GST & Statutory Accounting Engine test suite (full scope, per the user's 5.21
 * checklist). Pure JVM, following the exact Phase 2/4 pattern: [VoucherPostingEngine] is exercised
 * directly against a fake DAO (bypassing [AccountingRepository.postVoucher], which needs a real
 * Room `AppDatabase` for its atomic transaction and so is only usable here for its `db == null`
 * read/validation-only paths - generateGSTSummary, ensureGstLedgersExist, allocateSettlement,
 * updateLedger/deleteLedgerSafely protection checks).
 *
 * [Phase5AwareDao] wraps [Phase4TestSuite.InventoryAwareDao] (real stock backing, reused rather
 * than duplicated) and adds real in-memory backing for gst_transactions/settlement_allocations/
 * gst_filing_periods - [FakeAccountingDao] (Phase0TestSuite.kt) stubs all of these as no-ops,
 * harmless for Phase 0-4 (which never exercise them) but necessary here.
 */
class Phase5TestSuite {

    private class Phase5AwareDao(delegate: AccountingDao) : AccountingDao by delegate {
        private val gstTransactions = mutableListOf<GstTransactionEntity>()
        private val allocations = mutableListOf<SettlementAllocationEntity>()
        private val filingPeriods = LinkedHashMap<String, GstFilingPeriodEntity>()

        override suspend fun getGstTransactionsForVoucher(voucherId: String) = gstTransactions.filter { it.voucherId == voucherId }
        override suspend fun getGstTransactionsForCompanyFY(companyId: String, fyId: String) =
            gstTransactions.filter { it.companyId == companyId && it.financialYearId == fyId }
        override suspend fun insertGstTransactions(transactions: List<GstTransactionEntity>) { gstTransactions += transactions }

        override suspend fun getAllocationsForInvoice(invoiceVoucherId: String) = allocations.filter { it.invoiceVoucherId == invoiceVoucherId }
        override suspend fun getAllocationsForSettlement(settlementVoucherId: String) = allocations.filter { it.settlementVoucherId == settlementVoucherId }
        override suspend fun insertSettlementAllocations(rows: List<SettlementAllocationEntity>) { allocations += rows }

        override fun getGstFilingPeriodsByCompany(companyId: String): Flow<List<GstFilingPeriodEntity>> = flowOf(filingPeriods.values.filter { it.companyId == companyId })
        override suspend fun insertGstFilingPeriod(period: GstFilingPeriodEntity) { filingPeriods[period.filingPeriodId] = period }
        override suspend fun setGstFilingPeriodLock(companyId: String, filingPeriodId: String, isLocked: Boolean, lockedAt: Long?, lockedBy: String?) {
            filingPeriods[filingPeriodId]?.let { filingPeriods[filingPeriodId] = it.copy(isLocked = isLocked, lockedAt = lockedAt, lockedBy = lockedBy) }
        }
    }

    private val companyId = "COMP_P5"
    private val fyId = "FY_P5_2026_27"

    private fun freshDao() = Phase5AwareDao(Phase4TestSuite.InventoryAwareDao(FakeAccountingDao()))

    private suspend fun AccountingDao.seedCompany(company: String = companyId, financialYearId: String = fyId) {
        insertCompany(CompanyEntity(
            companyId = company, name = "Company $company", tradeName = "Company $company", gstin = "27AAAAA0000A1Z5",
            pan = "AAAAA0000A", stateCode = "27", stateName = "Maharashtra", email = "", phone = "", address = "",
            currency = "INR", financialYearStartMonth = 4, isDefault = true, createdAt = 0L,
            accountingMode = AccountingMode.ACCOUNT_WITH_INVENTORY, businessType = BusinessType.TRADING
        ))
        insertFinancialYear(FinancialYearEntity(financialYearId, company, "FY 2026-27", "2026-04-01", "2027-03-31", true, false, null, null))
        insertPeriods(listOf(AccountingPeriodEntity("PER_${company}_1", company, financialYearId, "Full Year", "2026-04-01", "2027-03-31", PeriodStatus.OPEN, null, null)))
        insertGroups(StandardSystemGroups.getStandardGroupsForCompany(company).map {
            GroupEntity(it.groupId, it.companyId, it.name, it.primaryGroup, it.parentGroupId, it.isSystem, it.affectsGrossProfit, it.displayOrder)
        })
    }

    private fun ledger(id: String, company: String, groupBare: String, openingPaise: Long = 0L, openingType: DrCr = DrCr.DEBIT, stateCode: String = "27") =
        LedgerEntity(id, company, "${groupBare}_$company", id, id, openingPaise, openingType, openingPaise, openingType, "", "", stateCode, "", "", "", "", "", false, true, "", 0.0)

    private suspend fun AccountingDao.seedTradingLedgers(company: String = companyId) {
        insertLedger(ledger("LED_BANK", company, StandardSystemGroups.BANK_GROUP_ID, 1_00_00_000_00L))
        insertLedger(ledger("LED_CASH", company, StandardSystemGroups.CASH_GROUP_ID, 5_00_000_00L))
        insertLedger(ledger("LED_DEBTOR", company, StandardSystemGroups.DEBTORS_GROUP_ID))
        insertLedger(ledger("LED_DEBTOR_INTERSTATE", company, StandardSystemGroups.DEBTORS_GROUP_ID, stateCode = "09"))
        insertLedger(ledger("LED_CREDITOR", company, StandardSystemGroups.CREDITORS_GROUP_ID, openingType = DrCr.CREDIT))
        insertLedger(ledger("LED_SALES", company, StandardSystemGroups.SALES_GROUP_ID, openingType = DrCr.CREDIT))
        insertLedger(ledger("LED_PURCHASE", company, StandardSystemGroups.PURCHASE_GROUP_ID))
        insertLedger(ledger("LED_CAPITAL", company, StandardSystemGroups.CAPITAL_GROUP_ID, openingType = DrCr.CREDIT))
        insertLedger(ledger(StandardSystemGroups.ROUND_OFF_LEDGER_ID + "_$company", company, StandardSystemGroups.ROUND_OFF_GROUP_ID))
    }

    private fun stockItem(itemId: String, company: String = companyId, gstRate: Double = 18.0, hsn: String = "8471", openingQty: Long = 0L) = StockItemEntity(
        itemId = itemId, companyId = company, name = itemId, sku = itemId, hsnCode = hsn, unit = "Pcs", gstRatePercent = gstRate,
        openingQuantity = openingQty, openingRatePaise = 100_00L, currentQuantity = openingQty, standardCostPaise = 100_00L, standardSellingPricePaise = 100_00L, currentAvgCostPaise = 100_00L
    )

    private fun gstLedgerRefs(company: String) = TradingGstLedgers(
        outputCgst = LedgerRef("${GstLedgerIds.OUTPUT_CGST_LEDGER_ID}_$company", "Output CGST A/c"),
        outputSgst = LedgerRef("${GstLedgerIds.OUTPUT_SGST_LEDGER_ID}_$company", "Output SGST A/c"),
        outputIgst = LedgerRef("${GstLedgerIds.OUTPUT_IGST_LEDGER_ID}_$company", "Output IGST A/c"),
        inputCgst = LedgerRef("${GstLedgerIds.INPUT_CGST_LEDGER_ID}_$company", "Input CGST A/c"),
        inputSgst = LedgerRef("${GstLedgerIds.INPUT_SGST_LEDGER_ID}_$company", "Input SGST A/c"),
        inputIgst = LedgerRef("${GstLedgerIds.INPUT_IGST_LEDGER_ID}_$company", "Input IGST A/c"),
        cess = LedgerRef("${GstLedgerIds.CESS_LEDGER_ID}_$company", "CESS A/c")
    )

    private fun JournalItem.toEntity() = JournalItemEntity(itemId, voucherId, companyId, financialYearId, ledgerId, type, amount.paise, narration, lineOrder)
    private fun VoucherStockLine.toEntity() = VoucherStockLineEntity(lineId, voucherId, companyId, financialYearId, itemId, direction, quantity.rawValue, rate.paise, amount.paise, lineOrder)
    private fun GstTransaction.toEntity() = GstTransactionEntity(
        gstTransactionId, companyId, financialYearId, voucherId, voucherType, partyLedgerId, partyGstin, placeOfSupply,
        supplyType, itemId, hsnSacCode, quantity?.rawValue, taxableAmount.paise, gstRatePercent, cgst.paise, sgst.paise,
        igst.paise, cess.paise, direction, lineOrder, createdAt = 0L
    )

    private suspend fun postResult(
        dao: AccountingDao, voucherId: String, voucherType: VoucherType, result: TradingWorkflowResult,
        company: String = companyId, fy: String = fyId, refVoucherId: String? = null
    ) {
        val voucherEntity = VoucherEntity(
            voucherId = voucherId, companyId = company, financialYearId = fy, voucherNumber = voucherId, voucherType = voucherType,
            date = "2026-05-10", referenceNumber = "", narration = "", totalAmountPaise = result.totalAmount.paise,
            isPosted = true, isCancelled = false, syncState = SyncState.PENDING, createdAt = 0L, updatedAt = 0L,
            createdBy = "TESTER", partyGstin = "", isGstApplicable = true, referenceVoucherId = refVoucherId, paymentMode = ""
        )
        VoucherPostingEngine.post(
            dao, voucherEntity, result.journalItems.map { it.toEntity() }, "IK_$voucherId", "TESTER",
            result.stockLines.map { it.toEntity() }, result.gstTransactions.map { it.toEntity() }
        )
    }

    private fun line(itemId: String, qty: Long, ratePaise: Long, gstRate: Double, hsn: String = "8471") = TradingLineInput(
        itemId = itemId, itemName = itemId, hsnSacCode = hsn, quantity = Quantity.fromLong(qty), rate = Money.fromPaise(ratePaise), gstRatePercent = gstRate
    )

    // ==========================================
    // A. GST ENGINE (CESS, per-component rates, supply nature, isolation)
    // ==========================================
    @Test
    fun a1_CalculateDetailed_IntraState_SplitsCgstSgst_WithCess() {
        val breakdown = GstCalculationEngine.calculateDetailed(
            GstTransactionFacts(Money.fromRupees(1000L), 18.0, cessRatePercent = 1.0, supplierStateCode = "27", placeOfSupply = "27")
        )
        assertEquals(90_00L, breakdown.cgstAmount.paise)
        assertEquals(90_00L, breakdown.sgstAmount.paise)
        assertEquals(0L, breakdown.igstAmount.paise)
        assertEquals(10_00L, breakdown.cessAmount.paise)
        assertEquals(9.0, breakdown.cgstRatePercent, 0.0001)
        assertEquals(9.0, breakdown.sgstRatePercent, 0.0001)
        assertEquals(190_00L, breakdown.totalTax.paise)
        assertEquals(1190_00L, breakdown.totalWithTax.paise)
    }

    @Test
    fun a2_CalculateDetailed_InterState_IgstOnly() {
        val breakdown = GstCalculationEngine.calculateDetailed(
            GstTransactionFacts(Money.fromRupees(1000L), 18.0, supplierStateCode = "27", placeOfSupply = "09")
        )
        assertEquals(0L, breakdown.cgstAmount.paise)
        assertEquals(0L, breakdown.sgstAmount.paise)
        assertEquals(180_00L, breakdown.igstAmount.paise)
        assertEquals(18.0, breakdown.igstRatePercent, 0.0001)
    }

    @Test
    fun a3_CalculateDetailed_ZeroRate_NoTax() {
        val breakdown = GstCalculationEngine.calculateDetailed(
            GstTransactionFacts(Money.fromRupees(1000L), 0.0, supplierStateCode = "27", placeOfSupply = "27")
        )
        assertEquals(0L, breakdown.totalTax.paise)
        assertEquals(1000_00L, breakdown.totalWithTax.paise)
    }

    @Test
    fun a4_CalculateDetailed_Export_ZeroTax_SupplyTypeExport() {
        val breakdown = GstCalculationEngine.calculateDetailed(
            GstTransactionFacts(Money.fromRupees(1000L), 18.0, supplierStateCode = "27", placeOfSupply = "09", supplyNature = GstSupplyNature.EXPORT)
        )
        assertEquals(SupplyType.EXPORT, breakdown.supplyType)
        assertEquals(0L, breakdown.totalTax.paise)
    }

    @Test
    fun a5_CalculateDetailed_Exempt_ZeroTax_SupplyTypeExempt() {
        val breakdown = GstCalculationEngine.calculateDetailed(
            GstTransactionFacts(Money.fromRupees(1000L), 18.0, supplierStateCode = "27", placeOfSupply = "27", supplyNature = GstSupplyNature.EXEMPT)
        )
        assertEquals(SupplyType.EXEMPT, breakdown.supplyType)
        assertEquals(0L, breakdown.totalTax.paise)
    }

    @Test
    fun a6_CalculateDetailed_MultipleRates_5And28Percent() {
        val at5 = GstCalculationEngine.calculateDetailed(GstTransactionFacts(Money.fromRupees(1000L), 5.0, supplierStateCode = "27", placeOfSupply = "27"))
        val at28 = GstCalculationEngine.calculateDetailed(GstTransactionFacts(Money.fromRupees(1000L), 28.0, supplierStateCode = "27", placeOfSupply = "27"))
        assertEquals(50_00L, at5.totalTax.paise)
        assertEquals(280_00L, at28.totalTax.paise)
    }

    @Test
    fun a7_TradingWorkflow_ItemDrivenRate_NeverHardcoded() {
        val result = TradingWorkflowEngine.buildSale(
            voucherId = "V1", companyId = companyId, financialYearId = fyId,
            customerLedgerId = "LED_DEBTOR", customerName = "Cust", customerGstin = "",
            salesLedgerId = "LED_SALES", salesLedgerName = "Sales",
            companyStateCode = "27", placeOfSupply = "27",
            lines = listOf(line("ITEM_A", 10, 100_00L, gstRate = 5.0), line("ITEM_B", 5, 200_00L, gstRate = 28.0)),
            gstLedgers = gstLedgerRefs(companyId), roundOffLedgerId = "LED_RO", roundOffLedgerName = "Round Off"
        )
        // ITEM_A: taxable 1000.00 @ 5% = 50.00 tax. ITEM_B: taxable 1000.00 @ 28% = 280.00 tax.
        val taxLines = result.journalItems.filter { it.ledgerId.startsWith("LED_GST") }
        val totalTax = taxLines.fold(Money.ZERO) { acc, i -> acc + i.amount }
        assertEquals(330_00L, totalTax.paise)
        assertEquals(2, result.gstTransactions.size)
        assertEquals(5.0, result.gstTransactions.first { it.itemId == "ITEM_A" }.gstRatePercent, 0.0001)
        assertEquals(28.0, result.gstTransactions.first { it.itemId == "ITEM_B" }.gstRatePercent, 0.0001)
    }

    @Test
    fun a8_TradingWorkflow_Sale_GstTransactionsAreOutputDirection() {
        val result = TradingWorkflowEngine.buildSale(
            voucherId = "V1", companyId = companyId, financialYearId = fyId,
            customerLedgerId = "LED_DEBTOR", customerName = "Cust", customerGstin = "",
            salesLedgerId = "LED_SALES", salesLedgerName = "Sales", companyStateCode = "27", placeOfSupply = "27",
            lines = listOf(line("ITEM_A", 1, 100_00L, 18.0)), gstLedgers = gstLedgerRefs(companyId),
            roundOffLedgerId = "LED_RO", roundOffLedgerName = "Round Off"
        )
        assertTrue(result.gstTransactions.all { it.direction == GstDirection.OUTPUT })
    }

    @Test
    fun a9_TradingWorkflow_Purchase_GstTransactionsAreInputDirection() {
        val result = TradingWorkflowEngine.buildPurchase(
            voucherId = "V1", companyId = companyId, financialYearId = fyId,
            supplierLedgerId = "LED_CREDITOR", supplierName = "Supp", supplierGstin = "",
            purchaseLedgerId = "LED_PURCHASE", purchaseLedgerName = "Purchase", companyStateCode = "27", placeOfSupply = "27",
            lines = listOf(line("ITEM_A", 1, 100_00L, 18.0)), gstLedgers = gstLedgerRefs(companyId),
            roundOffLedgerId = "LED_RO", roundOffLedgerName = "Round Off"
        )
        assertTrue(result.gstTransactions.all { it.direction == GstDirection.INPUT })
    }

    @Test
    fun a10_GstSummary_CompanyIsolation() = runBlocking {
        val dao = freshDao()
        dao.seedCompany(company = "COMP_A", financialYearId = "FY_A")
        dao.seedCompany(company = "COMP_B", financialYearId = "FY_B")
        dao.seedTradingLedgers(company = "COMP_A")
        dao.seedTradingLedgers(company = "COMP_B")
        // Company-suffixed item IDs: the fake DAO's stock-item map is keyed only by itemId (same
        // limitation Phase4TestSuite's d1_CompanyIsolation hit and fixed the same way), so two
        // companies cannot share a bare "ITEM_A" id without one silently overwriting the other.
        dao.insertStockItem(stockItem("ITEM_A_COMP_A", company = "COMP_A", openingQty = 100_000L))
        dao.insertStockItem(stockItem("ITEM_A_COMP_B", company = "COMP_B", openingQty = 100_000L))

        val resultA = TradingWorkflowEngine.buildSale(
            "VA", "COMP_A", "FY_A", "LED_DEBTOR", "Cust", "", "LED_SALES", "Sales", "27", "27",
            listOf(line("ITEM_A_COMP_A", 1, 1000_00L, 18.0)), gstLedgerRefs("COMP_A"), "LED_RO", "Round Off"
        )
        postResult(dao, "VA", VoucherType.SALES, resultA, company = "COMP_A", fy = "FY_A")

        val resultB = TradingWorkflowEngine.buildSale(
            "VB", "COMP_B", "FY_B", "LED_DEBTOR", "Cust", "", "LED_SALES", "Sales", "27", "27",
            listOf(line("ITEM_A_COMP_B", 1, 5000_00L, 18.0)), gstLedgerRefs("COMP_B"), "LED_RO", "Round Off"
        )
        postResult(dao, "VB", VoucherType.SALES, resultB, company = "COMP_B", fy = "FY_B")

        val repo = AccountingRepository(dao)
        val summaryA = repo.generateGSTSummary("COMP_A", "FY_A")
        assertEquals(1000_00L, summaryA.totalTaxableOutward.paise)
    }

    @Test
    fun a11_GstSummary_FinancialYearIsolation() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        dao.insertStockItem(stockItem("ITEM_A", openingQty = 100_000L))

        val resultFy1 = TradingWorkflowEngine.buildSale(
            "V1", companyId, fyId, "LED_DEBTOR", "Cust", "", "LED_SALES", "Sales", "27", "27",
            listOf(line("ITEM_A", 1, 1000_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off"
        )
        postResult(dao, "V1", VoucherType.SALES, resultFy1)

        val resultFy2 = TradingWorkflowEngine.buildSale(
            "V2", companyId, "FY_OTHER", "LED_DEBTOR", "Cust", "", "LED_SALES", "Sales", "27", "27",
            listOf(line("ITEM_A", 1, 9000_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off"
        )
        postResult(dao, "V2", VoucherType.SALES, resultFy2, fy = "FY_OTHER")

        val repo = AccountingRepository(dao)
        val summary = repo.generateGSTSummary(companyId, fyId)
        assertEquals(1000_00L, summary.totalTaxableOutward.paise)
    }

    // ==========================================
    // B. SALE / PURCHASE TRADING FLOW
    // ==========================================
    @Test
    fun b1_Sale_CreatesReceivable_MovesStock_ChargesOutputGst() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        dao.insertStockItem(stockItem("ITEM_A"))
        // Purchase first to establish stock + cost basis.
        val purchase = TradingWorkflowEngine.buildPurchase(
            "V_PUR", companyId, fyId, "LED_CREDITOR", "Supp", "", "LED_PURCHASE", "Purchase", "27", "27",
            listOf(line("ITEM_A", 10, 500_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off"
        )
        postResult(dao, "V_PUR", VoucherType.PURCHASE, purchase)

        val sale = TradingWorkflowEngine.buildSale(
            "V_SAL", companyId, fyId, "LED_DEBTOR", "Cust", "", "LED_SALES", "Sales", "27", "27",
            listOf(line("ITEM_A", 4, 800_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off"
        )
        postResult(dao, "V_SAL", VoucherType.SALES, sale)

        val debtorLine = sale.journalItems.first { it.ledgerId == "LED_DEBTOR" }
        assertEquals(DrCr.DEBIT, debtorLine.type)
        assertTrue("Sale must charge Output GST", sale.journalItems.any { it.ledgerId.contains("OUTPUT") })

        val item = dao.getStockItemById(companyId, "ITEM_A")!!
        assertEquals(6_000L, item.currentQuantity) // 10 in - 4 out = 6 remaining (thousandths)
    }

    @Test
    fun b2_Purchase_CreatesPayable_MovesStock_ClaimsInputGst() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        dao.insertStockItem(stockItem("ITEM_A"))

        val purchase = TradingWorkflowEngine.buildPurchase(
            "V_PUR", companyId, fyId, "LED_CREDITOR", "Supp", "", "LED_PURCHASE", "Purchase", "27", "27",
            listOf(line("ITEM_A", 10, 500_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off"
        )
        postResult(dao, "V_PUR", VoucherType.PURCHASE, purchase)

        val creditorLine = purchase.journalItems.first { it.ledgerId == "LED_CREDITOR" }
        assertEquals(DrCr.CREDIT, creditorLine.type)
        assertTrue("Purchase must claim Input GST", purchase.journalItems.any { it.ledgerId.contains("INPUT") })

        val item = dao.getStockItemById(companyId, "ITEM_A")!!
        assertEquals(10_000L, item.currentQuantity)
    }

    @Test
    fun b3_Sale_ProducesCogsEligibleMovement() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        dao.insertStockItem(stockItem("ITEM_A"))

        val purchase = TradingWorkflowEngine.buildPurchase(
            "V_PUR", companyId, fyId, "LED_CREDITOR", "Supp", "", "LED_PURCHASE", "Purchase", "27", "27",
            listOf(line("ITEM_A", 10, 500_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off"
        )
        postResult(dao, "V_PUR", VoucherType.PURCHASE, purchase)

        val sale = TradingWorkflowEngine.buildSale(
            "V_SAL", companyId, fyId, "LED_DEBTOR", "Cust", "", "LED_SALES", "Sales", "27", "27",
            listOf(line("ITEM_A", 4, 800_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off"
        )
        postResult(dao, "V_SAL", VoucherType.SALES, sale)

        val movements = dao.getStockMovementsForVoucher("V_SAL")
        assertEquals(1, movements.size)
        assertEquals(StockDirection.OUT, movements.first().direction)
        // OUT movements cost at the weighted-average purchase cost, not the selling price -
        // directly usable as COGS (Phase 4 valuation rule, unaffected by Phase 5).
        assertEquals(500_00L, movements.first().ratePaise)
    }

    @Test
    fun b4_InvoiceNumberUniqueness_RejectsDuplicate() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        dao.insertStockItem(stockItem("ITEM_A", openingQty = 100_000L))
        val sale = TradingWorkflowEngine.buildSale(
            "V1", companyId, fyId, "LED_DEBTOR", "Cust", "", "LED_SALES", "Sales", "27", "27",
            listOf(line("ITEM_A", 1, 100_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off"
        )
        val v1 = VoucherEntity("V1", companyId, fyId, "INV-0001", VoucherType.SALES, "2026-05-10", "", "", sale.totalAmount.paise, true, false, SyncState.PENDING, 0L, 0L, "TESTER", "", true)
        VoucherPostingEngine.post(dao, v1, sale.journalItems.map { it.toEntity() }, "IK1", "TESTER", sale.stockLines.map { it.toEntity() }, sale.gstTransactions.map { it.toEntity() })

        val v2 = VoucherEntity("V2", companyId, fyId, "INV-0001", VoucherType.SALES, "2026-05-11", "", "", sale.totalAmount.paise, true, false, SyncState.PENDING, 0L, 0L, "TESTER", "", true)
        try {
            VoucherPostingEngine.post(dao, v2, sale.journalItems.map { it.toEntity() }, "IK2", "TESTER")
            fail("Expected duplicate voucher-number rejection")
        } catch (e: AccountingTransactionException) {
            assertTrue(e.appError is AppError.DuplicateVoucherNumber)
        }
    }

    // ==========================================
    // C. CREDIT NOTE / DEBIT NOTE
    // ==========================================
    @Test
    fun c1_CreditNote_ReversesSale_OutputGst_Inventory_KeepsOriginalImmutable() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        dao.insertStockItem(stockItem("ITEM_A"))

        val purchase = TradingWorkflowEngine.buildPurchase(
            "V_PUR", companyId, fyId, "LED_CREDITOR", "Supp", "", "LED_PURCHASE", "Purchase", "27", "27",
            listOf(line("ITEM_A", 10, 500_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off"
        )
        postResult(dao, "V_PUR", VoucherType.PURCHASE, purchase)

        val sale = TradingWorkflowEngine.buildSale(
            "V_SAL", companyId, fyId, "LED_DEBTOR", "Cust", "", "LED_SALES", "Sales", "27", "27",
            listOf(line("ITEM_A", 4, 800_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off"
        )
        postResult(dao, "V_SAL", VoucherType.SALES, sale)

        val originalJournalItemsSnapshot = dao.getJournalItemsForVoucherSync("V_SAL").map { it.copy() }
        val originalStockLines = dao.getStockLinesForVoucher("V_SAL")
        val originalGst = dao.getGstTransactionsForVoucher("V_SAL")

        val note = TradingWorkflowEngine.buildNote(
            noteVoucherId = "V_CRN",
            originalJournalItems = originalJournalItemsSnapshot.map {
                JournalItem(it.itemId, it.voucherId, it.companyId, it.financialYearId, it.ledgerId, "", it.type, Money.fromPaise(it.amountPaise), it.narration, it.lineOrder)
            },
            originalStockLines = originalStockLines.map {
                VoucherStockLine(it.lineId, it.voucherId, it.companyId, it.financialYearId, it.itemId, "", it.direction, Quantity(it.quantityRaw), Money.fromPaise(it.ratePaise), Money.fromPaise(it.amountPaise), it.lineOrder)
            },
            originalGstTransactions = originalGst.map {
                GstTransaction(it.gstTransactionId, it.companyId, it.financialYearId, it.voucherId, it.voucherType, it.partyLedgerId, it.partyGstin, it.placeOfSupply, it.supplyType, it.itemId, it.hsnSacCode, it.quantityRaw?.let { q -> Quantity(q) }, Money.fromPaise(it.taxableAmountPaise), it.gstRatePercent, Money.fromPaise(it.cgstPaise), Money.fromPaise(it.sgstPaise), Money.fromPaise(it.igstPaise), Money.fromPaise(it.cessPaise), it.direction, it.lineOrder)
            }
        )
        postResult(dao, "V_CRN", VoucherType.CREDIT_NOTE, note, refVoucherId = "V_SAL")

        // Inventory reverses: 10 purchased - 4 sold + 4 returned = 10 back on hand.
        val item = dao.getStockItemById(companyId, "ITEM_A")!!
        assertEquals(10_000L, item.currentQuantity)

        // Output GST nets to zero across the Sale + Credit Note.
        val repo = AccountingRepository(dao)
        val summary = repo.generateGSTSummary(companyId, fyId)
        assertEquals(0L, summary.totalTaxableOutward.paise)
        assertEquals(0L, summary.totalTaxOutward.paise)

        // Original voucher/journal items are never modified (compared sorted by lineOrder - the
        // fake DAO's ConcurrentHashMap backing does not guarantee iteration order the way the real
        // Room query's `ORDER BY lineOrder ASC` does).
        val afterOriginal = dao.getJournalItemsForVoucherSync("V_SAL")
        assertEquals(
            originalJournalItemsSnapshot.sortedBy { it.lineOrder }.map { it.amountPaise to it.type },
            afterOriginal.sortedBy { it.lineOrder }.map { it.amountPaise to it.type }
        )
        assertFalse(dao.getVoucherById(companyId, "V_SAL")!!.isCancelled)
    }

    @Test
    fun c2_DebitNote_ReversesPurchase_InputGst_Inventory() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        dao.insertStockItem(stockItem("ITEM_A"))

        val purchase = TradingWorkflowEngine.buildPurchase(
            "V_PUR", companyId, fyId, "LED_CREDITOR", "Supp", "", "LED_PURCHASE", "Purchase", "27", "27",
            listOf(line("ITEM_A", 10, 500_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off"
        )
        postResult(dao, "V_PUR", VoucherType.PURCHASE, purchase)

        val originalJournalItemsSnapshot = dao.getJournalItemsForVoucherSync("V_PUR").map { it.copy() }
        val originalStockLines = dao.getStockLinesForVoucher("V_PUR")
        val originalGst = dao.getGstTransactionsForVoucher("V_PUR")

        val note = TradingWorkflowEngine.buildNote(
            noteVoucherId = "V_DRN",
            originalJournalItems = originalJournalItemsSnapshot.map {
                JournalItem(it.itemId, it.voucherId, it.companyId, it.financialYearId, it.ledgerId, "", it.type, Money.fromPaise(it.amountPaise), it.narration, it.lineOrder)
            },
            originalStockLines = originalStockLines.map {
                VoucherStockLine(it.lineId, it.voucherId, it.companyId, it.financialYearId, it.itemId, "", it.direction, Quantity(it.quantityRaw), Money.fromPaise(it.ratePaise), Money.fromPaise(it.amountPaise), it.lineOrder)
            },
            originalGstTransactions = originalGst.map {
                GstTransaction(it.gstTransactionId, it.companyId, it.financialYearId, it.voucherId, it.voucherType, it.partyLedgerId, it.partyGstin, it.placeOfSupply, it.supplyType, it.itemId, it.hsnSacCode, it.quantityRaw?.let { q -> Quantity(q) }, Money.fromPaise(it.taxableAmountPaise), it.gstRatePercent, Money.fromPaise(it.cgstPaise), Money.fromPaise(it.sgstPaise), Money.fromPaise(it.igstPaise), Money.fromPaise(it.cessPaise), it.direction, it.lineOrder)
            }
        )
        postResult(dao, "V_DRN", VoucherType.DEBIT_NOTE, note, refVoucherId = "V_PUR")

        val item = dao.getStockItemById(companyId, "ITEM_A")!!
        assertEquals(0L, item.currentQuantity) // fully returned to supplier

        val repo = AccountingRepository(dao)
        val summary = repo.generateGSTSummary(companyId, fyId)
        assertEquals(0L, summary.totalTaxableInward.paise)
        assertEquals(0L, summary.totalTaxInwardITC.paise)
    }

    // ==========================================
    // D. SETTLEMENT / ALLOCATION (Receipt / Payment)
    // ==========================================
    @Test
    fun d1_Receipt_FullAllocation_ClearsOutstanding() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        dao.insertStockItem(stockItem("ITEM_A"))
        val purchase = TradingWorkflowEngine.buildPurchase("V_PUR", companyId, fyId, "LED_CREDITOR", "Supp", "", "LED_PURCHASE", "Purchase", "27", "27", listOf(line("ITEM_A", 10, 500_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off")
        postResult(dao, "V_PUR", VoucherType.PURCHASE, purchase)
        val sale = TradingWorkflowEngine.buildSale("V_SAL", companyId, fyId, "LED_DEBTOR", "Cust", "", "LED_SALES", "Sales", "27", "27", listOf(line("ITEM_A", 4, 800_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off")
        postResult(dao, "V_SAL", VoucherType.SALES, sale)

        val receipt = VoucherEntity("V_RCT", companyId, fyId, "RCT-0001", VoucherType.RECEIPT, "2026-05-12", "", "", sale.totalAmount.paise, true, false, SyncState.PENDING, 0L, 0L, "TESTER", "", false)
        VoucherPostingEngine.post(dao, receipt, listOf(
            JournalItemEntity("I1", "V_RCT", companyId, fyId, "LED_BANK", DrCr.DEBIT, sale.totalAmount.paise, "", 1),
            JournalItemEntity("I2", "V_RCT", companyId, fyId, "LED_DEBTOR", DrCr.CREDIT, sale.totalAmount.paise, "", 2)
        ), "IK_RCT", "TESTER")

        val repo = AccountingRepository(dao)
        val alloc = repo.allocateSettlement(companyId, fyId, "V_RCT", listOf("V_SAL" to sale.totalAmount), Money.ZERO)
        assertTrue(alloc is com.example.accounting.core.common.AccountingResult.Success)

        val outstanding = repo.getOutstandingInvoices(companyId, "LED_DEBTOR")
        assertTrue("Fully allocated invoice must no longer be outstanding", outstanding.none { it.voucherId == "V_SAL" })
    }

    @Test
    fun d2_Receipt_PartialAllocation_LeavesRemainderOutstanding() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        dao.insertStockItem(stockItem("ITEM_A", openingQty = 100_000L))
        val sale = TradingWorkflowEngine.buildSale("V_SAL", companyId, fyId, "LED_DEBTOR", "Cust", "", "LED_SALES", "Sales", "27", "27", listOf(line("ITEM_A", 1, 1000_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off")
        postResult(dao, "V_SAL", VoucherType.SALES, sale)

        val half = Money.fromPaise(sale.totalAmount.paise / 2)
        val receipt = VoucherEntity("V_RCT", companyId, fyId, "RCT-0001", VoucherType.RECEIPT, "2026-05-12", "", "", half.paise, true, false, SyncState.PENDING, 0L, 0L, "TESTER", "", false)
        VoucherPostingEngine.post(dao, receipt, listOf(
            JournalItemEntity("I1", "V_RCT", companyId, fyId, "LED_BANK", DrCr.DEBIT, half.paise, "", 1),
            JournalItemEntity("I2", "V_RCT", companyId, fyId, "LED_DEBTOR", DrCr.CREDIT, half.paise, "", 2)
        ), "IK_RCT", "TESTER")

        val repo = AccountingRepository(dao)
        repo.allocateSettlement(companyId, fyId, "V_RCT", listOf("V_SAL" to half), Money.ZERO)

        val outstanding = repo.getOutstandingInvoices(companyId, "LED_DEBTOR").first { it.voucherId == "V_SAL" }
        assertEquals(sale.totalAmount.paise - half.paise, outstanding.outstandingAmount.paise)
    }

    @Test
    fun d3_Receipt_MultiInvoiceAllocation() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        dao.insertStockItem(stockItem("ITEM_A", openingQty = 100_000L))
        val sale1 = TradingWorkflowEngine.buildSale("V_S1", companyId, fyId, "LED_DEBTOR", "Cust", "", "LED_SALES", "Sales", "27", "27", listOf(line("ITEM_A", 1, 1000_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off")
        postResult(dao, "V_S1", VoucherType.SALES, sale1)
        val sale2 = TradingWorkflowEngine.buildSale("V_S2", companyId, fyId, "LED_DEBTOR", "Cust", "", "LED_SALES", "Sales", "27", "27", listOf(line("ITEM_A", 1, 2000_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off")
        postResult(dao, "V_S2", VoucherType.SALES, sale2)

        val total = sale1.totalAmount + sale2.totalAmount
        val receipt = VoucherEntity("V_RCT", companyId, fyId, "RCT-0001", VoucherType.RECEIPT, "2026-05-12", "", "", total.paise, true, false, SyncState.PENDING, 0L, 0L, "TESTER", "", false)
        VoucherPostingEngine.post(dao, receipt, listOf(
            JournalItemEntity("I1", "V_RCT", companyId, fyId, "LED_BANK", DrCr.DEBIT, total.paise, "", 1),
            JournalItemEntity("I2", "V_RCT", companyId, fyId, "LED_DEBTOR", DrCr.CREDIT, total.paise, "", 2)
        ), "IK_RCT", "TESTER")

        val repo = AccountingRepository(dao)
        val result = repo.allocateSettlement(companyId, fyId, "V_RCT", listOf("V_S1" to sale1.totalAmount, "V_S2" to sale2.totalAmount), Money.ZERO)
        assertTrue(result is com.example.accounting.core.common.AccountingResult.Success)
        val outstanding = repo.getOutstandingInvoices(companyId, "LED_DEBTOR")
        assertTrue(outstanding.none { it.voucherId == "V_S1" || it.voucherId == "V_S2" })
    }

    @Test
    fun d4_Receipt_AdvanceUnallocated_RecordedWithoutInvoice() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        val advance = Money.fromRupees(5000L)
        val receipt = VoucherEntity("V_RCT", companyId, fyId, "RCT-0001", VoucherType.RECEIPT, "2026-05-12", "", "", advance.paise, true, false, SyncState.PENDING, 0L, 0L, "TESTER", "", false)
        VoucherPostingEngine.post(dao, receipt, listOf(
            JournalItemEntity("I1", "V_RCT", companyId, fyId, "LED_BANK", DrCr.DEBIT, advance.paise, "", 1),
            JournalItemEntity("I2", "V_RCT", companyId, fyId, "LED_DEBTOR", DrCr.CREDIT, advance.paise, "", 2)
        ), "IK_RCT", "TESTER")

        val repo = AccountingRepository(dao)
        val result = repo.allocateSettlement(companyId, fyId, "V_RCT", emptyList(), advance)
        assertTrue(result is com.example.accounting.core.common.AccountingResult.Success)
        assertEquals(1, dao.getAllocationsForSettlement("V_RCT").size)
        assertNull(dao.getAllocationsForSettlement("V_RCT").first().invoiceVoucherId)
    }

    @Test
    fun d5_Payment_FullAllocation_ClearsOutstandingPayable() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        dao.insertStockItem(stockItem("ITEM_A"))
        val purchase = TradingWorkflowEngine.buildPurchase("V_PUR", companyId, fyId, "LED_CREDITOR", "Supp", "", "LED_PURCHASE", "Purchase", "27", "27", listOf(line("ITEM_A", 10, 500_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off")
        postResult(dao, "V_PUR", VoucherType.PURCHASE, purchase)

        val payment = VoucherEntity("V_PMT", companyId, fyId, "PMT-0001", VoucherType.PAYMENT, "2026-05-12", "", "", purchase.totalAmount.paise, true, false, SyncState.PENDING, 0L, 0L, "TESTER", "", false)
        VoucherPostingEngine.post(dao, payment, listOf(
            JournalItemEntity("I1", "V_PMT", companyId, fyId, "LED_CREDITOR", DrCr.DEBIT, purchase.totalAmount.paise, "", 1),
            JournalItemEntity("I2", "V_PMT", companyId, fyId, "LED_BANK", DrCr.CREDIT, purchase.totalAmount.paise, "", 2)
        ), "IK_PMT", "TESTER")

        val repo = AccountingRepository(dao)
        repo.allocateSettlement(companyId, fyId, "V_PMT", listOf("V_PUR" to purchase.totalAmount), Money.ZERO)
        val outstanding = repo.getOutstandingInvoices(companyId, "LED_CREDITOR")
        assertTrue(outstanding.none { it.voucherId == "V_PUR" })
    }

    @Test
    fun d6_Settlement_NeverGeneratesGstTransactions() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        val receipt = VoucherEntity("V_RCT", companyId, fyId, "RCT-0001", VoucherType.RECEIPT, "2026-05-12", "", "", 5000_00L, true, false, SyncState.PENDING, 0L, 0L, "TESTER", "", false)
        VoucherPostingEngine.post(dao, receipt, listOf(
            JournalItemEntity("I1", "V_RCT", companyId, fyId, "LED_BANK", DrCr.DEBIT, 5000_00L, "", 1),
            JournalItemEntity("I2", "V_RCT", companyId, fyId, "LED_DEBTOR", DrCr.CREDIT, 5000_00L, "", 2)
        ), "IK_RCT", "TESTER")
        assertTrue(dao.getGstTransactionsForVoucher("V_RCT").isEmpty())
    }

    @Test
    fun d7_Receipt_CannotOverAllocate_BeyondInvoiceOutstanding() = runBlocking {
        // Phase 5 final audit finding: allocateSettlement originally validated only the settlement
        // voucher's own total, never the target invoice's remaining outstanding - a second
        // settlement against an already-fully-paid invoice silently double-paid it. Two separate
        // Receipts, each individually valid on its own total, both fully allocated to the SAME
        // already-settled invoice - the second allocation must be rejected.
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        dao.insertStockItem(stockItem("ITEM_A", openingQty = 100_000L))
        val sale = TradingWorkflowEngine.buildSale("V_SAL", companyId, fyId, "LED_DEBTOR", "Cust", "", "LED_SALES", "Sales", "27", "27", listOf(line("ITEM_A", 1, 1000_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off")
        postResult(dao, "V_SAL", VoucherType.SALES, sale)

        val repo = AccountingRepository(dao)

        val receipt1 = VoucherEntity("V_RCT1", companyId, fyId, "RCT-0001", VoucherType.RECEIPT, "2026-05-12", "", "", sale.totalAmount.paise, true, false, SyncState.PENDING, 0L, 0L, "TESTER", "", false)
        VoucherPostingEngine.post(dao, receipt1, listOf(
            JournalItemEntity("I1", "V_RCT1", companyId, fyId, "LED_BANK", DrCr.DEBIT, sale.totalAmount.paise, "", 1),
            JournalItemEntity("I2", "V_RCT1", companyId, fyId, "LED_DEBTOR", DrCr.CREDIT, sale.totalAmount.paise, "", 2)
        ), "IK_RCT1", "TESTER")
        val firstAlloc = repo.allocateSettlement(companyId, fyId, "V_RCT1", listOf("V_SAL" to sale.totalAmount), Money.ZERO)
        assertTrue("First full allocation against an unpaid invoice must succeed", firstAlloc is com.example.accounting.core.common.AccountingResult.Success)

        val receipt2 = VoucherEntity("V_RCT2", companyId, fyId, "RCT-0002", VoucherType.RECEIPT, "2026-05-13", "", "", sale.totalAmount.paise, true, false, SyncState.PENDING, 0L, 0L, "TESTER", "", false)
        VoucherPostingEngine.post(dao, receipt2, listOf(
            JournalItemEntity("I1", "V_RCT2", companyId, fyId, "LED_BANK", DrCr.DEBIT, sale.totalAmount.paise, "", 1),
            JournalItemEntity("I2", "V_RCT2", companyId, fyId, "LED_DEBTOR", DrCr.CREDIT, sale.totalAmount.paise, "", 2)
        ), "IK_RCT2", "TESTER")
        val secondAlloc = repo.allocateSettlement(companyId, fyId, "V_RCT2", listOf("V_SAL" to sale.totalAmount), Money.ZERO)
        assertTrue(
            "A second allocation against an already-fully-paid invoice must be rejected, not silently double-paid",
            secondAlloc is com.example.accounting.core.common.AccountingResult.Failure
        )
    }

    // ==========================================
    // E. CONTRA / ROUND OFF
    // ==========================================
    @Test
    fun e1_Contra_CashBankOnly_Accepted() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        val contra = VoucherEntity("V_CTR", companyId, fyId, "CTR-0001", VoucherType.CONTRA, "2026-05-12", "", "", 1000_00L, true, false, SyncState.PENDING, 0L, 0L, "TESTER", "", false)
        VoucherPostingEngine.post(dao, contra, listOf(
            JournalItemEntity("I1", "V_CTR", companyId, fyId, "LED_CASH", DrCr.DEBIT, 1000_00L, "", 1),
            JournalItemEntity("I2", "V_CTR", companyId, fyId, "LED_BANK", DrCr.CREDIT, 1000_00L, "", 2)
        ), "IK_CTR", "TESTER")
        assertNotNull(dao.getVoucherById(companyId, "V_CTR"))
    }

    @Test
    fun e2_Contra_InvalidLedger_RejectedAtEngineLevel() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        val contra = VoucherEntity("V_CTR", companyId, fyId, "CTR-0001", VoucherType.CONTRA, "2026-05-12", "", "", 1000_00L, true, false, SyncState.PENDING, 0L, 0L, "TESTER", "", false)
        try {
            VoucherPostingEngine.post(dao, contra, listOf(
                JournalItemEntity("I1", "V_CTR", companyId, fyId, "LED_SALES", DrCr.DEBIT, 1000_00L, "", 1),
                JournalItemEntity("I2", "V_CTR", companyId, fyId, "LED_BANK", DrCr.CREDIT, 1000_00L, "", 2)
            ), "IK_CTR", "TESTER")
            fail("Expected Contra-to-Sales-ledger rejection")
        } catch (e: AccountingTransactionException) {
            assertTrue(e.appError is AppError.InvalidContraLedger)
        }
    }

    @Test
    fun e3_RoundOffEngine_RoundsToNearestRupee() {
        val result = com.example.accounting.domain.accounting.RoundOffEngine.roundInvoiceTotal(Money.fromPaise(1000_37L))
        assertEquals(1000_00L, result.roundedTotal.paise)
        assertEquals(-37L, result.roundOffAmount.paise)

        val roundUp = com.example.accounting.domain.accounting.RoundOffEngine.roundInvoiceTotal(Money.fromPaise(1000_63L))
        assertEquals(1001_00L, roundUp.roundedTotal.paise)
        assertEquals(37L, roundUp.roundOffAmount.paise)

        val exact = com.example.accounting.domain.accounting.RoundOffEngine.roundInvoiceTotal(Money.fromRupees(1000L))
        assertEquals(0L, exact.roundOffAmount.paise)
    }

    @Test
    fun e4_TradingWorkflow_RoundOff_KeepsVoucherBalanced() {
        // A rate producing a non-rupee-exact raw total (1000 x 12.345 = 123.45, taxable ends on
        // paise cleanly but the 18% split across two rupee-rounding-sensitive halves can still
        // leave a paise remainder after Round Off is applied to the party line only).
        val result = TradingWorkflowEngine.buildSale(
            "V1", companyId, fyId, "LED_DEBTOR", "Cust", "", "LED_SALES", "Sales", "27", "27",
            listOf(line("ITEM_A", 3, 333_00L, 18.0)), gstLedgerRefs(companyId), "LED_RO", "Round Off"
        )
        val totalDebit = result.journalItems.filter { it.type == DrCr.DEBIT }.fold(Money.ZERO) { acc, i -> acc + i.amount }
        val totalCredit = result.journalItems.filter { it.type == DrCr.CREDIT }.fold(Money.ZERO) { acc, i -> acc + i.amount }
        assertEquals("Round Off must keep every posted voucher balanced", totalDebit.paise, totalCredit.paise)
    }

    @Test
    fun e5_RoundOffLedger_ProtectedLikeSuspense_CannotBeDeletedOrRenamed() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        dao.seedTradingLedgers()
        val repo = AccountingRepository(dao)
        val roundOffId = "${StandardSystemGroups.ROUND_OFF_LEDGER_ID}_$companyId"

        val deleteResult = repo.deleteLedgerSafely(companyId, roundOffId)
        assertTrue(deleteResult is com.example.accounting.core.common.AccountingResult.Failure)

        val ledger = dao.getLedgerById(companyId, roundOffId)!!
        val renamed = com.example.accounting.domain.accounting.Ledger(
            ledgerId = ledger.ledgerId, companyId = companyId, groupId = ledger.groupId, name = "Renamed Round Off",
            openingBalance = Money.ZERO, openingBalanceType = DrCr.DEBIT, currentBalance = Money.ZERO, currentBalanceType = DrCr.DEBIT,
            isSystem = ledger.isSystem
        )
        val renameResult = repo.updateLedger(renamed)
        assertTrue(renameResult is com.example.accounting.core.common.AccountingResult.Failure)
    }

    @Test
    fun e6_RoundOff_And_Suspense_AreDistinctLedgers() {
        assertTrue(StandardSystemGroups.ROUND_OFF_LEDGER_ID != StandardSystemGroups.SUSPENSE_LEDGER_ID)
        assertTrue(StandardSystemGroups.ROUND_OFF_GROUP_ID != StandardSystemGroups.SUSPENSE_GROUP_ID)
    }

    @Test
    fun e7_EnsureGstLedgersExist_BackfillsCessLedger() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        val repo = AccountingRepository(dao)
        repo.ensureGstLedgersExist(companyId)
        val cessId = "${GstLedgerIds.CESS_LEDGER_ID}_$companyId"
        assertNotNull(dao.getLedgerById(companyId, cessId))
    }

    @Test
    fun e8_EnsureRoundOffLedgerExists_IsIdempotent() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        val repo = AccountingRepository(dao)
        repo.ensureRoundOffLedgerExists(companyId)
        repo.ensureRoundOffLedgerExists(companyId)
        val matches = dao.getLedgersByCompany(companyId).first().count { it.ledgerId == "${StandardSystemGroups.ROUND_OFF_LEDGER_ID}_$companyId" }
        assertEquals(1, matches)
    }

    // ==========================================
    // F. PERIOD GOVERNANCE (GST filing period isolation)
    // ==========================================
    @Test
    fun f1_GstFilingPeriod_LockDoesNotLockAccountingPeriod() = runBlocking {
        val dao = freshDao()
        dao.seedCompany()
        val repo = AccountingRepository(dao)
        repo.createGstFilingPeriod(companyId, "2026-04", "2026-04-01", "2026-04-30")
        val period = repo.getGstFilingPeriods(companyId).first().first()
        repo.setGstFilingPeriodLock(companyId, period.filingPeriodId, true)

        val lockedPeriod = repo.getGstFilingPeriods(companyId).first().first()
        assertTrue(lockedPeriod.isLocked)

        // The accounting period itself must remain untouched/open.
        val accountingPeriod = dao.getPeriodById("PER_${companyId}_1")
        assertEquals(PeriodStatus.OPEN, accountingPeriod!!.status)
    }

    @Test
    fun f2_GstFilingPeriod_CompanyIsolation() = runBlocking {
        val dao = freshDao()
        dao.seedCompany(company = "COMP_A", financialYearId = "FY_A")
        dao.seedCompany(company = "COMP_B", financialYearId = "FY_B")
        val repo = AccountingRepository(dao)
        repo.createGstFilingPeriod("COMP_A", "2026-04", "2026-04-01", "2026-04-30")
        val periodsB = repo.getGstFilingPeriods("COMP_B").first()
        assertTrue(periodsB.isEmpty())
    }
}
