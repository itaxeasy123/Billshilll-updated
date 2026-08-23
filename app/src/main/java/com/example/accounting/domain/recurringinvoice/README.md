# Recurring Invoice Architecture (Phase 7J)

## Status: architecture and models only

`RecurringInvoiceSchedule.kt` - `RecurringInvoiceSchedule` (template), `RecurringInvoiceGenerationOutcome`,
`RecurringInvoicePeriod` (pure due-date/period-key logic). No wiring into `AccountingRepository`/
`AccountingScheduler`, no Room entity, no migration, no implementation, no UI.

See `docs/52_MANAGEMENT_ARCHITECTURE.md` for the full Phase 7J audit this came out of.

## The key design insight

Unlike `Voucher` (which needed a whole new `RecurringVoucherDraftEntity` table in Phase 7F because
Voucher itself has no draft concept), `Invoice` **already is** its own draft - it has zero
accounting effect until `postInvoice` sets its `voucherId` (Phase 7A). So a future implementation
of recurring invoices needs no new draft type at all: it only needs to call the existing,
unmodified `AccountingRepository.createDraftInvoice` to produce a real, ordinary DRAFT `Invoice`
for a human to review, edit, discard, or post through the existing, unmodified `postInvoice` -
exactly like every other invoice in this app.

## What a future implementation still needs (not built here)

A thin `AccountingRepository.generateInvoiceIfDue`-style function (same pattern as
`generateRecurringVoucherIfDue`) and a small idempotency table (`(scheduleId, periodKey)`,
unique-indexed, storing the resulting `invoiceId`) so a monthly cycle can't propose the same
retainer invoice twice - the same shape `RecurringVoucherDraftEntity` already established.
