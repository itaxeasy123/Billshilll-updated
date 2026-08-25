package com.example.accounting

import com.example.accounting.core.common.Money
import com.example.accounting.domain.accounting.GstRegistrationStatus
import com.example.accounting.domain.accounting.Ledger
import com.example.accounting.domain.taxation.gst.GSTRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused tests for the new [GstRegistrationStatus]/[Ledger.gstRegistrationStatus] domain
 * addition - the approved modeling decision from the read-only GST-registration audit (status
 * belongs on Ledger, not Party, alongside GSTIN/PAN/stateCode). Pure domain-model tests only: no
 * RCM, no GST calculation, no Purchase/Sales change is exercised here, because none was made.
 */
class LedgerGstRegistrationStatusTestSuite {

    private fun minimalLedger(gstRegistrationStatus: GstRegistrationStatus? = null) = Ledger(
        ledgerId = "LED_1", companyId = "COMP_1", groupId = "GRP_1", name = "Test Ledger",
        gstRegistrationStatus = gstRegistrationStatus
    )

    @Test
    fun testExistingLedgerConstruction_withoutNewField_stillCompilesAndWorks() {
        // No gstRegistrationStatus argument at all - proves every pre-existing call site (which
        // never mentions this field) still compiles and behaves identically.
        val ledger = Ledger(ledgerId = "LED_1", companyId = "COMP_1", groupId = "GRP_1", name = "Test Ledger")

        assertEquals("LED_1", ledger.ledgerId)
        assertEquals(Money.ZERO, ledger.openingBalance)
    }

    @Test
    fun testDefaultGstRegistrationStatus_isNull() {
        val ledger = minimalLedger()

        assertNull(ledger.gstRegistrationStatus)
    }

    @Test
    fun testExplicitRegistered_isStoredCorrectly() {
        val ledger = minimalLedger(GstRegistrationStatus.REGISTERED)

        assertEquals(GstRegistrationStatus.REGISTERED, ledger.gstRegistrationStatus)
    }

    @Test
    fun testExplicitUnregistered_isStoredCorrectly() {
        val ledger = minimalLedger(GstRegistrationStatus.UNREGISTERED)

        assertEquals(GstRegistrationStatus.UNREGISTERED, ledger.gstRegistrationStatus)
    }

    @Test
    fun testLedgerEqualityAndCopy_preserveGstRegistrationStatus() {
        val registered = minimalLedger(GstRegistrationStatus.REGISTERED)
        val sameRegistered = minimalLedger(GstRegistrationStatus.REGISTERED)
        val unregisteredCopy = registered.copy(gstRegistrationStatus = GstRegistrationStatus.UNREGISTERED)

        assertEquals(registered, sameRegistered)
        assertEquals(GstRegistrationStatus.UNREGISTERED, unregisteredCopy.gstRegistrationStatus)
        // copy() must not have disturbed any other field.
        assertEquals(registered.ledgerId, unregisteredCopy.ledgerId)
        assertEquals(registered.name, unregisteredCopy.name)
    }

    @Test
    fun testNull_isNeverEqualToEitherExplicitStatus() {
        // Guards the "null means UNKNOWN, never REGISTERED or UNREGISTERED" rule at the type level.
        val unknown = minimalLedger(null)

        assertTrue(unknown.gstRegistrationStatus != GstRegistrationStatus.REGISTERED)
        assertTrue(unknown.gstRegistrationStatus != GstRegistrationStatus.UNREGISTERED)
    }

    @Test
    fun testExistingGstinValidation_behaviorUnchanged() {
        // GSTRules.isValidGSTIN was not touched by this change - blank remains valid ("optional for
        // non-GST parties"), a well-formed GSTIN remains valid, malformed text remains invalid.
        assertTrue(GSTRules.isValidGSTIN(""))
        assertTrue(GSTRules.isValidGSTIN("27AAAAA0000A1Z5"))
        assertTrue(!GSTRules.isValidGSTIN("not-a-gstin"))
    }
}
