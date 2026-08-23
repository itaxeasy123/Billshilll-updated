# 47. JSON Export (Phase 7E)

See `docs/46_EXPORT_ARCHITECTURE.md` for the architecture shared by every format; this doc covers
the JSON envelope/schema specifically.

## The versioned envelope

Every JSON export (Android: `ExportJsonSerializer.serialize`; Python: `json_envelope.envelope`) is
wrapped identically:

```json
{
  "schemaVersion": "1.0",
  "exportType": "VOUCHER",
  "generatedAt": 1787270400000,
  "companyId": "COMP_123",
  "financialYearId": null,
  "applicationVersion": "7.5.0",
  "data": { }
}
```

`schemaVersion` is a plain string (`"1.0"`) - a future breaking change bumps this rather than
silently reshaping `data` underneath an unversioned consumer (Section 6/24 -
`ExportSchemaUnsupported` is the reserved error for a consumer that only understands an older/newer
version than what it receives, though no consumer-side version negotiation exists yet since only
one schema version has ever been produced). `generatedAt` is the export event's own timestamp
(epoch milliseconds) - metadata about *this export*, never part of the accounting data itself.
`financialYearId` is `null` for exports that aren't FY-scoped (Voucher/Party/Ledger/Invoice/
Outstanding).

## Money stays an exact paise integer, always

Every monetary field in every export DTO is named `...Paise` and is always a `Long`/Python `int` -
never a `Double`/`float`. `500000.15` never appears as the authoritative value anywhere in this
layer; a human-readable formatted string (with a currency symbol, decimal point, or locale
grouping) is a presentation concern for a future consumer to derive from the paise integer, never
something this export layer produces itself. This is the same discipline `docs/42-45` already
established for Phase 7D's rendering layer, extended to the export layer.

## Per-export-type shape

- **VOUCHER**: `voucherId`, `voucherNumber`, `voucherType`, `date`, `referenceNumber`, `narration`,
  `totalAmountPaise`, `isPosted`, `isCancelled`, `referenceVoucherId`, `journalLines[]` (each:
  `ledgerId`, `ledgerName`, `type`, `amountPaise`, `narration`, `lineOrder`). Never exposes
  `companyId`/`financialYearId`/`syncState`/`createdBy` or any other internal bookkeeping field a
  Room/SQLAlchemy row carries but an external consumer doesn't need (Section 14).
- **PARTY**: `partyId`, `ledgerId`, `role`, `entityType`, `displayName`, `contactName`,
  `creditLimitPaise`, `paymentTerms` (a plain string - `"NET_30"` or `"CUSTOM:15"`), `isActive`.
- **LEDGER**: `ledgerId`, `groupId`, `name`, `code`, `openingBalancePaise`/`openingBalanceType`,
  `currentBalancePaise`/`currentBalanceType`, `gstin`, `pan`, `stateCode`, `address`, `isSystem`,
  `isActive`.
- **INVOICE**: a thin thirteen-field wrapper over Phase 7D's `DocumentData`/`assemble_document_data`
  (`documentId`, `documentType`, `documentNumber`, `documentDate`, `dueDate`, `sellerName`,
  `buyerName`, `buyerGstin`, `lineCount`, `taxableAmountPaise`, `cgstPaise`/`sgstPaise`/`igstPaise`/
  `cessPaise`, `roundOffPaise`, `grandTotalPaise`, `isPosted`, `accountingVoucherNumber`) - every
  value already computed by 7D's assembly, never recomputed here. Python's invoice export inherits
  7D's own posted-only limitation (`docs/43_DOCUMENT_RENDERING.md`) - a draft/non-posting document
  raises `DOCUMENT_PREVIEW_NOT_AVAILABLE`, not a fabricated result.
- **TRIAL_BALANCE / PROFIT_AND_LOSS / BALANCE_SHEET / OUTSTANDING / GST_SUMMARY**: field-for-field
  mirrors of the corresponding Phase 7C report, paise-renamed for the stable export contract (e.g.
  `TrialBalanceRow.closingDebit: Money` becomes `TrialBalanceRowExportDto.closingDebitPaise: Long`).
- **GST_TRANSACTIONS**: one entry per persisted `GstTransaction` fact - see
  `docs/49_GSTR_JSON_EXPORT.md` for the field list and the `isService` extension point.

## Determinism

Two exports of the same underlying data at the same schema version produce byte-identical `data`
(the `generatedAt` timestamp is the only field that legitimately differs between two calls) -
verified directly in both test suites by exporting the same Trial Balance/GSTR JSON twice and
diffing the `data` payload. `LinkedHashMap`-backed trees (Android) and Python's insertion-ordered
`dict`s guarantee key order never varies run-to-run.

## Null handling

A nullable field (`dueDate`, `creditLimitPaise`, `referenceVoucherId`, `isService`, ...) always
appears in the output as an explicit `null` - never a silently-omitted key. This was a real bug
found during testing (see `docs/46_EXPORT_ARCHITECTURE.md`'s "Bugs found and fixed" section) -
Moshi's default `Map<String, Any?>` adapter drops null-valued entries unless built with
`.serializeNulls()`; both `ExportJsonSerializer` and `GstrJsonSerializer` now use it, so every
export DTO's JSON carries a stable, complete key set regardless of which fields are null.

## Unicode

No transliteration, normalization, or ASCII-narrowing happens anywhere in this layer - a party
name, ledger address, or voucher narration containing non-ASCII characters passes through
unchanged into the JSON string (verified in both test suites with genuinely non-ASCII test data).

## API

```
GET /api/v1/exports/vouchers/{id}?companyId=&format=json
GET /api/v1/exports/parties/{id}?companyId=&format=json
GET /api/v1/exports/ledgers/{id}?companyId=&format=json
GET /api/v1/exports/invoices/{id}?companyId=&documentType=&format=json
GET /api/v1/exports/reports/trial-balance?companyId=&financialYearId=&format=json
GET /api/v1/exports/reports/profit-loss?companyId=&financialYearId=&format=json
GET /api/v1/exports/reports/balance-sheet?companyId=&financialYearId=&format=json
GET /api/v1/exports/reports/outstanding?companyId=&format=json
GET /api/v1/exports/gst?companyId=&financialYearId=&format=json
```

`format` defaults to `json`; all routes are thin, `require_company_access`-gated, GET-only.
