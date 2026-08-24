package com.example.accounting

import com.example.accounting.Phase7JBFixtures.seedCompanyAndFy
import com.example.accounting.application.automation.RecurringVoucherManagementService
import com.example.accounting.application.voucher.VoucherDraftStatus
import com.example.accounting.application.voucher.VoucherManagementServiceImpl
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.DrCr
import com.example.accounting.core.common.Money
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.JournalItem
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.domain.recurring.RecurringVoucherDraftStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/**
 * Phase 7J-B - [VoucherManagementServiceImpl] (real implementation of the frozen 7J-A
 * [com.example.accounting.application.voucher.VoucherManagementService] interface) and
 * [RecurringVoucherManagementService] (thin facade over the existing, unmodified Phase 7F
 * recurring-draft functions).
 *
 * `postDraft`'s *success* path requires a real Room `AppDatabase` (delegates to
 * [AccountingRepository.postVoucher], whose `dbTransaction` is only non-null with a real `db` -
 * see [Phase7FTestSuite]'s class doc for the identical, already-documented constraint) - that path
 * is covered separately in [Phase7JBVoucherPostingTest] (Robolectric), currently blocked by this
 * environment's Robolectric SDK infrastructure exactly like [Phase7FRecurringVoucherPostingTest].
 * `postDraft`'s *failure* propagation (no `db` supplied) is fully testable here with `db = null`.
 */
class Phase7JBVoucherManagementTestSuite {

    private val companyId = Phase7JBFixtures.COMPANY_ID
    private val fyId = Phase7JBFixtures.FY_ID

    private fun freshDao() = Phase7JBAwareDao(FakeAccountingDao())

    private fun journalItem(ledgerId: String, type: DrCr, amountPaise: Long, order: Int) = JournalItem(
        itemId = "", voucherId = "", companyId = companyId, financialYearId = fyId,
        ledgerId = ledgerId, ledgerName = ledgerId, type = type, amount = Money(amountPaise), lineOrder = order
    )

    private fun sampleVoucher(voucherId: String = "") = Voucher(
        voucherId = voucherId, companyId = companyId, financialYearId = fyId, voucherNumber = "JNL-0001",
        voucherType = VoucherType.JOURNAL, date = LocalDate.of(2026, 6, 15), narration = "Test draft",
        items = listOf(
            journalItem("LED_RENT_$companyId", DrCr.DEBIT, 5_000_00L, 1),
            journalItem("LED_CASH_$companyId", DrCr.CREDIT, 5_000_00L, 2)
        )
    )

    @Test
    fun testCreateDraft_validVoucher_persistsAsPendingReviewWithZeroLedgerEffect() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        val result = service.createDraft(sampleVoucher())
        val draft = (result as AccountingResult.Success).data
        assertTrue("A draft's own voucherId must be assigned (used as the draftId)", draft.voucherId.isNotBlank())
        assertTrue(dao.getVoucherDraftsByStatus(companyId, VoucherDraftStatus.PENDING_REVIEW).first().isNotEmpty())

        // Zero journal/ledger effect - a draft is never written to vouchers/journal_items.
        assertTrue(dao.getVouchersByCompany(companyId).first().isEmpty())
    }

    @Test
    fun testCreateDraft_emptyLines_rejected() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        val result = service.createDraft(sampleVoucher().copy(items = emptyList()))
        assertTrue(result is AccountingResult.Failure)
    }

    @Test
    fun testEditDraft_pendingDraft_replacesLines() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        val created = (service.createDraft(sampleVoucher()) as AccountingResult.Success).data
        val edited = created.copy(
            narration = "Edited narration",
            items = listOf(
                journalItem("LED_RENT_$companyId", DrCr.DEBIT, 9_000_00L, 1),
                journalItem("LED_CASH_$companyId", DrCr.CREDIT, 9_000_00L, 2)
            )
        )

        val editResult = service.editDraft(created.voucherId, edited)
        assertTrue(editResult is AccountingResult.Success)

        val lines = dao.getLinesForVoucherDraft(created.voucherId)
        assertEquals(2, lines.size)
        assertEquals(9_000_00L, lines.first().amountPaise)
    }

    @Test
    fun testEditDraft_unknownDraft_rejectedWithResourceNotFound() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        val result = service.editDraft("NONEXISTENT", sampleVoucher(voucherId = "NONEXISTENT"))
        assertTrue(result is AccountingResult.Failure)
    }

    @Test
    fun testDiscardDraft_pendingDraft_thenEditRejected() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        val created = (service.createDraft(sampleVoucher()) as AccountingResult.Success).data
        val discardResult = service.discardDraft(companyId, created.voucherId)
        assertTrue(discardResult is AccountingResult.Success)

        val discarded = dao.getVoucherDraftById(companyId, created.voucherId)
        assertEquals(VoucherDraftStatus.DISCARDED, discarded?.status)

        val editAfterDiscard = service.editDraft(created.voucherId, created)
        assertTrue("A discarded draft must never be editable", editAfterDiscard is AccountingResult.Failure)
    }

    @Test
    fun testPostDraft_noRealDatabase_failsGracefully_andDraftStaysPending() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null) // no real AppDatabase - dbTransaction is null
        val service = VoucherManagementServiceImpl(dao, repository)

        val created = (service.createDraft(sampleVoucher()) as AccountingResult.Success).data
        val postResult = service.postDraft(created, UUID.randomUUID().toString())
        assertTrue("postVoucher must fail without a real AppDatabase, never silently succeed", postResult is AccountingResult.Failure)

        // The never-a-second-posting-mechanism guarantee: a failed post never flips the draft to POSTED.
        val draftAfter = dao.getVoucherDraftById(companyId, created.voucherId)
        assertEquals(VoucherDraftStatus.PENDING_REVIEW, draftAfter?.status)
        assertNull(draftAfter?.postedVoucherId)
        assertTrue(dao.getVouchersByCompany(companyId).first().isEmpty())
    }

    @Test
    fun testAttachDocumentReference_persistsMetadataOnly() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        val result = service.attachDocumentReference(companyId, "VCH_1", "ASSET_1")
        assertTrue(result is AccountingResult.Success)
        val refs = dao.getDocumentReferencesForVoucher(companyId, "VCH_1")
        assertEquals(1, refs.size)
        assertEquals("ASSET_1", refs.first().documentAssetId)
    }

    // ==========================================
    // RecurringVoucherManagementService - thin facade over the existing, unmodified Phase 7F functions
    // ==========================================
    @Test
    fun testRecurringVoucherManagementService_getDrafts_delegatesToRepository() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = RecurringVoucherManagementService(repository)

        val drafts = service.getDrafts(companyId, RecurringVoucherDraftStatus.PENDING_REVIEW).first()
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun testRecurringVoucherManagementService_discardUnknownDraft_returnsFailure() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = RecurringVoucherManagementService(repository)

        val result = service.discardDraft(companyId, "NO_SUCH_DRAFT")
        assertTrue(result is AccountingResult.Failure)
    }
}
