"""
CREATE_PARTY / UPDATE_PARTY command handlers (Phase 7A) - mirrors Android's
`AccountingRepository.createParty`: a Party is a thin 1:1 extension of an already-existing Ledger
row (created independently via its own CREATE_LEDGER sync event), never a replacement. This
handler never creates, mutates, or deletes a Ledger itself.
"""
from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.domain.errors import PartyNotFound, ValidationError
from app.infrastructure.database.models import Party
from app.schemas.sync import SyncEvent


async def apply_party_event(db: AsyncSession, event: SyncEvent) -> dict:
    if event.party is None:
        raise ValidationError("A party event must include party data.")

    if event.operation == "CREATE_PARTY":
        db.add(
            Party(
                party_id=event.party.partyId,
                company_id=event.companyId,
                ledger_id=event.party.ledgerId,
                role=event.party.role,
                entity_type=event.party.entityType,
                display_name=event.party.displayName,
                contact_name=event.party.contactName,
                credit_limit_paise=event.party.creditLimitPaise,
                payment_terms_type=event.party.paymentTermsType,
                payment_terms_custom_days=event.party.paymentTermsCustomDays,
                is_active=event.party.isActive,
            )
        )
        return {"partyId": event.party.partyId, "status": "CREATED"}

    # UPDATE_PARTY
    result = await db.execute(select(Party).where(Party.company_id == event.companyId, Party.party_id == event.party.partyId))
    existing = result.scalar_one_or_none()
    if existing is None:
        raise PartyNotFound(f"Party '{event.party.partyId}' not found.")

    existing.display_name = event.party.displayName
    existing.contact_name = event.party.contactName
    existing.credit_limit_paise = event.party.creditLimitPaise
    existing.payment_terms_type = event.party.paymentTermsType
    existing.payment_terms_custom_days = event.party.paymentTermsCustomDays
    existing.is_active = event.party.isActive
    return {"partyId": event.party.partyId, "status": "UPDATED"}
