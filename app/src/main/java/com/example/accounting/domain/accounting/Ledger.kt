package com.example.accounting.domain.accounting

import com.example.accounting.core.common.DrCr
import com.example.accounting.core.common.Money

data class Ledger(
    val ledgerId: String,
    val companyId: String,
    val groupId: String,
    val groupName: String = "",
    val primaryGroup: PrimaryGroup = PrimaryGroup.ASSETS,
    val name: String,
    val code: String = "",
    val openingBalance: Money = Money.ZERO,
    val openingBalanceType: DrCr = DrCr.DEBIT,
    val currentBalance: Money = Money.ZERO,
    val currentBalanceType: DrCr = DrCr.DEBIT,
    val gstin: String = "",
    val pan: String = "",
    val stateCode: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val bankAccountNumber: String = "",
    val bankIfsc: String = "",
    val isSystem: Boolean = false,
    val isActive: Boolean = true,
    val hsnSacCode: String = "",
    val defaultTaxRate: Double = 0.0
) {
    /**
     * Calculates signed balance: Positive if natural balance matches current type, negative if opposite.
     */
    fun signedBalancePaise(): Long {
        val sign = if (currentBalanceType == primaryGroup.naturalBalance) 1L else -1L
        return currentBalance.paise * sign
    }
}
