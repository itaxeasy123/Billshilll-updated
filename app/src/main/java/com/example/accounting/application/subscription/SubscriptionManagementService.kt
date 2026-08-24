package com.example.accounting.application.subscription

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.AppError
import com.example.accounting.data.local.dao.AccountingDao
import com.example.accounting.data.local.entity.CompanySubscriptionEntity
import com.example.accounting.domain.subscription.CompanySubscription
import com.example.accounting.domain.subscription.EntitlementFeature
import com.example.accounting.domain.subscription.SubscriptionEntitlementChecker
import com.example.accounting.domain.subscription.SubscriptionPlanType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID

/**
 * Real persistence + orchestration for [CompanySubscription] (Phase 7J-B) - the domain model and
 * [SubscriptionEntitlementChecker] (Phase 7J) are both frozen and reused verbatim; this class adds
 * only the new `company_subscriptions` table (via [dao] directly, this subsystem's own
 * self-contained table, mirroring how [com.example.accounting.application.voucher.VoucherManagementServiceImpl]
 * owns `voucher_drafts`) plus the FY-resolution orchestration around it. **Paid validity is always
 * derived from the referenced [com.example.accounting.domain.financialyear.FinancialYear]'s own
 * stored `startDate`/`endDate` - never a hardcoded "1 Apr-31 Mar" literal anywhere in this file** -
 * `company_subscriptions` itself stores no date column at all, only `financialYearId`, matching
 * every other FY-scoped concept in this codebase.
 */
class SubscriptionManagementService(private val dao: AccountingDao) {

    suspend fun createOrRenew(
        companyId: String,
        financialYearId: String,
        planType: SubscriptionPlanType,
        planName: String,
        entitlements: Set<EntitlementFeature>
    ): AccountingResult<CompanySubscription> {
        val fy = dao.getFinancialYearById(financialYearId)
            ?: return AccountingResult.Failure(AppError.ValidationError("Financial year '$financialYearId' was not found."))
        if (fy.companyId != companyId) {
            return AccountingResult.Failure(AppError.TenantMismatch(companyId, fy.companyId))
        }

        val existing = dao.getSubscriptionForCompanyAndFy(companyId, financialYearId)
        val now = System.currentTimeMillis()
        val entity = CompanySubscriptionEntity(
            subscriptionId = existing?.subscriptionId ?: "SUB_${UUID.randomUUID().toString().take(8)}_$companyId",
            companyId = companyId,
            financialYearId = financialYearId,
            planType = planType,
            planName = planName,
            entitlementsCsv = entitlements.joinToString(",") { it.name },
            isActive = true,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        if (existing != null) dao.updateSubscription(entity) else dao.insertSubscription(entity)
        return AccountingResult.Success(entity.toDomain())
    }

    suspend fun getCurrent(companyId: String, financialYearId: String): CompanySubscription? =
        dao.getSubscriptionForCompanyAndFy(companyId, financialYearId)?.toDomain()

    /**
     * Whether [feature] is available right now for [companyId] under [financialYearId]. Resolves
     * the referenced FinancialYear and reuses its existing, frozen `contains`-style date check
     * (reimplemented here as a plain boolean expression, identical in shape to
     * [com.example.accounting.domain.financialyear.FinancialYear.contains], since this class only
     * has the raw entity's string dates, not the domain object itself) plus the existing, frozen
     * [SubscriptionEntitlementChecker.hasEntitlement] - never a second entitlement rule.
     */
    suspend fun checkEntitlement(companyId: String, financialYearId: String, feature: EntitlementFeature): Boolean {
        val subscriptionEntity = dao.getSubscriptionForCompanyAndFy(companyId, financialYearId) ?: return false
        val fyEntity = dao.getFinancialYearById(financialYearId) ?: return false
        val fyStart = parseDate(fyEntity.startDate) ?: return false
        val fyEnd = parseDate(fyEntity.endDate) ?: return false
        val today = LocalDate.now()
        val fyIsCurrentlyActive = !today.isBefore(fyStart) && !today.isAfter(fyEnd)
        if (!fyIsCurrentlyActive) return false
        return SubscriptionEntitlementChecker.hasEntitlement(subscriptionEntity.toDomain(), feature)
    }

    suspend fun deactivate(companyId: String, subscriptionId: String): AccountingResult<Unit> {
        val existing = dao.getSubscriptionsForCompany(companyId).first().firstOrNull { it.subscriptionId == subscriptionId }
            ?: return AccountingResult.Failure(AppError.ResourceNotFound("CompanySubscription", subscriptionId))
        dao.updateSubscription(existing.copy(isActive = false, updatedAt = System.currentTimeMillis()))
        return AccountingResult.Success(Unit)
    }

    private fun parseDate(value: String): LocalDate? = try { LocalDate.parse(value) } catch (e: Exception) { null }

    private fun CompanySubscriptionEntity.toDomain(): CompanySubscription = CompanySubscription(
        subscriptionId = subscriptionId,
        companyId = companyId,
        financialYearId = financialYearId,
        planType = planType,
        planName = planName,
        entitlements = entitlementsCsv.split(",").mapNotNull { name -> runCatching { EntitlementFeature.valueOf(name) }.getOrNull() }.toSet(),
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
