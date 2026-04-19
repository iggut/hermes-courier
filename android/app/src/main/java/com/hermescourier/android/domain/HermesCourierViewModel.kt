
package com.hermescourier.android.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermescourier.android.domain.gateway.DemoHermesGatewayClient
import com.hermescourier.android.domain.gateway.HermesGatewayClient
import com.hermescourier.android.domain.model.HermesAuthSession
import com.hermescourier.android.domain.model.HermesCourierUiState
import com.hermescourier.android.domain.model.HermesDeviceIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HermesCourierViewModel : ViewModel() {

    private val gatewayClient: HermesGatewayClient = DemoHermesGatewayClient()
    private val deviceIdentity = HermesDeviceIdentity(
        deviceId = "android-demo-device-001",
        platform = "android",
        appVersion = "0.1.0",
        publicKeyFingerprint = "demo-fingerprint",
    )

    private val _uiState = MutableStateFlow(HermesCourierUiState())
    val uiState: StateFlow<HermesCourierUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    bootstrapState = "Negotiating secure gateway",
                    authStatus = "Requesting device challenge",
                )
            }
            runCatching {
                val session = gatewayClient.bootstrap(deviceIdentity)
                loadAll(session)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        bootstrapState = "Demo fallback active",
                        authStatus = "Using offline-safe sample data (${error.message ?: "unknown error"})",
                    )
                }
            }
        }
    }

    private suspend fun loadAll(session: HermesAuthSession) {
        val dashboard = gatewayClient.fetchDashboard(session)
        val sessions = gatewayClient.fetchSessions(session)
        val approvals = gatewayClient.fetchApprovals(session)
        val conversation = gatewayClient.fetchConversation(session)
        _uiState.update {
            it.copy(
                bootstrapState = "Secure gateway ready",
                authStatus = "Session ${session.sessionId} authenticated through ${session.gatewayUrl}",
                dashboard = dashboard,
                sessions = sessions,
                approvals = approvals,
                conversationEvents = conversation,
            )
        }
    }
}
