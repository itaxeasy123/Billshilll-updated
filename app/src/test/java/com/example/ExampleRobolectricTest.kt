package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.DrCr
import com.example.accounting.core.common.Money
import com.example.accounting.core.database.AppDatabase
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.DoubleEntryValidator
import com.example.accounting.domain.accounting.JournalItem
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.domain.financialyear.AccountingPeriod
import com.example.accounting.domain.financialyear.FinancialYear
import com.example.accounting.domain.financialyear.PeriodStatus
import com.example.accounting.domain.taxation.gst.GSTRules
import com.example.accounting.domain.taxation.gst.SupplyType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: AccountingRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AccountingRepository(db.accountingDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LedgerPrime", appName)
    }

    @Test
    fun `money arithmetic maintains exact paise precision`() {
        val m1 = Money.parse("1500.50")
        val m2 = Money.parse("250.25")
        val sum = m1 + m2
        assertEquals(175075L, sum.paise)
        assertEquals("₹1,750.75", sum.format())

        val tax18 = sum.percentage(18.0)
        assertEquals(31514L, tax18.paise) // ₹315.135 rounds half-up to 315.14
    }

    @Test
    fun `gst rules calculate intra-state and inter-state accurately`() {
        val taxable = Money.fromRupees(10000.0) // ₹10,000

        // Intra-State 18% -> CGST 9% (₹900) + SGST 9% (₹900) = ₹1,800
        val intra = GSTRules.calculateTax(taxable, 18.0, SupplyType.INTRA_STATE)
        assertEquals(90000L, intra.cgstAmount.paise)
        assertEquals(90000L, intra.sgstAmount.paise)
        assertEquals(0L, intra.igstAmount.paise)
        assertEquals(180000L, intra.totalTax.paise)
        assertEquals(1180000L, intra.totalWithTax.paise)

        // Inter-State 18% -> IGST 18% (₹1,800)
        val inter = GSTRules.calculateTax(taxable, 18.0, SupplyType.INTER_STATE)
        assertEquals(0L, inter.cgstAmount.paise)
        assertEquals(0L, inter.sgstAmount.paise)
        assertEquals(180000L, inter.igstAmount.paise)
        assertEquals(180000L, inter.totalTax.paise)
    }

    @Test
    fun `double entry validator rejects unbalanced vouchers`() {
        val fy = FinancialYear(
            financialYearId = "FY1",
            companyId = "COMP1",
            fyCode = "FY 2026-27",
            startDate = LocalDate.parse("2026-04-01"),
            endDate = LocalDate.parse("2027-03-31")
        )
        val period = AccountingPeriod(
            periodId = "P1",
            companyId = "COMP1",
            financialYearId = "FY1",
            name = "Aug 2026",
            startDate = LocalDate.parse("2026-08-01"),
            endDate = LocalDate.parse("2026-08-31"),
            status = PeriodStatus.OPEN
        )

        // Unbalanced: Dr ₹5,000 vs Cr ₹4,000
        val unbalancedVoucher = Voucher(
            voucherId = "V1",
            companyId = "COMP1",
            financialYearId = "FY1",
            voucherNumber = "PMT-001",
            voucherType = VoucherType.PAYMENT,
            date = LocalDate.parse("2026-08-15"),
            items = listOf(
                JournalItem("1", "V1", "COMP1", "FY1", "LED1", "Rent", DrCr.DEBIT, Money.fromRupees(5000.0)),
                JournalItem("2", "V1", "COMP1", "FY1", "LED2", "Bank", DrCr.CREDIT, Money.fromRupees(4000.0))
            )
        )

        val result = DoubleEntryValidator.validate(
            voucher = unbalancedVoucher,
            activeFinancialYear = fy,
            activePeriod = period,
            validLedgerIdsForCompany = setOf("LED1", "LED2")
        )

        assertTrue(result is AccountingResult.Failure)
    }

    @Test
    fun `double entry validator accepts balanced vouchers within period`() {
        val fy = FinancialYear(
            financialYearId = "FY1",
            companyId = "COMP1",
            fyCode = "FY 2026-27",
            startDate = LocalDate.parse("2026-04-01"),
            endDate = LocalDate.parse("2027-03-31")
        )
        val period = AccountingPeriod(
            periodId = "P1",
            companyId = "COMP1",
            financialYearId = "FY1",
            name = "Aug 2026",
            startDate = LocalDate.parse("2026-08-01"),
            endDate = LocalDate.parse("2026-08-31"),
            status = PeriodStatus.OPEN
        )

        // Balanced: Dr ₹5,000 == Cr ₹5,000
        val balancedVoucher = Voucher(
            voucherId = "V1",
            companyId = "COMP1",
            financialYearId = "FY1",
            voucherNumber = "PMT-001",
            voucherType = VoucherType.PAYMENT,
            date = LocalDate.parse("2026-08-15"),
            items = listOf(
                JournalItem("1", "V1", "COMP1", "FY1", "LED1", "Rent", DrCr.DEBIT, Money.fromRupees(5000.0)),
                JournalItem("2", "V1", "COMP1", "FY1", "LED2", "Bank", DrCr.CREDIT, Money.fromRupees(5000.0))
            )
        )

        val result = DoubleEntryValidator.validate(
            voucher = balancedVoucher,
            activeFinancialYear = fy,
            activePeriod = period,
            validLedgerIdsForCompany = setOf("LED1", "LED2")
        )

        assertTrue(result is AccountingResult.Success)
    }

    @Test
    fun `full repository seeding and company initialization`() = runBlocking {
        repository.initializeDatabaseIfNeeded()

        val companies = repository.getCompanies().first()
        assertFalse(companies.isEmpty())
        val defaultComp = companies.first()
        assertEquals("COMP_APEX_01", defaultComp.companyId)

        val fyList = repository.getFinancialYears(defaultComp.companyId).first()
        assertEquals(3, fyList.size)
        assertTrue(fyList.any { it.fyCode == "FY 2025-26" })
        assertTrue(fyList.any { it.fyCode == "FY 2026-27" })
        assertTrue(fyList.any { it.fyCode == "FY 2027-28" })

        // Check Chart of Accounts Groups
        val groups = repository.getGroups(defaultComp.companyId).first()
        assertTrue(groups.size >= 22)

        // Check Chart of Accounts Ledgers and Balanced Opening Balances
        val ledgers = repository.getLedgers(defaultComp.companyId).first()
        assertFalse(ledgers.isEmpty())
        val totalDebitOpening = ledgers.filter { it.openingBalanceType == DrCr.DEBIT }.sumOf { it.openingBalance.paise }
        val totalCreditOpening = ledgers.filter { it.openingBalanceType == DrCr.CREDIT }.sumOf { it.openingBalance.paise }
        assertEquals(totalDebitOpening, totalCreditOpening)
    }
}
