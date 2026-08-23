package com.example.accounting

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.DrCr
import com.example.accounting.core.database.AppDatabase
import com.example.accounting.data.local.entity.LedgerEntity
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.StandardSystemGroups
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.domain.recurring.RecurringFrequency
import com.example.accounting.domain.recurring.RecurringVoucherDraftStatus
import com.example.accounting.domain.recurring.RecurringVoucherGenerationOutcome
import com.example.accounting.domain.recurring.RecurringVoucherLine
import com.example.accounting.domain.recurring.RecurringVoucherSchedule
import kotlinx.coroutines.flow.first
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
 * Phase 7F, "B" (Recurring Voucher Engine, draft-first) - the one behavior that cannot be
 * exercised from pure JVM tests: [AccountingRepository.postRecurringVoucherDraft], which calls
 * the existing, unmodified [AccountingRepository.postVoucher] and therefore needs a real Room
 * `AppDatabase` (`dbTransaction` is only non-null when a real `db` is supplied - see
 * [Phase7FTestSuite]'s class doc). This mirrors [SuspenseControlArchitectureTest]'s setup exactly
 * and is, like that pre-existing suite, currently blocked by this environment's Robolectric SDK
 * infrastructure (`DefaultSdkProvider` `UnsupportedOperationException` at `classMethod`, before
 * any `@Before`/`@Test` runs) - not by anything in the code under test. The assertions below are
 * the correct, real check for this behavior and should be re-verified the next time Robolectric
 * is functional in this environment.
 *
 * Covers the user's explicit requirement (superseding the earlier auto-post design): a generated
 * draft has zero journal/ledger/balance effect, and a voucher is only ever created when
 * [AccountingRepository.postRecurringVoucherDraft] is called in direct response to a user "Post"
 * action - at which point it goes through the exact same posting path (ledger balance updates
 * included) as every other voucher, and calling it twice on the same draft is rejected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase7FRecurringVoucherPostingTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: AccountingRepository
    private val companyId = "COMP_P7F_POST"
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

        repository.seedInitialDataForCompany(companyId, "Recurring Test Co", "27AAAAA0000A1Z5")
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

    private fun rentSchedule() = RecurringVoucherSchedule(
        scheduleId = "SCH_RENT", companyId = companyId, financialYearId = fyId, name = "Monthly Rent",
        voucherType = VoucherType.JOURNAL, frequency = RecurringFrequency.MONTHLY, dayOfMonth = 5,
        startDate = LocalDate.of(2026, 4, 1),
        lines = listOf(
            RecurringVoucherLine(rentLedgerId, DrCr.DEBIT, 25_000_00L, "Office rent"),
            RecurringVoucherLine(cashLedgerId, DrCr.CREDIT, 25_000_00L, "Office rent")
        )
    )

    @Test
    fun testGenerateRecurringVoucherIfDue_dueSchedule_createsDraftWithZeroAccountingEffect() = runBlocking {
        repository.createRecurringVoucherSchedule(rentSchedule())

        val result = repository.generateRecurringVoucherIfDue(companyId, "SCH_RENT", LocalDate.of(2026, 6, 5))
        val outcome = (result as AccountingResult.Success).data
        assertTrue(outcome is RecurringVoucherGenerationOutcome.DraftGenerated)

        // Zero journal/ledger/balance effect from generation alone.
        assertTrue(db.accountingDao().getVouchersByCompany(companyId).first().isEmpty())
        val rentLedger = db.accountingDao().getLedgerById(companyId, rentLedgerId)
        assertEquals(0L, rentLedger?.currentBalancePaise)
    }

    @Test
    fun testPostRecurringVoucherDraft_pendingDraft_postsThroughRealEnginePath() = runBlocking {
        repository.createRecurringVoucherSchedule(rentSchedule())
        val generateResult = repository.generateRecurringVoucherIfDue(companyId, "SCH_RENT", LocalDate.of(2026, 6, 5))
        val draftId = ((generateResult as AccountingResult.Success).data as RecurringVoucherGenerationOutcome.DraftGenerated).draftId

        val postResult = repository.postRecurringVoucherDraft(companyId, draftId)
        val voucher = (postResult as AccountingResult.Success).data

        val postedVoucher = db.accountingDao().getVoucherById(companyId, voucher.voucherId)
        assertNotNull("Posted voucher must actually exist via the real posting path", postedVoucher)
        assertTrue(postedVoucher!!.isPosted)

        // Same ledger-balance-update path every other voucher uses - not a parallel mechanism.
        val rentLedger = db.accountingDao().getLedgerById(companyId, rentLedgerId)
        assertEquals(25_000_00L, rentLedger?.currentBalancePaise)
        assertEquals(DrCr.DEBIT, rentLedger?.currentBalanceType)

        val draftAfter = db.accountingDao().getRecurringVoucherDraftById(companyId, draftId)
        assertEquals(RecurringVoucherDraftStatus.POSTED, draftAfter?.status)
        assertEquals(voucher.voucherId, draftAfter?.generatedVoucherId)
    }

    @Test
    fun testPostRecurringVoucherDraft_calledTwiceOnSameDraft_postsExactlyOnce() = runBlocking {
        repository.createRecurringVoucherSchedule(rentSchedule())
        val draftId = ((repository.generateRecurringVoucherIfDue(companyId, "SCH_RENT", LocalDate.of(2026, 6, 5)) as AccountingResult.Success).data
            as RecurringVoucherGenerationOutcome.DraftGenerated).draftId

        val first = repository.postRecurringVoucherDraft(companyId, draftId)
        assertTrue(first is AccountingResult.Success)

        // Same draft, posted again (e.g. a double-tap on "Post") - must be rejected, not double-posted.
        val second = repository.postRecurringVoucherDraft(companyId, draftId)
        assertTrue("Posting an already-posted draft must be rejected", second is AccountingResult.Failure)

        val allVouchers = db.accountingDao().getVouchersByCompany(companyId).first()
        assertEquals(1, allVouchers.count { it.voucherType == VoucherType.JOURNAL })
        val rentLedger = db.accountingDao().getLedgerById(companyId, rentLedgerId)
        assertEquals("Ledger must reflect exactly one rent posting, not two", 25_000_00L, rentLedger?.currentBalancePaise)
    }

    @Test
    fun testDiscardRecurringVoucherDraft_pendingDraft_neverPosts_andLedgerUntouched() = runBlocking {
        repository.createRecurringVoucherSchedule(rentSchedule())
        val draftId = ((repository.generateRecurringVoucherIfDue(companyId, "SCH_RENT", LocalDate.of(2026, 6, 5)) as AccountingResult.Success).data
            as RecurringVoucherGenerationOutcome.DraftGenerated).draftId

        val discardResult = repository.discardRecurringVoucherDraft(companyId, draftId)
        assertTrue(discardResult is AccountingResult.Success)

        assertTrue(db.accountingDao().getVouchersByCompany(companyId).first().isEmpty())
        val rentLedger = db.accountingDao().getLedgerById(companyId, rentLedgerId)
        assertEquals(0L, rentLedger?.currentBalancePaise)

        val postAfterDiscard = repository.postRecurringVoucherDraft(companyId, draftId)
        assertTrue("A discarded draft must never be postable", postAfterDiscard is AccountingResult.Failure)
    }
}
