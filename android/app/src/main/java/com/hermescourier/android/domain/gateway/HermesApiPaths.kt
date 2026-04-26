package com.hermescourier.android.domain.gateway

object HermesApiPaths {
    const val AUTH_CHALLENGE = "v1/auth/challenge"
    const val AUTH_RESPONSE = "v1/auth/response"
    const val DASHBOARD = "v1/dashboard"
    const val SESSIONS = "v1/sessions"
    const val SESSION_DETAIL = "v1/sessions/\$sessionId"
    const val SESSION_ACTIONS = "v1/sessions/\$sessionId/actions"
    const val SESSION_ACTION = "v1/sessions/\$sessionId/\$action"
    const val APPROVALS = "v1/approvals"
    const val CONVERSATION = "v1/conversation"
    const val EVENTS = "v1/events"
    const val SKILLS = "v1/skills"
    const val SKILL_DETAIL = "v1/skills/\$skillId"
    const val MEMORY = "v1/memory"
    const val MEMORY_DETAIL = "v1/memory/\$memoryId"
    const val CRON = "v1/cron"
    const val CRON_DETAIL = "v1/cron/\$cronId"
    const val LOGS = "v1/logs"
}
