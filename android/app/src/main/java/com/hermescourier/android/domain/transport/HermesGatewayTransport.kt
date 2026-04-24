
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
    suspend fun delete(path: String, bearerToken: String? = null): String
}

class OkHttpHermesGatewayTransport(
    private val baseUrl: HttpUrl,
    private val client: OkHttpClient,
) : HermesGatewayTransport {
    override suspend fun get(path: String, bearerToken: String?): String {
        val requestBuilder = Request.Builder().url(baseUrl.resolvePathAndQuery(path))
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
            .url(baseUrl.resolvePathAndQuery(path))
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        bearerToken?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }
        val request = requestBuilder.build()
        return client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            println("Hermes: POST $path status: ${response.code}")
            if (!response.isSuccessful) throw IOException("POST $path failed with ${response.code}: $responseBody")
            responseBody
        }
    }

    override suspend fun delete(path: String, bearerToken: String?): String {
        val requestBuilder = Request.Builder().url(baseUrl.resolvePathAndQuery(path))
        bearerToken?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }
        val request = requestBuilder.delete().build()
        return client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("DELETE $path failed with ${response.code}: $responseBody")
            responseBody
        }
    }
}

/**
 * Splits `path` into segments and an optional query string before appending it
 * to [this]. OkHttp's `addPathSegments` percent-encodes `?`, `=`, and `&`, so a
 * caller-supplied path like `v1/logs?limit=100` would otherwise resolve to
 * `/v1/logs%3Flimit=100` and 404 against real gateways. Handling the split here
 * keeps every caller free to pass canonical paths that include query strings.
 */
internal fun HttpUrl.resolvePathAndQuery(path: String): HttpUrl {
    val trimmed = path.trimStart('/')
    val questionIndex = trimmed.indexOf('?')
    val builder = newBuilder()
    if (questionIndex < 0) {
        builder.addPathSegments(trimmed)
        return builder.build()
    }
    builder.addPathSegments(trimmed.substring(0, questionIndex))
    val rawQuery = trimmed.substring(questionIndex + 1)
    for (pair in rawQuery.split('&')) {
        if (pair.isBlank()) continue
        val eq = pair.indexOf('=')
        if (eq < 0) {
            builder.addQueryParameter(pair, null)
        } else {
            builder.addQueryParameter(pair.substring(0, eq), pair.substring(eq + 1))
        }
    }
    return builder.build()
}
