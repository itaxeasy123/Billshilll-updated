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

    // ==========================================
    // Phase 7J-B.2 - Document Attachments (Slice 1: DAO/service infrastructure only, no UI)
    // ==========================================

    private fun voucherEntity(voucherId: String, forCompanyId: String = companyId) =
        com.example.accounting.data.local.entity.VoucherEntity(
            voucherId = voucherId, companyId = forCompanyId, financialYearId = fyId, voucherNumber = "JNL-0002",
            voucherType = VoucherType.JOURNAL, date = "2026-06-15", referenceNumber = "", narration = "Seed voucher",
            totalAmountPaise = 5_000_00L, isPosted = true, isCancelled = false,
            syncState = com.example.accounting.domain.accounting.SyncState.PENDING, createdAt = 0L, updatedAt = 0L,
            createdBy = "TEST", partyGstin = "", isGstApplicable = false
        )

    private fun documentAssetEntity(
        assetId: String,
        forCompanyId: String = companyId,
        type: com.example.accounting.domain.rendering.DocumentAssetType = com.example.accounting.domain.rendering.DocumentAssetType.VOUCHER_ATTACHMENT
    ) = com.example.accounting.data.local.entity.DocumentAssetEntity(
        assetId = assetId, companyId = forCompanyId, type = type,
        storageReference = "/data/user/0/com.example/files/voucher_attachments/$assetId.jpg",
        checksum = "checksum_$assetId", mimeType = "image/jpeg", sizeBytes = 1024L, createdAt = 0L
    )

    @Test
    fun testAttachDocumentReference_validVoucherAndAsset_persists() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        dao.insertVoucher(voucherEntity("VCH_1"))
        dao.insertDocumentAsset(documentAssetEntity("ASSET_1"))
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        val result = service.attachDocumentReference(companyId, "VCH_1", "ASSET_1")
        assertTrue(result is AccountingResult.Success)
        val refs = dao.getDocumentReferencesForVoucher(companyId, "VCH_1")
        assertEquals(1, refs.size)
        assertEquals("ASSET_1", refs.first().documentAssetId)
    }

    @Test
    fun testAttachDocumentReference_missingVoucher_rejected() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        dao.insertDocumentAsset(documentAssetEntity("ASSET_1"))
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        val result = service.attachDocumentReference(companyId, "VCH_NONEXISTENT", "ASSET_1")
        assertTrue("A voucher that does not exist must never be attachable", result is AccountingResult.Failure)
        assertTrue(dao.getDocumentReferencesForVoucher(companyId, "VCH_NONEXISTENT").isEmpty())
    }

    @Test
    fun testAttachDocumentReference_missingAsset_rejected() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        dao.insertVoucher(voucherEntity("VCH_1"))
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        val result = service.attachDocumentReference(companyId, "VCH_1", "ASSET_NONEXISTENT")
        assertTrue("A document asset that does not exist must never be attachable", result is AccountingResult.Failure)
        assertTrue(dao.getDocumentReferencesForVoucher(companyId, "VCH_1").isEmpty())
    }

    @Test
    fun testAttachDocumentReference_crossCompanyVoucherAndAsset_rejected() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val otherCompanyId = "COMP_OTHER"
        dao.seedCompanyAndFy(companyId = otherCompanyId, fyId = "FY_OTHER")
        // Voucher belongs to companyId, asset belongs to otherCompanyId.
        dao.insertVoucher(voucherEntity("VCH_1", forCompanyId = companyId))
        dao.insertDocumentAsset(documentAssetEntity("ASSET_1", forCompanyId = otherCompanyId))
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        val result = service.attachDocumentReference(companyId, "VCH_1", "ASSET_1")
        assertTrue("A cross-company voucher/asset pair must never be linkable", result is AccountingResult.Failure)
        assertTrue(dao.getDocumentReferencesForVoucher(companyId, "VCH_1").isEmpty())

        // Also verify the reverse is rejected: a Company-A caller must not link Company-B's own
        // voucher to Company-B's own asset by passing Company-A's companyId.
        dao.insertVoucher(voucherEntity("VCH_OTHER", forCompanyId = otherCompanyId))
        val reverseResult = service.attachDocumentReference(companyId, "VCH_OTHER", "ASSET_1")
        assertTrue(reverseResult is AccountingResult.Failure)
    }

    @Test
    fun testAttachDocumentReference_exactDuplicate_isIdempotentNotDuplicated() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        dao.insertVoucher(voucherEntity("VCH_1"))
        dao.insertDocumentAsset(documentAssetEntity("ASSET_1"))
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        val first = service.attachDocumentReference(companyId, "VCH_1", "ASSET_1")
        val second = service.attachDocumentReference(companyId, "VCH_1", "ASSET_1")
        assertTrue(first is AccountingResult.Success)
        assertTrue("Re-attaching the exact same (voucherId, documentAssetId) pair must succeed idempotently, never error", second is AccountingResult.Success)

        val refs = dao.getDocumentReferencesForVoucher(companyId, "VCH_1")
        assertEquals("Exactly one row must exist, never a second duplicate", 1, refs.size)
    }

    @Test
    fun testAttachDocumentReference_sameAssetDifferentVouchers_bothAllowed() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        dao.insertVoucher(voucherEntity("VCH_1"))
        dao.insertVoucher(voucherEntity("VCH_2"))
        dao.insertDocumentAsset(documentAssetEntity("ASSET_1"))
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        val toFirst = service.attachDocumentReference(companyId, "VCH_1", "ASSET_1")
        val toSecond = service.attachDocumentReference(companyId, "VCH_2", "ASSET_1")
        assertTrue(toFirst is AccountingResult.Success)
        assertTrue("The same asset must be attachable to a different voucher", toSecond is AccountingResult.Success)

        assertEquals(1, dao.getDocumentReferencesForVoucher(companyId, "VCH_1").size)
        assertEquals(1, dao.getDocumentReferencesForVoucher(companyId, "VCH_2").size)
    }

    // ==========================================
    // Phase 7J-B.2 Slice 2 - UI/ViewModel-facing behaviors, exercised at the service layer
    // (AccountingViewModel itself extends AndroidViewModel and cannot be unit-tested without a
    // working Robolectric Application context - currently broken in this environment, the same
    // root cause behind the 5 known-failing Robolectric suites. Every one of these functions is a
    // thin pass-through to VoucherManagementServiceImpl, so exercising the service directly is a
    // faithful test of the same logic the ViewModel wraps.)
    // ==========================================

    @Test
    fun testRemovingFromOneVoucher_leavesTheSameAssetAttachedToTheOtherVoucherUntouched() = runBlocking {
        // Steps 13.6/13.7: same asset attached to two vouchers; removing from one must never
        // affect the other.
        val dao = freshDao()
        dao.seedCompanyAndFy()
        dao.insertVoucher(voucherEntity("VCH_1"))
        dao.insertVoucher(voucherEntity("VCH_2"))
        dao.insertDocumentAsset(documentAssetEntity("ASSET_1"))
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        service.attachDocumentReference(companyId, "VCH_1", "ASSET_1")
        service.attachDocumentReference(companyId, "VCH_2", "ASSET_1")
        val referenceOnVoucher1 = dao.getDocumentReferencesForVoucher(companyId, "VCH_1").first().referenceId

        val removeResult = service.removeDocumentReference(companyId, referenceOnVoucher1)
        assertTrue(removeResult is AccountingResult.Success)

        assertTrue("Voucher 1's reference must be gone", dao.getDocumentReferencesForVoucher(companyId, "VCH_1").isEmpty())
        assertEquals("Voucher 2's reference to the SAME asset must be completely unaffected", 1, dao.getDocumentReferencesForVoucher(companyId, "VCH_2").size)
        assertEquals("The underlying asset itself must still exist", "ASSET_1", dao.getDocumentAssetById(companyId, "ASSET_1")?.assetId)
    }

    @Test
    fun testGetAttachmentsForVoucher_afterAttachThenReload_showsExactlyOneRowNeverADuplicate() = runBlocking {
        // Step 13.10: "no duplicate attachment appears after refresh" - simulates the ViewModel's
        // loadVoucherAttachments()/reload-after-attach pattern by calling the same joined query
        // twice, once before and once after a repeated attach attempt.
        val dao = freshDao()
        dao.seedCompanyAndFy()
        dao.insertVoucher(voucherEntity("VCH_1"))
        dao.insertDocumentAsset(documentAssetEntity("ASSET_1"))
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        service.attachDocumentReference(companyId, "VCH_1", "ASSET_1")
        val afterFirstLoad = service.getAttachmentsForVoucher(companyId, "VCH_1")
        assertEquals(1, afterFirstLoad.size)

        // Simulates a UI double-tap / retried attach on the same file - the "reload" must still
        // show exactly one row, never two.
        service.attachDocumentReference(companyId, "VCH_1", "ASSET_1")
        val afterSecondLoad = service.getAttachmentsForVoucher(companyId, "VCH_1")
        assertEquals("Reloading after a repeated attach must never surface a duplicate row", 1, afterSecondLoad.size)
    }

    @Test
    fun testRemoveDocumentReference_removesOnlyTheReference() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        dao.insertVoucher(voucherEntity("VCH_1"))
        dao.insertDocumentAsset(documentAssetEntity("ASSET_1"))
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)
        service.attachDocumentReference(companyId, "VCH_1", "ASSET_1")
        val referenceId = dao.getDocumentReferencesForVoucher(companyId, "VCH_1").first().referenceId

        val removeResult = service.removeDocumentReference(companyId, referenceId)
        assertTrue(removeResult is AccountingResult.Success)

        // 7: only the reference is gone.
        assertTrue(dao.getDocumentReferencesForVoucher(companyId, "VCH_1").isEmpty())
        // 8: the DocumentAsset row survives untouched.
        assertEquals("ASSET_1", dao.getDocumentAssetById(companyId, "ASSET_1")?.assetId)
        // 9: the voucher itself is untouched.
        val voucher = dao.getVoucherById(companyId, "VCH_1")
        assertTrue(voucher != null && !voucher.isCancelled && voucher.isPosted)
        // 10: no JournalItems exist to affect - this attachment flow never touches journal_items at all.
        assertTrue(dao.getJournalItemsForVoucherSync("VCH_1").isEmpty())
    }

    @Test
    fun testRemoveDocumentReference_unknownReference_returnsFailure() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        val result = service.removeDocumentReference(companyId, "NO_SUCH_REFERENCE")
        assertTrue(result is AccountingResult.Failure)
    }

    @Test
    fun testDocumentAssetType_voucherAttachment_persistsAndRoundTrips() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)

        val created = repository.createDocumentAsset(
            companyId, com.example.accounting.domain.rendering.DocumentAssetType.VOUCHER_ATTACHMENT,
            "/data/user/0/com.example/files/voucher_attachments/receipt.jpg", "abc123", "image/jpeg", 2048L
        )
        assertTrue(created is AccountingResult.Success)
        val assetId = (created as AccountingResult.Success).data.assetId

        val fetched = repository.getDocumentAsset(companyId, assetId)
        assertEquals(com.example.accounting.domain.rendering.DocumentAssetType.VOUCHER_ATTACHMENT, fetched?.type)
    }

    @Test
    fun testAttachDocumentReference_existingAssetTypes_stillWork() = runBlocking {
        // Backward-compatibility check: hardening attachDocumentReference must not disturb the
        // pre-existing LOGO/SIGNATURE/etc. asset types - only VOUCHER_ATTACHMENT is new.
        val dao = freshDao()
        dao.seedCompanyAndFy()
        dao.insertVoucher(voucherEntity("VCH_1"))
        dao.insertDocumentAsset(documentAssetEntity("ASSET_LOGO", type = com.example.accounting.domain.rendering.DocumentAssetType.LOGO))
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        val result = service.attachDocumentReference(companyId, "VCH_1", "ASSET_LOGO")
        assertTrue(result is AccountingResult.Success)
    }

    @Test
    fun testGetAttachmentsForVoucher_joinsAssetMetadataInOneCall() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        dao.insertVoucher(voucherEntity("VCH_1"))
        dao.insertDocumentAsset(documentAssetEntity("ASSET_1"))
        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)
        service.attachDocumentReference(companyId, "VCH_1", "ASSET_1")

        val attachments = service.getAttachmentsForVoucher(companyId, "VCH_1")
        assertEquals(1, attachments.size)
        assertEquals("ASSET_1", attachments.first().documentAssetId)
        assertEquals("image/jpeg", attachments.first().mimeType)
        assertEquals(1024L, attachments.first().sizeBytes)
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
