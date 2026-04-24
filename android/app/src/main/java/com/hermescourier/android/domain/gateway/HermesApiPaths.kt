package com.hermescourier.android.domain.gateway

/** Canonical paths aligned with [shared/contract/hermes-courier-api.yaml]. */
object HermesApiPaths {
    const val PAIRING_STATUS = "api/courier/pairing/status"
    const val AUTH_CHALLENGE = "v1/auth/challenge"
    const val AUTH_RESPONSE = "v1/auth/response"
    const val DASHBOARD = "v1/dashboard"
    const val SESSIONS = "v1/sessions"
    const val APPROVALS = "v1/approvals"
    const val CONVERSATION = "v1/conversation"
    const val MODELS = "v1/models"
    /** Mobile clients use a WebSocket at this path; the contract describes live events. */
    const val EVENTS_STREAM = "v1/events"

    fun approvalDecision(approvalId: String): String = "v1/approvals/$approvalId/decision"
    fun sessionDetail(sessionId: String): String = "v1/sessions/$sessionId"
    fun sessionControlAction(sessionId: String): String = "v1/sessions/$sessionId/actions"
    fun sessionActionEndpoint(sessionId: String, action: String): String = "v1/sessions/$sessionId/$action"

    // Phase-1 WebUI-parity surfaces. Gateways that have not implemented these
    // respond with an `UnavailablePayload`; the client treats `supported:false`
    // as terminal.
    const val SKILLS = "v1/skills"
    const val MEMORY = "v1/memory"
    const val CRON = "v1/cron"
    const val LOGS = "v1/logs"

    fun skillDetail(skillId: String): String = "v1/skills/$skillId"
    fun memoryDetail(memoryId: String): String = "v1/memory/$memoryId"
    fun cronDetail(cronId: String): String = "v1/cron/$cronId"

    // Skill mutation endpoints (WebUI parity: /v1/skills/save, /v1/skills/delete)
    const val SKILLS_SAVE = "v1/skills/save"
    const val SKILLS_DELETE = "v1/skills/delete"
    const val SKILL_CONTENT = "v1/skills/content"
}
