package com.hermescourier.android.domain.operator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesOperatorNavTargetTest {
    @Test
    fun parseApprovalsList() {
        val t = HermesOperatorNavTarget.parse("hermes-courier://nav/approvals")
        assertEquals(HermesOperatorNavTarget.ApprovalsList, t)
    }

    @Test
    fun parseApprovalDetail_encodesRoundTrip() {
        val id = "app/42-β"
        val uri = HermesOperatorNavTarget.buildApprovalDetailUri(id)
        val t = HermesOperatorNavTarget.parse(uri)
        assertTrue(t is HermesOperatorNavTarget.ApprovalDetail)
        assertEquals(id, (t as HermesOperatorNavTarget.ApprovalDetail).approvalId)
    }

    @Test
    fun parseSessionDetail() {
        val sid = "sess-1"
        val t = HermesOperatorNavTarget.parse(HermesOperatorNavTarget.buildSessionDetailUri(sid))
        assertTrue(t is HermesOperatorNavTarget.SessionDetailNav)
        assertEquals(sid, (t as HermesOperatorNavTarget.SessionDetailNav).sessionId)
    }

    @Test
    fun parseChatSession() {
        val sid = "sess-9"
        val t = HermesOperatorNavTarget.parse(HermesOperatorNavTarget.buildChatSessionUri(sid))
        assertTrue(t is HermesOperatorNavTarget.ChatSession)
        assertEquals(sid, (t as HermesOperatorNavTarget.ChatSession).sessionId)
    }

    @Test
    fun rejectsWrongScheme() {
        assertNull(HermesOperatorNavTarget.parse("https://example.com/nav/approvals"))
    }
}
