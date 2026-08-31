package com.example.accounting.domain.profile

/** Result of one PIN-code-to-address lookup - every field a real fact returned by the lookup
 * source, never guessed/fabricated. [success] false means the caller should leave City/State/
 * Country for manual entry, never fall back to a default. */
data class PinCodeLookupResult(
    val pinCode: String,
    val city: String = "",
    val state: String = "",
    val country: String = "",
    val success: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Adapter boundary for PIN-code-to-City/State/Country lookup (Business/Individual Profile address
 * fields) - a pure Kotlin interface, no Android/network-library dependency here, mirroring
 * [com.example.accounting.domain.ocr.OcrIngestionAdapter]'s shape. The one real implementation
 * (`data/network/PostalPinCodeLookupAdapter`) calls a public, unauthenticated third-party API -
 * this is the one deliberate exception to this app's offline-first design, scoped to exactly this
 * lookup; it never blocks Business/Individual Profile from being saved when unavailable (City/
 * State/Country simply stay editable, empty, or whatever the user already typed).
 */
interface PinCodeLookupAdapter {
    suspend fun lookup(pinCode: String): PinCodeLookupResult
}
