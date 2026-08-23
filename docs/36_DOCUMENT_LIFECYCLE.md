# 36. Document/Voucher Lifecycle Architecture (Phase 7B)

## Status

Phase 7B of the amended Phase 7 scope. Extends Phase 7A's Party/Invoice foundation with the
remaining 6 trade-document types, document numbering decoupled from voucher numbering, a typed
document-relationship model, and document conversion — all with **zero UI** and **zero changes**
to `VoucherPostingEngine`/`apply_voucher_event`/`TradingWorkflowEngine`, exactly as in 7A.

## Document classification

**Posting documents** (Sales Invoice, Purchase Bill, Credit Note, Debit Note) are `Invoice` (7A) -
unchanged, already reference the original via `referenceInvoiceId`, already reverse correctly via
`TradingWorkflowEngine.buildNote`.

**Non-posting documents** (Quotation, Proforma Invoice, Sales Order, Purchase Order, Delivery
Note, Receipt Note) are the new `TradeDocument` (Android: `domain/document/TradeDocument.kt`;
server: `infrastructure/database/models.py`'s `TradeDocument`) - a genuinely separate table from
`invoices`, not a rename of it. Renaming `invoices` into a generic polymorphic `documents` table
was considered and rejected: it would force two structurally different lifecycles (Invoice's
always-derived status vs. TradeDocument's genuinely-stored status) into one shape, and would
require an actual data-rewriting migration on an already-frozen, audited table for zero benefit.

`DocumentType` (10 entries, `domain/document/DocumentType.kt`) covers both categories with an
`isPostingDocument` flag; `createTradeDocument` rejects the 4 posting types outright (those are
created as Invoices, not TradeDocuments).

## Non-posting lifecycle

`DocumentStatus`: `DRAFT → ISSUED → CONVERTED → CANCELLED`. Unlike `InvoiceStatus` (always derived,
never stored - see `docs/35_PARTY_INVOICE_DOMAIN.md`), this is a genuine stored column: a
TradeDocument never touches accounting at all, so there is no risk of the UI "arbitrarily changing
accounting status" the way there would be for a posting document.

- **DRAFT**: freely editable.
- **ISSUED**: lines become immutable.
- **CONVERTED**: set automatically once another document/invoice references this one as its
  source; a `CONVERTED` document can never be cancelled directly.
- **CANCELLED**: a `DRAFT` is hard-deleted (never shown to anyone); an `ISSUED` document is marked
  `CANCELLED` instead (record preserved).

## Document relationships - reverse pointers, not a forward "convertedDocument" field

Every `TradeDocument`/`Invoice` carries one nullable `sourceTradeDocumentId` (no declared FK - same
convention as `Voucher.referenceVoucherId`/`Invoice.referenceInvoiceId`). "What did document X
convert into?" is answered by a reverse query (another `TradeDocument`, or an `Invoice`, whose own
`sourceTradeDocumentId` equals X's id) - never a forward-pointing field on X that would need to be
kept in sync with whatever it eventually became. A user can always create a Sales Invoice (or any
document) directly, with no source document at all.

## Document conversion never re-implements posting

`convertTradeDocument` (TradeDocument → TradeDocument, e.g. Quotation → Sales Order) is a plain
copy of header + lines with `sourceTradeDocumentId` set. `convertTradeDocumentToInvoice`
(TradeDocument → Invoice, e.g. Sales Order → Sales Invoice) calls the **existing, unmodified**
`createDraftInvoice` (7A) with lines copied over. Either way, the source's status flips to
`CONVERTED` via a **second, independent** event (`CONVERT_TRADE_DOCUMENT`) - the same
"never thread new fields through the frozen path" discipline 7A established for
`LINK_INVOICE_VOUCHER`. Actually posting the resulting Invoice still goes through the existing,
unmodified `postInvoice` → `TradingWorkflowEngine` → `postVoucher()` path. No new posting mechanism
exists anywhere in this phase.

## Document numbering is fully decoupled from voucher numbering

This is the one deliberate, explicit retrofit to already-frozen 7A code, directed by this phase's
own requirement ("the accounting voucher number and user-facing invoice number should not be
assumed to be the same identifier"):

- **Before 7B**: `Invoice.invoiceNumber` was `null` while DRAFT and was copied verbatim from the
  linked `Voucher.voucherNumber` at posting time.
- **After 7B**: `Invoice.invoiceNumber` is assigned once, at DRAFT-creation time, via the new
  `generateNextDocumentNumber` (mirrors `generateNextVoucherNumber`'s exact shape - count existing
  rows of that type for the company+FY, format `prefix + year + zero-padded count` - with its own
  independent counter). `postInvoice`'s linking step now only ever sets `voucherId`; it never
  touches `invoiceNumber` again.

The 4 posting-document prefixes are new and intentionally distinct from the Voucher's own:

| Document type | Document prefix | Voucher's own prefix (unchanged) |
|---|---|---|
| Sales Invoice | `SI-` | `INV-` |
| Purchase Bill | `PB-` | `PUR-` |
| Credit Note | `CN-` | `CRN-` |
| Debit Note | `DN-` | `DRN-` |

The 6 non-posting prefixes reuse `VoucherType`'s existing values verbatim (established in Phase 5,
never used for an actual number until now): Quotation `EST-`, Proforma Invoice `PI-`, Sales Order
`SO-`, Purchase Order `PO-`, Delivery Note `DC-`, Receipt Note `GRN-`.

## API

- `GET /api/v1/trade-documents?companyId=&documentType=` - mirrors `parties.py`'s filtered-list
  pattern.
- `POST /api/v1/{quotations,proforma-invoices,sales-orders,purchase-orders,delivery-notes,receipt-notes}`
  - real mutating routes, each requiring an `Idempotency-Key` header and calling
  `apply_trade_document_event` directly - the *same* function the Outbox path calls for
  `CREATE_TRADE_DOCUMENT`. Android's own client continues to exclusively use the Outbox batch
  endpoint for every mutation (offline-first guarantee unchanged); these routes exist for
  hypothetical non-Android callers, matching Phase 6A's original design intent (business-level
  routes calling the same command handlers Outbox dispatch uses, never a raw database mutation).
- Invoice/Sales-Invoice/Purchase-Bill/Credit-Debit-Note stay GET-only (unchanged from 7A) -
  posting documents remain exclusively Outbox-driven, since posting always requires the
  Android-side `TradingWorkflowEngine`/`postVoucher` path this server never re-implements. A single
  polymorphic `GET /documents`/`/documents/{id}` spanning both `Invoice` and `TradeDocument` rows
  was considered and rejected - it would force a lossy merged shape across two structurally
  different row types.

## Sync/Outbox additions (all purely additive)

New `SyncOperation` entries: `CREATE_TRADE_DOCUMENT`, `ISSUE_TRADE_DOCUMENT`,
`CONVERT_TRADE_DOCUMENT`, `CANCEL_TRADE_DOCUMENT`. New `SyncAggregateType.TRADE_DOCUMENT`. New
optional `tradeDocument` field on `SyncEvent` (both sides), plus a new optional
`sourceTradeDocumentId` on the existing `invoice` field. Every 7A/6A operation, branch, and field
is unchanged.

## Database changes (both sides purely additive)

- Android: Room schema version 4 → 5 (`MIGRATION_4_5`: two new tables - `trade_documents`,
  `trade_document_lines` - plus one new nullable column on `invoices`).
- Python: Alembic revision `0003` (`down_revision = "0002"`), mirrors the same two tables plus the
  same new nullable column.

## What Phase 7B deliberately does not include

- Any UI - per the project's Phase 7 gate, UI is Phase 7J, built only after 7A-7I are complete,
  tested, documented, and independently audited.
- Report Management, Document Template Engine, Export/GSTR JSON, Automation wiring, Business/
  Individual Profiles - Phases 7C/7D/7E/7F/7G respectively.
- Any change to GST calculation, inventory valuation, Round-Off/Suspense protection, Contra
  restriction, or settlement allocation - all frozen and independently re-verified unchanged as
  part of this phase's freeze audit.
