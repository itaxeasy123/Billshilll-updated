package com.example.accounting.automation.jobs

import com.example.accounting.automation.compliance.YearEndValidator
import com.example.accounting.automation.notifications.AutomationNotification
import com.example.accounting.automation.notifications.AutomationNotificationCenter
import com.example.accounting.automation.notifications.NotificationCategory
import com.example.accounting.automation.notifications.NotificationPriority
import com.example.accounting.automation.tasks.AutomationTask
import com.example.accounting.automation.tasks.AutomationTaskResult
import com.example.accounting.automation.tasks.TaskExecutionStatus
import com.example.accounting.automation.tasks.TaskFrequency
import com.example.accounting.data.local.dao.AccountingDao
import com.example.accounting.domain.financialyear.PeriodStatus
import kotlinx.coroutines.flow.first

class YearlyClosingCheckTask(
    private val yearEndValidator: YearEndValidator
) : AutomationTask {
    override val taskId: String = "YEARLY_CLOSING_CHECK"
    override val name: String = "Financial Year Closing Readiness Check"
    override val frequency: TaskFrequency = TaskFrequency.YEARLY

    override suspend fun execute(companyId: String, financialYearId: String?): AutomationTaskResult {
        if (financialYearId == null) {
            return AutomationTaskResult(
                taskName = name,
                frequency = frequency,
                status = TaskExecutionStatus.SKIPPED,
                message = "No active financial year specified for closing verification."
            )
        }
        return yearEndValidator.validateYearEndReadiness(companyId, financialYearId)
    }
}

/**
 * Financial-year closing is a consequential accounting operation (Phase 7F safety decision,
 * Option X) and must always require explicit, authenticated human action -
 * `AccountingRepository.closeFinancialYear` is NEVER called from automation, by this task or any
 * other. This task is strictly read-only: when every accounting period is already locked (the
 * same readiness condition `closeFinancialYear` itself requires), it emits a reminder notification
 * prompting the user to close the year themselves; it never closes it automatically.
 *
 * (Renamed and rewritten from a prior `YearlyOpeningBalanceGenerationTask`, which called
 * `repository.closeFinancialYear(..., "AUTOMATION_ENGINE")` directly - a real violation of this
 * safety rule found during the Phase 7F structural audit. Fixed here, not carried forward.)
 */
class YearlyClosingReminderTask(
    private val dao: AccountingDao
) : AutomationTask {
    override val taskId: String = "YEARLY_CLOSING_REMINDER"
    override val name: String = "Financial Year Closing Reminder"
    override val frequency: TaskFrequency = TaskFrequency.YEARLY

    override suspend fun execute(companyId: String, financialYearId: String?): AutomationTaskResult {
        if (financialYearId == null) {
            return AutomationTaskResult(
                taskName = name,
                frequency = frequency,
                status = TaskExecutionStatus.SKIPPED,
                message = "No financial year provided for the closing reminder check."
            )
        }

        val periods = dao.getPeriodsByFinancialYear(financialYearId).first()
        val allPeriodsLocked = periods.isNotEmpty() && periods.all { it.status == PeriodStatus.LOCKED || it.status == PeriodStatus.AUDIT_LOCKED }

        if (allPeriodsLocked) {
            AutomationNotificationCenter.emitNotification(
                AutomationNotification(
                    companyId = companyId,
                    title = "Financial Year Ready to Close",
                    message = "All accounting periods for $financialYearId are locked. Review and close the year when ready - this always requires your explicit confirmation.",
                    priority = NotificationPriority.NORMAL,
                    category = NotificationCategory.YEAR_END_ALERT,
                    actionDeepLink = "#financial-year/$financialYearId"
                )
            )
            return AutomationTaskResult(
                taskName = name,
                frequency = frequency,
                status = TaskExecutionStatus.SUCCESS,
                message = "Financial year $financialYearId is ready to close - a human must perform this action explicitly.",
                itemsProcessed = periods.size
            )
        }

        return AutomationTaskResult(
            taskName = name,
            frequency = frequency,
            status = TaskExecutionStatus.SUCCESS,
            message = "Financial year $financialYearId is not yet ready to close (not all periods are locked).",
            itemsProcessed = periods.size
        )
    }
}
