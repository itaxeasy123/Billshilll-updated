"""
CREATE_LEDGER / UPDATE_LEDGER / DELETE_LEDGER command handlers (Phase 6, Priority 6.13) - mirrors
Android's `AccountingRepository.createLedger`/`updateLedger`/`deleteLedgerSafely` protection rules
(System ledgers, Suspense, Round Off never deletable/renamable/reparented; a ledger with posted
journal entries can never be deleted) as the server's own independent enforcement.
"""
from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.domain.errors import LedgerHasEntries, RoundOffInvalid, SuspenseProtected, SystemLedgerProtected, ValidationError
from app.infrastructure.database.models import JournalItem, Ledger
from app.schemas.sync import SyncEvent

SUSPENSE_LEDGER_PREFIX = "LED_SYS_SUSPENSE"
ROUND_OFF_LEDGER_PREFIX = "LED_SYS_ROUND_OFF"


def _is_protected(ledger_id: str, is_system: bool) -> bool:
    return is_system or ledger_id.startswith(SUSPENSE_LEDGER_PREFIX) or ledger_id.startswith(ROUND_OFF_LEDGER_PREFIX)


async def apply_ledger_event(db: AsyncSession, event: SyncEvent) -> dict:
    if event.ledger is None:
        raise ValidationError("A ledger event must include ledger data.")

    if event.operation == "CREATE_LEDGER":
        db.add(
            Ledger(
                ledger_id=event.ledger.ledgerId, company_id=event.companyId, group_id=event.ledger.groupId,
                name=event.ledger.name, code=event.ledger.code,
                opening_balance_paise=event.ledger.openingBalancePaise, opening_balance_type=event.ledger.openingBalanceType,
                current_balance_paise=event.ledger.openingBalancePaise, current_balance_type=event.ledger.openingBalanceType,
                gstin=event.ledger.gstin, pan=event.ledger.pan, state_code=event.ledger.stateCode,
                hsn_sac_code=event.ledger.hsnSacCode, default_tax_rate=event.ledger.defaultTaxRate,
                is_system=False, is_active=True,
            )
        )
        return {"ledgerId": event.ledger.ledgerId, "status": "CREATED"}

    result = await db.execute(select(Ledger).where(Ledger.company_id == event.companyId, Ledger.ledger_id == event.ledger.ledgerId))
    existing = result.scalar_one_or_none()
    if existing is None:
        raise ValidationError(f"Ledger '{event.ledger.ledgerId}' not found.")

    if event.operation == "DELETE_LEDGER":
        if existing.ledger_id.startswith(SUSPENSE_LEDGER_PREFIX):
            raise SuspenseProtected("The Suspense account is permanent and cannot be deleted.")
        if existing.ledger_id.startswith(ROUND_OFF_LEDGER_PREFIX):
            raise RoundOffInvalid("The Round Off account is permanent and cannot be deleted.")
        if _is_protected(existing.ledger_id, existing.is_system):
            raise SystemLedgerProtected("System ledgers are permanent and cannot be deleted.")

        count_result = await db.execute(
            select(func.count()).select_from(JournalItem).where(
                JournalItem.company_id == event.companyId, JournalItem.ledger_id == existing.ledger_id
            )
        )
        entry_count = count_result.scalar_one()
        if entry_count > 0:
            raise LedgerHasEntries(f"Ledger '{existing.name}' has {entry_count} posted entries and cannot be deleted.")

        await db.delete(existing)
        return {"ledgerId": event.ledger.ledgerId, "status": "DELETED"}

    # UPDATE_LEDGER
    if _is_protected(existing.ledger_id, existing.is_system):
        if existing.name != event.ledger.name or existing.group_id != event.ledger.groupId:
            raise SystemLedgerProtected("System ledgers/Suspense/Round Off cannot be renamed or reparented.")
    existing.name = event.ledger.name
    existing.group_id = event.ledger.groupId
    existing.gstin = event.ledger.gstin
    existing.pan = event.ledger.pan
    existing.state_code = event.ledger.stateCode
    existing.hsn_sac_code = event.ledger.hsnSacCode
    existing.default_tax_rate = event.ledger.defaultTaxRate
    return {"ledgerId": event.ledger.ledgerId, "status": "UPDATED"}
