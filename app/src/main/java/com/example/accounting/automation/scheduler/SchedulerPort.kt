package com.example.accounting.automation.scheduler

import java.time.LocalDate

/**
 * Real Scheduling Infrastructure (Phase 7F, "C") - the abstraction that decides *whether* the
 * existing [AccountingScheduler]'s daily/monthly/yearly job runners actually get invoked. Before
 * this, [AccountingScheduler] existed but nothing in the app ever called `runDailyJobs`/
 * `runMonthlyJobs`/`runYearlyJobs` outside of manual/test invocation - this closes that gap with
 * OS-level periodic scheduling (see [com.example.accounting.automation.scheduler.work.WorkManagerSchedulerPort]).
 *
 * This interface itself is Android-framework-independent so it stays unit-testable; only its
 * concrete WorkManager-backed implementation touches Android APIs.
 */
interface SchedulerPort {
    fun scheduleRecurringAutomation(companyId: String, financialYearId: String?)
    fun cancelRecurringAutomation(companyId: String)
}

/**
 * Pure decision function for "which job cycles are due right now" - the deterministic core of
 * "C". WorkManager's periodic APIs only support fixed millisecond intervals, which cannot express
 * "once per calendar month" or "once per calendar year" (months/years have different lengths), so
 * production runs one recurring cycle (roughly once a day) and asks [decide] which of
 * Daily/Monthly/Yearly are actually due today given when each last ran.
 *
 * Same inputs -> same output, always: this is what makes the runner deterministic. Two cycle
 * firings on the same calendar day never re-run Monthly/Yearly twice, and a firing that arrives
 * late (e.g. after the device was off) still runs everything that's overdue exactly once, not
 * once per missed day.
 */
object AutomationRunGate {
    data class Decision(
        val runDaily: Boolean,
        val runMonthly: Boolean,
        val runYearly: Boolean
    )

    fun decide(
        today: LocalDate,
        lastDailyRun: LocalDate?,
        lastMonthlyRun: LocalDate?,
        lastYearlyRun: LocalDate?
    ): Decision = Decision(
        runDaily = lastDailyRun == null || lastDailyRun.isBefore(today),
        runMonthly = lastMonthlyRun == null ||
            lastMonthlyRun.year != today.year || lastMonthlyRun.monthValue != today.monthValue,
        runYearly = lastYearlyRun == null || lastYearlyRun.year != today.year
    )
}
