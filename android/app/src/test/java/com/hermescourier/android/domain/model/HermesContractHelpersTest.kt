package com.hermescourier.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HermesContractHelpersTest {

    @Test
    fun userFacingApprovalVerb_mapsWireAndLegacyReject() {
        assertEquals("Reject", userFacingApprovalVerb("deny"))
        assertEquals("Reject", userFacingApprovalVerb("reject"))
        assertEquals("Approve", userFacingApprovalVerb("approve"))
        assertEquals("Reject", userFacingApprovalVerb("  Deny  "))
    }

    @Test
    fun userFacingApprovalVerb_unknownPassesThroughCapitalized() {
        assertEquals("Hold", userFacingApprovalVerb("hold"))
    }

    @Test
    fun normalizeApprovalDecisionWire_rejectToDenyAndLowercases() {
        assertEquals("deny", normalizeApprovalDecisionWire("reject"))
        assertEquals("deny", normalizeApprovalDecisionWire("REJECT"))
        assertEquals("approve", normalizeApprovalDecisionWire("Approve"))
        assertEquals("deny", normalizeApprovalDecisionWire("deny"))
    }

    @Test
    fun migrateQueuedApprovalAction_onlyMapsRejectCaseInsensitively() {
        assertEquals("deny", migrateQueuedApprovalAction("reject"))
        assertEquals("deny", migrateQueuedApprovalAction("Reject"))
        assertEquals("approve", migrateQueuedApprovalAction("approve"))
        assertEquals("deny", migrateQueuedApprovalAction("deny"))
    }
}
