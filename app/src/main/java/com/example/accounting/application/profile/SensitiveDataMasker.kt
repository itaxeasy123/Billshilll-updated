package com.example.accounting.application.profile

/**
 * Masks statutory/financial identifiers before they ever reach a log line or a generic summary
 * list endpoint - PAN, GSTIN, and bank account numbers are exactly the fields
 * `docs/45_DOCUMENT_BRANDING.md`'s security section flags as sensitive. This is presentation-of-
 * logs/lists only - it never touches the authoritative stored value, and no masked value is ever
 * written back to persistence.
 */
object SensitiveDataMasker {
    private const val MASK_CHAR = '*'

    /** PAN is a fixed 10-character format (5 letters, 4 digits, 1 letter), e.g. `"ABCDE1234F"` ->
     * `"ABCDE****F"` - keeps the first 5 and last 1 characters, masks the middle 4. */
    fun maskPan(pan: String): String = maskMiddle(pan, keepStart = 5, keepEnd = 1)

    /** GSTIN is a fixed 15-character format (2-digit state code + 10-char PAN + 3 more), e.g.
     * `"27AAAAA0000A1Z5"` -> `"27AAA********Z5"` - keeps the first 5 (state code + first 3 PAN
     * letters) and last 2, masks the middle 8. */
    fun maskGstin(gstin: String): String = maskMiddle(gstin, keepStart = 5, keepEnd = 2)

    /** Bank account numbers have no fixed length/format across banks - the standard banking-
     * industry convention (statements, card numbers) is to reveal only the last 4 digits, e.g.
     * `"123456789012"` -> `"********9012"`. */
    fun maskBankAccountNumber(accountNumber: String): String = maskMiddle(accountNumber, keepStart = 0, keepEnd = 4)

    /**
     * Generic sensitive-value masking for logs and generic summary lists (e.g.
     * [com.example.accounting.domain.banking.BankUpiProfile] account numbers, or a PAN in a
     * context that just needs "reveal the last N characters" rather
     * than [maskPan]'s fixed PAN-format masking) - reveals only the last [visibleSuffixLength]
     * characters, masking everything before them with `'X'`, e.g.
     * `maskSensitiveData("123456789012", 4)` -> `"XXXXXXXX1234"`. A blank input passes through
     * unchanged (nothing to leak); an input no longer than [visibleSuffixLength] is masked in full
     * rather than accidentally revealing the whole value. This never touches the authoritative
     * stored value and never mutates any ledger, account, or party record - it is a pure string
     * transform for presentation/log use only.
     */
    fun maskSensitiveData(input: String, visibleSuffixLength: Int): String {
        require(visibleSuffixLength >= 0) { "visibleSuffixLength must not be negative" }
        if (input.isBlank()) return input
        if (input.length <= visibleSuffixLength) return "X".repeat(input.length)
        val visibleSuffix = input.takeLast(visibleSuffixLength)
        return "X".repeat(input.length - visibleSuffixLength) + visibleSuffix
    }

    /** Shared masking primitive: reveals [keepStart] leading and [keepEnd] trailing characters,
     * masks everything in between. A blank input passes through unchanged (nothing to leak); an
     * input too short to have a meaningful "middle" is masked in full rather than accidentally
     * revealing the whole value. */
    private fun maskMiddle(value: String, keepStart: Int, keepEnd: Int): String {
        if (value.isBlank()) return value
        if (value.length <= keepStart + keepEnd) return MASK_CHAR.toString().repeat(value.length)
        val start = value.take(keepStart)
        val end = value.takeLast(keepEnd)
        val maskedLength = value.length - keepStart - keepEnd
        return start + MASK_CHAR.toString().repeat(maskedLength) + end
    }
}
