package com.example.accounting.domain.banking

/**
 * UPI metadata for a [BankUpiProfile] - kept as its own value type rather than flattened onto
 * [BankUpiProfile] since a single bank account can have more than one linked UPI handle over
 * time, and a UPI-only settlement path (no bank account captured yet) is common enough to model
 * independently.
 */
data class UpiMetadata(
    val upiId: String,
    val payeeName: String = "",
    val isVerified: Boolean = false
)

/**
 * Bank + UPI settlement/contact details (Phase 7G) - deliberately outside the double-entry
 * accounting stream: this is metadata for issuing or receiving a payment, never a
 * [com.example.accounting.domain.accounting.Ledger], [com.example.accounting.domain.accounting.Voucher],
 * or [com.example.accounting.domain.accounting.JournalItem]. Nothing in this file reads or writes
 * `AccountingDao`/`AccountingRepository` - it is a pure data holder, so no ledger running balance
 * can ever be mutated by creating, reading, or editing a [BankUpiProfile].
 *
 * Distinct from [com.example.accounting.domain.rendering.BusinessProfile]'s single
 * `bankName`/`bankAccountNumber`/`bankIfsc`/`upiId` fields (one bank identity used for document
 * branding) - this model supports multiple bank/UPI profiles per company or per party (e.g. a
 * supplier's payout account, or a customer's refund account), each independently identified.
 * [partyId] is null for the company's own profile, non-null when this profile belongs to a
 * specific [com.example.accounting.domain.party.Party].
 *
 * Sensitive fields ([accountNumber], [ifscCode]) should never be logged or shown in a generic
 * summary list unmasked - see [com.example.accounting.application.profile.SensitiveDataMasker.maskSensitiveData].
 */
data class BankUpiProfile(
    val bankUpiProfileId: String,
    val companyId: String,
    val partyId: String? = null,
    val bankName: String,
    val accountHolderName: String = "",
    val accountNumber: String,
    val ifscCode: String,
    val branchName: String = "",
    val upi: UpiMetadata? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
