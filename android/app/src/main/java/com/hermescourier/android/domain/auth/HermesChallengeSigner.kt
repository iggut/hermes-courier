
package com.hermescourier.android.domain.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.hermescourier.android.domain.model.HermesDeviceIdentity
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64

interface HermesChallengeSigner {
    fun publicKeyFingerprint(): String
    fun sign(nonce: String, device: HermesDeviceIdentity): String
}

/**
 * Builds the canonical signable message from a nonce and device identity.
 * The pipe-delimited format is: `nonce|deviceId|platform|appVersion`.
 */
internal fun buildChallengeSignableMessage(nonce: String, device: HermesDeviceIdentity): String =
    listOf(nonce, device.deviceId, device.platform, device.appVersion).joinToString("|")

class AndroidKeystoreChallengeSigner(
    private val alias: String = "hermes_courier_device_signing",
) : HermesChallengeSigner {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun publicKeyFingerprint(): String {
        ensureKeyPair()
        val publicKey = keyStore.getCertificate(alias).publicKey.encoded
        return sha256(publicKey)
    }

    override fun sign(nonce: String, device: HermesDeviceIdentity): String {
        ensureKeyPair()
        val privateKey = keyStore.getKey(alias, null) as java.security.PrivateKey? ?: error("Android Keystore private key missing for $alias")
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(buildChallengeSignableMessage(nonce, device).toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    private fun ensureKeyPair() {
        if (keyStore.containsAlias(alias)) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setKeySize(2048)
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
