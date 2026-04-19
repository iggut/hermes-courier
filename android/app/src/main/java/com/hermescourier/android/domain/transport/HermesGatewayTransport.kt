
package com.hermescourier.android.domain.transport

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException

interface HermesGatewayTransport {
    suspend fun get(path: String, bearerToken: String? = null): String
    suspend fun post(path: String, body: JSONObject, bearerToken: String? = null): String
}

class OkHttpHermesGatewayTransport(
    private val baseUrl: HttpUrl,
    private val client: OkHttpClient,
) : HermesGatewayTransport {
    override suspend fun get(path: String, bearerToken: String?): String {
        val requestBuilder = Request.Builder().url(baseUrl.newBuilder().addPathSegments(path.trimStart('/')).build())
        bearerToken?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }
        val request = requestBuilder.get().build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("GET $path failed with ${response.code}: $body")
            body
        }
    }

    override suspend fun post(path: String, body: JSONObject, bearerToken: String?): String {
        val requestBuilder = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments(path.trimStart('/')).build())
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        bearerToken?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }
        val request = requestBuilder.build()
        return client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("POST $path failed with ${response.code}: $responseBody")
            responseBody
        }
    }
}
