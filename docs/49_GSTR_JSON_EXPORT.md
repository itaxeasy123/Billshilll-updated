# 49. GSTR JSON Export (Phase 7E)

See `docs/46_EXPORT_ARCHITECTURE.md` for the shared architecture; this doc covers the GSTR JSON
serializer specifically.

## Source of truth: `GstTransaction`, never ledger names

`GstrJsonSerializer.kt` (Android) / `gstr_json.py` (Python) build a GSTR-shaped JSON document
exclusively from `GSTTransactionExportDto` facts - a thin mapping of the already-persisted
`GstTransaction`/`GstTransactionEntity` row Phase 5 established as this project's sole GST source of
truth. Neither implementation, nor anything else in this codebase, ever does
`ledgerName.contains("CGST")`/`"SGST"`/`"IGST"` string-guessing - confirmed by an explicit,
project-wide search before this phase's implementation began (the only matches anywhere are code
comments documenting that this was the *old, since-replaced* approach, predating Phase 5).

## Shape

```json
{
  "schemaVersion": "1.0",
  "exportType": "GST_TRANSACTIONS",
  "generatedAt": 1787270400000,
  "companyId": "COMP_123",
  "financialYearId": "FY_2026_27",
  "applicationVersion": "7.5.0",
  "data": {
    "gstin": "27AAAAA0000A1Z5",
    "outwardSupplies": [ /* GSTR-1-shaped: direction == OUTPUT */ ],
    "inwardSupplies": [ /* GSTR-3B-ITC-shaped: direction == INPUT */ ],
    "totals": {
      "totalTaxableOutwardPaise": 0, "totalTaxOutwardPaise": 0, "totalCessOutwardPaise": 0,
      "totalTaxableInwardPaise": 0, "totalTaxInwardPaise": 0, "totalCessInwardPaise": 0
    }
  }
}
```

Each line in `outwardSupplies`/`inwardSupplies` carries: `voucherId`, `voucherType`, `partyGstin`,
`placeOfSupply`, `supplyType`, `hsnSacCode`, `isService`, `taxableAmountPaise`, `gstRatePercent`,
`cgstPaise`, `sgstPaise`, `igstPaise`, `cessPaise` - every one of these is a direct, unmodified read
of the corresponding `GstTransaction` field (Section 11's requested field list, minus statutory
fields this domain doesn't store - see below).

The outward/inward split (Section: reusing the one real piece of GSTR structure this project's data
actually supports) comes directly from `GstTransaction.direction` (`OUTPUT`/`INPUT`) - Phase 5's own
established fact, not invented for this phase.

## HSN vs. SAC: a documented extension point, not a guess

Section 12 asks that "GOODS -> HSN, SERVICES -> SAC" be preserved from "the existing item/service
classification." The Phase 7E structural audit confirmed, by reading every relevant model
(`StockItem`, `InvoiceLine`, `TradeDocumentLine`, `GstTransaction`, `Ledger`, on both platforms),
that **no domain model anywhere in this project stores a goods-vs-service classification** - only a
bare `hsnSacCode`/`hsn_sac_code` string exists, with nothing distinguishing whether that code is an
HSN or an SAC code.

Rather than guess from the code's digit count or a lookup table (which this project's own
discipline - "do not invent unavailable statutory fields... create an extension point rather than
fabricating it" - explicitly forbids), `GSTTransactionExportDto.isService: Boolean? = null` exists
as exactly that extension point: always `null` today, present in every export (never silently
omitted - see `docs/47_JSON_EXPORT.md`'s null-handling section), ready for a future phase to
populate once a real goods/service classification is added to the domain (most likely alongside
`StockItem`, since that's where such a flag would naturally belong).

## What is NOT invented

Per Section 11's "do not invent unavailable statutory fields": no GSTIN checksum validation, no
e-invoice IRN, no e-way bill reference, no HSN-code-to-description lookup, and no return-period
(GSTR-1/3B filing-period) grouping beyond the caller-supplied `financialYearId` exist anywhere in
this serializer. Each is a plausible future GSTR schema requirement this phase deliberately does
not fabricate data for.

## Filing is out of scope

This phase produces the GSTR-shaped JSON *document* only - actually submitting it to the GST
Network (filing) is an entirely separate future concern (Section 28 lists "GST filing" as a future
extension point) and has no code, route, or even a stub in this phase.

## API

```
GET /api/v1/exports/gst?companyId=&financialYearId=&format=gstr-json
GET /api/v1/exports/gst/transactions?companyId=&financialYearId=&format=json|csv|gstr-json
```

`gstr-json` is the only format `GST_SUMMARY`/`GST_TRANSACTIONS` share that no other export type
supports (`ExportFormatSupport.supports`/`supports_format` reject `gstr-json` for every other
export type with `EXPORT_FORMAT_UNSUPPORTED`).
