package com.example.accounting.domain.rendering

/**
 * Structured, validated wrapper types for [BusinessProfile]'s statutory identifier fields
 * (PAN/GSTIN/TAN/UDYAM). These are constructed on demand from the plain, authoritative stored
 * strings (`BusinessProfile.pan`/`.gstin`/`.tan`/`.udyam`) - never a second, independently
 * persisted copy of the identifier, and never a replacement for the raw field every existing
 * Phase 7D/7E consumer (`assembleDocumentData`, the export DTOs) already reads unchanged. Use
 * these wherever format validation or a typed, self-documenting representation is genuinely
 * needed (e.g. a future profile-edit form); the domain model itself stays a plain string for
 * maximum backward compatibility.
 *
 * Each `from()` factory returns `null` (never throws) for a blank or malformed input - validation
 * failure is the caller's business logic to react to, not an exception this parsing layer forces
 * on every call site.
 */

private val PAN_PATTERN = Regex("^[A-Z]{5}[0-9]{4}[A-Z]$")
private val GSTIN_PATTERN = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")
private val TAN_PATTERN = Regex("^[A-Z]{4}[0-9]{5}[A-Z]$")
private val UDYAM_PATTERN = Regex("^UDYAM-[A-Z]{2}-[0-9]{2}-[0-9]{7}$")

data class Pan(val value: String) {
    companion object {
        fun from(raw: String): Pan? = raw.trim().uppercase().takeIf { PAN_PATTERN.matches(it) }?.let { Pan(it) }
        fun isValid(raw: String): Boolean = PAN_PATTERN.matches(raw.trim().uppercase())
    }
}

data class Gstin(val value: String) {
    /** The first 2 characters of a valid GSTIN are the GST state code, and characters 3-12 are
     * the embedded PAN - both are read-only derived views over the same stored string, never
     * separately persisted. */
    val stateCode: String get() = value.take(2)
    val embeddedPan: String get() = value.substring(2, 12)

    companion object {
        fun from(raw: String): Gstin? = raw.trim().uppercase().takeIf { GSTIN_PATTERN.matches(it) }?.let { Gstin(it) }
        fun isValid(raw: String): Boolean = GSTIN_PATTERN.matches(raw.trim().uppercase())
    }
}

data class Tan(val value: String) {
    companion object {
        fun from(raw: String): Tan? = raw.trim().uppercase().takeIf { TAN_PATTERN.matches(it) }?.let { Tan(it) }
        fun isValid(raw: String): Boolean = TAN_PATTERN.matches(raw.trim().uppercase())
    }
}

/** UDYAM registration number (MSME registration, e.g. `"UDYAM-MH-01-0001234"`) - optional on
 * [BusinessProfile]; most companies have no UDYAM registration at all, so a blank string is the
 * normal case, not an error. */
data class Udyam(val value: String) {
    companion object {
        fun from(raw: String): Udyam? = raw.trim().uppercase().takeIf { UDYAM_PATTERN.matches(it) }?.let { Udyam(it) }
        fun isValid(raw: String): Boolean = UDYAM_PATTERN.matches(raw.trim().uppercase())
    }
}
