package com.hermescourier.android.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.hermescourier.android.MainActivity
import com.hermescourier.android.R
import com.hermescourier.android.domain.model.HermesApprovalSummary
import com.hermescourier.android.domain.model.HermesCourierUiState
import com.hermescourier.android.domain.model.HermesRealtimeEnvelope
import com.hermescourier.android.domain.operator.HermesOperatorNavTarget

/**
 * Maps Hermes realtime (and polling) snapshots into grouped notifications with deep links.
 * Dedupes and rate-limits so reconnect storms stay quiet.
 */
class HermesOperatorNotificationDispatcher(
    private val app: Context,
) {
    private val nm = NotificationManagerCompat.from(app)

    private val recentKeys = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 120
    }

    private val rateWindowMs = 60_000L
    private val rateWindow = ArrayDeque<Long>(16)
    private val perKeyCooldownMs = 4_000L

    fun onRealtimeEnvelope(envelope: HermesRealtimeEnvelope, uiBefore: HermesCourierUiState) {
        if (!canPost()) return
        if (suppressBecauseForeground()) return

        envelope.approvalResult?.let { result ->
            val key = "approval-result:${result.approvalId}:${result.status}"
            if (shouldEmit(key)) {
                nm.cancel(result.approvalId, result.approvalId.hashCode())
                postNotification(
                    channelId = HermesNotificationChannels.APPROVALS_ID,
                    notificationId = NOTIF_ID_APPROVAL_OUTCOME,
                    tag = "approval-outcome-${result.approvalId}",
                    title = "Approval updated",
                    text = "${result.approvalId}: ${result.status}",
                    deepLink = HermesOperatorNavTarget.buildApprovalDetailUri(result.approvalId),
                    group = GROUP_APPROVALS,
                    isSummary = false,
                    number = null,
                )
            }
        }

        envelope.approvals?.let { incoming ->
            val oldIds = uiBefore.approvals.map { it.approvalId }.toSet()
            val newlyAdded = incoming.filter { it.approvalId !in oldIds }
            for (approval in newlyAdded) {
                val key = "approval-new:${approval.approvalId}"
                if (!shouldEmit(key)) continue
                postNotification(
                    channelId = HermesNotificationChannels.APPROVALS_ID,
                    notificationId = approval.approvalId.hashCode(),
                    tag = approval.approvalId,
                    title = "Approval required",
                    text = approval.title.ifBlank { approval.approvalId },
                    deepLink = HermesOperatorNavTarget.buildApprovalDetailUri(approval.approvalId),
                    group = GROUP_APPROVALS,
                    isSummary = false,
                    number = null,
                )
            }
            refreshApprovalGroupSummary(incoming)
        }

        envelope.dashboard?.let { dash ->
            val prev = uiBefore.dashboard.pendingApprovalCount
            if (dash.pendingApprovalCount > prev && envelope.approvals == null) {
                val key = "approval-dash:${dash.pendingApprovalCount}"
                if (shouldEmit(key)) {
                    postNotification(
                        channelId = HermesNotificationChannels.APPROVALS_ID,
                        notificationId = NOTIF_ID_APPROVAL_GENERIC,
                        tag = "approval-dashboard",
                        title = "Approvals pending",
                        text = "${dash.pendingApprovalCount} approval(s) need attention",
                        deepLink = HermesOperatorNavTarget.buildApprovalsListUri(),
                        group = GROUP_APPROVALS,
                        isSummary = false,
                        number = dash.pendingApprovalCount.takeIf { it > 0 },
                    )
                }
            }
        }

        envelope.sessionControlResult?.let { result ->
            val key = "session-ctl:${result.sessionId}:${result.action}:${result.status}"
            if (shouldEmit(key)) {
                postNotification(
                    channelId = HermesNotificationChannels.SESSIONS_ID,
                    notificationId = (result.sessionId + result.action).hashCode(),
                    tag = "session-ctl-${result.sessionId}",
                    title = "Session ${result.action}",
                    text = "${result.sessionId}: ${result.status}",
                    deepLink = HermesOperatorNavTarget.buildSessionDetailUri(result.sessionId),
                    group = GROUP_SESSIONS,
                    isSummary = false,
                    number = null,
                )
            }
        }

        envelope.sessions?.let { incoming ->
            val oldMap = uiBefore.sessions.associateBy { it.sessionId }
            for (s in incoming) {
                val prev = oldMap[s.sessionId] ?: continue
                if (prev.status.equals(s.status, ignoreCase = true)) continue
                if (!sessionStatusNeedsAttention(s.status)) continue
                val key = "session-attn:${s.sessionId}:${s.status}"
                if (!shouldEmit(key)) continue
                postNotification(
                    channelId = HermesNotificationChannels.SESSIONS_ID,
                    notificationId = s.sessionId.hashCode(),
                    tag = "session-attn-${s.sessionId}",
                    title = "Session needs attention",
                    text = "${s.title}: ${s.status}",
                    deepLink = HermesOperatorNavTarget.buildSessionDetailUri(s.sessionId),
                    group = GROUP_SESSIONS,
                    isSummary = false,
                    number = null,
                )
            }
        }

        envelope.conversation?.let { event ->
            if (event.author.trim().equals("you", ignoreCase = true)) return
            val sid = event.sessionId
            val active = uiBefore.activeSessionId
            if (sid != null && active != null && sid == active) return
            val key = "conv:${event.eventId}"
            if (!shouldEmit(key)) return
            val targetSession = when {
                !sid.isNullOrBlank() -> sid
                !active.isNullOrBlank() -> active
                else -> null
            }
            val link = if (targetSession.isNullOrBlank()) {
                HermesOperatorNavTarget.buildApprovalsListUri()
            } else {
                HermesOperatorNavTarget.buildChatSessionUri(targetSession)
            }
            postNotification(
                channelId = HermesNotificationChannels.CONVERSATION_ID,
                notificationId = event.eventId.hashCode(),
                tag = "conv-${event.eventId}",
                title = "Hermes: ${event.author}",
                text = event.body.ifBlank { "New message" },
                deepLink = link,
                group = GROUP_CONVERSATION,
                isSummary = false,
                number = null,
            )
        }
    }

    /**
     * Polling fallback when the gateway disables realtime: same approval discovery without WS.
     */
    fun onApprovalListPolled(incoming: List<HermesApprovalSummary>, previous: List<HermesApprovalSummary>) {
        if (!canPost()) return
        if (suppressBecauseForeground()) return
        val oldIds = previous.map { it.approvalId }.toSet()
        val newlyAdded = incoming.filter { it.approvalId !in oldIds }
        for (approval in newlyAdded) {
            val key = "approval-poll:${approval.approvalId}"
            if (!shouldEmit(key)) continue
            postNotification(
                channelId = HermesNotificationChannels.APPROVALS_ID,
                notificationId = approval.approvalId.hashCode(),
                tag = approval.approvalId,
                title = "Approval required",
                text = approval.title.ifBlank { approval.approvalId },
                deepLink = HermesOperatorNavTarget.buildApprovalDetailUri(approval.approvalId),
                group = GROUP_APPROVALS,
                isSummary = false,
                number = null,
            )
        }
        refreshApprovalGroupSummary(incoming)
    }

    private fun refreshApprovalGroupSummary(approvals: List<HermesApprovalSummary>) {
        val pending = approvals.size
        if (pending <= 0) {
            nm.cancel(TAG_APPROVAL_SUMMARY, NOTIF_ID_APPROVAL_SUMMARY)
            return
        }
        val headline = approvals.firstOrNull()?.title?.ifBlank { null } ?: "Hermes approvals"
        val text = if (pending == 1) "1 approval needs attention" else "$pending approvals need attention"
        postNotification(
            channelId = HermesNotificationChannels.APPROVALS_ID,
            notificationId = NOTIF_ID_APPROVAL_SUMMARY,
            tag = TAG_APPROVAL_SUMMARY,
            title = headline,
            text = text,
            deepLink = HermesOperatorNavTarget.buildApprovalsListUri(),
            group = GROUP_APPROVALS,
            isSummary = true,
            number = pending,
        )
    }

    private fun postNotification(
        channelId: String,
        notificationId: Int,
        tag: String,
        title: String,
        text: String,
        deepLink: String,
        group: String,
        isSummary: Boolean,
        number: Int?,
    ) {
        if (!canPost()) return
        if (!acquireRateSlot()) return
        val pending = pendingIntent(deepLink, notificationId)
        val builder = NotificationCompat.Builder(app, channelId)
            .setSmallIcon(R.drawable.ic_stat_hermes)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(group)
            .setGroupSummary(isSummary)
        number?.let { builder.setNumber(it) }
        nm.notify(tag, notificationId, builder.build())
    }

    private fun pendingIntent(deepLink: String, requestCode: Int): PendingIntent {
        val intent = Intent(app, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(deepLink)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            app,
            requestCode and 0xffff,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun shouldEmit(dedupeKey: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = recentKeys[dedupeKey]
        if (last != null && now - last < perKeyCooldownMs) {
            return false
        }
        recentKeys[dedupeKey] = now
        return true
    }

    private fun acquireRateSlot(): Boolean {
        val now = System.currentTimeMillis()
        while (rateWindow.isNotEmpty() && now - rateWindow.first() > rateWindowMs) {
            rateWindow.removeFirst()
        }
        if (rateWindow.size >= MAX_NOTIFICATIONS_PER_MINUTE) {
            return false
        }
        rateWindow.addLast(now)
        return true
    }

    private fun canPost(): Boolean {
        if (!nm.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return true
    }

    private fun suppressBecauseForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    private companion object {
        const val GROUP_APPROVALS = "hermes.group.approvals"
        const val GROUP_SESSIONS = "hermes.group.sessions"
        const val GROUP_CONVERSATION = "hermes.group.conversation"
        const val TAG_APPROVAL_SUMMARY = "hermes-approval-summary"
        const val NOTIF_ID_APPROVAL_SUMMARY = 48_001
        const val NOTIF_ID_APPROVAL_OUTCOME = 48_003
        const val NOTIF_ID_APPROVAL_GENERIC = 48_004
        const val MAX_NOTIFICATIONS_PER_MINUTE = 12
    }
}

private fun sessionStatusNeedsAttention(status: String): Boolean {
    val s = status.trim().lowercase()
    return s == "paused" || s == "terminated" || s == "failed" || s == "error" ||
        s.contains("blocked") || s.contains("attention")
}
