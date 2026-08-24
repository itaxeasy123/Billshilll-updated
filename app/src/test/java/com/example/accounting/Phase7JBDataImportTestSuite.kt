package com.example.accounting

import com.example.accounting.Phase7JBFixtures.seedCompanyAndFy
import com.example.accounting.application.imports.DataImportManagementService
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.data.dataimport.CsvJsonDataImportAdapter
import com.example.accounting.data.local.entity.DocumentAssetEntity
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.dataimport.ImportFileFormat
import com.example.accounting.domain.dataimport.ImportSuggestionType
import com.example.accounting.domain.rendering.BusinessProfile
import com.example.accounting.domain.rendering.ConstitutionType
import com.example.accounting.domain.rendering.DocumentAssetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 7J-B - real CSV/JSON import ([CsvJsonDataImportAdapter]) + the Draft/Suggestion/Review
 * orchestration ([DataImportManagementService]). Proves: real parsing (not a stub), the adapter
 * itself never reaches [AccountingRepository] (reflection scan over its own declared members),
 * and [DataImportManagementService.reviewAndCreate] is the only path from a suggestion to a real
 * [com.example.accounting.domain.party.Party]/[com.example.accounting.domain.accounting.Ledger]/
 * [com.example.accounting.domain.inventory.StockItem].
 */
class Phase7JBDataImportTestSuite {

    private val companyId = Phase7JBFixtures.COMPANY_ID

    private fun freshDao() = Phase7JBAwareDao(Phase7BTestSuite.Phase7BAwareDao(FakeAccountingDao()))

    private fun profile(companyId: String) = BusinessProfile(
        businessProfileId = "BP_1", companyId = companyId, businessName = "Test Co", legalName = "Test Co",
        address = "", phone = "", email = "", website = "", gstin = "", pan = "", constitutionType = ConstitutionType.PROPRIETORSHIP
    )

    private fun tempCsvAsset(dao: Phase7JBAwareDao, content: String): String = runBlocking {
        val file = File.createTempFile("import_test", ".csv")
        file.writeText(content)
        dao.insertDocumentAsset(
            DocumentAssetEntity(
                assetId = "ASSET_CSV", companyId = companyId, type = DocumentAssetType.LOGO,
                storageReference = file.absolutePath, checksum = "", mimeType = "text/csv", sizeBytes = file.length(), createdAt = 0L
            )
        )
        "ASSET_CSV"
    }

    @Test
    fun testParseFile_realCsv_parsesRowsIntoSuggestions() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val assetId = tempCsvAsset(dao, "name,role\nAcme Traders,CUSTOMER\nBeta Supplies,SUPPLIER\n")

        val adapter = CsvJsonDataImportAdapter(dao)
        val result = adapter.parseFile(profile(companyId), ImportFileFormat.CSV, assetId)
        val importResult = (result as AccountingResult.Success).data

        assertEquals(2, importResult.totalRowsParsed)
        assertEquals(2, importResult.suggestions.size)
        assertEquals(ImportSuggestionType.PARTY, importResult.suggestions.first().suggestionType)
        assertEquals("Acme Traders", importResult.suggestions.first().fieldValues["name"])
    }

    @Test
    fun testParseFile_malformedRow_reportedAsUnparsed() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val assetId = tempCsvAsset(dao, "name,role\nAcme Traders,CUSTOMER\nMalformedRowTooFewColumns\n")

        val adapter = CsvJsonDataImportAdapter(dao)
        val result = adapter.parseFile(profile(companyId), ImportFileFormat.CSV, assetId)
        val importResult = (result as AccountingResult.Success).data

        assertEquals(1, importResult.suggestions.size)
        assertEquals(listOf(3), importResult.unparsedRowNumbers)
    }

    @Test
    fun testParseFile_excelFormat_notImplemented() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val assetId = tempCsvAsset(dao, "name\nAcme\n")

        val adapter = CsvJsonDataImportAdapter(dao)
        val result = adapter.parseFile(profile(companyId), ImportFileFormat.EXCEL, assetId)
        assertTrue(result is AccountingResult.Failure)
    }

    @Test
    fun testParseFile_unknownAsset_returnsResourceNotFound() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val adapter = CsvJsonDataImportAdapter(dao)
        val result = adapter.parseFile(profile(companyId), ImportFileFormat.CSV, "NO_SUCH_ASSET")
        assertTrue(result is AccountingResult.Failure)
    }

    @Test
    fun testAdapter_neverReachesAccountingRepository() {
        // Structural guarantee, mirroring every 7H/7I/7J contract's reflection-based check: no
        // declared field on the concrete adapter class is (or contains) an AccountingRepository.
        val fields = CsvJsonDataImportAdapter::class.java.declaredFields
        assertTrue(fields.none { it.type.name.contains("AccountingRepository") })
    }

    @Test
    fun testReviewAndCreate_partySuggestion_createsRealParty() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val assetId = tempCsvAsset(dao, "name,role\nAcme Traders,CUSTOMER\n")
        val adapter = CsvJsonDataImportAdapter(dao)
        val repository = AccountingRepository(dao, db = null)
        val service = DataImportManagementService(adapter, repository)

        val parseResult = (service.parseFile(profile(companyId), ImportFileFormat.CSV, assetId) as AccountingResult.Success).data
        val suggestion = parseResult.suggestions.first()

        val createResult = service.reviewAndCreate(companyId, suggestion, ImportSuggestionType.PARTY)
        assertTrue(createResult is AccountingResult.Success)

        val parties = repository.getParties(companyId).first()
        assertEquals(1, parties.size)
        assertEquals("Acme Traders", parties.first().displayName)
    }

    @Test
    fun testReviewAndCreate_missingRequiredColumn_rejectedWithValidationError() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val adapter = CsvJsonDataImportAdapter(dao)
        val service = DataImportManagementService(adapter, repository)

        val suggestion = com.example.accounting.domain.dataimport.ImportRowSuggestion(
            rowNumber = 1, suggestionType = ImportSuggestionType.LEDGER, fieldValues = mapOf("unrelated" to "value"), confidenceScore = 1.0
        )
        val result = service.reviewAndCreate(companyId, suggestion, ImportSuggestionType.LEDGER)
        assertTrue(result is AccountingResult.Failure)
    }
}
