package com.hermescourier.android.ui

import com.hermescourier.android.domain.model.HermesApprovalSummary
import com.hermescourier.android.domain.model.HermesSessionSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class HermesCourierUiHelpersTest {

    @Test
    fun navigationLabel_addsCountOnlyWhenPositive() {
        assertEquals("Approvals", navigationLabel("Approvals", 0))
        assertEquals("Approvals (3)", navigationLabel("Approvals", 3))
    }

    @Test
    fun dashboardNextStep_prefersActionablePendingApprovals() {
        assertEquals(
            "Review 2 pending approvals before they time out.",
            dashboardNextStep(
                bootstrapState = "Ready for use",
                pendingApprovals = 2,
                activeSessions = 5,
            ),
        )
    }

    @Test
    fun dashboardNextStep_guidesDisconnectedUsersToSettings() {
        assertEquals(
            "Gateway access is unavailable. Open Settings to check the URL, certificate, and reconnect.",
            dashboardNextStep(
                bootstrapState = "Gateway unavailable",
                pendingApprovals = 0,
                activeSessions = 0,
            ),
        )
    }

    @Test
    fun dashboardNextStep_handlesDemoMode() {
        assertEquals(
            "Connect to a live gateway to see real sessions, approvals, and conversation activity.",
            dashboardNextStep(
                bootstrapState = "Demo data mode",
                pendingApprovals = 0,
                activeSessions = 0,
            ),
        )
    }

    @Test
    fun dashboardFreshnessLabel_callsOutStaleData_whenOffline() {
        assertEquals(
            "Data may be stale · Last update: 12:34",
            dashboardFreshnessLabel(
                lastSyncLabel = "12:34",
                streamStatus = "Realtime stream disconnected",
            ),
        )
        assertEquals(
            "Live sync · Last update: Just now",
            dashboardFreshnessLabel(
                lastSyncLabel = "Just now",
                streamStatus = "Realtime stream connected",
            ),
        )
    }

    @Test
    fun sessionAndApprovalBadges_readWell_onMobile() {
        assertEquals("Live session", sessionStatusBadge("active"))
        assertEquals("Needs attention", sessionStatusBadge("error"))
        assertEquals("Biometrics required", approvalStatusBadge(HermesApprovalSummary("a", "t", "d", true)))
        assertEquals("Standard review", approvalStatusBadge(HermesApprovalSummary("a", "t", "d", false)))
    }

    @Test
    fun detailSubtitles_includeKeyIdentifiers() {
        val session = HermesSessionSummary("s-1", "Morning sync", "active", "just now")
        val approval = HermesApprovalSummary("a-7", "Rotate credentials", "Rotate the shared token", true)

        assertEquals("Updated just now · Live session", sessionDetailSubtitle(session))
        assertEquals(
            "Biometrics required · a-7",
            approvalDetailSubtitle(approval),
        )
    }
}
