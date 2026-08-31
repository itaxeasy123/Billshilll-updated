package com.example.accounting

import com.example.accounting.core.common.DrCr
import com.example.accounting.core.common.Money
import com.example.accounting.data.local.entity.JournalItemEntity
import com.example.accounting.data.local.entity.VoucherEntity
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.JournalItem
import com.example.accounting.domain.accounting.StandardSystemGroups
import com.example.accounting.domain.accounting.SyncState
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.accounting.VoucherType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/**
 * D2 (Voucher Detail + Attachment Reachability) - Chart of Accounts and Money tab Cash/Bank both
 * drill into the SAME [com.example.accounting.presentation.features.ledgers.LedgerStatementDetailView]
 * (Money's `onLedgerClick` routes to `AppRoute.ChartOfAccounts`, confirmed by reading
 * `MainAppScreen.kt` before this change), so the one real thing D2 adds - a row-tap that resolves
 * [com.example.accounting.domain.reports.LedgerStatementRow.voucherId] against `uiState.vouchers`
 * to open the existing `VoucherDetailDialog` - is covered here at the data layer. Matches this
 * suite's own documented pattern (see `Phase7JUITestSuite`): the Compose screens are not exercised
 * here, only the real, pure-JVM logic behind them - `AccountingRepository.generateLedgerStatement`
 * and the exact `vouchers.find { it.voucherId == id }` lookup used at the new call site.
 */
class D2VoucherDetailReachabilityTestSuite {

    private val companyId = "COMP_D2"
    private val fyId = "FY_D2_2026_27"

    private fun freshDao() = FakeAccountingDao()

    private fun voucherEntity(
        voucherId: String,
        number: String,
        type: VoucherType,
        date: String = "2026-04-15"
    ) = VoucherEntity(
        voucherId = voucherId, companyId = companyId, financialYearId = fyId, voucherNumber = number,
        voucherType = type, date = date, referenceNumber = "", narration = "",
        totalAmountPaise = 500_00L, isPosted = true, isCancelled = false, syncState = SyncState.SYNCED,
        createdAt = 0L, updatedAt = 0L, createdBy = "TESTER", partyGstin = "", isGstApplicable = false
    )

    private fun line(voucherId: String, ledgerId: String, type: DrCr, order: Int, amountPaise: Long = 500_00L) =
        JournalItemEntity(
            itemId = UUID.randomUUID().toString(), voucherId = voucherId, companyId = companyId,
            financialYearId = fyId, ledgerId = ledgerId, type = type, amountPaise = amountPaise,
            narration = "", lineOrder = order
        )

    /** Mirrors `MainAppScreen`'s new wiring: `uiState.vouchers.find { it.voucherId == voucherId }`. */
    private fun resolveVoucher(vouchers: List<Voucher>, voucherId: String): Voucher? =
        vouchers.find { it.voucherId == voucherId }

    private fun domainVoucherOf(entity: VoucherEntity) = Voucher(
        voucherId = entity.voucherId, companyId = entity.companyId, financialYearId = entity.financialYearId,
        voucherNumber = entity.voucherNumber, voucherType = entity.voucherType, date = LocalDate.parse(entity.date),
        totalAmount = Money.fromPaise(entity.totalAmountPaise), items = emptyList(), createdBy = entity.createdBy
    )

    @Test
    fun ledgerStatementRow_voucherId_matchesRealPostedVoucher_notSynthesized() = runBlocking {
        val dao = freshDao()
        val v = voucherEntity("VCH_D2_1", "PMT-D2-1", VoucherType.PAYMENT)
        dao.insertVoucher(v)
        dao.insertJournalItems(listOf(line(v.voucherId, "LED_CASH", DrCr.DEBIT, 1), line(v.voucherId, "LED_SUPPLIER", DrCr.CREDIT, 2)))
        val repo = AccountingRepository(dao)

        val statement = repo.generateLedgerStatement(companyId, "LED_CASH")
        assertEquals(1, statement.rows.size)
        assertEquals("A ledger-statement row's voucherId must be the real posted voucher's id, never reconstructed", v.voucherId, statement.rows.first().voucherId)
    }

    @Test
    fun voucherLookupByRowId_resolvesCorrectVoucher_fromVoucherList() = runBlocking {
        val dao = freshDao()
        val v1 = voucherEntity("VCH_D2_2A", "PMT-D2-2A", VoucherType.PAYMENT)
        val v2 = voucherEntity("VCH_D2_2B", "PMT-D2-2B", VoucherType.RECEIPT)
        dao.insertVoucher(v1)
        dao.insertVoucher(v2)
        dao.insertJournalItems(listOf(line(v1.voucherId, "LED_CASH", DrCr.DEBIT, 1), line(v1.voucherId, "LED_SUPPLIER", DrCr.CREDIT, 2)))
        dao.insertJournalItems(listOf(line(v2.voucherId, "LED_CASH", DrCr.DEBIT, 1), line(v2.voucherId, "LED_CUSTOMER", DrCr.CREDIT, 2)))
        val repo = AccountingRepository(dao)

        val statement = repo.generateLedgerStatement(companyId, "LED_CASH")
        val vouchers = listOf(domainVoucherOf(v1), domainVoucherOf(v2))
        val targetRow = statement.rows.first { it.voucherId == v2.voucherId }

        val resolved = resolveVoucher(vouchers, targetRow.voucherId)
        assertEquals("PMT-D2-2B", resolved?.voucherNumber)
        assertEquals(VoucherType.RECEIPT, resolved?.voucherType)
    }

    @Test
    fun voucherLookupByRowId_returnsNull_whenVoucherIdNotInList_neverCrashes() {
        val vouchers = listOf(domainVoucherOf(voucherEntity("VCH_D2_3A", "PMT-D2-3A", VoucherType.PAYMENT)))
        val resolved = resolveVoucher(vouchers, "VCH_NOT_LOADED_YET")
        assertNull("A row referencing a voucher not yet present in uiState.vouchers must resolve to null, never throw", resolved)
    }

    @Test
    fun moneyTabCashPath_ledgerStatementRow_resolvesToRealContraVoucher() = runBlocking {
        val dao = freshDao()
        dao.insertLedger(com.example.accounting.data.local.entity.LedgerEntity(
            ledgerId = "LED_CASH_MONEY", companyId = companyId, groupId = StandardSystemGroups.CASH_GROUP_ID, name = "Cash in Hand",
            code = "CASH", openingBalancePaise = 0L, openingBalanceType = DrCr.DEBIT, currentBalancePaise = 0L,
            currentBalanceType = DrCr.DEBIT, gstin = "", pan = "", stateCode = "27", email = "", phone = "", address = "",
            bankAccountNumber = "", bankIfsc = "", isSystem = true, isActive = true, hsnSacCode = "", defaultTaxRate = 0.0
        ))
        val contra = voucherEntity("VCH_D2_CTR", "CTR-D2-1", VoucherType.CONTRA)
        dao.insertVoucher(contra)
        dao.insertJournalItems(listOf(line(contra.voucherId, "LED_CASH_MONEY", DrCr.DEBIT, 1), line(contra.voucherId, "LED_BANK_MONEY", DrCr.CREDIT, 2)))
        val repo = AccountingRepository(dao)

        val statement = repo.generateLedgerStatement(companyId, "LED_CASH_MONEY")
        val resolved = resolveVoucher(listOf(domainVoucherOf(contra)), statement.rows.single().voucherId)
        assertEquals("Money tab > Cash must reach the real Contra voucher behind the cash-ledger row", VoucherType.CONTRA, resolved?.voucherType)
    }

    @Test
    fun moneyTabBankPath_ledgerStatementRow_resolvesToRealContraVoucher() = runBlocking {
        val dao = freshDao()
        dao.insertLedger(com.example.accounting.data.local.entity.LedgerEntity(
            ledgerId = "LED_BANK_MONEY", companyId = companyId, groupId = StandardSystemGroups.BANK_GROUP_ID, name = "HDFC Bank",
            code = "BANK", openingBalancePaise = 0L, openingBalanceType = DrCr.DEBIT, currentBalancePaise = 0L,
            currentBalanceType = DrCr.DEBIT, gstin = "", pan = "", stateCode = "27", email = "", phone = "", address = "",
            bankAccountNumber = "", bankIfsc = "", isSystem = true, isActive = true, hsnSacCode = "", defaultTaxRate = 0.0
        ))
        val contra = voucherEntity("VCH_D2_CTR2", "CTR-D2-2", VoucherType.CONTRA)
        dao.insertVoucher(contra)
        dao.insertJournalItems(listOf(line(contra.voucherId, "LED_CASH_MONEY", DrCr.DEBIT, 1), line(contra.voucherId, "LED_BANK_MONEY", DrCr.CREDIT, 2)))
        val repo = AccountingRepository(dao)

        val statement = repo.generateLedgerStatement(companyId, "LED_BANK_MONEY")
        val resolved = resolveVoucher(listOf(domainVoucherOf(contra)), statement.rows.single().voucherId)
        assertEquals("Money tab > Bank must reach the same real Contra voucher behind the bank-ledger row", VoucherType.CONTRA, resolved?.voucherType)
    }

    @Test
    fun multipleJournalLinesForSameVoucher_allRowsCarryIdenticalVoucherId() = runBlocking {
        val dao = freshDao()
        val v = voucherEntity("VCH_D2_SPLIT", "JRN-D2-1", VoucherType.JOURNAL)
        dao.insertVoucher(v)
        // A split entry touching LED_CASH twice within the same voucher.
        dao.insertJournalItems(listOf(
            line(v.voucherId, "LED_CASH", DrCr.DEBIT, 1, 300_00L),
            line(v.voucherId, "LED_CASH", DrCr.DEBIT, 2, 200_00L),
            line(v.voucherId, "LED_OTHER", DrCr.CREDIT, 3, 500_00L)
        ))
        val repo = AccountingRepository(dao)

        val statement = repo.generateLedgerStatement(companyId, "LED_CASH")
        assertEquals(2, statement.rows.size)
        assertTrue("Every row from the same split voucher must carry the identical voucherId so either tap opens the same correct voucher", statement.rows.all { it.voucherId == v.voucherId })
    }

    @Test
    fun emptyLedgerStatement_producesNoRows_noVoucherIdToResolve() = runBlocking {
        val dao = freshDao()
        dao.insertLedger(com.example.accounting.data.local.entity.LedgerEntity(
            ledgerId = "LED_EMPTY", companyId = companyId, groupId = "GRP_TEST", name = "Unused Ledger",
            code = "EMPTY", openingBalancePaise = 0L, openingBalanceType = DrCr.DEBIT, currentBalancePaise = 0L,
            currentBalanceType = DrCr.DEBIT, gstin = "", pan = "", stateCode = "27", email = "", phone = "", address = "",
            bankAccountNumber = "", bankIfsc = "", isSystem = false, isActive = true, hsnSacCode = "", defaultTaxRate = 0.0
        ))
        val repo = AccountingRepository(dao)

        val statement = repo.generateLedgerStatement(companyId, "LED_EMPTY")
        assertTrue("A ledger with no postings must yield zero rows, never a row with a missing/null voucherId", statement.rows.isEmpty())
    }
}
