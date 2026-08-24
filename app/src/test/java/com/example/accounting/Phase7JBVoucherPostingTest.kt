package com.example.accounting

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.application.voucher.VoucherManagementServiceImpl
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.DrCr
import com.example.accounting.core.database.AppDatabase
import com.example.accounting.data.local.entity.LedgerEntity
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.JournalItem
import com.example.accounting.domain.accounting.StandardSystemGroups
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.core.common.Money
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Phase 7J-B - the one [VoucherManagementServiceImpl.postDraft] behavior that cannot be exercised
 * from a pure JVM test: its real posting delegation to [AccountingRepository.postVoucher], which
 * needs a real Room `AppDatabase` (`dbTransaction` is only non-null with a real `db` - see
 * [Phase7JBVoucherManagementTestSuite]'s class doc). This mirrors
 * [Phase7FRecurringVoucherPostingTest]'s setup exactly and is, like that pre-existing suite,
 * currently blocked by this environment's Robolectric SDK infrastructure (`DefaultSdkProvider`
 * `UnsupportedOperationException` at `classMethod`, before any `@Before`/`@Test` runs) - not by
 * anything in the code under test. The assertions below are the correct, real check for this
 * behavior and should be re-verified the next time Robolectric is functional in this environment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase7JBVoucherPostingTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: AccountingRepository
    private lateinit var service: VoucherManagementServiceImpl
    private val companyId = "COMP_P7JB_POST"
    private val fyId = "FY_2026_27_$companyId"
    private val cashLedgerId = "LED_CASH_$companyId"
    private val rentLedgerId = "LED_RENT_$companyId"

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AccountingRepository(db.accountingDao(), db)
        service = VoucherManagementServiceImpl(db.accountingDao(), repository)

        repository.seedInitialDataForCompany(companyId, "Voucher Draft Test Co", "27AAAAA0000A1Z5")
        db.accountingDao().insertLedger(
            LedgerEntity(
                ledgerId = rentLedgerId, companyId = companyId, groupId = "${StandardSystemGroups.INDIRECT_EXPENSE_GROUP_ID}_$companyId",
                name = "Rent Expense", code = "5001", openingBalancePaise = 0L, openingBalanceType = DrCr.DEBIT,
                currentBalancePaise = 0L, currentBalanceType = DrCr.DEBIT, gstin = "", pan = "", stateCode = "27",
                email = "", phone = "", address = "", bankAccountNumber = "", bankIfsc = "", isSystem = false,
                isActive = true, hsnSacCode = "", defaultTaxRate = 0.0
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun draftVoucher(voucherId: String = "") = Voucher(
        voucherId = voucherId, companyId = companyId, financialYearId = fyId, voucherNumber = "JNL-0001",
        voucherType = VoucherType.JOURNAL, date = LocalDate.of(2026, 6, 5), narration = "Office rent",
        items = listOf(
            JournalItem("", "", companyId, fyId, rentLedgerId, "Rent Expense", DrCr.DEBIT, Money(25_000_00L), "Office rent", 1),
            JournalItem("", "", companyId, fyId, cashLedgerId, "Cash", DrCr.CREDIT, Money(25_000_00L), "Office rent", 2)
        )
    )

    @Test
    fun testCreateDraft_thenPostDraft_postsThroughRealEnginePath() = runBlocking {
        val created = (service.createDraft(draftVoucher()) as AccountingResult.Success).data

        val postResult = service.postDraft(created)
        val posted = (postResult as AccountingResult.Success).data

        val postedEntity = db.accountingDao().getVoucherById(companyId, posted.voucherId)
        assertNotNull("Posted voucher must actually exist via the real posting path", postedEntity)
        assertTrue(postedEntity!!.isPosted)

        val rentLedger = db.accountingDao().getLedgerById(companyId, rentLedgerId)
        assertEquals(25_000_00L, rentLedger?.currentBalancePaise)
        assertEquals(DrCr.DEBIT, rentLedger?.currentBalanceType)

        val draftAfter = db.accountingDao().getVoucherDraftById(companyId, created.voucherId)
        assertEquals(com.example.accounting.application.voucher.VoucherDraftStatus.POSTED, draftAfter?.status)
        assertEquals(posted.voucherId, draftAfter?.postedVoucherId)
    }

    @Test
    fun testEditDraft_thenPostDraft_reflectsEditedAmount() = runBlocking {
        val created = (service.createDraft(draftVoucher()) as AccountingResult.Success).data
        val edited = created.copy(
            items = listOf(
                JournalItem("", "", companyId, fyId, rentLedgerId, "Rent Expense", DrCr.DEBIT, Money(30_000_00L), "Office rent (revised)", 1),
                JournalItem("", "", companyId, fyId, cashLedgerId, "Cash", DrCr.CREDIT, Money(30_000_00L), "Office rent (revised)", 2)
            )
        )
        service.editDraft(created.voucherId, edited)

        val postResult = service.postDraft(edited)
        assertTrue(postResult is AccountingResult.Success)

        val rentLedger = db.accountingDao().getLedgerById(companyId, rentLedgerId)
        assertEquals(30_000_00L, rentLedger?.currentBalancePaise)
    }
}
