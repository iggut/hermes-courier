
package com.hermescourier.android.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermescourier.android.domain.gateway.DemoHermesGatewayClient
import com.hermescourier.android.domain.gateway.HermesGatewayClient
import com.hermescourier.android.domain.gateway.HermesGatewayClientFactory
import com.hermescourier.android.domain.model.HermesCourierUiState
import com.hermescourier.android.domain.model.HermesDeviceIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HermesCourierViewModel(application: Application) : AndroidViewModel(application) {

    private val realGatewayClient: HermesGatewayClient = HermesGatewayClientFactory.create(application)
    private val fallbackGatewayClient: HermesGatewayClient = DemoHermesGatewayClient()
    private val deviceIdentity = HermesDeviceIdentity(
        deviceId = "android-demo-device-001",
        platform = "android",
        appVersion = "0.1.0",
        publicKeyFingerprint = "pending-keystore-bootstrap",
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
                loadFromGateway(realGatewayClient)
            }.onSuccess { state ->
                _uiState.value = state
            }.onFailure { error ->
                runCatching {
                    loadFromGateway(fallbackGatewayClient)
                }.onSuccess { fallbackState ->
                    _uiState.value = fallbackState.copy(
                        bootstrapState = "Demo fallback active",
                        authStatus = "Using offline-safe sample data (${error.message ?: "unknown error"})",
                    )
                }
            }
        }
    }

    private suspend fun loadFromGateway(client: HermesGatewayClient): HermesCourierUiState {
        val session = client.bootstrap(deviceIdentity)
        val dashboard = client.fetchDashboard(session)
        val sessions = client.fetchSessions(session)
        val approvals = client.fetchApprovals(session)
        val conversation = client.fetchConversation(session)
        return HermesCourierUiState(
            bootstrapState = "Secure gateway ready",
            authStatus = "Session ${session.sessionId} authenticated through ${session.gatewayUrl}",
            dashboard = dashboard,
            sessions = sessions,
            approvals = approvals,
            conversationEvents = conversation,
            gatewayUrl = session.gatewayUrl,
        )
    }
}
