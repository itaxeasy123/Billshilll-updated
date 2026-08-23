"""
The versioned JSON envelope every export wraps its data in (Phase 7E, Section 6) - mirrors
Android's `ExportJsonSerializer.envelope` field-for-field so the two platforms' exports are
logically compatible (Section 25).
"""
from __future__ import annotations

import time


def build_metadata(company_id: str, export_type: str, financial_year_id: str | None = None) -> dict:
    return {
        "schemaVersion": "1.0",
        "exportType": export_type,
        "generatedAt": int(time.time() * 1000),
        "companyId": company_id,
        "financialYearId": financial_year_id,
        "applicationVersion": "7.5.0",
    }


def envelope(metadata: dict, data: dict) -> dict:
    return {**metadata, "data": data}
