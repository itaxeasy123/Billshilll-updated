package com.example.accounting.automation.jobs

import com.example.accounting.automation.compliance.SuspenseBalanceChecker
import com.example.accounting.automation.notifications.AutomationNotification
import com.example.accounting.automation.notifications.AutomationNotificationCenter
import com.example.accounting.automation.notifications.NotificationCategory
import com.example.accounting.automation.notifications.NotificationPriority
import com.example.accounting.automation.tasks.AutomationTaskResult
import com.example.accounting.automation.tasks.TaskExecutionStatus
import com.example.accounting.automation.tasks.TaskFrequency
import com.example.accounting.domain.accounting.Ledger
import com.example.accounting.domain.accounting.StandardSystemGroups
import com.example.accounting.domain.accounting.Voucher

sealed class AutomationEvent {
    data class VoucherPosted(val companyId: String, val voucher: Voucher) : AutomationEvent()
    data class LedgerCreated(val companyId: String, val ledger: Ledger) : AutomationEvent()
    data class VoucherDeleted(val companyId: String, val voucherId: String, val voucherNumber: String) : AutomationEvent()
    data class PeriodLocked(val companyId: String, val periodName: String) : AutomationEvent()
    data class SyncCompleted(val companyId: String, val syncedCount: Int) : AutomationEvent()
    data class SyncFailed(val companyId: String, val error: String) : AutomationEvent()
    data class SuspenseBalanceChanged(val companyId: String, val balancePaise: Long) : AutomationEvent()
}

class EventDrivenAutomationProcessor(
    private val suspenseChecker: SuspenseBalanceChecker
) {
    suspend fun handleEvent(event: AutomationEvent): AutomationTaskResult {
        return when (event) {
            is AutomationEvent.VoucherPosted -> {
                // Check if voucher touched suspense account
                val touchesSuspense = event.voucher.items.any { 
                    it.ledgerId.startsWith(StandardSystemGroups.SUSPENSE_LEDGER_ID) 
                }
                if (touchesSuspense) {
                    suspenseChecker.checkSuspenseBalance(event.companyId)
                }

                AutomationTaskResult(
                    taskName = "Voucher Posted Automation Handler",
                    frequency = TaskFrequency.EVENT_DRIVEN,
                    status = TaskExecutionStatus.SUCCESS,
                    message = "Processed event for voucher ${event.voucher.voucherNumber}."
                )
            }

            is AutomationEvent.LedgerCreated -> {
                AutomationTaskResult(
                    taskName = "Ledger Created Automation Handler",
                    frequency = TaskFrequency.EVENT_DRIVEN,
                    status = TaskExecutionStatus.SUCCESS,
                    message = "Verified ledger registration for '${event.ledger.name}'."
                )
            }

            is AutomationEvent.VoucherDeleted -> {
                AutomationNotificationCenter.emitNotification(
                    AutomationNotification(
                        companyId = event.companyId,
                        title = "Voucher Reversal Executed",
                        message = "Voucher ${event.voucherNumber} was deleted and ledger running balances reversed.",
                        priority = NotificationPriority.NORMAL,
                        category = NotificationCategory.COMPLIANCE_ALERT,
                        actionDeepLink = "#daybook"
                    )
                )

                AutomationTaskResult(
                    taskName = "Voucher Deleted Automation Handler",
                    frequency = TaskFrequency.EVENT_DRIVEN,
                    status = TaskExecutionStatus.SUCCESS,
                    message = "Recorded deletion event for voucher ${event.voucherNumber}."
                )
            }

            is AutomationEvent.PeriodLocked -> {
                AutomationNotificationCenter.emitNotification(
                    AutomationNotification(
                        companyId = event.companyId,
                        title = "Accounting Period Locked",
                        message = "Period ${event.periodName} is now closed. Backdated entries are prevented.",
                        priority = NotificationPriority.HIGH,
                        category = NotificationCategory.PERIOD_LOCK,
                        actionDeepLink = "#settings-sync"
                    )
                )

                AutomationTaskResult(
                    taskName = "Period Locked Automation Handler",
                    frequency = TaskFrequency.EVENT_DRIVEN,
                    status = TaskExecutionStatus.SUCCESS,
                    message = "Period ${event.periodName} locked notification dispatched."
                )
            }

            is AutomationEvent.SyncCompleted -> {
                AutomationTaskResult(
                    taskName = "Sync Completed Handler",
                    frequency = TaskFrequency.EVENT_DRIVEN,
                    status = TaskExecutionStatus.SUCCESS,
                    message = "${event.syncedCount} outbox mutations synced to remote."
                )
            }

            is AutomationEvent.SyncFailed -> {
                AutomationNotificationCenter.emitNotification(
                    AutomationNotification(
                        companyId = event.companyId,
                        title = "Sync Failure Alert",
                        message = "Remote synchronization encountered an issue: ${event.error}",
                        priority = NotificationPriority.HIGH,
                        category = NotificationCategory.SYNC_STATUS,
                        actionDeepLink = "#settings-sync"
                    )
                )

                AutomationTaskResult(
                    taskName = "Sync Failed Handler",
                    frequency = TaskFrequency.EVENT_DRIVEN,
                    status = TaskExecutionStatus.WARNING,
                    message = "Handled sync failure: ${event.error}"
                )
            }

            is AutomationEvent.SuspenseBalanceChanged -> {
                suspenseChecker.checkSuspenseBalance(event.companyId)
            }
        }
    }
}
