package com.example.accounting

import com.example.accounting.Phase7JBFixtures.seedCompanyAndFy
import com.example.accounting.application.imports.DataImportManagementService
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.DrCr
import com.example.accounting.core.common.Money
import com.example.accounting.data.dataimport.CsvJsonDataImportAdapter
import com.example.accounting.data.local.entity.GroupEntity
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.PrimaryGroup
import com.example.accounting.domain.dataimport.ImportRowSuggestion
import com.example.accounting.domain.dataimport.ImportSuggestionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused tests for Ledger Opening Balance Import - extends
 * [DataImportManagementService.reviewAndCreate]'s `LEDGER` branch to read an opening
 * balance/opening balance type from the import row, fail-closed on a bad value (never
 * [Money.parse]'s silent-zero, never raw `DrCr.valueOf`'s throw), and enforce the documented
 * partial-column rule. Existing group-id validation ([LedgerImportGroupValidationTestSuite]) and
 * the base CSV/JSON import path ([Phase7JBDataImportTestSuite]) are unaffected and re-verified by
 * the full regression run, not duplicated here.
 */
class LedgerOpeningBalanceImportTestSuite {

    private val companyId = Phase7JBFixtures.COMPANY_ID
    private val groupId = "GRP_OB_TEST"

    private fun freshDao() = Phase7JBAwareDao(Phase7BTestSuite.Phase7BAwareDao(FakeAccountingDao()))

    private suspend fun preparedService(): Pair<DataImportManagementService, AccountingRepository> {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        dao.insertGroup(GroupEntity(groupId, companyId, "Opening Balance Test Group", PrimaryGroup.ASSETS, null, false, false, 1))
        val repository = AccountingRepository(dao, db = null)
        val service = DataImportManagementService(CsvJsonDataImportAdapter(dao), repository)
        return service to repository
    }

    private fun ledgerSuggestion(extraFields: Map<String, String>, name: String = "Imported Ledger") = ImportRowSuggestion(
        rowNumber = 1, suggestionType = ImportSuggestionType.LEDGER,
        fieldValues = mapOf("name" to name, "groupid" to groupId) + extraFields, confidenceScore = 1.0
    )

    @Test
    fun testReviewAndCreate_validDebitOpeningBalance_importsSuccessfully() = runBlocking {
        val (service, repository) = preparedService()
        // "opening_balance" - one of the underscore/space aliases, proving normalization works too.
        val suggestion = ledgerSuggestion(mapOf("opening_balance" to "5000", "type" to "Debit"))

        val result = service.reviewAndCreate(companyId, suggestion, ImportSuggestionType.LEDGER)

        assertTrue(result is AccountingResult.Success)
        val ledger = repository.getLedgers(companyId).first().first()
        assertEquals(Money.fromRupees(5000L), ledger.openingBalance)
        assertEquals(DrCr.DEBIT, ledger.openingBalanceType)
    }

    @Test
    fun testReviewAndCreate_validCreditOpeningBalance_importsSuccessfully() = runBlocking {
        val (service, repository) = preparedService()
        val suggestion = ledgerSuggestion(mapOf("openingbalance" to "2500", "drcr" to "cr"))

        val result = service.reviewAndCreate(companyId, suggestion, ImportSuggestionType.LEDGER)

        assertTrue(result is AccountingResult.Success)
        val ledger = repository.getLedgers(companyId).first().first()
        assertEquals(Money.fromRupees(2500L), ledger.openingBalance)
        assertEquals(DrCr.CREDIT, ledger.openingBalanceType)
    }

    @Test
    fun testReviewAndCreate_decimalOpeningBalance_importsSuccessfully() = runBlocking {
        val (service, repository) = preparedService()
        val suggestion = ledgerSuggestion(mapOf("balance" to "1234.56", "type" to "DEBIT"))

        val result = service.reviewAndCreate(companyId, suggestion, ImportSuggestionType.LEDGER)

        assertTrue(result is AccountingResult.Success)
        val ledger = repository.getLedgers(companyId).first().first()
        assertEquals(123456L, ledger.openingBalance.paise)
    }

    @Test
    fun testReviewAndCreate_openingBalancePresent_typeMissing_rejectedAndNoLedgerCreated() = runBlocking {
        val (service, repository) = preparedService()
        val suggestion = ledgerSuggestion(mapOf("openingbalance" to "1000"))

        val result = service.reviewAndCreate(companyId, suggestion, ImportSuggestionType.LEDGER)

        assertTrue(result is AccountingResult.Failure)
        assertTrue(repository.getLedgers(companyId).first().isEmpty())
    }

    @Test
    fun testReviewAndCreate_typePresent_openingBalanceMissing_rejectedAndNoLedgerCreated() = runBlocking {
        val (service, repository) = preparedService()
        val suggestion = ledgerSuggestion(mapOf("type" to "CREDIT"))

        val result = service.reviewAndCreate(companyId, suggestion, ImportSuggestionType.LEDGER)

        assertTrue(result is AccountingResult.Failure)
        assertTrue(repository.getLedgers(companyId).first().isEmpty())
    }

    @Test
    fun testReviewAndCreate_invalidOpeningBalanceAmount_rejectedAndNoLedgerCreated() = runBlocking {
        val (service, repository) = preparedService()
        val suggestion = ledgerSuggestion(mapOf("openingbalance" to "not-a-number", "type" to "DEBIT"))

        val result = service.reviewAndCreate(companyId, suggestion, ImportSuggestionType.LEDGER)

        assertTrue(result is AccountingResult.Failure)
        val message = (result as AccountingResult.Failure).error.message
        assertTrue(message.contains("not a valid opening balance amount"))
        assertTrue(repository.getLedgers(companyId).first().isEmpty())
    }

    @Test
    fun testReviewAndCreate_invalidOpeningBalanceType_rejectedAndNoLedgerCreated() = runBlocking {
        val (service, repository) = preparedService()
        val suggestion = ledgerSuggestion(mapOf("openingbalance" to "1000", "type" to "SIDEWAYS"))

        val result = service.reviewAndCreate(companyId, suggestion, ImportSuggestionType.LEDGER)

        assertTrue(result is AccountingResult.Failure)
        val message = (result as AccountingResult.Failure).error.message
        assertTrue(message.contains("not a valid opening balance type"))
        assertTrue(repository.getLedgers(companyId).first().isEmpty())
    }

    @Test
    fun testReviewAndCreate_bothOpeningBalanceFieldsAbsent_existingZeroBalanceDefaultUnchanged() = runBlocking {
        val (service, repository) = preparedService()
        val suggestion = ledgerSuggestion(emptyMap())

        val result = service.reviewAndCreate(companyId, suggestion, ImportSuggestionType.LEDGER)

        assertTrue(result is AccountingResult.Success)
        val ledger = repository.getLedgers(companyId).first().first()
        assertEquals(Money.ZERO, ledger.openingBalance)
        assertEquals(DrCr.DEBIT, ledger.openingBalanceType)
    }

    @Test
    fun testReviewAndCreate_importedOpeningBalance_isActuallyPersistedInLedger() = runBlocking {
        val (service, repository) = preparedService()
        val suggestion = ledgerSuggestion(mapOf("openingbalance" to "75000", "type" to "Credit"), name = "Persisted Ledger")

        service.reviewAndCreate(companyId, suggestion, ImportSuggestionType.LEDGER)

        val ledger = repository.getLedgers(companyId).first().first { it.name == "Persisted Ledger" }
        assertEquals(Money.fromRupees(75000L), ledger.openingBalance)
        assertEquals(DrCr.CREDIT, ledger.openingBalanceType)
    }

    @Test
    fun testReviewAndCreate_ledgerCurrentBalance_initiallyEqualsImportedOpeningBalance() = runBlocking {
        val (service, repository) = preparedService()
        val suggestion = ledgerSuggestion(mapOf("openingbalance" to "42000", "type" to "Debit"))

        service.reviewAndCreate(companyId, suggestion, ImportSuggestionType.LEDGER)

        val ledger = repository.getLedgers(companyId).first().first()
        assertEquals(ledger.openingBalance, ledger.currentBalance)
        assertEquals(ledger.openingBalanceType, ledger.currentBalanceType)
        assertEquals(Money.fromRupees(42000L), ledger.currentBalance)
    }
}
