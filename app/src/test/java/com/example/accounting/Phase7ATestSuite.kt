package com.example.accounting

import com.example.accounting.domain.invoice.InvoiceStatus
import com.example.accounting.domain.invoice.InvoiceStatusEngine
import com.example.accounting.domain.party.PaymentTerms
import com.example.accounting.domain.party.PaymentTermsType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 7A (Party + Invoice Domain Foundation) - pure JVM tests for [InvoiceStatusEngine] and
 * [PaymentTerms], the two new Room-independent engines. No Robolectric/DAO dependency, same
 * pattern as the Phase 5 RoundOffEngine/GstCalculationEngine unit tests.
 */
class Phase7ATestSuite {

    private val today = LocalDate.of(2026, 8, 21)

    // ==================== InvoiceStatusEngine ====================

    @Test
    fun testDeriveStatus_nullVoucherId_isDraft() {
        val status = InvoiceStatusEngine.deriveStatus(
            voucherId = null, isCancelled = false, totalAmountPaise = 100_00L,
            outstandingPaise = 100_00L, dueDate = null, today = today
        )
        assertEquals(InvoiceStatus.DRAFT, status)
    }

    @Test
    fun testDeriveStatus_cancelledVoucher_isCancelled_regardlessOfOutstanding() {
        val status = InvoiceStatusEngine.deriveStatus(
            voucherId = "V1", isCancelled = true, totalAmountPaise = 100_00L,
            outstandingPaise = 0L, dueDate = today.minusDays(10), today = today
        )
        assertEquals(InvoiceStatus.CANCELLED, status)
    }

    @Test
    fun testDeriveStatus_fullyOutstanding_notOverdue_isPosted() {
        val status = InvoiceStatusEngine.deriveStatus(
            voucherId = "V1", isCancelled = false, totalAmountPaise = 100_00L,
            outstandingPaise = 100_00L, dueDate = today.plusDays(5), today = today
        )
        assertEquals(InvoiceStatus.POSTED, status)
    }

    @Test
    fun testDeriveStatus_partiallyAllocated_isPartiallyPaid() {
        val status = InvoiceStatusEngine.deriveStatus(
            voucherId = "V1", isCancelled = false, totalAmountPaise = 100_00L,
            outstandingPaise = 40_00L, dueDate = today.plusDays(5), today = today
        )
        assertEquals(InvoiceStatus.PARTIALLY_PAID, status)
    }

    @Test
    fun testDeriveStatus_zeroOutstanding_isPaid() {
        val status = InvoiceStatusEngine.deriveStatus(
            voucherId = "V1", isCancelled = false, totalAmountPaise = 100_00L,
            outstandingPaise = 0L, dueDate = today.minusDays(5), today = today
        )
        assertEquals(InvoiceStatus.PAID, status)
    }

    @Test
    fun testDeriveStatus_negativeOutstanding_stillPaid() {
        // Over-allocation is guarded elsewhere (allocateSettlement); the status engine must still
        // treat any non-positive outstanding as fully paid, never crash or misclassify.
        val status = InvoiceStatusEngine.deriveStatus(
            voucherId = "V1", isCancelled = false, totalAmountPaise = 100_00L,
            outstandingPaise = -5_00L, dueDate = null, today = today
        )
        assertEquals(InvoiceStatus.PAID, status)
    }

    @Test
    fun testDeriveStatus_unpaidPastDueDate_isOverdue() {
        val status = InvoiceStatusEngine.deriveStatus(
            voucherId = "V1", isCancelled = false, totalAmountPaise = 100_00L,
            outstandingPaise = 100_00L, dueDate = today.minusDays(1), today = today
        )
        assertEquals(InvoiceStatus.OVERDUE, status)
    }

    @Test
    fun testDeriveStatus_partiallyPaidPastDueDate_isOverdue() {
        val status = InvoiceStatusEngine.deriveStatus(
            voucherId = "V1", isCancelled = false, totalAmountPaise = 100_00L,
            outstandingPaise = 40_00L, dueDate = today.minusDays(1), today = today
        )
        assertEquals(InvoiceStatus.OVERDUE, status)
    }

    @Test
    fun testDeriveStatus_dueDateIsToday_notYetOverdue() {
        val status = InvoiceStatusEngine.deriveStatus(
            voucherId = "V1", isCancelled = false, totalAmountPaise = 100_00L,
            outstandingPaise = 100_00L, dueDate = today, today = today
        )
        assertEquals(InvoiceStatus.POSTED, status)
    }

    @Test
    fun testDeriveStatus_paidPastDueDate_neverOverdue() {
        val status = InvoiceStatusEngine.deriveStatus(
            voucherId = "V1", isCancelled = false, totalAmountPaise = 100_00L,
            outstandingPaise = 0L, dueDate = today.minusDays(30), today = today
        )
        assertEquals(InvoiceStatus.PAID, status)
    }

    @Test
    fun testDeriveStatus_noDueDate_neverOverdue() {
        val status = InvoiceStatusEngine.deriveStatus(
            voucherId = "V1", isCancelled = false, totalAmountPaise = 100_00L,
            outstandingPaise = 100_00L, dueDate = null, today = today
        )
        assertEquals(InvoiceStatus.POSTED, status)
    }

    // ==================== PaymentTerms ====================

    @Test
    fun testPaymentTerms_dueOnReceipt_sameDayAsInvoice() {
        val terms = PaymentTerms(PaymentTermsType.DUE_ON_RECEIPT)
        assertEquals(today, terms.dueDate(today))
    }

    @Test
    fun testPaymentTerms_net30_addsThirtyDays() {
        val terms = PaymentTerms(PaymentTermsType.NET_30)
        assertEquals(today.plusDays(30), terms.dueDate(today))
    }

    @Test
    fun testPaymentTerms_net7Net15Net45Net60_addExactDayCounts() {
        assertEquals(today.plusDays(7), PaymentTerms(PaymentTermsType.NET_7).dueDate(today))
        assertEquals(today.plusDays(15), PaymentTerms(PaymentTermsType.NET_15).dueDate(today))
        assertEquals(today.plusDays(45), PaymentTerms(PaymentTermsType.NET_45).dueDate(today))
        assertEquals(today.plusDays(60), PaymentTerms(PaymentTermsType.NET_60).dueDate(today))
    }

    @Test
    fun testPaymentTerms_custom_addsCustomDays() {
        val terms = PaymentTerms(PaymentTermsType.CUSTOM, customDays = 21)
        assertEquals(today.plusDays(21), terms.dueDate(today))
    }

    @Test
    fun testPaymentTerms_customWithNullDays_defaultsToZero() {
        val terms = PaymentTerms(PaymentTermsType.CUSTOM, customDays = null)
        assertEquals(today, terms.dueDate(today))
    }
}
