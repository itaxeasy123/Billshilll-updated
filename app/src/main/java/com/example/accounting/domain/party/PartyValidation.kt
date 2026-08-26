package com.example.accounting.domain.party

import com.example.accounting.domain.accounting.GstRegistrationStatus
import com.example.accounting.domain.taxation.gst.GSTRules

/**
 * Rule 30 (Party/Customer/Supplier Data Validation) - the one authoritative check for a Party's
 * GST facts (entity type, registration status, GSTIN), called from
 * [com.example.accounting.data.repository.AccountingRepository.createParty] so no caller - UI or
 * otherwise - can bypass it (Rule 30, Section 7). Reuses [GSTRules.isValidGSTIN] - never a second
 * GSTIN regex/validator (Rule 30, Section 8).
 *
 * Deliberately says nothing about State/Place-of-Supply: that invariant already lives at the
 * posting boundary ([com.example.accounting.presentation.viewmodel.AccountingViewModel.postTradingDocument] /
 * [com.example.accounting.data.repository.AccountingRepository.postGstOnlySale], Rule 29) - a Party
 * may legitimately exist with an unknown State (e.g. a walk-in customer's name captured before
 * their full address is known); it simply cannot be used in a GST-relevant posting until the State
 * is filled in. Blocking Party *creation* on a missing State would be a workflow requirement this
 * phase never asked for (Rule 30, Section 10: "Only enforce required information when the relevant
 * workflow requires it").
 */
object PartyValidation {

    /** Returns a human-readable validation error, or `null` if [gstin]/[gstRegistrationStatus] are
     * consistent with [entityType]. Never silently "fixes" an invalid GSTIN (Rule 30, Section 8) and
     * never derives [gstRegistrationStatus] from whether [gstin] happens to be blank (Section 2). */
    fun validateGstFacts(
        entityType: PartyEntityType,
        gstRegistrationStatus: GstRegistrationStatus?,
        gstin: String
    ): String? {
        if (!GSTRules.isValidGSTIN(gstin)) {
            return "GSTIN '$gstin' is not a valid GSTIN."
        }
        if (entityType == PartyEntityType.BUSINESS && gstRegistrationStatus == GstRegistrationStatus.REGISTERED && gstin.isBlank()) {
            return "GSTIN is required for a GST-registered Business party."
        }
        return null
    }
}
