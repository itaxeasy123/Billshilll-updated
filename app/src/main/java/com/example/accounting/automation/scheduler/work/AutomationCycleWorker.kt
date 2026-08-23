package com.example.accounting.automation.scheduler.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.accounting.automation.scheduler.AccountingScheduler
import com.example.accounting.automation.scheduler.AutomationRunGate
import com.example.accounting.core.database.AppDatabase
import com.example.accounting.core.sync.OutboxProcessor
import com.example.accounting.data.repository.AccountingRepository
import java.time.LocalDate

/**
 * WorkManager entry point for real scheduling infrastructure (Phase 7F, "C"). Fires roughly once
 * a day (see [WorkManagerSchedulerPort]) and uses [AutomationRunGate] - a pure function - to
 * decide which of [AccountingScheduler]'s Daily/Monthly/Yearly job runners are actually due,
 * based on per-cycle last-run dates persisted in SharedPreferences. This worker itself does no
 * decision-making of its own; it only reads the gate's decision and calls the existing,
 * unmodified `AccountingScheduler.runDailyJobs`/`runMonthlyJobs`/`runYearlyJobs` - there is no
 * second automation-execution path here, only a trigger.
 *
 * Rebuilds [AccountingRepository]/[AccountingScheduler] from [AppDatabase.getInstance] rather
 * than receiving them via injection, matching how [com.example.accounting.presentation.viewmodel.AccountingViewModel]
 * already constructs the same composition root from an Android [Context] - this app has no DI
 * framework, so both entry points independently follow the same Context-based construction
 * pattern rather than introducing a first one just for this worker.
 */
class AutomationCycleWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val companyId = inputData.getString(KEY_COMPANY_ID) ?: return Result.failure()
        val financialYearId = inputData.getString(KEY_FINANCIAL_YEAR_ID)

        val db = AppDatabase.getInstance(applicationContext)
        val repository = AccountingRepository(dao = db.accountingDao(), db = db)
        val outboxProcessor = OutboxProcessor(applicationContext, db.accountingDao())
        val scheduler = AccountingScheduler(db.accountingDao(), repository, outboxProcessor)

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = LocalDate.now()
        val lastDaily = prefs.getString(prefKey(companyId, CYCLE_DAILY), null)?.let { safeParse(it) }
        val lastMonthly = prefs.getString(prefKey(companyId, CYCLE_MONTHLY), null)?.let { safeParse(it) }
        val lastYearly = prefs.getString(prefKey(companyId, CYCLE_YEARLY), null)?.let { safeParse(it) }

        val decision = AutomationRunGate.decide(today, lastDaily, lastMonthly, lastYearly)

        return try {
            if (decision.runDaily) {
                scheduler.runDailyJobs(companyId, financialYearId)
                prefs.edit().putString(prefKey(companyId, CYCLE_DAILY), today.toString()).apply()
            }
            if (decision.runMonthly) {
                scheduler.runMonthlyJobs(companyId, financialYearId)
                prefs.edit().putString(prefKey(companyId, CYCLE_MONTHLY), today.toString()).apply()
            }
            if (decision.runYearly) {
                scheduler.runYearlyJobs(companyId, financialYearId)
                prefs.edit().putString(prefKey(companyId, CYCLE_YEARLY), today.toString()).apply()
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun safeParse(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (e: Exception) {
        null
    }

    companion object {
        const val KEY_COMPANY_ID = "companyId"
        const val KEY_FINANCIAL_YEAR_ID = "financialYearId"
        private const val PREFS_NAME = "ledgerprime_automation_cycle_prefs"
        private const val CYCLE_DAILY = "daily"
        private const val CYCLE_MONTHLY = "monthly"
        private const val CYCLE_YEARLY = "yearly"

        private fun prefKey(companyId: String, cycle: String) = "${companyId}_${cycle}_lastRun"
    }
}
