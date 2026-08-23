"""
Server-side Contra restriction (Phase 6, Priority 6.7/6.9/6.21) - mirrors the same principle
Android's `VoucherPostingEngine.post()` enforces (core/database/DatabaseTransaction.kt): a Contra
voucher may only move funds between Cash/Bank ledgers. Enforced here independently so a client
that bypasses/lacks Android's own check (a future non-Android client, a malformed payload) still
cannot post an invalid Contra.
"""
from __future__ import annotations


def validate_contra_ledgers(ledger_group_ids: list[str]) -> None:
    from app.domain.errors import InvalidContra

    for group_id in ledger_group_ids:
        if not (group_id.startswith("GRP_BANK_") or group_id.startswith("GRP_CASH_")):
            raise InvalidContra(
                f"Contra vouchers may only move funds between Cash/Bank accounts; ledger group '{group_id}' is neither."
            )
