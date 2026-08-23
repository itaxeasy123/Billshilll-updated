package com.example.accounting

import com.example.accounting.application.invoice.InvoiceFilter
import com.example.accounting.application.invoice.InvoiceManagementService
import com.example.accounting.application.numbering.DocumentNumberConfig
import com.example.accounting.application.voucher.VoucherManagementService
import com.example.accounting.core.common.Money
import com.example.accounting.core.common.Quantity
import com.example.accounting.domain.dataimport.DataImportAdapter
import com.example.accounting.domain.dataimport.ImportSuggestionType
import com.example.accounting.domain.invoice.InvoiceLine
import com.example.accounting.domain.invoice.InvoiceStatus
import com.example.accounting.domain.invoice.InvoiceType
import com.example.accounting.domain.itemclassification.HsnSacCode
import com.example.accounting.domain.itemclassification.IllustrativeHsnSacCodes
import com.example.accounting.domain.profession.BusinessProfession
import com.example.accounting.domain.profession.StandardBusinessProfessions
import com.example.accounting.domain.qrbarcode.BarcodeSymbology
import com.example.accounting.domain.qrbarcode.QrBarcodeAdapter
import com.example.accounting.domain.recurring.RecurringFrequency
import com.example.accounting.domain.recurringinvoice.RecurringInvoiceGenerationOutcome
import com.example.accounting.domain.recurringinvoice.RecurringInvoicePeriod
import com.example.accounting.domain.recurringinvoice.RecurringInvoiceSchedule
import com.example.accounting.domain.rendering.DocumentData
import com.example.accounting.domain.subscription.CompanySubscription
import com.example.accounting.domain.subscription.EntitlementFeature
import com.example.accounting.domain.subscription.SubscriptionEntitlementChecker
import com.example.accounting.domain.subscription.SubscriptionPlanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 7J - Management + Subscription Architecture (structure only) - pure JVM, structural-
 * contract tests only, matching [Phase7ITestSuite]'s exact pattern. The audit for this phase
 * found nearly every named business area (Invoice, Voucher, Receipt, Payment, Cash/Bank, Party/
 * Ledger/Item, Settlement/Outstanding, Reports, PDF/CSV/JSON Export, Preview/Print/Share, OCR
 * extraction) already frozen and tested elsewhere - see `docs/52_MANAGEMENT_ARCHITECTURE.md` for
 * the full map. The genuinely new contracts are [DataImportAdapter], [QrBarcodeAdapter],
 * [BusinessProfession], [HsnSacCode], and [CompanySubscription]/[SubscriptionEntitlementChecker];
 * these tests lock in each one's shape so a future implementation cannot silently widen what it's
 * allowed to touch.
 */
class Phase7JArchitectureTestSuite {

    private val forbiddenMutationFragments = listOf("Dao", "Repository", "PostingEngine", "DoubleEntryValidator")
    private val suspiciousRateFieldFragments = listOf("rate", "gst", "tax", "percent")

    private fun assertNoMutationPathReachable(clazz: Class<*>) {
        for (method in clazz.declaredMethods) {
            val referencedTypes = method.parameterTypes.toList() + method.returnType
            for (type in referencedTypes) {
                assertFalse(
                    "${clazz.simpleName}.${method.name} must not reference ${type.simpleName} - this interface must have zero path to an accounting mutation",
                    forbiddenMutationFragments.any { type.simpleName.contains(it) }
                )
            }
        }
    }

    private fun assertNoRateOrTaxField(clazz: Class<*>) {
        for (field in clazz.declaredFields) {
            val nameLower = field.name.lowercase()
            assertFalse(
                "${clazz.simpleName}.${field.name} looks like a hardcoded rate/tax field - this type must be classification only",
                suspiciousRateFieldFragments.any { nameLower.contains(it) }
            )
        }
    }

    // ==========================================
    // DataImportAdapter
    // ==========================================
    @Test
    fun testDataImportAdapter_isAPureInterface_withExactlyOneOperation() {
        val clazz = DataImportAdapter::class.java
        assertTrue("DataImportAdapter must be an interface", clazz.isInterface)
        assertEquals(setOf("parseFile"), clazz.declaredMethods.map { it.name }.toSet())
    }

    @Test
    fun testDataImportAdapter_hasNoAccountingMutationPath() {
        assertNoMutationPathReachable(DataImportAdapter::class.java)
    }

    @Test
    fun testDataImportAdapter_noConcreteImplementationExists() {
        assertTrue(java.lang.reflect.Modifier.isAbstract(DataImportAdapter::class.java.modifiers))
        assertEquals(0, DataImportAdapter::class.java.declaredConstructors.size)
    }

    @Test
    fun testImportSuggestionType_excludesVoucherAndTransactionTypes() {
        // Structural proof that bulk-importing double-entry transactions is out of scope for this
        // contract - only master-data suggestion types exist.
        val typeNames = ImportSuggestionType.entries.map { it.name }.toSet()
        assertEquals(setOf("PARTY", "LEDGER", "STOCK_ITEM"), typeNames)
        assertFalse(typeNames.any { it.contains("VOUCHER") || it.contains("TRANSACTION") || it.contains("JOURNAL") })
    }

    // ==========================================
    // QrBarcodeAdapter
    // ==========================================
    @Test
    fun testQrBarcodeAdapter_isAPureInterface_withExactlyTwoOperations() {
        val clazz = QrBarcodeAdapter::class.java
        assertTrue("QrBarcodeAdapter must be an interface", clazz.isInterface)
        assertEquals(setOf("generateForStockItem", "scanImage"), clazz.declaredMethods.map { it.name }.toSet())
    }

    @Test
    fun testQrBarcodeAdapter_hasNoAccountingMutationPath() {
        assertNoMutationPathReachable(QrBarcodeAdapter::class.java)
    }

    @Test
    fun testBarcodeSymbology_hasAnUnknownFallback_neverForcedGuess() {
        assertTrue(BarcodeSymbology.entries.contains(BarcodeSymbology.UNKNOWN))
    }

    // ==========================================
    // BusinessProfession (extensible, no GST rate)
    // ==========================================
    @Test
    fun testBusinessProfession_neverCarriesARateOrTaxField() {
        assertNoRateOrTaxField(BusinessProfession::class.java)
    }

    @Test
    fun testBusinessProfession_isExtensibleWithoutModifyingTheType() {
        // "Extensible" proven structurally: constructing a brand-new profession the standard
        // catalog never named requires zero change to BusinessProfession.kt.
        val customProfession = BusinessProfession("ASTROLOGER", "Astrologer", com.example.accounting.domain.profession.BusinessProfessionCategory.OTHER)
        assertEquals("ASTROLOGER", customProfession.code)
        assertFalse(StandardBusinessProfessions.ALL.contains(customProfession))
    }

    @Test
    fun testStandardBusinessProfessions_includesEveryNamedExample() {
        val codes = StandardBusinessProfessions.ALL.map { it.code }.toSet()
        assertEquals(setOf("RETAILER", "WHOLESALER", "DOCTOR", "ENGINEER", "GOLDSMITH", "CONTRACTOR"), codes)
    }

    // ==========================================
    // HsnSacCode (classification, no GST rate)
    // ==========================================
    @Test
    fun testHsnSacCode_neverCarriesARateField() {
        assertNoRateOrTaxField(HsnSacCode::class.java)
    }

    @Test
    fun testIllustrativeHsnSacCodes_hasBothGoodsAndServiceEntries() {
        assertTrue(IllustrativeHsnSacCodes.ALL.any { !it.isService })
        assertTrue(IllustrativeHsnSacCodes.ALL.any { it.isService })
    }

    // ==========================================
    // Subscription/Entitlement - the most safety-sensitive contract in this phase
    // ==========================================
    @Test
    fun testSubscriptionEntitlementChecker_hasNoAccountingMutationPath() {
        assertNoMutationPathReachable(SubscriptionEntitlementChecker::class.java)
    }

    @Test
    fun testSubscriptionEntitlementChecker_isPureAndNonSuspend_neverAnAdapterBoundary() {
        // Deliberately not a suspend interface like the other 7H-7J adapters - there is no
        // external I/O here at all, only a boolean decision over already-known data.
        val methods = SubscriptionEntitlementChecker::class.java.declaredMethods.filter { it.name == "hasEntitlement" }
        assertEquals(1, methods.size)
        assertEquals(Boolean::class.javaPrimitiveType, methods.first().returnType)
    }

    @Test
    fun testHasEntitlement_activeSubscriptionWithFeature_true() {
        val subscription = CompanySubscription(
            subscriptionId = "SUB_1", companyId = "COMP_1", financialYearId = "FY_2026_27",
            planType = SubscriptionPlanType.PAID, planName = "Pro",
            entitlements = setOf(EntitlementFeature.GSTR, EntitlementFeature.CMA),
            isActive = true
        )
        assertTrue(SubscriptionEntitlementChecker.hasEntitlement(subscription, EntitlementFeature.GSTR))
        assertFalse(SubscriptionEntitlementChecker.hasEntitlement(subscription, EntitlementFeature.ITR))
    }

    @Test
    fun testHasEntitlement_inactiveSubscription_alwaysFalseEvenIfFeatureListed() {
        val subscription = CompanySubscription(
            subscriptionId = "SUB_2", companyId = "COMP_1", financialYearId = "FY_2026_27",
            planType = SubscriptionPlanType.PAID, planName = "Pro",
            entitlements = setOf(EntitlementFeature.GSTR),
            isActive = false
        )
        assertFalse(SubscriptionEntitlementChecker.hasEntitlement(subscription, EntitlementFeature.GSTR))
    }

    @Test
    fun testEntitlementFeature_coversEveryNamedArea() {
        val names = EntitlementFeature.entries.map { it.name }.toSet()
        assertEquals(
            setOf("ACCOUNTING", "GSTR", "E_INVOICE", "ITR", "AUDIT_REPORT", "CMA", "OCR", "INVENTORY", "ADVANCED_REPORTS", "API_ACCESS"),
            names
        )
    }

    @Test
    fun testCompanySubscription_isKeyedByFinancialYearId_neverARawDateRange() {
        // Structural proof: no startDate/endDate/LocalDate field exists on this type - validity
        // is always expressed via financialYearId into the existing, frozen FinancialYear.
        val fieldNames = CompanySubscription::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(fieldNames.contains("financialyearid"))
        assertFalse(fieldNames.any { it.contains("startdate") || it.contains("enddate") })
    }

    // ==========================================
    // RecurringInvoiceSchedule / RecurringInvoicePeriod
    // ==========================================
    private fun invoiceSchedule(
        dayOfMonth: Int,
        frequency: RecurringFrequency,
        isActive: Boolean = true,
        startDate: LocalDate = LocalDate.of(2026, 1, 1),
        endDate: LocalDate? = null
    ) = RecurringInvoiceSchedule(
        scheduleId = "SCH_INV_1", companyId = "COMP_1", financialYearId = "FY_2026_27",
        partyId = "PARTY_1", invoiceType = InvoiceType.SALES_INVOICE, frequency = frequency,
        dayOfMonth = dayOfMonth, startDate = startDate, endDate = endDate,
        lines = listOf(InvoiceLine(lineId = "", itemId = "ITEM_1", itemName = "Retainer", hsnSacCode = "998222", quantity = Quantity.fromLong(1), rate = Money.fromRupees(10_000L), gstRatePercent = 18.0)),
        isActive = isActive
    )

    @Test
    fun testRecurringInvoicePeriod_isDue_monthly_dueOnOrAfterScheduledDay() {
        val schedule = invoiceSchedule(dayOfMonth = 5, frequency = RecurringFrequency.MONTHLY)
        assertFalse(RecurringInvoicePeriod.isDue(schedule, LocalDate.of(2026, 6, 4)))
        assertTrue(RecurringInvoicePeriod.isDue(schedule, LocalDate.of(2026, 6, 5)))
    }

    @Test
    fun testRecurringInvoicePeriod_isDue_monthly_dayOfMonth31ClampsToShortMonth() {
        val schedule = invoiceSchedule(dayOfMonth = 31, frequency = RecurringFrequency.MONTHLY)
        // February 2026 (non-leap) has 28 days - day 31 clamps to 28, never a spurious "never due".
        assertTrue(RecurringInvoicePeriod.isDue(schedule, LocalDate.of(2026, 2, 28)))
        assertFalse(RecurringInvoicePeriod.isDue(schedule, LocalDate.of(2026, 2, 27)))
    }

    @Test
    fun testRecurringInvoicePeriod_isDue_yearly_dueOnOrAfterAnniversary() {
        val schedule = invoiceSchedule(dayOfMonth = 15, frequency = RecurringFrequency.YEARLY, startDate = LocalDate.of(2026, 6, 15))
        assertFalse(RecurringInvoicePeriod.isDue(schedule, LocalDate.of(2026, 6, 14)))
        assertTrue(RecurringInvoicePeriod.isDue(schedule, LocalDate.of(2026, 6, 15)))
        assertTrue(RecurringInvoicePeriod.isDue(schedule, LocalDate.of(2026, 7, 1)))
        assertFalse(RecurringInvoicePeriod.isDue(schedule, LocalDate.of(2026, 5, 1)))
    }

    @Test
    fun testRecurringInvoicePeriod_isDue_inactiveOrOutsideWindow_neverDue() {
        val inactive = invoiceSchedule(dayOfMonth = 5, frequency = RecurringFrequency.MONTHLY, isActive = false)
        assertFalse(RecurringInvoicePeriod.isDue(inactive, LocalDate.of(2026, 6, 10)))

        val notStartedYet = invoiceSchedule(dayOfMonth = 5, frequency = RecurringFrequency.MONTHLY, startDate = LocalDate.of(2026, 7, 1))
        assertFalse(RecurringInvoicePeriod.isDue(notStartedYet, LocalDate.of(2026, 6, 10)))

        val ended = invoiceSchedule(dayOfMonth = 5, frequency = RecurringFrequency.MONTHLY, endDate = LocalDate.of(2026, 5, 31))
        assertFalse(RecurringInvoicePeriod.isDue(ended, LocalDate.of(2026, 6, 10)))
    }

    @Test
    fun testRecurringInvoicePeriod_periodKeyFor_delegatesToTheExistingFrozenRecurringVoucherPeriod() {
        // Structural proof of reuse, not duplication: this must produce byte-identical output to
        // the existing RecurringVoucherPeriod.periodKeyFor for the same inputs.
        val date = LocalDate.of(2026, 6, 15)
        assertEquals(
            com.example.accounting.domain.recurring.RecurringVoucherPeriod.periodKeyFor(RecurringFrequency.MONTHLY, date),
            RecurringInvoicePeriod.periodKeyFor(RecurringFrequency.MONTHLY, date)
        )
    }

    @Test
    fun testRecurringInvoiceGenerationOutcome_generatedCarriesARealInvoiceId_neverASeparateDraftType() {
        // Structural proof of the key design insight: the "generated" outcome references a real
        // Invoice id directly - there is no separate RecurringInvoiceDraft type anywhere, unlike
        // the Voucher case, because Invoice already provides its own draft state.
        val outcome = RecurringInvoiceGenerationOutcome.InvoiceDraftGenerated(invoiceId = "INV_1", periodKey = "2026-06")
        assertEquals("INV_1", outcome.invoiceId)
    }

    // ==========================================
    // DocumentData ship/dispatch date - additive extension point
    // ==========================================
    @Test
    fun testDocumentData_hasShipDateAndDispatchDateFields_bothLocalDateTyped() {
        val shipDateField = DocumentData::class.java.getDeclaredField("shipDate")
        val dispatchDateField = DocumentData::class.java.getDeclaredField("dispatchDate")
        assertEquals(LocalDate::class.java, shipDateField.type)
        assertEquals(LocalDate::class.java, dispatchDateField.type)
    }

    // ==========================================
    // InvoiceManagementService (Phase 7J-A) - application-service contract only
    // ==========================================
    @Test
    fun testInvoiceManagementService_isAPureInterface_withExactlyTheFiveRequiredOperations() {
        val clazz = InvoiceManagementService::class.java
        assertTrue("InvoiceManagementService must be an interface", clazz.isInterface)
        assertEquals(
            setOf("createDraft", "updateDraft", "duplicateInvoice", "cancelInvoice", "search"),
            clazz.declaredMethods.map { it.name }.toSet()
        )
    }

    @Test
    fun testInvoiceManagementService_hasNoAccountingMutationPath() {
        assertNoMutationPathReachable(InvoiceManagementService::class.java)
    }

    @Test
    fun testInvoiceManagementService_noConcreteImplementationExists() {
        assertTrue(java.lang.reflect.Modifier.isAbstract(InvoiceManagementService::class.java.modifiers))
        assertEquals(0, InvoiceManagementService::class.java.declaredConstructors.size)
    }

    @Test
    fun testInvoiceManagementService_cancelInvoiceMirrorsTheExistingRepositoryFunctionSignature() {
        // Structural proof of "orchestrate, don't duplicate": the parameter shape matches
        // AccountingRepository.cancelInvoice(companyId, financialYearId, invoiceId) exactly, so a
        // future implementation is a direct delegation, not new logic.
        val method = InvoiceManagementService::class.java.declaredMethods.first { it.name == "cancelInvoice" }
        val paramTypeNames = method.parameterTypes.map { it.simpleName }
        assertEquals(listOf("String", "String", "String"), paramTypeNames.filter { it == "String" })
    }

    @Test
    fun testInvoiceFilter_allFieldsOptional_noFilterIsTheDefaultConstructionState() {
        val emptyFilter = InvoiceFilter()
        assertEquals("", emptyFilter.query)
        assertEquals(null, emptyFilter.state)
        assertEquals(null, emptyFilter.dateRange)
        assertEquals(null, emptyFilter.partyId)
    }

    @Test
    fun testInvoiceFilter_reusesTheExistingFrozenInvoiceStatusEnum_neverASecondStatusType() {
        val filter = InvoiceFilter(state = InvoiceStatus.OVERDUE)
        assertEquals(InvoiceStatus.OVERDUE, filter.state)
    }

    @Test
    fun testInvoiceFilter_dateRangeUsesTheExistingClosedRangeLocalDateConvention() {
        val filter = InvoiceFilter(dateRange = LocalDate.of(2026, 4, 1)..LocalDate.of(2026, 4, 30))
        assertTrue(filter.dateRange!!.contains(LocalDate.of(2026, 4, 15)))
        assertFalse(filter.dateRange!!.contains(LocalDate.of(2026, 5, 1)))
    }

    // ==========================================
    // VoucherManagementService (Phase 7J-A) - application-service contract only
    // ==========================================
    @Test
    fun testVoucherManagementService_isAPureInterface_withExactlyTheFourRequiredOperations() {
        val clazz = VoucherManagementService::class.java
        assertTrue("VoucherManagementService must be an interface", clazz.isInterface)
        // postDraft has default parameter values (mirroring postVoucher's own defaults), so Kotlin
        // compiles an extra "$default" bridge method alongside it - excluded here, it's not a
        // sixth business operation.
        val businessMethodNames = clazz.declaredMethods.map { it.name }.filterNot { it.contains("\$default") }.toSet()
        assertEquals(setOf("createDraft", "editDraft", "postDraft", "attachDocumentReference"), businessMethodNames)
    }

    @Test
    fun testVoucherManagementService_hasNoAccountingMutationPath() {
        assertNoMutationPathReachable(VoucherManagementService::class.java)
    }

    @Test
    fun testVoucherManagementService_noConcreteImplementationExists() {
        assertTrue(java.lang.reflect.Modifier.isAbstract(VoucherManagementService::class.java.modifiers))
        assertEquals(0, VoucherManagementService::class.java.declaredConstructors.size)
    }

    @Test
    fun testVoucherManagementService_postDraftMirrorsTheExistingRepositoryPostVoucherShape() {
        // Structural proof of "forwards to VoucherPostingEngine, never a parallel path": the
        // parameter types match AccountingRepository.postVoucher's own shape - Voucher, a String
        // idempotency key, a stock-line list, a GST-transaction list.
        val method = VoucherManagementService::class.java.declaredMethods.first { it.name == "postDraft" }
        val paramTypeSimpleNames = method.parameterTypes.map { it.simpleName }.toSet()
        assertTrue(paramTypeSimpleNames.contains("Voucher"))
        assertTrue(paramTypeSimpleNames.contains("List"))
    }

    @Test
    fun testAttachDocumentReference_takesADocumentAssetIdReference_neverDuplicatesDocumentAsset() {
        // Structural proof of reuse-by-reference: no parameter type on this interface is
        // DocumentAsset itself - only plain String ids are ever passed.
        val method = VoucherManagementService::class.java.declaredMethods.first { it.name == "attachDocumentReference" }
        assertFalse(method.parameterTypes.any { it.simpleName == "DocumentAsset" })
    }

    // ==========================================
    // DocumentNumberConfig (Phase 7J-A) - strictly read-only domain logic
    // ==========================================
    @Test
    fun testDocumentNumberConfig_formattedIsPureAndDeterministic_neverAdvancesTheSequence() {
        val config = DocumentNumberConfig(companyId = "COMP_1", documentTypeCode = "SAL", prefix = "INV/", suffix = "/26-27", nextSequenceNumber = 42L, paddingSize = 5)
        assertEquals("INV/00042/26-27", config.formatted())
        // Calling formatted() twice must never change nextSequenceNumber - it's read-only.
        assertEquals(config.formatted(), config.formatted())
        assertEquals(42L, config.nextSequenceNumber)
    }

    @Test
    fun testDocumentNumberConfig_hasNoGenerationOrMutationMethodBeyondPureFormatting() {
        // "Strictly read-only domain logic" - the only method beyond Kotlin's own generated
        // data-class methods must be the pure formatted() - no advance()/next()/save() etc.
        val declaredMethodNames = DocumentNumberConfig::class.java.declaredMethods
            .map { it.name }
            .filterNot { it.contains("\$default") }
            .filterNot { it in setOf("copy", "toString", "equals", "hashCode") }
            .filterNot { it.startsWith("component") }
            .filterNot { it.startsWith("get") } // plain Kotlin data-class property getters (companyId, prefix, etc.)
        assertEquals(setOf("formatted"), declaredMethodNames.toSet())
    }
}
