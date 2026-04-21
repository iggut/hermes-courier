package com.hermescourier.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun parseHermesEnrollmentPayload_readsBearerPairingFields() {
        val parsed = parseHermesEnrollmentPayload(
            payload = "hermes-courier-enroll://gateway?gatewayUrl=https%3A%2F%2Fexample.test&courierMode=bearer-token&bearerToken=abc123",
            defaultDeviceId = "device-default",
            defaultPublicKeyFingerprint = "fp-default",
            defaultAppVersion = "1.0.0",
            defaultIssuedAt = "now",
        )

        requireNotNull(parsed)
        assertEquals("https://example.test", parsed.gatewayUrl)
        assertEquals("bearer-token", parsed.courierMode)
        assertEquals("abc123", parsed.bearerToken)
        assertEquals("device-default", parsed.deviceId)
    }

    @Test
    fun parseHermesEnrollmentPayload_keepsLegacyPayloadCompatible() {
        val parsed = parseHermesEnrollmentPayload(
            payload = "hermes-courier-enroll://gateway?gatewayUrl=http%3A%2F%2F127.0.0.1%3A8787&deviceId=android-1",
            defaultDeviceId = "device-default",
            defaultPublicKeyFingerprint = "fp-default",
            defaultAppVersion = "1.0.0",
            defaultIssuedAt = "now",
        )

        requireNotNull(parsed)
        assertEquals("http://127.0.0.1:8787", parsed.gatewayUrl)
        assertEquals("android-1", parsed.deviceId)
        assertNull(parsed.courierMode)
        assertNull(parsed.bearerToken)
    }
}
