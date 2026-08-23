package com.example.accounting.domain.accounting

import com.example.accounting.core.common.DrCr
import com.example.accounting.core.common.Money
import java.time.LocalDate

data class JournalItem(
    val itemId: String,
    val voucherId: String,
    val companyId: String,
    val financialYearId: String,
    val ledgerId: String,
    val ledgerName: String,
    val type: DrCr,
    val amount: Money,
    val narration: String = "",
    val lineOrder: Int = 0
) {
    fun toJournalEntry(): JournalEntry = JournalEntry(
        journalEntryId = itemId,
        voucherId = voucherId,
        companyId = companyId,
        financialYearId = financialYearId,
        ledgerId = ledgerId,
        ledgerName = ledgerName,
        debit = if (type == DrCr.DEBIT) amount else Money.ZERO,
        credit = if (type == DrCr.CREDIT) amount else Money.ZERO,
        lineNumber = lineOrder,
        narration = narration
    )
}

/**
 * Double-Entry Journal Entry Model (Rule 26)
 * Total Debit == Total Credit
 * debit >= 0, credit >= 0
 * A line cannot contain both debit and credit.
 */
data class JournalEntry(
    val journalEntryId: String,
    val voucherId: String,
    val companyId: String,
    val financialYearId: String,
    val ledgerId: String,
    val ledgerName: String = "",
    val debit: Money = Money.ZERO,
    val credit: Money = Money.ZERO,
    val lineNumber: Int = 0,
    val narration: String = ""
) {
    init {
        require(debit.paise >= 0) { "Debit must be >= 0" }
        require(credit.paise >= 0) { "Credit must be >= 0" }
        require(!(debit.isPositive && credit.isPositive)) { "A line cannot contain both debit and credit" }
    }

    val type: DrCr get() = if (debit.isPositive) DrCr.DEBIT else DrCr.CREDIT
    val amount: Money get() = if (debit.isPositive) debit else credit

    fun toJournalItem(): JournalItem = JournalItem(
        itemId = journalEntryId,
        voucherId = voucherId,
        companyId = companyId,
        financialYearId = financialYearId,
        ledgerId = ledgerId,
        ledgerName = ledgerName,
        type = type,
        amount = amount,
        narration = narration,
        lineOrder = lineNumber
    )
}

enum class SyncState {
    PENDING,
    IN_FLIGHT,
    SYNCED,
    FAILED,
    CONFLICT
}

data class Voucher(
    val voucherId: String,
    val companyId: String,
    val financialYearId: String,
    val voucherNumber: String,
    val voucherType: VoucherType,
    val date: LocalDate,
    val referenceNumber: String = "",
    val narration: String = "",
    val totalAmount: Money = Money.ZERO,
    val items: List<JournalItem> = emptyList(),
    val isPosted: Boolean = true,
    val isCancelled: Boolean = false,
    val syncState: SyncState = SyncState.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String = "SYSTEM_USER",
    val partyGstin: String = "",
    val isGstApplicable: Boolean = false,
    val referenceVoucherId: String? = null,
    val paymentMode: String = ""
) {
    val totalDebits: Money
        get() = items.filter { it.type == DrCr.DEBIT }
            .fold(Money.ZERO) { acc, item -> acc + item.amount }

    val totalCredits: Money
        get() = items.filter { it.type == DrCr.CREDIT }
            .fold(Money.ZERO) { acc, item -> acc + item.amount }

    val isBalanced: Boolean
        get() = items.isNotEmpty() && totalDebits.paise == totalCredits.paise && totalDebits.isPositive

    val imbalanceDifference: Money
        get() = (totalDebits - totalCredits).abs()
}
