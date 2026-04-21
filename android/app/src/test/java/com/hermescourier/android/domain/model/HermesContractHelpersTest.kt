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
            payload = "hermes-courier-enroll://gateway?gatewayUrl=https%3A%2F%2Fexample.test&deviceId=device-default&publicKeyFingerprint=fp-default&appVersion=1.0.0&issuedAt=now&courierMode=bearer-token&pairingMode=token-only&pairingContractVersion=2026-04-21&apiBasePath=%2Fv1&bearerToken=abc123",
        )

        requireNotNull(parsed)
        assertEquals("https://example.test", parsed.gatewayUrl)
        assertEquals("bearer-token", parsed.courierMode)
        assertEquals("abc123", parsed.bearerToken)
        assertEquals("token-only", parsed.pairingMode)
        assertEquals("2026-04-21", parsed.pairingContractVersion)
        assertEquals("/v1", parsed.apiBasePath)
        assertEquals("device-default", parsed.deviceId)
    }

    @Test
    fun parseHermesEnrollmentPayload_rejectsMissingRequiredContractFields() {
        val parsed = parseHermesEnrollmentPayload(
            payload = "hermes-courier-enroll://gateway?gatewayUrl=http%3A%2F%2F127.0.0.1%3A8787&deviceId=android-1",
        )

        assertNull(parsed)
    }

    @Test
    fun validateTokenOnlyPairingContract_rejectsMissingBearerToken() {
        val payload = HermesEnrollmentPayload(
            gatewayUrl = "http://127.0.0.1:8787",
            deviceId = "android-1",
            publicKeyFingerprint = "fp",
            appVersion = "1.0.0",
            issuedAt = "now",
            courierMode = "bearer-token",
            pairingMode = "token-only",
            bearerToken = null,
        )

        val validationError = validateTokenOnlyPairingContract(payload)
        assertEquals("Pairing import failed: token-only pairing requires bearerToken", validationError)
    }
}
