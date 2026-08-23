package com.example.accounting.automation.jobs

import com.example.accounting.automation.notifications.AutomationNotification
import com.example.accounting.automation.notifications.AutomationNotificationCenter
import com.example.accounting.automation.notifications.NotificationCategory
import com.example.accounting.automation.notifications.NotificationPriority
import com.example.accounting.automation.tasks.AutomationTask
import com.example.accounting.automation.tasks.AutomationTaskResult
import com.example.accounting.automation.tasks.TaskExecutionStatus
import com.example.accounting.automation.tasks.TaskFrequency
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.recurring.RecurringVoucherGenerationOutcome
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Recurring Voucher **draft** generation task (Phase 7F, "B") - iterates every active schedule
 * for the company and asks [AccountingRepository.generateRecurringVoucherIfDue] to propose a
 * review-only draft for today, if it's due and not already proposed this period. This task itself
 * never builds a Voucher, never touches the DAO's write path directly, and - critically - never
 * posts anything: [AccountingRepository.generateRecurringVoucherIfDue] only ever produces a
 * [RecurringVoucherGenerationOutcome.DraftGenerated] outcome, never a posted voucher.
 * [AccountingRepository.postRecurringVoucherDraft] is a separate function this task never calls,
 * reachable only from an explicit user "Post" action - there is no automated code path from a
 * schedule to a real ledger effect anywhere in this class.
 *
 * Runs MONTHLY (the frequency the user specified - "a monthly task cannot generate the same
 * rent/depreciation voucher twice"). YEARLY schedules are still handled correctly here: since
 * `generateRecurringVoucherIfDue` re-checks `RecurringVoucherPeriod.isDue` against `asOfDate`
 * itself, a YEARLY schedule simply reports [RecurringVoucherGenerationOutcome.NotDue] on the 11
 * months its anniversary date hasn't arrived - no separate yearly runner is needed.
 */
class MonthlyRecurringVoucherGenerationTask(
    private val repository: AccountingRepository
) : AutomationTask {
    override val taskId: String = "MONTHLY_RECURRING_VOUCHER_GENERATION"
    override val name: String = "Recurring Voucher Draft Generation"
    override val frequency: TaskFrequency = TaskFrequency.MONTHLY

    override suspend fun execute(companyId: String, financialYearId: String?): AutomationTaskResult {
        val today = LocalDate.now()
        val schedules = repository.getRecurringVoucherSchedules(companyId).first()

        var drafted = 0
        var failed = 0
        val draftedNames = mutableListOf<String>()
        val failures = mutableListOf<String>()

        for (schedule in schedules) {
            when (val result = repository.generateRecurringVoucherIfDue(companyId, schedule.scheduleId, today)) {
                is AccountingResult.Success -> {
                    val outcome = result.data
                    if (outcome is RecurringVoucherGenerationOutcome.DraftGenerated) {
                        drafted++
                        draftedNames.add("${schedule.name}=${outcome.draftId}")
                    }
                }
                is AccountingResult.Failure -> {
                    failed++
                    failures.add("${schedule.name}: ${result.error.message}")
                }
            }
        }

        if (drafted > 0) {
            AutomationNotificationCenter.emitNotification(
                AutomationNotification(
                    companyId = companyId,
                    title = "Recurring Voucher Drafts Ready for Review",
                    message = "$drafted recurring voucher draft(s) generated for $today - review and post them yourself; nothing has been posted to the ledger yet.",
                    priority = NotificationPriority.NORMAL,
                    category = NotificationCategory.RECURRING_VOUCHER,
                    actionDeepLink = "#automation/recurring-vouchers"
                )
            )
        }

        val status = when {
            failed > 0 -> TaskExecutionStatus.WARNING
            else -> TaskExecutionStatus.SUCCESS
        }
        val message = when {
            failed > 0 -> "$drafted draft(s) generated, $failed failed out of ${schedules.size} active schedule(s)."
            schedules.isEmpty() -> "No active recurring voucher schedules."
            else -> "$drafted draft(s) generated out of ${schedules.size} active schedule(s)."
        }

        return AutomationTaskResult(
            taskName = name,
            frequency = frequency,
            status = status,
            message = message,
            itemsProcessed = schedules.size,
            details = mapOf(
                "draftedCount" to drafted.toString(),
                "failedCount" to failed.toString(),
                "drafted" to draftedNames.joinToString(";"),
                "failures" to failures.joinToString(";")
            )
        )
    }
}
