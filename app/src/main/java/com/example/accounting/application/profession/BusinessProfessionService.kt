package com.example.accounting.application.profession

import com.example.accounting.domain.profession.BusinessProfession
import com.example.accounting.domain.profession.StandardBusinessProfessions

/**
 * Read-only catalog facade over [StandardBusinessProfessions] (Phase 7J-B, "Business/Profession") -
 * no persistence, no company attachment. How a profession eventually attaches to
 * [com.example.accounting.domain.company.Company]/[com.example.accounting.domain.rendering.BusinessProfile]
 * stays an explicitly open question (per [BusinessProfession]'s own doc comment) - not resolved
 * here, matching the "never touch a frozen type speculatively" discipline this codebase has already
 * applied twice to this exact question.
 */
class BusinessProfessionService {

    fun listStandardProfessions(): List<BusinessProfession> = StandardBusinessProfessions.ALL

    fun findByCode(code: String): BusinessProfession? = StandardBusinessProfessions.ALL.firstOrNull { it.code == code }
}
