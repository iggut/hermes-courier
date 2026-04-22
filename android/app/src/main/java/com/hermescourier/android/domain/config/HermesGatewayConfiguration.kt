package com.hermescourier.android.domain.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hermescourier.android.domain.model.HermesGatewaySettings
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val PREFS_NAME = "hermes_courier_gateway"
private const val KEY_BASE_URL = "gateway_base_url"
private const val KEY_CERT_PATH = "gateway_certificate_path"
private const val KEY_CERT_PASSWORD = "gateway_certificate_password"
// First-run default for this deployment: Tailscale HTTPS (devices cannot use dev-machine localhost).
// Token-only pairing overwrites persisted baseUrl immediately; this value only applies before pairing
// or after clearing app data.
private const val DEFAULT_GATEWAY_BASE_URL = "https://jupiter.tailecd7e7.ts.net"

private fun gatewayPrefs(context: Context) = EncryptedSharedPreferences.create(
    context,
    PREFS_NAME,
    MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
)

data class HermesGatewayConfiguration(
    val baseUrl: HttpUrl,
    val mtlsPkcs12File: File? = null,
    val mtlsPkcs12Password: CharArray? = null,
) {
    companion object {
        fun from(context: Context): HermesGatewayConfiguration = runCatching {
            val prefs = gatewayPrefs(context)
            val baseUrl = prefs.getString(KEY_BASE_URL, DEFAULT_GATEWAY_BASE_URL)!!.toHttpUrlOrNull()
                ?: DEFAULT_GATEWAY_BASE_URL.toHttpUrl()
            val certificatePath = prefs.getString(KEY_CERT_PATH, null)
            val certificatePassword = prefs.getString(KEY_CERT_PASSWORD, null)?.toCharArray()
            HermesGatewayConfiguration(
                baseUrl = baseUrl,
                mtlsPkcs12File = certificatePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() },
                mtlsPkcs12Password = certificatePassword?.takeIf { it.isNotEmpty() },
            )
        }.getOrElse {
            HermesGatewayConfiguration(baseUrl = DEFAULT_GATEWAY_BASE_URL.toHttpUrl())
        }

        suspend fun save(context: Context, settings: HermesGatewaySettings) {
            save(
                context,
                HermesGatewayConfiguration(
                    baseUrl = settings.baseUrl.toHttpUrl(),
                    mtlsPkcs12File = settings.certificatePath.takeIf { it.isNotBlank() }?.let(::File),
                    mtlsPkcs12Password = settings.certificatePassword.takeIf { it.isNotBlank() }?.toCharArray(),
                ),
            )
        }

        suspend fun save(context: Context, configuration: HermesGatewayConfiguration) {
            withContext(Dispatchers.IO) {
                val prefs = gatewayPrefs(context)
                val ok = prefs.edit()
                    .putString(KEY_BASE_URL, configuration.baseUrl.toString())
                    .putString(KEY_CERT_PATH, configuration.mtlsPkcs12File?.absolutePath.orEmpty())
                    .putString(KEY_CERT_PASSWORD, configuration.mtlsPkcs12Password?.concatToString().orEmpty())
                    .commit()
                if (!ok) throw IOException("Gateway settings commit failed")
            }
        }

        /**
         * Parses a user- or WebUI-provided base URL (including Tailscale MagicDNS / `*.ts.net` HTTPS) into
         * OkHttp's canonical [HttpUrl] for storage and bearer-token session matching.
         * Returns null when the value is not a valid http(s) base URL.
         */
        fun parseBaseUrlForPairingOrNull(input: String): HttpUrl? {
            val url = input.trim().toHttpUrlOrNull() ?: return null
            if (url.scheme != "https" && url.scheme != "http") return null
            return url.newBuilder().build()
        }
    }
}
