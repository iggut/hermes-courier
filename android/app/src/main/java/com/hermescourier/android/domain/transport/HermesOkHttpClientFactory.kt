
package com.hermescourier.android.domain.transport

import com.hermescourier.android.domain.config.HermesGatewayConfiguration
import java.io.FileInputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

object HermesOkHttpClientFactory {
    fun create(configuration: HermesGatewayConfiguration): OkHttpClient {
        // OkHttp's default readTimeout of 10s is too tight for POST /v1/conversation, which
        // runs a synchronous Hermes CLI turn on the backend and can take 10–15s to answer
        // (or return an explicit "unsupported / did not complete in time" payload after ~15s).
        // With the old defaults the client raised a SocketTimeoutException and surfaced
        // "Send failed — timeout" while the server had already accepted the message and was
        // still producing a response. Widen the call/read/write ceilings so we either see
        // the real server response or a genuine network failure. connectTimeout stays tight
        // so truly unreachable gateways still fail fast.
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
        configuration.mtlsPkcs12File?.takeIf { it.exists() }?.let { pkcs12File ->
            val password = configuration.mtlsPkcs12Password
                ?: error("mTLS password required for enrolment before loading the imported PKCS#12 bundle")
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                FileInputStream(pkcs12File).use { load(it, password) }
            }
            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, password)
            }
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore)
            }
            val trustManager = trustManagerFactory.trustManagers.filterIsInstance<X509TrustManager>().first()
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(keyManagerFactory.keyManagers, arrayOf(trustManager), SecureRandom())
            }
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        }
        return builder.build()
    }
}
