package com.hermescourier.android.domain

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermescourier.android.domain.auth.AndroidKeystoreChallengeSigner
import com.hermescourier.android.domain.config.HermesGatewayConfiguration
import com.hermescourier.android.domain.gateway.DemoHermesGatewayClient
import com.hermescourier.android.domain.gateway.HermesGatewayClient
import com.hermescourier.android.domain.gateway.HermesGatewayClientFactory
import com.hermescourier.android.domain.model.HermesApprovalActionResult
import com.hermescourier.android.domain.model.HermesCourierUiState
import com.hermescourier.android.domain.model.HermesDeviceIdentity
import com.hermescourier.android.domain.model.HermesGatewaySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream

class HermesCourierViewModel(application: Application) : AndroidViewModel(application) {
    private val applicationContext = application.applicationContext
    private val signer = AndroidKeystoreChallengeSigner()
    private val fallbackGatewayClient: HermesGatewayClient = DemoHermesGatewayClient()
    private var realtimeHandle: Closeable? = null
    private var currentSession: com.hermescourier.android.domain.model.HermesAuthSession? = null

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<HermesCourierUiState> = _uiState.asStateFlow()

    private val deviceIdentity = HermesDeviceIdentity(
        deviceId = "android-courier-${android.os.Build.MODEL.lowercase().replace(' ', '-')}",
        platform = "android",
        appVersion = "0.1.0",
        publicKeyFingerprint = runCatching { signer.publicKeyFingerprint() }.getOrElse { "fingerprint-unavailable" },
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            syncSettingsFromDisk()
            _uiState.update {
                it.copy(
                    bootstrapState = "Negotiating secure gateway",
                    authStatus = "Requesting device challenge",
                )
            }
            val liveClient = HermesGatewayClientFactory.create(applicationContext)
            runCatching {
                loadFromGateway(liveClient)
            }.onSuccess { state ->
                _uiState.value = state
            }.onFailure { error ->
                runCatching {
                    loadFromGateway(fallbackGatewayClient)
                }.onSuccess { fallbackState ->
                    _uiState.value = fallbackState.copy(
                        bootstrapState = "Demo fallback active",
                        authStatus = "Using offline-safe sample data (${error.message ?: "unknown error"})",
                        streamStatus = "Demo realtime stream active",
                    )
                }.onFailure { fallbackError ->
                    _uiState.update {
                        it.copy(
                            bootstrapState = "Gateway unavailable",
                            authStatus = fallbackError.message ?: error.message ?: "Unknown error",
                        )
                    }
                }
            }
        }
    }

    fun updateGatewayBaseUrl(baseUrl: String) {
        _uiState.update {
            it.copy(gatewaySettings = it.gatewaySettings.copy(baseUrl = baseUrl.trim()))
        }
    }

    fun updateCertificatePassword(password: String) {
        _uiState.update {
            it.copy(gatewaySettings = it.gatewaySettings.copy(certificatePassword = password))
        }
    }

    fun importCertificate(uri: Uri) {
        viewModelScope.launch {
            val importedFile = copyCertificateToPrivateStorage(uri)
            _uiState.update {
                it.copy(
                    gatewaySettings = it.gatewaySettings.copy(certificatePath = importedFile.absolutePath),
                    enrollmentStatus = "Imported certificate bundle: ${importedFile.name}",
                )
            }
            persistGatewaySettings()
            refresh()
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            persistGatewaySettings()
            _uiState.update { it.copy(enrollmentStatus = "Gateway settings saved securely") }
            refresh()
        }
    }

    fun approveApproval(approvalId: String, note: String? = null) {
        submitApprovalAction(approvalId = approvalId, action = "approve", note = note)
    }

    fun rejectApproval(approvalId: String, note: String? = null) {
        submitApprovalAction(approvalId = approvalId, action = "reject", note = note)
    }

    private fun submitApprovalAction(approvalId: String, action: String, note: String?) {
        viewModelScope.launch {
            val session = currentSession ?: run {
                _uiState.update { it.copy(approvalActionStatus = "No authenticated session available for approval actions") }
                return@launch
            }
            val client = HermesGatewayClientFactory.create(applicationContext)
            val result = runCatching {
                client.submitApprovalAction(session, approvalId, action, note)
            }.getOrElse {
                fallbackGatewayClient.submitApprovalAction(session, approvalId, action, note)
            }
            _uiState.update {
                it.copy(
                    approvalActionStatus = approvalActionMessage(result),
                )
            }
            refresh()
        }
    }

    private suspend fun loadFromGateway(client: HermesGatewayClient): HermesCourierUiState {
        val session = client.bootstrap(deviceIdentity)
        currentSession = session
        val dashboard = client.fetchDashboard(session)
        val sessions = client.fetchSessions(session)
        val approvals = client.fetchApprovals(session)
        val conversation = client.fetchConversation(session)
        startRealtime(client, session)
        val settings = HermesGatewayConfiguration.from(applicationContext).toSettings()
        return _uiState.value.copy(
            bootstrapState = "Secure gateway ready",
            authStatus = "Session ${session.sessionId} authenticated through ${session.gatewayUrl}",
            dashboard = dashboard,
            sessions = sessions,
            approvals = approvals,
            conversationEvents = conversation,
            gatewaySettings = settings,
            deviceFingerprint = deviceIdentity.publicKeyFingerprint,
            enrollmentStatus = enrollmentStatus(settings),
            streamStatus = "Realtime stream connected",
        )
    }

    private fun startRealtime(client: HermesGatewayClient, session: com.hermescourier.android.domain.model.HermesAuthSession) {
        realtimeHandle?.close()
        realtimeHandle = client.connectRealtime(
            session = session,
            onStatus = { status ->
                _uiState.update { it.copy(streamStatus = status) }
            },
            onEnvelope = { envelope ->
                _uiState.update { state ->
                    val updatedConversation = envelope.conversation?.let { state.conversationEvents + it } ?: state.conversationEvents
                    state.copy(
                        dashboard = envelope.dashboard ?: state.dashboard,
                        sessions = envelope.sessions ?: state.sessions,
                        approvals = envelope.approvals ?: state.approvals,
                        conversationEvents = updatedConversation,
                        streamStatus = "Realtime event: ${envelope.type}",
                        approvalActionStatus = envelope.approvalResult?.let { approvalActionMessage(it) } ?: state.approvalActionStatus,
                    )
                }
            },
        )
    }

    private suspend fun syncSettingsFromDisk() {
        val loadedSettings = HermesGatewayConfiguration.from(applicationContext).toSettings()
        _uiState.update {
            it.copy(
                gatewaySettings = loadedSettings,
                deviceFingerprint = deviceIdentity.publicKeyFingerprint,
                enrollmentStatus = enrollmentStatus(loadedSettings),
            )
        }
    }

    private suspend fun persistGatewaySettings() {
        HermesGatewayConfiguration.save(applicationContext, _uiState.value.gatewaySettings)
    }

    private fun copyCertificateToPrivateStorage(uri: Uri): File {
        val directory = File(applicationContext.filesDir, "hermes-courier-certs").apply { mkdirs() }
        val target = File(directory, "gateway-mtls.p12")
        applicationContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected certificate file" }
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
        return target
    }

    private fun HermesGatewayConfiguration.toSettings(): HermesGatewaySettings = HermesGatewaySettings(
        baseUrl = baseUrl.toString(),
        certificatePath = mtlsPkcs12File?.absolutePath.orEmpty(),
        certificatePassword = mtlsPkcs12Password?.concatToString().orEmpty(),
    )

    private fun initialState(): HermesCourierUiState {
        val settings = HermesGatewayConfiguration.from(applicationContext).toSettings()
        return HermesCourierUiState(
            gatewaySettings = settings,
            deviceFingerprint = runCatching { signer.publicKeyFingerprint() }.getOrElse { "fingerprint-unavailable" },
            enrollmentStatus = enrollmentStatus(settings),
        )
    }

    private fun enrollmentStatus(settings: HermesGatewaySettings): String = when {
        settings.certificatePath.isBlank() -> "No certificate imported yet"
        settings.certificatePassword.isBlank() -> "Certificate imported; password required for mTLS enrollment"
        else -> "Certificate bundle enrolled and ready"
    }

    private fun approvalActionMessage(result: HermesApprovalActionResult): String =
        "${result.action.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} approval ${result.approvalId}: ${result.status}"
}
