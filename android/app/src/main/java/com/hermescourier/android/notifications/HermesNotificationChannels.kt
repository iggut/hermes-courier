package com.hermescourier.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object HermesNotificationChannels {
    const val APPROVALS_ID = "hermes_operator_approvals"
    const val SESSIONS_ID = "hermes_operator_sessions"
    const val CONVERSATION_ID = "hermes_operator_conversation"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val approvals = NotificationChannel(
            APPROVALS_ID,
            "Approvals",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Human approvals and approval outcomes from Hermes"
            setShowBadge(true)
        }
        val sessions = NotificationChannel(
            SESSIONS_ID,
            "Sessions",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Session lifecycle and control confirmations"
            setShowBadge(true)
        }
        val conversation = NotificationChannel(
            CONVERSATION_ID,
            "Conversation",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Replies on Hermes sessions you are not actively viewing"
            setShowBadge(true)
        }
        manager.createNotificationChannels(listOf(approvals, sessions, conversation))
    }
}
