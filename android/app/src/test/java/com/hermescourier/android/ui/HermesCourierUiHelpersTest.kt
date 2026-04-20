package com.hermescourier.android.ui

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
}
