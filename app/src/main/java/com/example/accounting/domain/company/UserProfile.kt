package com.example.accounting.domain.company

/**
 * An individual/professional's identity - distinct from [Company] (the business entity). Scope
 * note: this is a domain model only. There is no login/authentication or user<->company mapping
 * anywhere in this codebase today, so this deliberately does NOT introduce a new persistence
 * table, auth flow, or multi-company-per-user relation - that is a separate identity concern
 * from Phase 4's "Inventory & COGS" mandate. A future phase that adds real authentication can
 * persist this shape without redesigning it.
 */
data class UserProfile(
    val userId: String,
    val name: String,
    val guardianName: String = "", // Father's/Mother's name
    val dateOfBirth: String = "", // ISO-8601 YYYY-MM-DD
    val pan: String = "",
    val aadhaarLast4: String = "", // only the last 4 digits are ever retained, per PII minimization
    val address: String = "",
    val email: String = "",
    val phone: String = ""
)
