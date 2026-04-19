
package com.hermescourier.android.domain.transport

import com.hermescourier.android.domain.config.HermesGatewayConfiguration
import java.io.FileInputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

object HermesOkHttpClientFactory {
    fun create(configuration: HermesGatewayConfiguration): OkHttpClient {
        val builder = OkHttpClient.Builder()
        configuration.mtlsPkcs12File?.takeIf { it.exists() }?.let { pkcs12File ->
            val password = configuration.mtlsPkcs12Password ?: charArrayOf()
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                FileInputStream(pkcs12File).use { load(it, password) }
            }
            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, password)
            }
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(null as KeyStore?)
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
