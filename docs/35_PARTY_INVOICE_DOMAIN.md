# 35. Party + Invoice Domain (Phase 7A)

## Status

Phase 7A of the amended Phase 7 scope. Extends, never modifies, the frozen Phase 0-6A
foundation (Chart of Accounts, `VoucherPostingEngine`, Settlement allocation, sync/Outbox,
Python/PostgreSQL server). No UI work is included in this phase - see `26_API_SECURITY.md`'s
sibling phases for the eventual Phase 7J UI gate.

## Why a separate Party concept

Before Phase 7A, a customer or supplier was just a plain `Ledger` row under the standard
`Sundry Debtors`/`Sundry Creditors` group - correct for accounting, but missing anything a real
invoicing workflow needs: is this counterparty an individual or a business, what's their credit
limit, what are their payment terms. Rather than bolt these fields onto `Ledger` (which is shared
by every account type, including pure P&L/balance-sheet ledgers that have no business being a
"party"), Phase 7A introduces `Party` as a thin 1:1 extension:

- `Party.ledgerId` always points at an existing `Ledger`, created the normal way via
  `AccountingRepository.createLedger` under `GRP_DEBTORS_$companyId`/`GRP_CREDITORS_$companyId`.
- GSTIN/PAN/address/bank details stay on `Ledger`, never duplicated onto `Party`.
- `Party` adds only: `role` (`CUSTOMER`/`SUPPLIER`), `entityType` (`INDIVIDUAL`/`BUSINESS`),
  `creditLimitPaise`, and `paymentTerms`.
- An entity that is genuinely both a customer and a supplier gets **two** `Party` rows (one per
  role, each its own ledger) - standard double-entry practice keeps debtor and creditor tracking
  on separate ledgers rather than netting them together.

## Payment terms and due dates

`PaymentTerms` (`domain/party/PaymentTerms.kt` on Android, mirrored server-side only as plain
strings on `Party`/passed through on `Invoice`) is a small pure value type: a `PaymentTermsType`
(`DUE_ON_RECEIPT`/`NET_7`/`NET_15`/`NET_30`/`NET_45`/`NET_60`/`CUSTOM`) plus an optional
`customDays`, with a single pure function `dueDate(invoiceDate): LocalDate`.

Every `Invoice` **snapshots** its own resolved `dueDate` at creation time (resolved from the
party's terms if not explicitly overridden). A later change to a party's payment terms never
retroactively alters an already-issued invoice's due date - exactly the same "immutable original,
mutate only forward" discipline the posting engine already applies to journal entries.

## Why Invoice is a separate table from Voucher

`VoucherPostingEngine.post()`/`.cancel()` have no concept of a "draft" - a `Voucher` is always
posted atomically, in full, with its journal/stock/GST rows, inside one Room transaction. A real
invoicing workflow needs an editable, non-accounting-affecting draft *before* that moment (add a
line, fix a rate, delete the whole thing) - Phase 5's non-posting `VoucherType` entries (Quotation,
Proforma, Sales/Purchase Order) already established the principle that a document can exist without
touching the ledgers; `Invoice` extends that principle to Sales Invoice/Purchase Bill/Credit
Note/Debit Note specifically, with a real lifecycle:

```
DRAFT  -- Invoice + InvoiceLine rows only. Zero Ledger/JournalItem/Trial-Balance/P&L effect.
  |
  |  postInvoice(): the caller builds a real Voucher via the EXISTING, unmodified
  |  TradingWorkflowEngine.buildSale/buildPurchase/buildNote (exactly the same call every
  |  Sale/Purchase/CN/DN already makes today), then AccountingRepository.postInvoice() calls the
  |  EXISTING, unmodified postVoucher() and links Invoice.voucherId + copies the Voucher's own
  |  voucherNumber onto Invoice.invoiceNumber. No separate invoice-numbering sequence exists -
  |  GST-required sequential numbering can never gap from an abandoned draft.
  v
POSTED / PARTIALLY_PAID / PAID / OVERDUE / CANCELLED  -- all derived, never stored (see below)
```

Cancelling an Invoice: a still-DRAFT invoice is simply deleted (it never had any accounting effect
to reverse); a posted invoice delegates to the existing, unmodified `deleteVoucherSafely` on its
linked voucher.

## Status is always derived, never stored

`InvoiceStatusEngine.deriveStatus(...)` (Android: `domain/invoice/InvoiceStatusEngine.kt`; server:
`domain/invoice/status.py`'s `derive_status(...)`, an intentional independent Python port, not a
shared library - the same "duplicate the principle, not the code" rule already applied to
double-entry/Contra/allocation validation) is the single source of truth:

1. `voucherId == null` -> `DRAFT`
2. linked `Voucher.isCancelled` -> `CANCELLED`
3. otherwise, compare `outstandingPaise` (from the **existing, unmodified**
   `computeOutstandingPaise`/`_compute_outstanding_paise`) against the voucher total ->
   `PAID` / `PARTIALLY_PAID` / `POSTED`
4. if not yet `PAID` and past `dueDate` -> `OVERDUE` overrides step 3's result

Nothing in this phase adds a mutable `status` column anywhere - the whole point is that a UI (built
later, in Phase 7J) can never "arbitrarily change accounting status"; it can only ever display what
this function derives from data the frozen engines already produce.

## Linking a posted Invoice to its Voucher never touches the frozen posting path

`apply_voucher_event` (Python) and `VoucherPostingEngine.post()`/`.cancel()` (Kotlin) are
**unmodified** by this phase - confirmed by the full existing Phase 6A test suite (27/27 Python,
same baseline Android suite) passing unchanged. Instead, once `postInvoice()` posts the real
Voucher through the existing path, it enqueues a **second, independent** sync event -
`LINK_INVOICE_VOUCHER` (`aggregateType=INVOICE`) - carrying only `{invoiceId, voucherId,
invoiceNumber}`. Server-side, this is handled entirely by the new `invoice_commands.py`, which only
ever touches the `invoices` table.

## Sync/Outbox additions (all purely additive)

- New `SyncOperation` entries: `CREATE_PARTY`, `UPDATE_PARTY`, `CREATE_DRAFT_INVOICE`,
  `CANCEL_DRAFT_INVOICE`, `LINK_INVOICE_VOUCHER`. Every existing operation is unchanged; posting a
  Sales Invoice/Purchase Bill/Credit/Debit Note still produces exactly the `POST_SALES_INVOICE`/
  `POST_PURCHASE_BILL`/`POST_CREDIT_NOTE`/`POST_DEBIT_NOTE` event it already did via
  `VoucherType.toPostOperation()`.
- New `SyncAggregateType` entries: `PARTY`, `INVOICE`.
- Two new optional fields on the `SyncEvent` envelope (both sides): `party: SyncPartyDto?`,
  `invoice: SyncInvoiceDto?`.
- Server-side `outbox_dispatcher.py` gained two new operation sets (`_PARTY_OPERATIONS`,
  `_INVOICE_OPERATIONS`) and two new `elif` branches routing to `apply_party_event`/
  `apply_invoice_event` - the existing `_VOUCHER_OPERATIONS`/`_LEDGER_OPERATIONS` branches and
  their handlers are untouched.

## API (read-only, business-level, matching the existing `vouchers.py` precedent)

- `GET /api/v1/parties?companyId=...&role=CUSTOMER|SUPPLIER`
- `GET /api/v1/invoices?companyId=...&partyId=...` - `status` is computed inline via
  `derive_status`, never a stored/trusted field.

Mutation is exclusively via the Outbox batch endpoint, exactly like every other Phase 6A resource -
no POST route was added for either resource.

## Database changes (both sides purely additive)

- Android: Room schema version 3 -> 4, `MIGRATION_3_4` creates three new tables (`parties`,
  `invoices`, `invoice_lines`) only - no existing table altered, dropped, or renamed.
- Python: Alembic revision `0002` (`down_revision = "0001"`), same three tables mirrored
  table-for-table (snake_case columns).

## What Phase 7A deliberately does not include

- Any UI (Compose screens, ViewModel-facing surface, navigation, dialogs) - per the project's Phase
  7 gate, UI is Phase 7J, built only after 7A-7I are complete, tested, documented, and independently
  audited.
- Document Templates, PDF/Print/Share, GSTR JSON export, Business/Individual Profile branding -
  Phases 7D/7E/7G respectively.
- Any change to GST calculation, inventory valuation, Round-Off/Suspense protection, or Contra
  restriction - all frozen and independently re-verified unchanged as part of this phase's freeze
  audit.
