package com.example.accounting.automation.compliance

import com.example.accounting.automation.notifications.AutomationNotification
import com.example.accounting.automation.notifications.AutomationNotificationCenter
import com.example.accounting.automation.notifications.NotificationCategory
import com.example.accounting.automation.notifications.NotificationPriority
import com.example.accounting.automation.tasks.AutomationTaskResult
import com.example.accounting.automation.tasks.TaskExecutionStatus
import com.example.accounting.automation.tasks.TaskFrequency
import com.example.accounting.core.common.Money
import com.example.accounting.data.local.dao.AccountingDao
import com.example.accounting.domain.accounting.StandardSystemGroups
import com.example.accounting.domain.financialyear.PeriodStatus
import com.example.accounting.domain.reports.TrialBalanceReport
import com.example.accounting.domain.taxation.gst.GstLedgerIds
import kotlinx.coroutines.flow.first

class SuspenseBalanceChecker(private val dao: AccountingDao) {

    suspend fun checkSuspenseBalance(companyId: String): AutomationTaskResult {
        val suspenseLedgerId = "${StandardSystemGroups.SUSPENSE_LEDGER_ID}_$companyId"
        val suspense = dao.getLedgerById(companyId, suspenseLedgerId)

        return if (suspense == null) {
            AutomationTaskResult(
                taskName = "Check Suspense Account Balance",
                frequency = TaskFrequency.DAILY,
                status = TaskExecutionStatus.WARNING,
                message = "Suspense ledger account not found for company $companyId."
            )
        } else if (suspense.currentBalancePaise != 0L) {
            val balance = Money(suspense.currentBalancePaise)
            val notification = AutomationNotification(
                companyId = companyId,
                title = "Suspense A/c Non-Zero Balance Warning",
                message = "Suspense Account has an uncleared balance of ${balance.format()} ${suspense.currentBalanceType}. Reconcile pending vouchers.",
                priority = NotificationPriority.HIGH,
                category = NotificationCategory.SUSPENSE_WARNING,
                actionDeepLink = "#statement/$suspenseLedgerId"
            )
            AutomationNotificationCenter.emitNotification(notification)

            AutomationTaskResult(
                taskName = "Check Suspense Account Balance",
                frequency = TaskFrequency.DAILY,
                status = TaskExecutionStatus.WARNING,
                message = "Suspense balance is non-zero (${balance.format()} ${suspense.currentBalanceType}). Reconcile immediately.",
                itemsProcessed = 1,
                details = mapOf("balance" to balance.format(), "type" to suspense.currentBalanceType.name)
            )
        } else {
            AutomationTaskResult(
                taskName = "Check Suspense Account Balance",
                frequency = TaskFrequency.DAILY,
                status = TaskExecutionStatus.SUCCESS,
                message = "Suspense account balance is zero (₹0.00). Ledger is clear.",
                itemsProcessed = 1
            )
        }
    }
}

class GstComplianceChecker(private val dao: AccountingDao) {

    suspend fun checkGstPreparation(companyId: String, financialYearId: String?): AutomationTaskResult {
        val ledgers = dao.getLedgersByCompany(companyId).first()
        // Exact groupId-prefix / exact ledgerId match (Phase 5 final audit finding) - was
        // `groupId.contains("DEBTORS"/"CREDITORS")` and `name.contains("GST", ...)`, the same
        // name-matching anti-pattern fixed everywhere else in the app.
        val debtorsAndCreditors = ledgers.filter {
            it.groupId.startsWith(StandardSystemGroups.DEBTORS_GROUP_ID) || it.groupId.startsWith(StandardSystemGroups.CREDITORS_GROUP_ID)
        }

        var missingGstinCount = 0
        debtorsAndCreditors.forEach { ledger ->
            if (ledger.gstin.isBlank()) {
                missingGstinCount++
            }
        }

        val gstLedgers = ledgers.filter { ledger -> GstLedgerIds.ALL_BARE_IDS.any { ledger.ledgerId == "${it}_$companyId" } }
        val gstDetails = gstLedgers.associate { it.name to Money(it.currentBalancePaise).format() }

        val status = if (missingGstinCount > 0) TaskExecutionStatus.WARNING else TaskExecutionStatus.SUCCESS
        val message = if (missingGstinCount > 0) {
            "GST Readiness Check: $missingGstinCount party ledgers do not have GSTIN configured."
        } else {
            "GST Readiness Check: All party ledgers compliant. GST balances computed."
        }

        if (missingGstinCount > 0) {
            AutomationNotificationCenter.emitNotification(
                AutomationNotification(
                    companyId = companyId,
                    title = "Monthly GST Preparation Notice",
                    message = "$missingGstinCount trading parties lack GSTIN for GSTR filing.",
                    priority = NotificationPriority.NORMAL,
                    category = NotificationCategory.COMPLIANCE_ALERT,
                    actionDeepLink = "#chart-of-accounts"
                )
            )
        }

        return AutomationTaskResult(
            taskName = "Monthly GST Preparation Check",
            frequency = TaskFrequency.MONTHLY,
            status = status,
            message = message,
            itemsProcessed = debtorsAndCreditors.size,
            details = gstDetails
        )
    }
}

class YearEndValidator(private val dao: AccountingDao) {

    suspend fun validateYearEndReadiness(companyId: String, financialYearId: String): AutomationTaskResult {
        val periods = dao.getPeriodsByFinancialYear(financialYearId).first()
        val allPeriodsClosed = periods.all { it.status == PeriodStatus.LOCKED || it.status == PeriodStatus.AUDIT_LOCKED }
        val pendingOutboxCount = dao.countPendingOutbox(companyId)

        val unpostedCount = dao.getVouchersByFinancialYear(companyId, financialYearId).first().count { !it.isPosted || it.isCancelled }

        val warnings = mutableListOf<String>()
        if (!allPeriodsClosed) {
            warnings.add("Not all 12 accounting periods are locked.")
        }
        if (pendingOutboxCount > 0) {
            warnings.add("$pendingOutboxCount pending outbox sync items must be cleared.")
        }
        if (unpostedCount > 0) {
            warnings.add("$unpostedCount unposted/cancelled vouchers detected.")
        }

        val status = if (warnings.isEmpty()) TaskExecutionStatus.SUCCESS else TaskExecutionStatus.WARNING
        val msg = if (warnings.isEmpty()) {
            "Year-End Validation: Financial year $financialYearId is fully verified and eligible for closing."
        } else {
            "Year-End Validation: ${warnings.joinToString("; ")}"
        }

        return AutomationTaskResult(
            taskName = "Year-End Validation & Closing Check",
            frequency = TaskFrequency.YEARLY,
            status = status,
            message = msg,
            itemsProcessed = periods.size,
            details = mapOf("openPeriods" to periods.count { it.status == PeriodStatus.OPEN }.toString(), "pendingOutbox" to pendingOutboxCount.toString())
        )
    }
}
