package com.hermescourier.android.domain.gateway

import org.junit.Assert.assertEquals
import org.junit.Test

class HermesProtocolFixtureTest {

    @Test
    fun sessionDetail_decodes() {
        val s = readFixture("session-detail").toJsonObject().toSessionSummary()
        assertEquals("sess-fixture-1", s.sessionId)
        assertEquals("Fixture session", s.title)
        assertEquals("Running", s.status)
        assertEquals("2026-04-21T12:00:00Z", s.updatedAt)
    }

    @Test
    fun approvalDecisionSuccess_decodes() {
        val r = readFixture("approval-decision-success").toJsonObject().toApprovalActionResult()
        assertEquals("appr-99", r.approvalId)
        assertEquals("deny", r.action)
        assertEquals("accepted", r.status)
    }

    @Test
    fun sessionControlSuccess_decodes_withEndpointAndSupportedFlag() {
        val r = readFixture("session-control-success").toJsonObject().toSessionControlActionResult()
        assertEquals("sess-fixture-1", r.sessionId)
        assertEquals("pause", r.action)
        assertEquals("accepted", r.status)
        assertEquals("/v1/sessions/sess-fixture-1/actions", r.endpoint)
        assertEquals(true, r.supported)
    }

    @Test
    fun sessionControlUnsupported_decodes() {
        val r = readFixture("session-control-unsupported").toJsonObject().toSessionControlActionResult()
        assertEquals("unsupported", r.status)
        assertEquals(false, r.supported)
    }

    @Test
    fun realtimeConversationEvent_usesKindAndEventAliases() {
        val env = readFixture("realtime-conversation-event").toJsonObject().toRealtimeEnvelope()
        assertEquals("conversation", env.type)
        val ev = checkNotNull(env.conversation)
        assertEquals("ev-fix-1", ev.eventId)
        assertEquals("env-fix-1", env.eventId)
        assertEquals("2026-04-21T12:04:01Z", env.eventTimestamp)
    }

    /**
     * Phase-3 backend stamps every conversation event with the session it belongs to.
     * The field is forwarded untouched when present and stays null otherwise, so older
     * servers and locally-authored optimistic events still decode.
     */
    @Test
    fun conversationEvent_parsesSessionIdWhenPresent() {
        val json = """
            {
              "eventId": "ev-sess-1",
              "author": "Hermes",
              "body": "Session-scoped reply",
              "timestamp": "2026-04-22T09:00:00Z",
              "sessionId": "sess-abc"
            }
        """.trimIndent().toJsonObject().toConversationEvent()
        assertEquals("sess-abc", json.sessionId)

        val legacy = """
            {
              "eventId": "ev-legacy-1",
              "author": "Hermes",
              "body": "Pre-Phase-3",
              "timestamp": "2026-04-22T09:00:00Z"
            }
        """.trimIndent().toJsonObject().toConversationEvent()
        assertEquals(null, legacy.sessionId)
    }

    @Test
    fun realtimeConversationEnvelope_carriesNestedSessionId() {
        val envelope = """
            {
              "type": "conversation",
              "conversation": {
                "eventId": "ev-sess-2",
                "author": "Hermes",
                "body": "Realtime scoped",
                "timestamp": "2026-04-22T09:01:00Z",
                "sessionId": "sess-xyz"
              },
              "eventId": "env-1"
            }
        """.trimIndent().toJsonObject().toRealtimeEnvelope()
        val conv = checkNotNull(envelope.conversation)
        assertEquals("sess-xyz", conv.sessionId)
    }

    @Test
    fun realtimeApprovalResult_usesSnakeCaseAlias() {
        val env = readFixture("realtime-approval-result").toJsonObject().toRealtimeEnvelope()
        val r = checkNotNull(env.approvalResult)
        assertEquals("appr-42", r.approvalId)
        assertEquals("approve", r.action)
    }

    @Test
    fun realtimeSessionControlResult_usesSnakeCaseAlias() {
        val env = readFixture("realtime-session-control-result").toJsonObject().toRealtimeEnvelope()
        val r = checkNotNull(env.sessionControlResult)
        assertEquals("sess-fix-x", r.sessionId)
        assertEquals("resume", r.action)
        assertEquals("/v1/sessions/sess-fix-x/actions", r.endpoint)
        assertEquals(true, r.supported)
    }

    @Test
    fun sessionsList_itemsWrapper() {
        val list = readFixture("sessions-list-items").toJsonArrayOrObject().toSessionList()
        assertEquals(1, list.size)
        assertEquals("sess-items-1", list[0].sessionId)
    }

    @Test
    fun sessionsList_dataWrapper() {
        val list = readFixture("sessions-list-data").toJsonArrayOrObject().toSessionList()
        assertEquals("sess-data-1", list[0].sessionId)
    }

    @Test
    fun sessionsList_resultsWrapper() {
        val list = readFixture("sessions-list-results").toJsonArrayOrObject().toSessionList()
        assertEquals("sess-results-1", list[0].sessionId)
    }

    private fun readFixture(name: String): String {
        val path = "/$name.json"
        val stream = javaClass.getResourceAsStream(path)
            ?: Thread.currentThread().contextClassLoader?.getResourceAsStream("$name.json")
        return checkNotNull(stream) {
            "Missing fixture resource $path (ensure android/app shared fixtures test resource dir is configured)"
        }.bufferedReader().use { it.readText() }
    }
}
