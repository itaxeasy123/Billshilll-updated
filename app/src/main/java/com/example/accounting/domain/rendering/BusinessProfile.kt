package com.example.accounting.domain.rendering

/**
 * A company's legal constitution/entity type - governs, among other things, whether a
 * "Share Capital Account" is ever an appropriate capital-ledger name (see
 * [CapitalAccountNaming]). Purely a classification/document-branding concern here - it does not
 * itself create or rename any ledger; [CapitalAccountNaming.resolveCapitalAccountName] is the one
 * function that consumes it for that purpose, and only when explicitly called.
 */
enum class ConstitutionType {
    PROPRIETORSHIP,
    PARTNERSHIP,
    LLP,
    PRIVATE_LIMITED,
    PUBLIC_LIMITED,
    HUF,
    TRUST,
    SOCIETY,
    OTHER
}

/**
 * Document-branding identity for a company (Phase 7D) - deliberately separate from
 * [com.example.accounting.domain.company.Company], which stays the sole authoritative source for
 * statutory/accounting fields (`gstin`, `pan`, `stateCode`, etc.). `businessName` (the trade name
 * shown on documents) and `address` here default-echo Company's own fields at creation time but
 * are independently editable afterward (e.g. a shorter trading name on invoices than the
 * registered legal name in [legalName]) - this is a rendering preference, never a correction to
 * Company's authoritative data. One [BusinessProfile] per companyId (company-level, never
 * financial-year-specific - there is deliberately no `financialYearId` field here, mirroring
 * [com.example.accounting.domain.party.Party]'s 1:1-with-Ledger convention).
 *
 * [gstin]/[pan]/[tan]/[udyam] are the plain, authoritative stored values (unchanged shape from
 * their Phase 7D introduction, so every existing consumer - `assembleDocumentData`, the Phase 7E
 * export DTOs - keeps reading them exactly as before). [BusinessIdentifiers.kt] provides
 * structured, validated wrapper types (`Pan`/`Gstin`/`Tan`/`Udyam`) constructed on demand from
 * these raw strings wherever format validation or a typed representation is actually needed -
 * never a second, independently-stored copy of the same identifier.
 *
 * [logoAssetId]/[signatureAssetId]/[qrCodeAssetId] are nullable references into [DocumentAsset] -
 * the binary image itself is never stored on this row (Section 8: "do not store huge binary
 * assets directly inside accounting tables").
 */
data class BusinessProfile(
    val businessProfileId: String,
    val companyId: String,
    val businessName: String,
    val legalName: String = businessName,
    val constitutionType: ConstitutionType = ConstitutionType.PROPRIETORSHIP,
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val gstin: String = "",
    val pan: String = "",
    val tan: String = "",
    val udyam: String = "",
    val logoAssetId: String? = null,
    val bankName: String = "",
    val bankAccountNumber: String = "",
    val bankIfsc: String = "",
    val bankBranch: String = "",
    val upiId: String = "",
    val qrCodeAssetId: String? = null,
    val signatureAssetId: String? = null,
    val termsAndConditions: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Proprietor identity for an individual-run company (Phase 7D), kept structurally separate from
 * [BusinessProfile] - a sole proprietor's accounting capital-account naming rule (see
 * [CapitalAccountNaming] - always literally `"Capital - <name>"`, never auto-labelled "Share
 * Capital Account") is already independent of this; this model exists purely so a proprietor can
 * render documents under a personal identity distinct from an incorporated business identity.
 * [name] is the proprietor's own name - the same value [CapitalAccountNaming.resolveCapitalAccountName]
 * expects as `personName` when this company's constitution is [ConstitutionType.PROPRIETORSHIP].
 * One [IndividualProfile] per companyId (company-level, never financial-year-specific, same as
 * [BusinessProfile]). Which of the two profiles a given render should prefer is a selection
 * concern deferred to Phase 7G/7J - [assembleDocumentData] (Phase 7D) defaults to [BusinessProfile]
 * when both exist; see `docs/45_DOCUMENT_BRANDING.md` for the open boundary this leaves for later
 * phases.
 */
data class IndividualProfile(
    val individualProfileId: String,
    val companyId: String,
    val name: String,
    val address: String = "",
    val pan: String = "",
    val phone: String = "",
    val email: String = "",
    val signatureAssetId: String? = null,
    val termsAndConditions: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
