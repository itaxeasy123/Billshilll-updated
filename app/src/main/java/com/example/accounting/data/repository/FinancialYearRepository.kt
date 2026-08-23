package com.example.accounting.data.repository

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.AppError
import com.example.accounting.data.local.dao.AccountingDao
import com.example.accounting.data.local.entity.AccountingPeriodEntity
import com.example.accounting.data.local.entity.FinancialYearEntity
import com.example.accounting.domain.financialyear.AccountingPeriod
import com.example.accounting.domain.financialyear.FinancialYear
import com.example.accounting.domain.financialyear.FinancialYearStatus
import com.example.accounting.domain.financialyear.PeriodStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Interface for Financial Year and Accounting Period repository.
 * Scoped strictly by companyId to enforce tenant boundaries.
 */
interface IFinancialYearRepository {
    fun getFinancialYears(companyId: String): Flow<List<FinancialYear>>
    suspend fun getFinancialYear(companyId: String, financialYearId: String): AccountingResult<FinancialYear>
    suspend fun createFinancialYear(companyId: String, financialYear: FinancialYear): AccountingResult<FinancialYear>
    suspend fun lockFinancialYear(companyId: String, financialYearId: String, lockedBy: String): AccountingResult<Unit>
    
    fun getAccountingPeriods(companyId: String, financialYearId: String): Flow<List<AccountingPeriod>>
    suspend fun getAccountingPeriod(companyId: String, periodId: String): AccountingResult<AccountingPeriod>
    suspend fun createAccountingPeriods(companyId: String, periods: List<AccountingPeriod>): AccountingResult<Unit>
    suspend fun setPeriodStatus(companyId: String, periodId: String, status: PeriodStatus, lockedBy: String?): AccountingResult<Unit>
}

class FinancialYearRepository(
    private val dao: AccountingDao
) : IFinancialYearRepository {

    override fun getFinancialYears(companyId: String): Flow<List<FinancialYear>> {
        return dao.getFinancialYearsByCompany(companyId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getFinancialYear(companyId: String, financialYearId: String): AccountingResult<FinancialYear> {
        val entity = dao.getFinancialYearById(companyId, financialYearId)
            ?: return AccountingResult.Failure(AppError.ValidationError("Financial Year '$financialYearId' not found for company '$companyId'"))
        return AccountingResult.Success(entity.toDomain())
    }

    override suspend fun createFinancialYear(companyId: String, financialYear: FinancialYear): AccountingResult<FinancialYear> {
        if (companyId != financialYear.companyId) {
            return AccountingResult.Failure(AppError.TenantMismatch(companyId, financialYear.companyId))
        }
        if (!financialYear.startDate.isBefore(financialYear.endDate)) {
            return AccountingResult.Failure(AppError.ValidationError("Start date must be before end date"))
        }

        return try {
            dao.insertFinancialYear(financialYear.toEntity())
            AccountingResult.Success(financialYear)
        } catch (e: Throwable) {
            AccountingResult.Failure(AppError.DatabaseError("Failed to insert financial year", e))
        }
    }

    override suspend fun lockFinancialYear(companyId: String, financialYearId: String, lockedBy: String): AccountingResult<Unit> {
        return try {
            dao.lockFinancialYear(companyId, financialYearId, lockedBy)
            AccountingResult.Success(Unit)
        } catch (e: Throwable) {
            AccountingResult.Failure(AppError.DatabaseError("Failed to lock financial year", e))
        }
    }

    override fun getAccountingPeriods(companyId: String, financialYearId: String): Flow<List<AccountingPeriod>> {
        return dao.getPeriodsByFinancialYear(companyId, financialYearId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getAccountingPeriod(companyId: String, periodId: String): AccountingResult<AccountingPeriod> {
        val entity = dao.getPeriodById(companyId, periodId)
            ?: return AccountingResult.Failure(AppError.ValidationError("Accounting Period '$periodId' not found for company '$companyId'"))
        return AccountingResult.Success(entity.toDomain())
    }

    override suspend fun createAccountingPeriods(companyId: String, periods: List<AccountingPeriod>): AccountingResult<Unit> {
        for (p in periods) {
            if (p.companyId != companyId) {
                return AccountingResult.Failure(AppError.TenantMismatch(companyId, p.companyId))
            }
        }
        return try {
            dao.insertPeriods(periods.map { it.toEntity() })
            AccountingResult.Success(Unit)
        } catch (e: Throwable) {
            AccountingResult.Failure(AppError.DatabaseError("Failed to insert periods", e))
        }
    }

    override suspend fun setPeriodStatus(
        companyId: String,
        periodId: String,
        status: PeriodStatus,
        lockedBy: String?
    ): AccountingResult<Unit> {
        return try {
            dao.setPeriodStatus(companyId, periodId, status, lockedBy)
            AccountingResult.Success(Unit)
        } catch (e: Throwable) {
            AccountingResult.Failure(AppError.DatabaseError("Failed to set period status", e))
        }
    }

    private fun FinancialYearEntity.toDomain(): FinancialYear {
        return FinancialYear(
            financialYearId = financialYearId,
            companyId = companyId,
            fyCode = fyCode,
            startDate = parseDate(startDate),
            endDate = parseDate(endDate),
            status = if (isLocked) FinancialYearStatus.CLOSED else FinancialYearStatus.OPEN,
            isCurrent = isCurrent,
            isLocked = isLocked,
            lockedAt = lockedAt,
            lockedBy = lockedBy
        )
    }

    private fun FinancialYear.toEntity(): FinancialYearEntity {
        return FinancialYearEntity(
            financialYearId = financialYearId,
            companyId = companyId,
            fyCode = fyCode,
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            isCurrent = isCurrent,
            isLocked = isLocked,
            lockedAt = lockedAt,
            lockedBy = lockedBy
        )
    }

    private fun AccountingPeriodEntity.toDomain(): AccountingPeriod {
        return AccountingPeriod(
            periodId = periodId,
            companyId = companyId,
            financialYearId = financialYearId,
            name = name,
            startDate = parseDate(startDate),
            endDate = parseDate(endDate),
            status = status,
            lockedAt = lockedAt,
            lockedBy = lockedBy
        )
    }

    private fun AccountingPeriod.toEntity(): AccountingPeriodEntity {
        return AccountingPeriodEntity(
            periodId = periodId,
            companyId = companyId,
            financialYearId = financialYearId,
            name = name,
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            status = status,
            lockedAt = lockedAt,
            lockedBy = lockedBy
        )
    }

    private fun parseDate(str: String): LocalDate {
        return try {
            LocalDate.parse(str)
        } catch (e: Exception) {
            LocalDate.now()
        }
    }
}
