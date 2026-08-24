package com.example.accounting

import com.example.accounting.Phase7JBFixtures.seedCompanyAndFy
import com.example.accounting.application.document.DocumentPreviewService
import com.example.accounting.application.export.ExportManagementService
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.document.DocumentType
import com.example.accounting.domain.export.ExportFormat
import com.example.accounting.domain.export.ExportResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7J-B - [DocumentPreviewService] and [ExportManagementService] (both Android-only per this
 * phase's explicit scope decision - `server/app/api/routes/exports.py` is left byte-identical, not
 * a runtime-testable claim, recorded in `docs/30_CHANGELOG.md` instead). Every method is a direct,
 * pure delegation to the existing, unmodified `AccountingRepository` function - these tests prove
 * delegation-equality, the same technique [Phase7JBReportManagementTestSuite] already used.
 *
 * PDF rendering/print/share themselves need an Android `Context` and are therefore not exercised by
 * a pure-JVM suite - the same, already-documented environment limitation
 * [com.example.accounting.data.rendering.PdfDocumentRenderer]'s own class doc records for Phase 7D.
 */
class Phase7JBDocumentAndExportFacadeTestSuite {

    private val companyId = Phase7JBFixtures.COMPANY_ID
    private val fyId = Phase7JBFixtures.FY_ID

    private fun freshDao() = Phase7JBAwareDao(FakeAccountingDao())

    /** Both calls stamp `metadata.generatedAt`/the embedded JSON `generatedAt` field independently
     * with `System.currentTimeMillis()` a few milliseconds apart - real, correct behavior for two
     * separate export calls, not something to weaken. Delegation-equality is compared on content
     * shape only, ignoring that inherently-different timestamp. */
    private fun AccountingResult<ExportResult>.withoutTimestamp(): AccountingResult<ExportResult> = when (this) {
        is AccountingResult.Success -> AccountingResult.Success(
            data.copy(
                metadata = data.metadata.copy(generatedAt = 0L),
                content = data.content.replace(Regex("\"generatedAt\":\\d+"), "\"generatedAt\":0")
            )
        )
        is AccountingResult.Failure -> this
    }

    @Test
    fun testPreview_unknownDocument_returnsFailure_delegatesToAssembleDocumentData() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = DocumentPreviewService(repository)

        val direct = repository.assembleDocumentData(companyId, DocumentType.SALES_INVOICE, "NO_SUCH_DOC")
        val viaFacade = service.preview(companyId, DocumentType.SALES_INVOICE, "NO_SUCH_DOC")
        assertTrue(direct is AccountingResult.Failure)
        assertTrue(viaFacade is AccountingResult.Failure)
    }

    @Test
    fun testPreviewAsJson_unknownDocument_returnsFailure_delegatesToRenderDocumentAsJson() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = DocumentPreviewService(repository)

        val result = service.previewAsJson(companyId, DocumentType.SALES_INVOICE, "NO_SUCH_DOC")
        assertTrue(result is AccountingResult.Failure)
    }

    @Test
    fun testExportTrialBalance_delegatesExactly() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = ExportManagementService(repository)

        val direct = repository.exportTrialBalanceAs(companyId, fyId, ExportFormat.JSON)
        val viaFacade = service.exportTrialBalance(companyId, fyId, ExportFormat.JSON)
        assertEquals(direct.withoutTimestamp(), viaFacade.withoutTimestamp())
    }

    @Test
    fun testExportOutstanding_delegatesExactly() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = ExportManagementService(repository)

        val direct = repository.exportOutstandingAs(companyId, ExportFormat.CSV)
        val viaFacade = service.exportOutstanding(companyId, ExportFormat.CSV)
        assertEquals(direct.withoutTimestamp(), viaFacade.withoutTimestamp())
    }

    @Test
    fun testExportGstSummary_delegatesExactly() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = ExportManagementService(repository)

        val direct = repository.exportGstSummaryAs(companyId, fyId, ExportFormat.JSON)
        val viaFacade = service.exportGstSummary(companyId, fyId, ExportFormat.JSON)
        assertEquals(direct.withoutTimestamp(), viaFacade.withoutTimestamp())
    }
}
