package com.hermescourier.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    }

    @Test
    fun migrateQueuedApprovalAction_onlyMapsRejectCaseInsensitively() {
        assertEquals("deny", migrateQueuedApprovalAction("reject"))
        assertEquals("deny", migrateQueuedApprovalAction("REJECT"))
        assertEquals("approve", migrateQueuedApprovalAction("approve"))
    }

    @Test
    fun queuedApprovalActionMatchesResult_reconcilesLegacyAndWireValues() {
        val queued = HermesQueuedApprovalAction(
            approvalId = "approval-1",
            action = "reject",
            note = "Needs a quick review",
            createdAt = 123L,
        )
        val result = HermesApprovalActionResult(
            approvalId = "approval-1",
            action = "deny",
            status = "accepted",
            detail = "Applied",
            updatedAt = "now",
        )

        assertTrue(queuedApprovalActionMatchesResult(queued, result))
        assertFalse(
            queuedApprovalActionMatchesResult(
                queued,
                result.copy(approvalId = "approval-2"),
            ),
        )
    }
}
