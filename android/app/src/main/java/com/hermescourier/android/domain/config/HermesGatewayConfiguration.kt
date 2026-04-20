package com.hermescourier.android.domain.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hermescourier.android.domain.model.HermesGatewaySettings
import java.io.File
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val PREFS_NAME = "hermes_courier_gateway"
private const val KEY_BASE_URL = "gateway_base_url"
private const val KEY_CERT_PATH = "gateway_certificate_path"
private const val KEY_CERT_PASSWORD = "gateway_certificate_password"

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
            val baseUrl = prefs.getString(KEY_BASE_URL, "https://gateway.hermes.local")!!.toHttpUrlOrNull()
                ?: "https://gateway.hermes.local".toHttpUrl()
            val certificatePath = prefs.getString(KEY_CERT_PATH, null)
            val certificatePassword = prefs.getString(KEY_CERT_PASSWORD, null)?.toCharArray()
            HermesGatewayConfiguration(
                baseUrl = baseUrl,
                mtlsPkcs12File = certificatePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() },
                mtlsPkcs12Password = certificatePassword?.takeIf { it.isNotEmpty() },
            )
        }.getOrElse {
            HermesGatewayConfiguration(baseUrl = "https://gateway.hermes.local".toHttpUrl())
        }

        fun save(context: Context, settings: HermesGatewaySettings) {
            save(
                context,
                HermesGatewayConfiguration(
                    baseUrl = settings.baseUrl.toHttpUrl(),
                    mtlsPkcs12File = settings.certificatePath.takeIf { it.isNotBlank() }?.let(::File),
                    mtlsPkcs12Password = settings.certificatePassword.takeIf { it.isNotBlank() }?.toCharArray(),
                ),
            )
        }

        fun save(context: Context, configuration: HermesGatewayConfiguration) {
            runCatching {
                val prefs = gatewayPrefs(context)
                prefs.edit()
                    .putString(KEY_BASE_URL, configuration.baseUrl.toString())
                    .putString(KEY_CERT_PATH, configuration.mtlsPkcs12File?.absolutePath.orEmpty())
                    .putString(KEY_CERT_PASSWORD, configuration.mtlsPkcs12Password?.concatToString().orEmpty())
                    .apply()
            }
        }
    }
}
