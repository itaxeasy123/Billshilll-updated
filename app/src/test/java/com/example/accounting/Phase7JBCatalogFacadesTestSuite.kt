package com.example.accounting

import com.example.accounting.application.profession.BusinessProfessionService
import com.example.accounting.domain.profession.StandardBusinessProfessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7J-B - [BusinessProfessionService] ("Business/Profession" scope) - a thin, read-only
 * catalog facade over the existing, frozen [StandardBusinessProfessions]. No persistence, no
 * company attachment (an explicitly open question this phase does not resolve, per
 * [com.example.accounting.domain.profession.BusinessProfession]'s own doc comment).
 */
class Phase7JBCatalogFacadesTestSuite {

    private val service = BusinessProfessionService()

    @Test
    fun testListStandardProfessions_returnsFullCatalog() {
        val professions = service.listStandardProfessions()
        assertEquals(StandardBusinessProfessions.ALL.size, professions.size)
        assertTrue(professions.contains(StandardBusinessProfessions.RETAILER))
    }

    @Test
    fun testFindByCode_knownCode_roundTrips() {
        val found = service.findByCode("GOLDSMITH")
        assertEquals(StandardBusinessProfessions.GOLDSMITH, found)
    }

    @Test
    fun testFindByCode_unknownCode_returnsNull() {
        assertNull(service.findByCode("NOT_A_REAL_CODE"))
    }

    @Test
    fun testNoRateOrTaxField_onAnyStandardProfession() {
        // Structural guarantee, matching every 7J contract's own "no rate field" check.
        val fields = com.example.accounting.domain.profession.BusinessProfession::class.java.declaredFields
        assertTrue(fields.none { it.name.lowercase().let { n -> n.contains("rate") || n.contains("tax") || n.contains("percent") } })
    }
}
