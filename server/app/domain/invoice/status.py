"""
Server-side port of the Android `InvoiceStatusEngine` (Phase 7A) - the same single source of
truth for an Invoice's lifecycle status, never a stored field. Pure function: every input is a
plain value the caller already has on hand, same shape as `domain/accounting/allocation.py`.
"""
from __future__ import annotations

from datetime import date as date_cls


def derive_status(
    voucher_id: str | None,
    is_cancelled: bool,
    total_amount_paise: int,
    outstanding_paise: int,
    due_date: str | None,
    today: str | None = None,
) -> str:
    if voucher_id is None:
        return "DRAFT"
    if is_cancelled:
        return "CANCELLED"

    if outstanding_paise <= 0:
        base = "PAID"
    elif outstanding_paise < total_amount_paise:
        base = "PARTIALLY_PAID"
    else:
        base = "POSTED"

    if base != "PAID" and due_date:
        today_value = date_cls.fromisoformat(today) if today else date_cls.today()
        if today_value > date_cls.fromisoformat(due_date):
            return "OVERDUE"

    return base
