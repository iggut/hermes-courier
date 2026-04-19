
package com.hermescourier.android.domain.config

import android.content.Context
import java.io.File
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

data class HermesGatewayConfiguration(
    val baseUrl: HttpUrl,
    val mtlsPkcs12File: File? = null,
    val mtlsPkcs12Password: CharArray? = null,
) {
    companion object {
        fun from(context: Context): HermesGatewayConfiguration {
            val prefs = context.getSharedPreferences("hermes_courier_gateway", Context.MODE_PRIVATE)
            val baseUrl = prefs.getString("gateway_base_url", "https://gateway.hermes.local")!!.toHttpUrl()
            val pkcs12Path = prefs.getString("gateway_client_pkcs12_path", null)
            val pkcs12Password = prefs.getString("gateway_client_pkcs12_password", null)
            return HermesGatewayConfiguration(
                baseUrl = baseUrl,
                mtlsPkcs12File = pkcs12Path?.let(::File),
                mtlsPkcs12Password = pkcs12Password?.toCharArray(),
            )
        }
    }
}
