package com.example.accounting.automation.scheduler.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.accounting.automation.scheduler.SchedulerPort
import java.util.concurrent.TimeUnit

/**
 * WorkManager-backed [SchedulerPort] (Phase 7F, "C") - the only Android-framework-touching piece
 * of the recurring-automation scheduling infrastructure. Enqueues one unique periodic
 * [AutomationCycleWorker] per company; [ExistingPeriodicWorkPolicy.KEEP] makes repeated calls
 * (e.g. on every app start) a no-op if a cycle is already scheduled, so scheduling is itself
 * idempotent - it never stacks up duplicate periodic requests for the same company.
 */
class WorkManagerSchedulerPort(private val context: Context) : SchedulerPort {

    override fun scheduleRecurringAutomation(companyId: String, financialYearId: String?) {
        val inputData = Data.Builder()
            .putString(AutomationCycleWorker.KEY_COMPANY_ID, companyId)
            .putString(AutomationCycleWorker.KEY_FINANCIAL_YEAR_ID, financialYearId)
            .build()

        val request = PeriodicWorkRequestBuilder<AutomationCycleWorker>(24, TimeUnit.HOURS)
            .setInputData(inputData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .addTag(AUTOMATION_WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueWorkName(companyId),
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    override fun cancelRecurringAutomation(companyId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(companyId))
    }

    companion object {
        const val AUTOMATION_WORK_TAG = "ledgerprime_automation_cycle"
        fun uniqueWorkName(companyId: String) = "automation_cycle_$companyId"
    }
}
