"""
Server-side double-entry validation (Phase 6, Priority 6.7/6.14/6.21) - a deliberate, independent
Python re-implementation of the *same principle* Android's `DoubleEntryValidator` enforces
(app/src/main/java/com/example/accounting/domain/accounting/DoubleEntryValidator.kt), not a port of
the Kotlin code (the two languages can't share code) and not a redesign of it. The server must
never trust a client-submitted total as authoritative - every journal line arriving in a SyncEvent
is re-summed here before anything is persisted.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class JournalLineFacts:
    ledger_id: str
    type: str  # "DEBIT" or "CREDIT"
    amount_paise: int


def validate_balanced(lines: list[JournalLineFacts]) -> None:
    from app.domain.errors import DoubleEntryNotBalanced, ValidationError

    if not lines:
        raise ValidationError("A voucher must contain at least one journal line.")

    total_debit = sum(l.amount_paise for l in lines if l.type == "DEBIT")
    total_credit = sum(l.amount_paise for l in lines if l.type == "CREDIT")

    has_debit = any(l.type == "DEBIT" and l.amount_paise > 0 for l in lines)
    has_credit = any(l.type == "CREDIT" and l.amount_paise > 0 for l in lines)
    if not has_debit or not has_credit:
        raise ValidationError("A voucher must contain at least one debit line and one credit line.")

    if any(l.amount_paise <= 0 for l in lines):
        raise ValidationError("Journal lines cannot have a zero or negative amount.")

    if total_debit != total_credit:
        raise DoubleEntryNotBalanced(
            f"Total debits ({total_debit}) must equal total credits ({total_credit})."
        )
