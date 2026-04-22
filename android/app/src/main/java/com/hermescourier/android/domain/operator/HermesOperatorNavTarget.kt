package com.hermescourier.android.domain.operator

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * In-app navigation targets carried on `hermes-courier://nav/...` deep links (notifications,
 * widgets, adb). Parsing is JVM-safe for unit tests.
 */
sealed class HermesOperatorNavTarget {
    data object ApprovalsList : HermesOperatorNavTarget()

    data class ApprovalDetail(val approvalId: String) : HermesOperatorNavTarget()

    data class SessionDetailNav(val sessionId: String) : HermesOperatorNavTarget()

    data class ChatSession(val sessionId: String) : HermesOperatorNavTarget()

    companion object {
        fun parse(uriString: String): HermesOperatorNavTarget? {
            val trimmed = uriString.trim()
            if (trimmed.isEmpty()) return null
            val uri = runCatching { URI.create(trimmed) }.getOrNull() ?: return null
            if (!uri.scheme.equals("hermes-courier", ignoreCase = true)) return null
            if (!uri.host.equals("nav", ignoreCase = true)) return null
            val path = uri.path?.trim().orEmpty().trimStart('/').lowercase()
            val query = parseQuery(uri.rawQuery)
            return when (path) {
                "approvals" -> ApprovalsList
                "approval" -> {
                    val id = query["approvalid"]?.trim().orEmpty()
                    if (id.isBlank()) null else ApprovalDetail(id)
                }
                "session" -> {
                    val id = query["sessionid"]?.trim().orEmpty()
                    if (id.isBlank()) null else SessionDetailNav(id)
                }
                "chat" -> {
                    val id = query["sessionid"]?.trim().orEmpty()
                    if (id.isBlank()) null else ChatSession(id)
                }
                else -> null
            }
        }

        fun buildApprovalsListUri(): String = "hermes-courier://nav/approvals"

        fun buildApprovalDetailUri(approvalId: String): String =
            "hermes-courier://nav/approval?approvalId=${encodeQuery(approvalId)}"

        fun buildSessionDetailUri(sessionId: String): String =
            "hermes-courier://nav/session?sessionId=${encodeQuery(sessionId)}"

        fun buildChatSessionUri(sessionId: String): String =
            "hermes-courier://nav/chat?sessionId=${encodeQuery(sessionId)}"

        private fun encodeQuery(value: String): String =
            java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name())

        private fun parseQuery(raw: String?): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            val out = linkedMapOf<String, String>()
            for (part in raw.split('&')) {
                if (part.isBlank()) continue
                val idx = part.indexOf('=')
                val key = URLDecoder.decode(if (idx >= 0) part.substring(0, idx) else part, StandardCharsets.UTF_8.name())
                    .trim()
                    .lowercase(Locale.ROOT)
                val value = if (idx >= 0) {
                    URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8.name())
                } else {
                    ""
                }
                if (key.isNotBlank()) out[key] = value
            }
            return out
        }
    }
}
