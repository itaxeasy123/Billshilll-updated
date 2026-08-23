package com.example.accounting.automation.jobs

import com.example.accounting.automation.compliance.GstComplianceChecker
import com.example.accounting.automation.reports.ScheduledReportTasks
import com.example.accounting.automation.tasks.AutomationTask
import com.example.accounting.automation.tasks.AutomationTaskResult
import com.example.accounting.automation.tasks.TaskFrequency

class MonthlyFinancialSummaryTask(
    private val reportTasks: ScheduledReportTasks
) : AutomationTask {
    override val taskId: String = "MONTHLY_FINANCIAL_SUMMARY"
    override val name: String = "Monthly Financial Summary & Outstanding Position"
    override val frequency: TaskFrequency = TaskFrequency.MONTHLY

    override suspend fun execute(companyId: String, financialYearId: String?): AutomationTaskResult {
        return reportTasks.generateMonthlyOutstandingReport(companyId)
    }
}

class MonthlyGstPreparationTask(
    private val gstChecker: GstComplianceChecker
) : AutomationTask {
    override val taskId: String = "MONTHLY_GST_PREP"
    override val name: String = "Monthly GST Preparation & Return Validation"
    override val frequency: TaskFrequency = TaskFrequency.MONTHLY

    override suspend fun execute(companyId: String, financialYearId: String?): AutomationTaskResult {
        return gstChecker.checkGstPreparation(companyId, financialYearId)
    }
}
