package com.example.accounting.automation.compliance

import com.example.accounting.automation.notifications.AutomationNotification
import com.example.accounting.automation.notifications.AutomationNotificationCenter
import com.example.accounting.automation.notifications.NotificationCategory
import com.example.accounting.automation.notifications.NotificationPriority
import com.example.accounting.automation.tasks.AutomationTaskResult
import com.example.accounting.automation.tasks.TaskExecutionStatus
import com.example.accounting.automation.tasks.TaskFrequency
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.invoice.InvoiceStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Invoice reminder checker (Phase 7F, "A") - the one concrete signal `docs/30_CHANGELOG.md`
 * flagged for this phase since Phase 7A. Strictly read-only/idempotent: it calls the existing,
 * unmodified [AccountingRepository.generateOutstandingReport] (Phase 7C) for every figure -
 * status, due date, days-outstanding are all already-derived via the frozen
 * `InvoiceStatusEngine`/`computeOutstandingPaise` - this checker never recalculates anything
 * itself, and it never mutates an invoice, voucher, or ledger. Running it any number of times for
 * the same day produces the same notifications - it has no side effect beyond emitting them.
 */
class InvoiceReminderChecker(private val repository: AccountingRepository) {

    /** An invoice due within this many days (and not yet overdue) gets a "due soon" reminder,
     * once it's actually overdue it gets the (louder) overdue reminder instead - never both for
     * the same invoice on the same day. */
    private val dueSoonWindowDays = 3L

    suspend fun checkInvoiceReminders(companyId: String, today: LocalDate = LocalDate.now()): AutomationTaskResult {
        val outstanding = repository.generateOutstandingReport(companyId, today = today)

        val overdue = outstanding.rows.filter { it.status == InvoiceStatus.OVERDUE }
        val dueSoon = outstanding.rows.filter {
            it.status != InvoiceStatus.OVERDUE && it.dueDate != null &&
                !it.dueDate.isBefore(today) && ChronoUnit.DAYS.between(today, it.dueDate) <= dueSoonWindowDays
        }

        if (overdue.isNotEmpty()) {
            AutomationNotificationCenter.emitNotification(
                AutomationNotification(
                    companyId = companyId,
                    title = "Overdue Invoices",
                    message = "${overdue.size} invoice(s) are overdue, totalling ${overdue.sumOf { it.outstandingAmount.paise }.let { com.example.accounting.core.common.Money.fromPaise(it).format() }}.",
                    priority = NotificationPriority.HIGH,
                    category = NotificationCategory.INVOICE_REMINDER,
                    actionDeepLink = "#reports/outstanding"
                )
            )
        }
        if (dueSoon.isNotEmpty()) {
            AutomationNotificationCenter.emitNotification(
                AutomationNotification(
                    companyId = companyId,
                    title = "Invoices Due Soon",
                    message = "${dueSoon.size} invoice(s) are due within $dueSoonWindowDays day(s).",
                    priority = NotificationPriority.NORMAL,
                    category = NotificationCategory.INVOICE_REMINDER,
                    actionDeepLink = "#reports/outstanding"
                )
            )
        }

        val status = if (overdue.isNotEmpty()) TaskExecutionStatus.WARNING else TaskExecutionStatus.SUCCESS
        val message = when {
            overdue.isNotEmpty() -> "${overdue.size} overdue, ${dueSoon.size} due within $dueSoonWindowDays day(s)."
            dueSoon.isNotEmpty() -> "No overdue invoices; ${dueSoon.size} due within $dueSoonWindowDays day(s)."
            else -> "No overdue or soon-due invoices."
        }

        return AutomationTaskResult(
            taskName = "Invoice Reminder Check",
            frequency = TaskFrequency.DAILY,
            status = status,
            message = message,
            itemsProcessed = outstanding.rows.size,
            details = mapOf("overdueCount" to overdue.size.toString(), "dueSoonCount" to dueSoon.size.toString())
        )
    }
}
