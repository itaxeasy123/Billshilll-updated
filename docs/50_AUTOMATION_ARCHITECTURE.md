# 50. Automation Architecture (Phase 7F)

## Status

Phase 7F of the amended Phase 7 scope, Android-only (`SchedulerPort`, `VoucherPostingEngine`,
`DoubleEntryValidator` are Kotlin/Android concepts - the user's own Phase 7F scope prompt never
mentions Python). Builds exactly A (Invoice & Compliance Reminders), B (Recurring Voucher Engine),
and C (Real Scheduling Infrastructure) on top of the pre-existing `automation/` package
(compliance checkers, task interface, notification bus, scheduler composition root, job runners) -
extended additively, never rebuilt. D (Auto-Categorization) is explicitly deferred. **Zero UI** -
no file under `app/src/main/java/com/example/accounting/presentation/` was touched except the
`AccountingViewModel` composition root wiring described in "C" below.

## The two safety decisions (Option X, both)

**1. Financial-year closing is MANUAL ONLY.** No scheduled task may call
`AccountingRepository.closeFinancialYear` - ever. The pre-existing `YearlyOpeningBalanceGenerationTask`
did call it directly (`closeFinancialYear(companyId, financialYearId, "AUTOMATION_ENGINE")`), found
during the pre-implementation structural audit. It has been removed entirely and replaced with
`YearlyClosingReminderTask` (`automation/jobs/YearlyJobs.kt`), which takes only an `AccountingDao` -
not an `AccountingRepository` - so it has no reference to anything that exposes `closeFinancialYear`
at all. When every accounting period is locked (the same readiness condition `closeFinancialYear`
itself checks) it emits a `YEAR_END_ALERT` notification prompting the user to close the year
themselves; it never closes it.

```
Automation  ---X--->  closeFinancialYear()

User -> Authenticated action -> Validation -> Explicit confirmation -> Close Financial Year
```

**2. Bank reconciliation is QUARANTINED.** No bank-feed-import or auto-match capability exists
anywhere in this codebase, before or after this phase. `DailyUnreconciledCheckTask`
(`automation/jobs/DailyJobs.kt`) only surfaces a read-only, human-facing *suggestion* - a
notification that certain posted Payment/Receipt/Contra vouchers have no reference number, which
would make them harder to match during a later, human-performed reconciliation. It never imports a
bank feed, never matches anything automatically, and never creates a reconciliation or accounting
entry. (A prior version of this same class had the correct name and doc comment but actually read
unrelated outbox-sync items - a mislabeling bug found during the same audit and fixed here; not a
behavior change to anything that previously worked.)

```
Bank Data -> Imported Transaction -> Suggested Match -> Human Review -> Existing Posting Engine

Never: Bank Feed -> Auto-match -> Direct Ledger Posting
```

## A: Invoice & Compliance Reminders

`InvoiceReminderChecker` (`automation/compliance/InvoiceReminders.kt`) calls the existing,
unmodified `AccountingRepository.generateOutstandingReport` (Phase 7C) and classifies each row as
overdue (`InvoiceStatus.OVERDUE`, already derived by the frozen `InvoiceStatusEngine`) or due
within 3 days. It never recalculates a due date, an outstanding amount, or a status - every figure
comes from the existing report. `DailyInvoiceReminderTask` wires it into the daily cycle.

## B: Recurring Voucher Engine (draft-first)

A schedule (`domain/recurring/RecurringVoucherSchedule.kt`, tables `recurring_voucher_schedules` /
`recurring_voucher_lines`, migration 7->8) is a **template only** - it never posts anything itself,
and neither does generation. **Automation may prepare a proposed voucher; only a human may post
it.** This was corrected mid-phase from an earlier auto-posting design after the user identified
that LedgerPrime's target users may not understand Dr/Cr well enough to trust an unattended system
to post on their behalf - the system should do the repetitive preparation, but the user must retain
the final accounting decision and the chance to correct a mistake before anything touches the
books.

```
Recurring Schedule -> Scheduler -> Generate Draft Voucher -> User Review/Edit/Discard/Post
                                                                              |
                                              Discard --------> no accounting effect
                                                                              |
                                              Post -> DoubleEntryValidator -> Existing VoucherPostingEngine -> Journal/Ledger
```

`AccountingRepository.generateRecurringVoucherIfDue` is the sole path from a due schedule to a
`RecurringVoucherDraft` (tables `recurring_voucher_drafts` / `recurring_voucher_draft_lines`). A
draft is **not** a `Voucher` and is never written to `vouchers`/`journal_items` - it has **no
journal effect, no ledger effect, no balance effect, no GST effect, no inventory movement**. This
function never calls `postVoucher`; there is no automated code path from a schedule to a posted
voucher anywhere in this codebase.

`AccountingRepository.postRecurringVoucherDraft` is the **only** function that turns a draft into
an actual voucher, and it is called exclusively in direct response to an explicit user "Post"
action (never by automation, never by any scheduler/worker). It builds a plain `Voucher` from the
draft's (possibly user-edited) lines and calls the existing, unmodified `postVoucher` - the exact
same `DoubleEntryValidator`/period-lock/atomic-transaction path every other voucher already goes
through. There is no second posting mechanism. Two companion functions complete the review
lifecycle: `updateRecurringVoucherDraft` (edit date/narration/lines while still pending) and
`discardRecurringVoucherDraft` (reject it) - both reject any draft that is no longer
`PENDING_REVIEW`, since `POSTED`/`DISCARDED` are terminal states.

**Idempotency.** Before ever inserting a draft, `generateRecurringVoucherIfDue` checks
`recurring_voucher_drafts` for a row matching `(scheduleId, periodKey)` -
`RecurringVoucherPeriod.periodKeyFor` computes `"2026-06"` for MONTHLY, `"2026"` for YEARLY, so
"which period does this date belong to" has one single source of truth. That table carries a
**unique composite index on `(scheduleId, periodKey)`**, so even a race between two overlapping
automation runs cannot double-insert a draft; the repository-level check is the primary guard, the
unique index is defense in depth. Because draft rows are never deleted (only status-transitioned to
`POSTED`/`DISCARDED`), a period the user has already decided on - posted *or* discarded - is never
re-proposed either. A generation attempt returns one of three outcomes - `DraftGenerated`,
`AlreadyGenerated`, or `NotDue` - never an exception for the two entirely routine "nothing happened"
cases. `MonthlyRecurringVoucherGenerationTask` (`automation/jobs/RecurringVoucherTask.kt`) iterates
every active schedule and calls this function once per company per monthly cycle, generating drafts
only, never posting; a YEARLY schedule simply reports `NotDue` on the 11 months its anniversary
hasn't arrived, so no separate yearly runner is needed.

## C: Real Scheduling Infrastructure

Before this phase, `AccountingScheduler`'s daily/monthly/yearly job runners existed but nothing in
the app ever called them outside of manual/test invocation. `SchedulerPort`
(`automation/scheduler/SchedulerPort.kt`) is the abstraction that closes that gap; its only
production implementation, `WorkManagerSchedulerPort`
(`automation/scheduler/work/WorkManagerSchedulerPort.kt`), enqueues one unique
`PeriodicWorkRequest` per company (`ExistingPeriodicWorkPolicy.KEEP`, so repeated scheduling calls
are a no-op) running roughly once every 24 hours.

**Deterministic runner.** WorkManager's periodic APIs only support fixed millisecond intervals,
which cannot express "once per calendar month" or "once per calendar year" (months/years have
different lengths). `AutomationRunGate.decide` (pure, Android-independent, unit-tested) is the
single decision function: given today's date and each cycle's last-run date, it returns which of
Daily/Monthly/Yearly are actually due. Same inputs -> same output, always - two firings on the same
day never re-run Monthly/Yearly twice, and a firing that arrives late (device was off) still runs
everything overdue exactly once. `AutomationCycleWorker` (a `CoroutineWorker`) is the thin adapter
that reads the gate's decision, persists each cycle's last-run date in `SharedPreferences`, and
calls the existing, unmodified `AccountingScheduler.runDailyJobs`/`runMonthlyJobs`/`runYearlyJobs` -
there is no second automation-execution path, only a trigger.

Scheduling is triggered from `AccountingViewModel.observeFinancialYearData` - the same place the
app already establishes its active company + financial year - via
`schedulerPort.scheduleRecurringAutomation(companyId, fyId)`. The worker rebuilds
`AccountingRepository`/`AccountingScheduler` from `AppDatabase.getInstance(applicationContext)`
inside `doWork()`, matching how `AccountingViewModel` already constructs the same composition root
from a `Context` - this app has no DI framework, so both entry points independently follow the same
Context-based construction pattern rather than introducing a first one just for this worker.

## What Phase 7F does NOT include

No UI, no automation settings screen, no AI/auto-categorization (Item D, deferred to its own
controlled phase), no automatic FY closing, no automatic bank reconciliation, no direct ledger
mutation from the scheduler, **no automatic posting of a recurring voucher** (generation only ever
produces a review-only draft - posting requires an explicit user action), no second posting engine,
no second synchronization engine. This gives a foundation for Phase 7H (Government/Tax API
integration) afterward.

## Testing

`Phase7FTestSuite.kt` (pure JVM) covers `RecurringVoucherPeriod`'s period-key/due-date logic
(including day-of-month clamping for short months and YEARLY anniversary logic), `AutomationRunGate`'s
decision matrix, both safety fixes (`YearlyClosingReminderTask` structurally cannot depend on
`AccountingRepository`; `DailyUnreconciledCheckTask` correctly flags only Payment/Receipt/Contra
vouchers missing a reference number), `InvoiceReminderChecker`'s overdue/due-soon classification,
and every `generateRecurringVoucherIfDue`/`updateRecurringVoucherDraft`/`discardRecurringVoucherDraft`
outcome reachable without a real Room database (`ResourceNotFound`/`NotDue`/`AlreadyGenerated`/
`DraftGenerated`-with-zero-ledger-effect/empty-lines validation/edit-or-discard-rejected-once-
terminal), including an explicit assertion that a freshly generated draft leaves
`getVouchersByCompany` empty.

`Phase7FRecurringVoucherPostingTest.kt` (Robolectric) covers the behaviors that need a real Room
`AppDatabase`: draft generation still has zero ledger effect even against a real database;
`postRecurringVoucherDraft` posts through the real engine path (ledger balance updated) and is
rejected on a second call against the same now-`POSTED` draft; and `discardRecurringVoucherDraft`
leaves the ledger untouched and makes the draft permanently unpostable. Like the pre-existing
`SuspenseControlArchitectureTest`, this suite is currently blocked by this environment's Robolectric
SDK infrastructure (`DefaultSdkProvider.UnsupportedOperationException` at `classMethod`, before any
test body runs) - not by anything in the code under test; it should be re-verified the next time
Robolectric is functional here.
