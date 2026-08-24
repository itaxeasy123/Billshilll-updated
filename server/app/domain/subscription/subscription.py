"""
Phase 7J-B - Subscription/Entitlements domain module. Independent Python re-implementation of the
Android `domain.subscription.CompanySubscription`/`SubscriptionEntitlementChecker` (Phase 7J) -
"duplicate the principle, not the code," the same discipline already applied to
`domain/invoice/status.py`. Pure, side-effect-free: no database import anywhere in this file.

FREE is always available (this app's founding offline-first/login-free principle). PAID validity is
always exactly the referenced FinancialYear's own date range - this module itself carries no date
literal at all, only the plan/entitlement vocabulary and the pure yes/no check.
"""
from __future__ import annotations

from enum import Enum


class SubscriptionPlanType(str, Enum):
    FREE = "FREE"
    PAID = "PAID"


class EntitlementFeature(str, Enum):
    ACCOUNTING = "ACCOUNTING"
    GSTR = "GSTR"
    E_INVOICE = "E_INVOICE"
    ITR = "ITR"
    AUDIT_REPORT = "AUDIT_REPORT"
    CMA = "CMA"
    OCR = "OCR"
    INVENTORY = "INVENTORY"
    ADVANCED_REPORTS = "ADVANCED_REPORTS"
    API_ACCESS = "API_ACCESS"


def has_entitlement(is_active: bool, entitlements: set[EntitlementFeature], feature: EntitlementFeature) -> bool:
    """Mirrors Android's `SubscriptionEntitlementChecker.hasEntitlement` exactly - the only thing a
    subscription is allowed to answer is "is this feature available right now." Never mutates or
    reads any accounting table."""
    return is_active and feature in entitlements
