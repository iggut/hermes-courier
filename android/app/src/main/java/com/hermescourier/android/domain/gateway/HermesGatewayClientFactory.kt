package com.hermescourier.android.domain.gateway

import android.content.Context
import com.hermescourier.android.domain.auth.AndroidKeystoreChallengeSigner
import com.hermescourier.android.domain.config.HermesGatewayConfiguration
import com.hermescourier.android.domain.storage.EncryptedHermesTokenStore
import com.hermescourier.android.domain.transport.HermesGatewayTransport
import com.hermescourier.android.domain.transport.HermesOkHttpClientFactory
import com.hermescourier.android.domain.transport.OkHttpHermesGatewayTransport

object HermesGatewayClientFactory {
    fun create(context: Context): HermesGatewayClient {
        val configuration = HermesGatewayConfiguration.from(context)
        val tokenStore = EncryptedHermesTokenStore(context)
        val signer = AndroidKeystoreChallengeSigner()
        val client = HermesOkHttpClientFactory.create(configuration)
        val transport: HermesGatewayTransport = OkHttpHermesGatewayTransport(
            baseUrl = configuration.baseUrl,
            client = client,
        )
        return NetworkHermesGatewayClient(
            transport = transport,
            tokenStore = tokenStore,
            signer = signer,
            okHttpClient = client,
            configuration = configuration,
        )
    }

    fun createOrNull(context: Context): HermesGatewayClient? = runCatching { create(context) }.getOrNull()
}
