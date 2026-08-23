"""
Builds a GSTR-shaped JSON document from already-persisted GST-transaction export dicts (Phase 7E,
Section 10/11) - mirrors Android's `GstrJsonSerializer` exactly (same grouping-by-direction logic,
same field names) so the two platforms' GSTR exports are logically compatible. Never reconstructs
GST from ledger names; never invents a statutory field this domain doesn't have (`isService` stays
whatever the transaction dict already carries - always `None` today, see `docs/49_GSTR_JSON_EXPORT.md`).
"""
from __future__ import annotations

from app.domain.export.json_envelope import envelope


def _line_tree(t: dict) -> dict:
    return {
        "voucherId": t["voucherId"], "voucherType": t["voucherType"], "partyGstin": t["partyGstin"],
        "placeOfSupply": t["placeOfSupply"], "supplyType": t["supplyType"], "hsnSacCode": t["hsnSacCode"],
        "isService": t["isService"], "taxableAmountPaise": t["taxableAmountPaise"], "gstRatePercent": t["gstRatePercent"],
        "cgstPaise": t["cgstPaise"], "sgstPaise": t["sgstPaise"], "igstPaise": t["igstPaise"], "cessPaise": t["cessPaise"],
    }


def build_gstr_json(metadata: dict, transactions: list[dict]) -> dict:
    outward = [t for t in transactions if t["direction"] == "OUTPUT"]
    inward = [t for t in transactions if t["direction"] == "INPUT"]
    gstin = next((t["partyGstin"] for t in transactions if t["partyGstin"]), "")
    data = {
        "gstin": gstin,
        "outwardSupplies": [_line_tree(t) for t in outward],
        "inwardSupplies": [_line_tree(t) for t in inward],
        "totals": {
            "totalTaxableOutwardPaise": sum(t["taxableAmountPaise"] for t in outward),
            "totalTaxOutwardPaise": sum(t["cgstPaise"] + t["sgstPaise"] + t["igstPaise"] for t in outward),
            "totalCessOutwardPaise": sum(t["cessPaise"] for t in outward),
            "totalTaxableInwardPaise": sum(t["taxableAmountPaise"] for t in inward),
            "totalTaxInwardPaise": sum(t["cgstPaise"] + t["sgstPaise"] + t["igstPaise"] for t in inward),
            "totalCessInwardPaise": sum(t["cessPaise"] for t in inward),
        },
    }
    return envelope(metadata, data)
