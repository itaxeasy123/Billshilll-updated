package com.example.accounting.domain.accounting

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.AppError
import com.example.accounting.core.common.DrCr
import com.example.accounting.core.common.Money
import com.example.accounting.domain.financialyear.AccountingPeriod
import com.example.accounting.domain.financialyear.FinancialYear

object DoubleEntryValidator {

    /**
     * Strictly verifies all foundational double-entry accounting rules:
     * 1. Total debits must strictly equal total credits.
     * 2. Voucher date must fall within the active financial year boundary.
     * 3. Associated period must not be locked.
     * 4. Must contain at least one Debit and one Credit line.
     * 5. No zero-value lines permitted.
     * 6. All ledgers must belong to the active companyId.
     */
    fun validate(
        voucher: Voucher,
        activeFinancialYear: FinancialYear?,
        activePeriod: AccountingPeriod?,
        validLedgerIdsForCompany: Set<String>
    ): AccountingResult<Unit> {
        if (voucher.items.isEmpty()) {
            return AccountingResult.Failure(AppError.ValidationError("Voucher must contain at least one journal item entry."))
        }

        val hasDebit = voucher.items.any { it.type == DrCr.DEBIT && it.amount.isPositive }
        val hasCredit = voucher.items.any { it.type == DrCr.CREDIT && it.amount.isPositive }

        if (!hasDebit || !hasCredit) {
            return AccountingResult.Failure(AppError.ValidationError("Voucher must contain at least one debit line and one credit line with non-zero amounts."))
        }

        val hasZeroAmount = voucher.items.any { it.amount.isZero }
        if (hasZeroAmount) {
            return AccountingResult.Failure(AppError.ValidationError("Journal items cannot have an amount of ₹0.00."))
        }

        val totalDebits = voucher.totalDebits
        val totalCredits = voucher.totalCredits

        if (totalDebits.paise != totalCredits.paise) {
            return AccountingResult.Failure(
                AppError.UnbalancedVoucher(
                    totalDebits = totalDebits,
                    totalCredits = totalCredits,
                    difference = (totalDebits - totalCredits).abs()
                )
            )
        }

        // Financial Year tenant-scoping: the FY object supplied must actually belong to this company
        if (activeFinancialYear != null && activeFinancialYear.companyId != voucher.companyId) {
            return AccountingResult.Failure(
                AppError.TenantMismatch(
                    companyId = voucher.companyId,
                    resourceCompanyId = activeFinancialYear.companyId
                )
            )
        }

        // Financial Year validation
        if (activeFinancialYear != null && !activeFinancialYear.contains(voucher.date)) {
            return AccountingResult.Failure(
                AppError.ValidationError(
                    "Voucher date ${voucher.date} is outside the active Financial Year (${activeFinancialYear.fyCode}: ${activeFinancialYear.startDate} to ${activeFinancialYear.endDate})."
                )
            )
        }

        // Accounting Period tenant-scoping: the period supplied must actually belong to this company
        if (activePeriod != null && activePeriod.companyId != voucher.companyId) {
            return AccountingResult.Failure(
                AppError.TenantMismatch(
                    companyId = voucher.companyId,
                    resourceCompanyId = activePeriod.companyId
                )
            )
        }

        // Accounting Period must belong to the voucher's own Financial Year
        if (activePeriod != null && activePeriod.financialYearId != voucher.financialYearId) {
            return AccountingResult.Failure(
                AppError.ValidationError(
                    "Accounting period '${activePeriod.name}' belongs to financial year '${activePeriod.financialYearId}', not the voucher's financial year '${voucher.financialYearId}'."
                )
            )
        }

        // Accounting Period locking validation
        if (activePeriod != null && !activePeriod.isOpen) {
            return AccountingResult.Failure(
                AppError.PeriodLocked(
                    periodName = activePeriod.name,
                    date = voucher.date.toString()
                )
            )
        }

        // Company boundary validation on ledgers
        for (item in voucher.items) {
            if (!validLedgerIdsForCompany.contains(item.ledgerId)) {
                return AccountingResult.Failure(
                    AppError.TenantMismatch(
                        companyId = voucher.companyId,
                        resourceCompanyId = "Unknown/Foreign Ledger (${item.ledgerId})"
                    )
                )
            }
        }

        return AccountingResult.Success(Unit)
    }
}
