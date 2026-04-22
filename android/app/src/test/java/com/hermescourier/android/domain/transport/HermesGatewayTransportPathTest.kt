package com.hermescourier.android.domain.transport

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class HermesGatewayTransportPathTest {

    private val base = "https://jupiter.tailecd7e7.ts.net/".toHttpUrl()

    @Test
    fun pathWithoutQuery_isAppendedAsSegments() {
        val resolved = base.resolvePathAndQuery("/v1/skills")
        assertEquals("https://jupiter.tailecd7e7.ts.net/v1/skills", resolved.toString())
    }

    @Test
    fun pathWithQueryString_isSplitIntoSegmentsAndQueryParams() {
        // Regression: the Phase-1 `/v1/logs?limit=100` call used to resolve to
        // `/v1/logs%3Flimit=100` because OkHttp's `addPathSegments` encodes `?`.
        val resolved = base.resolvePathAndQuery("v1/logs?limit=100&severity=info")
        assertEquals(
            "https://jupiter.tailecd7e7.ts.net/v1/logs?limit=100&severity=info",
            resolved.toString(),
        )
    }

    @Test
    fun trailingQuestionMark_addsNoQueryParams() {
        val resolved = base.resolvePathAndQuery("/v1/logs?")
        assertEquals("https://jupiter.tailecd7e7.ts.net/v1/logs", resolved.toString())
    }

    @Test
    fun valuelessQueryParam_isPreserved() {
        val resolved = base.resolvePathAndQuery("/v1/logs?tail")
        assertEquals("https://jupiter.tailecd7e7.ts.net/v1/logs?tail", resolved.toString())
    }
}
