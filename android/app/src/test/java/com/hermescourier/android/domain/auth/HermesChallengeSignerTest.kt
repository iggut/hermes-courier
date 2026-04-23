package com.hermescourier.android.domain.auth

import com.hermescourier.android.domain.model.HermesDeviceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/**
 * JVM-level verification that the signing flow (build message → sign → base64)
 * produces a non-empty, deterministic, verifiable signature.
 *
 * [AndroidKeystoreChallengeSigner] itself cannot run on a JVM runner because it
 * depends on the Android KeyStore SPI.  This test exercises the same crypto
 * primitives (SHA256withRSA + PKCS#1) that [AndroidKeystoreChallengeSigner.sign]
 * uses, proving that [buildChallengeSignableMessage] produces the correct input
 * and that the signing round-trip is stable.
 */
class HermesChallengeSignerTest {

    private val testDevice = HermesDeviceIdentity(
        deviceId = "test-device-abc",
        platform = "android",
        appVersion = "1.0.0",
        publicKeyFingerprint = "deadbeef",
    )

    /** A plain-JVM RSA keypair that mirrors what AndroidKeystore generates. */
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048)
    }.generateKeyPair()

    private fun signWithJreKey(nonce: String, device: HermesDeviceIdentity): String {
        val message = buildChallengeSignableMessage(nonce, device)
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(keyPair.private)
        sig.update(message.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    @Test
    fun sign_returnsNonEmptySignature() {
        val result = signWithJreKey("nonce-123", testDevice)
        assertTrue("Signature must not be blank", result.isNotBlank())
    }

    @Test
    fun sign_isDeterministicForSameInput() {
        val first = signWithJreKey("stable-nonce", testDevice)
        val second = signWithJreKey("stable-nonce", testDevice)
        assertEquals("Same nonce+device must produce the same signature", first, second)
    }

    @Test
    fun sign_variesWithNonce() {
        val sigA = signWithJreKey("nonce-a", testDevice)
        val sigB = signWithJreKey("nonce-b", testDevice)
        assertNotEquals("Different nonces must produce different signatures", sigA, sigB)
    }

    @Test
    fun sign_variesWithDevice() {
        val device2 = testDevice.copy(deviceId = "other-device")
        val sig1 = signWithJreKey("same-nonce", testDevice)
        val sig2 = signWithJreKey("same-nonce", device2)
        assertNotEquals("Different devices must produce different signatures", sig1, sig2)
    }

    @Test
    fun sign_verifiesAgainstPublicKey() {
        val nonce = "verify-me"
        val message = buildChallengeSignableMessage(nonce, testDevice)
        val signatureB64 = signWithJreKey(nonce, testDevice)

        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(keyPair.public)
        verifier.update(message.toByteArray(StandardCharsets.UTF_8))
        val verified = verifier.verify(Base64.getDecoder().decode(signatureB64))
        assertTrue("Signature must verify against the public key", verified)
    }

    @Test
    fun sign_usesFullSignableMessageNotJustNonce() {
        // Prove the signature covers the full pipe-delimited message, not just the nonce.
        val nonce = "covers-all"
        val signatureB64 = signWithJreKey(nonce, testDevice)

        // Attempting to verify with just the nonce (not the full message) must fail.
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(keyPair.public)
        verifier.update(nonce.toByteArray(StandardCharsets.UTF_8))
        val verified = verifier.verify(Base64.getDecoder().decode(signatureB64))
        assertTrue("Verifying with raw nonce must fail (sign must cover full message)", !verified)
    }
}
