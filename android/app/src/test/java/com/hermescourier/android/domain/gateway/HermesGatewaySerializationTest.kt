package com.hermescourier.android.domain.gateway

import com.hermescourier.android.domain.auth.buildChallengeSignableMessage
import com.hermescourier.android.domain.model.HermesAuthChallengeRequest
import com.hermescourier.android.domain.model.HermesAuthChallengeResponse
import com.hermescourier.android.domain.model.HermesAuthResponseRequest
import com.hermescourier.android.domain.model.HermesDeviceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesGatewaySerializationTest {

    private val testDevice = HermesDeviceIdentity(
        deviceId = "test-device-001",
        platform = "android",
        appVersion = "0.1.0",
        publicKeyFingerprint = "abcd1234",
    )

    @Test
    fun deviceIdentity_toJson_containsAllFields() {
        val json = testDevice.toJson()
        assertEquals("test-device-001", json.getString("deviceId"))
        assertEquals("android", json.getString("platform"))
        assertEquals("0.1.0", json.getString("appVersion"))
        assertEquals("abcd1234", json.getString("publicKeyFingerprint"))
    }

    @Test
    fun authChallengeRequest_toJson_containsDeviceAndNonce() {
        val request = HermesAuthChallengeRequest(device = testDevice, nonce = "test-nonce-value")
        val json = request.toJson()
        assertEquals("test-nonce-value", json.getString("nonce"))
        val device = json.getJSONObject("device")
        assertEquals("test-device-001", device.getString("deviceId"))
    }

    @Test
    fun authChallengeResponse_toJson_returnsRealPayload() {
        val response = HermesAuthChallengeResponse(
            challengeId = "challenge-123",
            nonce = "nonce-abc",
            expiresAt = "2026-01-01T00:00:00Z",
            trustLevel = "high",
        )
        val json = response.toJson()
        assertEquals("challenge-123", json.getString("challengeId"))
        assertEquals("nonce-abc", json.getString("nonce"))
        assertEquals("2026-01-01T00:00:00Z", json.getString("expiresAt"))
        assertEquals("high", json.getString("trustLevel"))
    }

    @Test
    fun authChallengeResponse_toJson_isDeterministic() {
        val response = HermesAuthChallengeResponse(
            challengeId = "ch-1",
            nonce = "nonce-1",
            expiresAt = "2026-06-01T00:00:00Z",
            trustLevel = "standard",
        )
        val first = response.toJson().toString()
        val second = response.toJson().toString()
        assertEquals(first, second)
    }

    @Test
    fun authResponseRequest_toJson_containsAllFields() {
        val request = HermesAuthResponseRequest(
            challengeId = "ch-42",
            signedNonce = "base64-signature",
            device = testDevice,
        )
        val json = request.toJson()
        assertEquals("ch-42", json.getString("challengeId"))
        assertEquals("base64-signature", json.getString("signedNonce"))
        val device = json.getJSONObject("device")
        assertEquals("test-device-001", device.getString("deviceId"))
    }

    @Test
    fun challengeSignableMessage_producesExpectedFormat() {
        val message = buildChallengeSignableMessage("test-nonce", testDevice)
        assertEquals("test-nonce|test-device-001|android|0.1.0", message)
    }

    @Test
    fun challengeSignableMessage_isDeterministic() {
        val first = buildChallengeSignableMessage("nonce-x", testDevice)
        val second = buildChallengeSignableMessage("nonce-x", testDevice)
        assertEquals(first, second)
    }

    @Test
    fun challengeSignableMessage_variesWithNonce() {
        val msg1 = buildChallengeSignableMessage("nonce-a", testDevice)
        val msg2 = buildChallengeSignableMessage("nonce-b", testDevice)
        assertTrue(msg1 != msg2)
    }

    @Test
    fun challengeSignableMessage_variesWithDevice() {
        val device1 = testDevice.copy(deviceId = "device-1")
        val device2 = testDevice.copy(deviceId = "device-2")
        val msg1 = buildChallengeSignableMessage("same-nonce", device1)
        val msg2 = buildChallengeSignableMessage("same-nonce", device2)
        assertTrue(msg1 != msg2)
    }
}
