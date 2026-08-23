package com.example.accounting.domain.rendering

/**
 * The capital-account naming rule, keyed off [ConstitutionType] - a pure, side-effect-free
 * function. It only decides WHAT NAME a company's capital ledger should carry; it never creates,
 * renames, or posts to any ledger itself (that stays whichever existing, frozen ledger-creation
 * path - `AccountingRepository.createLedger` - a future integration point calls this from). No
 * automatic wiring into company/ledger setup exists yet - this is the rule, documented and tested
 * in isolation, ready for a future phase to consume at the actual point a company's opening Chart
 * of Accounts is built.
 */
object CapitalAccountNaming {
    private val CORPORATE_CONSTITUTIONS = setOf(ConstitutionType.PRIVATE_LIMITED, ConstitutionType.PUBLIC_LIMITED)

    /**
     * - [ConstitutionType.PROPRIETORSHIP] -> always exactly `"Capital - <personName>"` - never a
     *   generic "Capital Account", and never "Share Capital Account". Requires [personName]
     *   (the proprietor's own name, e.g. [IndividualProfile.name]) - throws [IllegalArgumentException]
     *   if blank, since a proprietorship's capital account is meaningless without the person's name.
     * - [ConstitutionType.PRIVATE_LIMITED] / [ConstitutionType.PUBLIC_LIMITED] (the only
     *   constitutions with actual issued share capital) -> `"Share Capital Account"`. No other
     *   constitution type ever produces this name.
     * - Every other constitution (Partnership/LLP/HUF/Trust/Society/Other) -> the generic
     *   `"Capital Account"` - not a proprietor's personal capital, and not share capital.
     */
    fun resolveCapitalAccountName(constitutionType: ConstitutionType, personName: String? = null): String = when {
        constitutionType == ConstitutionType.PROPRIETORSHIP -> {
            require(!personName.isNullOrBlank()) { "A proprietorship's capital account name requires the proprietor's name." }
            "Capital - $personName"
        }
        constitutionType in CORPORATE_CONSTITUTIONS -> "Share Capital Account"
        else -> "Capital Account"
    }

    /** `true` only for the two constitutions where "Share Capital Account" is ever the correct
     * name - a convenience predicate for a caller that wants to branch without re-deriving the
     * corporate/non-corporate distinction itself. */
    fun requiresShareCapital(constitutionType: ConstitutionType): Boolean = constitutionType in CORPORATE_CONSTITUTIONS
}
