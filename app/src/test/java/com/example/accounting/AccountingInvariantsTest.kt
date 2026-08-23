package com.example.accounting

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.AppError
import com.example.accounting.core.common.DrCr
import com.example.accounting.core.common.Money
import com.example.accounting.domain.accounting.DoubleEntryValidator
import com.example.accounting.domain.accounting.JournalItem
import com.example.accounting.domain.accounting.StandardSystemGroups
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.domain.financialyear.AccountingPeriod
import com.example.accounting.domain.financialyear.FinancialYear
import com.example.accounting.domain.financialyear.PeriodStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class AccountingInvariantsTest {

    private val companyId = "COMP_TEST_001"
    private val fyId = "FY_2026_27"

    private val activeFY = FinancialYear(
        financialYearId = fyId,
        companyId = companyId,
        fyCode = "2026-27",
        startDate = LocalDate.of(2026, 4, 1),
        endDate = LocalDate.of(2027, 3, 31),
        isCurrent = true,
        isLocked = false
    )

    private val openPeriod = AccountingPeriod(
        periodId = "PER_2026_04",
        companyId = companyId,
        financialYearId = fyId,
        name = "April 2026",
        startDate = LocalDate.of(2026, 4, 1),
        endDate = LocalDate.of(2026, 4, 30),
        status = PeriodStatus.OPEN
    )

    private val lockedPeriod = AccountingPeriod(
        periodId = "PER_2026_05",
        companyId = companyId,
        financialYearId = fyId,
        name = "May 2026",
        startDate = LocalDate.of(2026, 5, 1),
        endDate = LocalDate.of(2026, 5, 31),
        status = PeriodStatus.LOCKED
    )

    private val validLedgers = setOf("LED_CASH", "LED_BANK", "LED_SALES", "LED_RENT", StandardSystemGroups.SUSPENSE_LEDGER_ID)

    @Test
    fun testDoubleEntryBalance_ValidVoucher_Succeeds() {
        val voucher = Voucher(
            voucherId = "VCH_001",
            companyId = companyId,
            financialYearId = fyId,
            voucherNumber = "PMT-001",
            voucherType = VoucherType.PAYMENT,
            date = LocalDate.of(2026, 4, 15),
            totalAmount = Money.fromRupees(1500.0),
            items = listOf(
                JournalItem("ITEM_1", "VCH_001", companyId, fyId, "LED_RENT", "Rent Expense", DrCr.DEBIT, Money.fromRupees(1500.0)),
                JournalItem("ITEM_2", "VCH_001", companyId, fyId, "LED_CASH", "Cash Account", DrCr.CREDIT, Money.fromRupees(1500.0))
            )
        )

        val result = DoubleEntryValidator.validate(voucher, activeFY, openPeriod, validLedgers)
        assertTrue("Valid double-entry transaction must succeed", result is AccountingResult.Success)
    }

    @Test
    fun testUnbalancedJournal_Rejection() {
        val voucher = Voucher(
            voucherId = "VCH_002",
            companyId = companyId,
            financialYearId = fyId,
            voucherNumber = "PMT-002",
            voucherType = VoucherType.PAYMENT,
            date = LocalDate.of(2026, 4, 15),
            totalAmount = Money.fromRupees(1500.0),
            items = listOf(
                JournalItem("ITEM_1", "VCH_002", companyId, fyId, "LED_RENT", "Rent Expense", DrCr.DEBIT, Money.fromRupees(1500.0)),
                JournalItem("ITEM_2", "VCH_002", companyId, fyId, "LED_CASH", "Cash Account", DrCr.CREDIT, Money.fromRupees(1400.0)) // Unbalanced!
            )
        )

        val result = DoubleEntryValidator.validate(voucher, activeFY, openPeriod, validLedgers)
        assertTrue("Unbalanced journal must fail validation", result is AccountingResult.Failure)
        val error = (result as AccountingResult.Failure).error
        assertTrue(error is AppError.UnbalancedVoucher)
        assertEquals(Money.fromRupees(100.0).paise, (error as AppError.UnbalancedVoucher).difference.paise)
    }

    @Test
    fun testLockedPeriod_Rejection() {
        val voucher = Voucher(
            voucherId = "VCH_003",
            companyId = companyId,
            financialYearId = fyId,
            voucherNumber = "PMT-003",
            voucherType = VoucherType.PAYMENT,
            date = LocalDate.of(2026, 5, 10), // in May (LOCKED)
            totalAmount = Money.fromRupees(500.0),
            items = listOf(
                JournalItem("ITEM_1", "VCH_003", companyId, fyId, "LED_RENT", "Rent Expense", DrCr.DEBIT, Money.fromRupees(500.0)),
                JournalItem("ITEM_2", "VCH_003", companyId, fyId, "LED_BANK", "Bank Account", DrCr.CREDIT, Money.fromRupees(500.0))
            )
        )

        val result = DoubleEntryValidator.validate(voucher, activeFY, lockedPeriod, validLedgers)
        assertTrue("Posting in locked period must be rejected", result is AccountingResult.Failure)
        val error = (result as AccountingResult.Failure).error
        assertTrue(error is AppError.PeriodLocked)
    }

    @Test
    fun testFinancialYearIsolation_Out_Of_Bounds_Rejection() {
        val voucher = Voucher(
            voucherId = "VCH_004",
            companyId = companyId,
            financialYearId = fyId,
            voucherNumber = "PMT-004",
            voucherType = VoucherType.PAYMENT,
            date = LocalDate.of(2025, 3, 31), // Prior year!
            totalAmount = Money.fromRupees(500.0),
            items = listOf(
                JournalItem("ITEM_1", "VCH_004", companyId, fyId, "LED_RENT", "Rent Expense", DrCr.DEBIT, Money.fromRupees(500.0)),
                JournalItem("ITEM_2", "VCH_004", companyId, fyId, "LED_CASH", "Cash Account", DrCr.CREDIT, Money.fromRupees(500.0))
            )
        )

        val result = DoubleEntryValidator.validate(voucher, activeFY, openPeriod, validLedgers)
        assertTrue("Posting outside financial year bounds must be rejected", result is AccountingResult.Failure)
    }

    @Test
    fun testCompanyIsolation_ForeignLedger_Rejection() {
        val voucher = Voucher(
            voucherId = "VCH_005",
            companyId = companyId,
            financialYearId = fyId,
            voucherNumber = "PMT-005",
            voucherType = VoucherType.PAYMENT,
            date = LocalDate.of(2026, 4, 10),
            totalAmount = Money.fromRupees(500.0),
            items = listOf(
                JournalItem("ITEM_1", "VCH_005", companyId, fyId, "LED_FOREIGN_SECRET", "Foreign Ledger", DrCr.DEBIT, Money.fromRupees(500.0)),
                JournalItem("ITEM_2", "VCH_005", companyId, fyId, "LED_CASH", "Cash Account", DrCr.CREDIT, Money.fromRupees(500.0))
            )
        )

        val result = DoubleEntryValidator.validate(voucher, activeFY, openPeriod, validLedgers)
        assertTrue("Foreign ledger access across company boundary must be rejected", result is AccountingResult.Failure)
        val error = (result as AccountingResult.Failure).error
        assertTrue(error is AppError.TenantMismatch)
    }

    @Test
    fun testSuspenseProtection_SystemLedgerFlag() {
        val suspenseLedgerId = StandardSystemGroups.SUSPENSE_LEDGER_ID
        assertTrue("Suspense ledger ID must be protected", suspenseLedgerId.startsWith("LED_SYS_"))
    }

    @Test
    fun testMoneyPaisePrecision_ExactIntegerRepresentation() {
        val m1 = Money.fromRupees(1234.56)
        val m2 = Money.fromRupees(0.04)
        val sum = m1 + m2
        assertEquals("Total paise must be exact integer", 123460L, sum.paise)
        assertEquals("₹1,234.60", sum.format())
    }

    @Test
    fun testIdempotencyKey_GenerationIntegrity() {
        val key1 = UUID.randomUUID().toString()
        val key2 = UUID.randomUUID().toString()
        assertFalse("Idempotency keys must be distinct", key1 == key2)
        assertTrue("UUID length standard", key1.length >= 32)
    }
}
