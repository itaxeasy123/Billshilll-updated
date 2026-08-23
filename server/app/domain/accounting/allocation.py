"""
Server-side settlement-allocation over-allocation guard (Phase 6, Priority 6.7/6.21) - mirrors the
same principle Android's `AccountingRepository.computeOutstandingPaise`/`allocateSettlement` enforce
(the exact gap the Phase 5 final audit found and fixed client-side): an allocation must never
exceed the target invoice's own remaining outstanding, not just the settlement voucher's total.
"""
from __future__ import annotations


def validate_allocation(allocated_amount_paise: int, invoice_outstanding_paise: int, invoice_id: str) -> None:
    from app.domain.errors import OverAllocation

    if allocated_amount_paise > invoice_outstanding_paise:
        raise OverAllocation(
            f"Allocation of {allocated_amount_paise} paise exceeds invoice '{invoice_id}''s "
            f"remaining outstanding ({max(invoice_outstanding_paise, 0)} paise)."
        )
