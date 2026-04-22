package com.hermescourier.android.domain

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermescourier.android.domain.auth.AndroidKeystoreChallengeSigner
import com.hermescourier.android.domain.config.HermesGatewayConfiguration
import com.hermescourier.android.domain.gateway.DemoHermesGatewayClient
import com.hermescourier.android.domain.gateway.HermesGatewayClient
import com.hermescourier.android.domain.gateway.HermesGatewayClientFactory
import com.hermescourier.android.domain.model.HermesApprovalActionResult
import com.hermescourier.android.domain.model.HermesAuthSession
import com.hermescourier.android.domain.model.HermesConversationActionState
import com.hermescourier.android.domain.model.HermesConversationEvent
import com.hermescourier.android.domain.model.HermesCourierUiState
import com.hermescourier.android.domain.model.HermesDeviceIdentity
import com.hermescourier.android.domain.model.HermesEndpointVerificationResult
import com.hermescourier.android.domain.model.HermesEnrollmentPayload
import com.hermescourier.android.domain.model.HermesGatewaySettings
import com.hermescourier.android.domain.model.HermesQueuedApprovalAction
import com.hermescourier.android.domain.model.HermesSessionControlActionResult
import com.hermescourier.android.domain.model.parseHermesEnrollmentPayload
import com.hermescourier.android.domain.model.validateEnrollmentContract
import com.hermescourier.android.domain.model.migrateQueuedApprovalAction
import com.hermescourier.android.domain.model.queuedApprovalActionMatchesResult
import com.hermescourier.android.domain.model.userFacingApprovalVerb
import com.hermescourier.android.domain.gateway.HermesApiPaths
import com.hermescourier.android.domain.transport.HermesOkHttpClientFactory
import com.hermescourier.android.domain.transport.OkHttpHermesGatewayTransport
import com.hermescourier.android.domain.storage.EncryptedHermesTokenStore
import com.hermescourier.android.notifications.HermesOperatorNotificationDispatcher
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

class HermesCourierViewModel(application: Application) : AndroidViewModel(application) {
    private val applicationContext = application.applicationContext
    private val deviceFingerprint = runCatching { AndroidKeystoreChallengeSigner().publicKeyFingerprint() }
        .getOrElse { "fingerprint-unavailable" }
    private val fallbackGatewayClient: HermesGatewayClient = DemoHermesGatewayClient()
    private var realtimeHandle: Closeable? = null
    private var currentSession: com.hermescourier.android.domain.model.HermesAuthSession? = null
    private val seenRealtimeEventIds = linkedSetOf<String>()
    private var reconnectCountdownJob: Job? = null
    private val queuedApprovalActions = ArrayDeque<HermesQueuedApprovalAction>()
    private val queuedActionsFile = File(applicationContext.filesDir, "hermes-queued-approval-actions.json")
    private val operatorNotifications = HermesOperatorNotificationDispatcher(applicationContext)
    private var approvalPollFallbackJob: Job? = null

    private val deviceIdentity = HermesDeviceIdentity(
        deviceId = "android-courier-${android.os.Build.MODEL.lowercase().replace(' ', '-')}",
        platform = "android",
        appVersion = appVersionName(),
        publicKeyFingerprint = deviceFingerprint,
    )

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<HermesCourierUiState> = _uiState.asStateFlow()

    init {
        loadQueuedApprovalActions()
    }

    /**
     * Marks [sessionId] as the active Hermes work-session focused in Chat. Loads session
     * detail if the summary isn't already in memory so the Chat header chip can render a
     * real title. No backend side-effect: the conversation endpoint remains global.
     */
    fun enterSession(sessionId: String) {
        val trimmed = sessionId.trim()
        if (trimmed.isBlank()) return
        val previousActive = _uiState.value.activeSessionId
        _uiState.update {
            it.copy(
                activeSessionId = trimmed,
                // Clear the transcript immediately on switch so the viewer cannot see
                // the previous session's history for a frame while the per-session
                // fetch is in flight. Phase-3 conversation is wire-scoped now.
                conversationEvents = if (previousActive == trimmed) it.conversationEvents else emptyList(),
            )
        }
        loadSessionDetailIfMissing(trimmed)
        reloadConversationForActiveSession()
    }

    /** Clears the client-side active-session focus in Chat and reloads the global transcript. */
    fun clearActiveSession() {
        val previousActive = _uiState.value.activeSessionId
        _uiState.update {
            it.copy(
                activeSessionId = null,
                conversationEvents = if (previousActive == null) it.conversationEvents else emptyList(),
            )
        }
        reloadConversationForActiveSession()
    }

    /**
     * Re-fetch the conversation transcript using the current `activeSessionId` as the wire
     * filter. Silent on network failure: the optimistic cleared transcript remains visible
     * so the operator sees an empty session rather than stale cross-session content.
     */
    private fun reloadConversationForActiveSession() {
        viewModelScope.launch {
            val session = currentSession ?: return@launch
            val client = liveClientOrNull() ?: return@launch
            val activeId = _uiState.value.activeSessionId
            runCatching { client.fetchConversation(session, activeId) }
                .onSuccess { events ->
                    _uiState.update { state ->
                        if (state.activeSessionId != activeId) state
                        else state.copy(conversationEvents = events)
                    }
                }
        }
    }

    fun loadSessionDetailIfMissing(sessionId: String) {
        viewModelScope.launch {
            if (_uiState.value.sessions.any { it.sessionId == sessionId }) {
                _uiState.update { it.copy(sessionDetailLoadError = null) }
                return@launch
            }
            val auth = currentSession
            if (auth == null) {
                _uiState.update {
                    it.copy(
                        sessionDetailLoading = false,
                        sessionDetailLoadError = "Not authenticated; open Settings and connect to the gateway.",
                    )
                }
                return@launch
            }
            val liveClient = liveClientOrNull()
            if (liveClient == null) {
                _uiState.update {
                    it.copy(
                        sessionDetailLoading = false,
                        sessionDetailLoadError = "Live gateway unavailable; cannot load session detail.",
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(sessionDetailLoading = true, sessionDetailLoadError = null) }
            runCatching {
                liveClient.fetchSessionDetail(auth, sessionId)
            }.onSuccess { detail ->
                _uiState.update { state ->
                    val merged = (state.sessions + detail).distinctBy { it.sessionId }
                    state.copy(
                        sessions = merged,
                        sessionDetailLoading = false,
                        sessionDetailLoadError = null,
                    )
                }
            }.onFailure { error ->
                val msg = error.message ?: error.toString()
                _uiState.update {
                    it.copy(sessionDetailLoading = false, sessionDetailLoadError = msg)
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            reconnectCountdownJob?.cancel()
            reconnectCountdownJob = null
            syncSettingsFromDisk()
            refreshPairingBackendStatus()
            _uiState.update {
                it.copy(
                    bootstrapState = "Negotiating secure gateway",
                    authStatus = "Requesting device challenge",
                    gatewayConnectionMode = "Checking live gateway",
                    gatewayConnectionDetail = "Starting connection bootstrap",
                    realtimeReconnectProgress = 0f,
                    realtimeReconnectCountdown = "Reconnect now",
                    sessionDetailLoading = false,
                    sessionDetailLoadError = null,
                )
            }
            val pairedTokenAvailable = hasPairedBearerToken()
            val liveClient = if (pairedTokenAvailable) liveClientOrNull() else null
            if (liveClient == null) {
                val detail = if (pairedTokenAvailable) {
                    "Secure gateway client could not be created from the current settings"
                } else {
                    "No paired bearer token configured. Scan the Hermes WebUI pairing QR first."
                }
                _uiState.update {
                    it.copy(
                        bootstrapState = if (pairedTokenAvailable) "Gateway unavailable" else "Awaiting paired bearer token",
                        authStatus = detail,
                        gatewayConnectionMode = "Unavailable",
                        gatewayConnectionDetail = detail,
                        streamStatus = "Realtime stream unavailable",
                    verificationMode = "Skipped (live gateway unavailable)",
                    endpointVerificationResults = defaultVerificationResults(
                        reason = if (pairedTokenAvailable) {
                            "Live gateway client could not be created from current settings."
                        } else {
                            "No paired bearer token is stored yet."
                        },
                        status = "failed",
                    ),
                    )
                }
                runCatching {
                    loadFromGateway(
                        fallbackGatewayClient,
                        connectionMode = "Demo fallback",
                        connectionDetail = detail,
                    )
                }.onSuccess { fallbackState ->
                    _uiState.value = fallbackState.copy(
                        bootstrapState = "Demo fallback active",
                        authStatus = "Using offline-safe sample data",
                        gatewayConnectionMode = "Demo fallback",
                        gatewayConnectionDetail = detail,
                        streamStatus = "Demo realtime stream active",
                        verificationMode = "Demo fallback (explicit)",
                    )
                }.onFailure { fallbackError ->
                    val fallbackDetail = fallbackError.localizedMessage ?: fallbackError.toString()
                    _uiState.update {
                        it.copy(
                            bootstrapState = "Gateway unavailable",
                            authStatus = fallbackDetail,
                            gatewayConnectionMode = "Unavailable",
                            gatewayConnectionDetail = fallbackDetail,
                            streamStatus = "Realtime stream unavailable",
                            verificationMode = "Failed",
                        )
                    }
                }
                return@launch
            }
            runCatching {
                loadFromGateway(
                    liveClient,
                    connectionMode = "Live gateway",
                    connectionDetail = "Live gateway handshake completed",
                )
            }.onSuccess { state ->
                _uiState.value = state.copy(
                    bootstrapState = "Live gateway connected",
                    gatewayConnectionMode = "Live gateway",
                    gatewayConnectionDetail = "Live gateway handshake completed",
                    verificationMode = "Live data load completed",
                )
                flushQueuedApprovalActions(liveClient, currentSession)
            }.onFailure { error ->
                val detail = error.localizedMessage ?: error.toString()
                runCatching {
                    loadFromGateway(
                        fallbackGatewayClient,
                        connectionMode = "Demo fallback",
                        connectionDetail = detail,
                    )
                }.onSuccess { fallbackState ->
                    _uiState.value = fallbackState.copy(
                        bootstrapState = "Demo fallback active",
                        authStatus = "Using offline-safe sample data ($detail)",
                        gatewayConnectionMode = "Demo fallback",
                        gatewayConnectionDetail = detail,
                        streamStatus = "Demo realtime stream active",
                        verificationMode = "Demo fallback (explicit)",
                    )
                }.onFailure { fallbackError ->
                    val fallbackDetail = fallbackError.localizedMessage ?: fallbackError.toString()
                    _uiState.update {
                        it.copy(
                            bootstrapState = "Gateway unavailable",
                            authStatus = fallbackDetail,
                            gatewayConnectionMode = "Unavailable",
                            gatewayConnectionDetail = fallbackDetail,
                            streamStatus = "Realtime stream unavailable",
                            verificationMode = "Failed",
                        )
                    }
                }
            }
        }
    }

    fun testLiveGateway() {
        viewModelScope.launch { performLiveGatewayTest() }
    }

    private suspend fun performLiveGatewayTest() {
        reconnectCountdownJob?.cancel()
        reconnectCountdownJob = null
        syncSettingsFromDisk()
        _uiState.update {
            it.copy(
                bootstrapState = "Testing live gateway",
                authStatus = "Attempting live secure bootstrap",
                gatewayConnectionMode = "Testing live gateway",
                gatewayConnectionDetail = "Running live connection test",
                realtimeReconnectProgress = 0f,
                realtimeReconnectCountdown = "Reconnect now",
                verificationMode = "Running live endpoint verification",
            )
        }
        val liveClient = liveClientOrNull()
        if (liveClient == null) {
            val detail = "Secure gateway client could not be created from the current settings"
            _uiState.update {
                it.copy(
                    bootstrapState = "Live gateway unavailable",
                    authStatus = detail,
                    gatewayConnectionMode = "Unavailable",
                    gatewayConnectionDetail = detail,
                    streamStatus = "Realtime stream unavailable",
                    verificationMode = "Failed (live gateway unavailable)",
                    endpointVerificationResults = defaultVerificationResults(
                        reason = detail,
                        status = "failed",
                    ),
                )
            }
            return
        }
        val verification = runCatching { liveClient.verifyLiveEndpoints(deviceIdentity) }
        verification.onSuccess { checks ->
            _uiState.update { state ->
                state.copy(
                    endpointVerificationResults = checks,
                    verificationMode = "Live verification completed",
                )
            }
        }.onFailure { error ->
            _uiState.update { state ->
                state.copy(
                    endpointVerificationResults = defaultVerificationResults(
                        reason = error.localizedMessage ?: error.toString(),
                        status = "failed",
                    ),
                    verificationMode = "Live verification failed",
                )
            }
        }
        runCatching {
            loadFromGateway(
                liveClient,
                connectionMode = "Live gateway",
                connectionDetail = "Live gateway handshake completed",
            )
        }.onSuccess { state ->
            _uiState.value = state.copy(
                bootstrapState = "Live gateway connected",
                authStatus = "Authenticated against ${state.dashboard.connectionState}",
                gatewayConnectionMode = "Live gateway",
                gatewayConnectionDetail = "Live gateway handshake completed",
                verificationMode = "Live verification completed",
                endpointVerificationResults = verification.getOrNull() ?: state.endpointVerificationResults,
            )
            flushQueuedApprovalActions(liveClient, currentSession)
        }.onFailure { error ->
            val detail = error.localizedMessage ?: error.toString()
            _uiState.update {
                it.copy(
                    bootstrapState = "Live gateway test failed",
                    authStatus = detail,
                    gatewayConnectionMode = "Unavailable",
                    gatewayConnectionDetail = detail,
                    streamStatus = "Realtime stream unavailable",
                    verificationMode = "Live load failed",
                )
            }
        }
    }

    fun submitSessionControlAction(sessionId: String, action: String) {
        viewModelScope.launch {
            val session = currentSession ?: run {
                _uiState.update { it.copy(sessionControlStatus = "Session-control skipped: no authenticated session") }
                return@launch
            }
            val liveClient = runCatching { HermesGatewayClientFactory.create(applicationContext) }.getOrNull()
            if (liveClient == null || liveClient is DemoHermesGatewayClient) {
                _uiState.update {
                    it.copy(
                        sessionControlStatus = "Session-control unavailable: live gateway required (demo fallback is explicit).",
                    )
                }
                return@launch
            }
            val result = runCatching { liveClient.submitSessionControlAction(session, sessionId, action) }
                .getOrElse { error ->
                    HermesSessionControlActionResult(
                        sessionId = sessionId,
                        action = action,
                        status = "failed",
                        detail = error.localizedMessage ?: error.toString(),
                        updatedAt = "now",
                    )
                }
            _uiState.update {
                it.copy(
                    sessionControlStatus = "${result.action} ${result.sessionId}: ${result.status} (${result.detail})",
                )
            }
            if (result.supported && result.status != "failed") {
                refresh()
            }
        }
    }

    fun updateGatewayBaseUrl(value: String) {
        _uiState.update {
            it.copy(
                gatewaySettings = it.gatewaySettings.copy(baseUrl = value.trim()),
                enrollmentStatus = enrollmentStatus(it.gatewaySettings.copy(baseUrl = value.trim())),
                enrollmentQrPayload = enrollmentPayload(it.gatewaySettings.copy(baseUrl = value.trim())),
            )
        }
    }

    fun updateCertificatePassword(value: String) {
        _uiState.update {
            it.copy(gatewaySettings = it.gatewaySettings.copy(certificatePassword = value))
        }
    }

    fun importCertificate(uri: Uri) {
        viewModelScope.launch {
            val copiedCertificate = copyCertificateToPrivateStorage(uri)
            _uiState.update {
                it.copy(
                    gatewaySettings = it.gatewaySettings.copy(certificatePath = copiedCertificate.absolutePath),
                    enrollmentStatus = enrollmentStatus(it.gatewaySettings.copy(certificatePath = copiedCertificate.absolutePath)),
                    enrollmentQrPayload = enrollmentPayload(it.gatewaySettings.copy(certificatePath = copiedCertificate.absolutePath)),
                )
            }
        }
    }

    /**
     * One-shot startup: if the activity was opened with a `hermes-courier-enroll://` deep link, apply
     * that pairing **before** the first [refresh] so a default localhost placeholder cannot race the
     * imported Tailscale or HTTPS gateway URL.
     */
    fun runInitialAppBootstrap(
        deepLinkPayload: String?,
        onDeepLinkConsumed: () -> Unit = {},
    ): Job = viewModelScope.launch {
        val trimmed = deepLinkPayload?.trim().orEmpty()
        if (trimmed.startsWith("hermes-courier-enroll://")) {
            applyTokenOnlyEnrollmentFromPayload(trimmed)
            performLiveGatewayTest()
            onDeepLinkConsumed()
        } else {
            refresh()
        }
    }

    fun applyEnrollmentQr(payload: String) {
        viewModelScope.launch {
            applyTokenOnlyEnrollmentFromPayload(payload)
            testLiveGateway()
        }
    }

    private suspend fun applyTokenOnlyEnrollmentFromPayload(payload: String) {
        val parsed = parseEnrollmentPayload(payload)
        if (parsed == null) {
            _uiState.update { it.copy(enrollmentStatus = "Pairing import failed: payload could not be parsed") }
            return
        }
        val validationError = validateEnrollmentContract(parsed)
        if (validationError != null) {
            _uiState.update {
                it.copy(
                    enrollmentStatus = validationError,
                    courierPairingStatus = "Pairing invalid: contract requirements were not met",
                )
            }
            return
        }
        val baseForStorage = HermesGatewayConfiguration.parseBaseUrlForPairingOrNull(parsed.gatewayUrl)
        if (baseForStorage == null) {
            _uiState.update {
                it.copy(
                    enrollmentStatus = "Pairing import failed: gatewayUrl must be a valid http(s) URL (use HTTPS for Tailscale)",
                    courierPairingStatus = "Invalid gateway URL",
                )
            }
            return
        }
        val updatedSettings = _uiState.value.gatewaySettings.copy(baseUrl = baseForStorage.toString())
        HermesGatewayConfiguration.save(applicationContext, updatedSettings)
        val canonical = HermesGatewayConfiguration.from(applicationContext).baseUrl
        persistPairedBearerToken(canonical.toString(), parsed.bearerToken)
        val statusMessage = "Pairing import succeeded: token-only bearer pairing configured for $canonical"
        _uiState.update {
            it.copy(
                gatewaySettings = updatedSettings,
                enrollmentStatus = statusMessage,
                courierPairingStatus = "Bearer pairing configured (scan-and-done ready)",
                enrollmentQrPayload = enrollmentPayload(updatedSettings),
            )
        }
        refreshPairingBackendStatus()
    }

    fun saveSettings() {
        viewModelScope.launch {
            persistGatewaySettings()
            _uiState.update {
                it.copy(
                    enrollmentStatus = "Gateway settings saved securely",
                    enrollmentQrPayload = enrollmentPayload(it.gatewaySettings),
                    queuedApprovalActions = queuedApprovalActions.size,
                    queuedApprovalActionQueue = queuedApprovalActions.toList(),
                )
            }
            refresh()
        }
    }

    fun retryQueuedApprovalActions() {
        viewModelScope.launch {
            val liveClient = runCatching { HermesGatewayClientFactory.create(applicationContext) }.getOrNull()
            if (liveClient == null) {
                _uiState.update { state ->
                    state.copy(
                        approvalActionStatus = "Unable to retry queued actions: live gateway unavailable",
                        queuedApprovalActions = queuedApprovalActions.size,
                        queuedApprovalActionQueue = queuedApprovalActions.toList(),
                    )
                }
                return@launch
            }
            flushQueuedApprovalActions(liveClient, currentSession)
        }
    }

    fun reconnectRealtime() {
        viewModelScope.launch {
            val session = currentSession ?: run {
                refresh()
                return@launch
            }
            val liveClient = runCatching { HermesGatewayClientFactory.create(applicationContext) }.getOrNull()
            if (liveClient == null) {
                _uiState.update { state -> state.copy(streamStatus = "Realtime reconnect unavailable: live gateway client could not be created") }
                return@launch
            }
            _uiState.update { state -> state.copy(streamStatus = "Manual realtime reconnect requested") }
            startRealtime(liveClient, session)
        }
    }

    /**
     * Reloads the Phase-1 WebUI-parity surfaces (skills, memory, cron, logs).
     * Runs against the live gateway when one is available; otherwise uses the
     * demo fallback so the library screens are never empty in offline mode.
     */
    fun refreshLibrary() {
        viewModelScope.launch {
            val session = currentSession
            val liveClient = liveClientOrNull()
            val client: HermesGatewayClient = liveClient ?: fallbackGatewayClient
            val effectiveSession = session ?: runCatching { client.bootstrap(deviceIdentity) }.getOrNull()
            if (effectiveSession == null) {
                _uiState.update { it.copy(libraryStatus = "Cannot load library: no authenticated session") }
                return@launch
            }
            if (session == null) currentSession = effectiveSession
            _uiState.update { it.copy(libraryLoading = true, libraryStatus = "Loading library from gateway") }
            loadLibraryQuietly(client, effectiveSession)
        }
    }

    private suspend fun loadLibraryQuietly(
        client: HermesGatewayClient,
        session: HermesAuthSession,
    ) {
        val skills = runCatching { client.fetchSkills(session) }
        val memory = runCatching { client.fetchMemory(session) }
        val cron = runCatching { client.fetchCronJobs(session) }
        val logs = runCatching { client.fetchLogs(session, limit = 100, severity = null) }
        val failures = listOf(skills, memory, cron, logs)
            .mapNotNull { it.exceptionOrNull() }
        val status = when {
            failures.isNotEmpty() -> "Library loaded with ${failures.size} transient failure(s): ${failures.first().message ?: failures.first()}"
            client is DemoHermesGatewayClient -> "Library loaded from demo fallback"
            else -> "Library loaded"
        }
        _uiState.update {
            it.copy(
                skills = skills.getOrDefault(it.skills),
                memory = memory.getOrDefault(it.memory),
                cronJobs = cron.getOrDefault(it.cronJobs),
                logs = logs.getOrDefault(it.logs),
                libraryLoading = false,
                libraryStatus = status,
            )
        }
    }

    fun copyEnrollmentQrPayload() {
        val payload = _uiState.value.enrollmentQrPayload
        if (payload.isBlank()) {
            _uiState.update { it.copy(enrollmentStatus = "No enrollment QR payload available to copy") }
            return
        }
        val clipboard = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Hermes Courier enrollment QR", payload))
        _uiState.update { it.copy(enrollmentStatus = "Enrollment QR payload copied to clipboard") }
    }

    fun retryQueuedApprovalAction(queued: HermesQueuedApprovalAction) {
        viewModelScope.launch {
            val session = currentSession ?: run {
                _uiState.update { state -> state.copy(approvalActionStatus = "Unable to retry queued action: no authenticated session available") }
                return@launch
            }
            val liveClient = runCatching { HermesGatewayClientFactory.create(applicationContext) }.getOrNull()
            if (liveClient == null || liveClient is DemoHermesGatewayClient) {
                _uiState.update { state ->
                    state.copy(approvalActionStatus = "Unable to retry queued action: live gateway unavailable", queuedApprovalActions = queuedApprovalActions.size, queuedApprovalActionQueue = queuedApprovalActions.toList())
                }
                return@launch
            }
            runCatching { liveClient.submitApprovalAction(session, queued.approvalId, queued.action, queued.note) }
                .onSuccess { result ->
                    removeQueuedApprovalAction(queued)
                    _uiState.update {
                        it.copy(
                            approvalActionStatus = "Retried queued ${userFacingApprovalVerb(result.action)} for ${result.approvalId}: ${result.status}",
                            queuedApprovalActions = queuedApprovalActions.size,
                            queuedApprovalActionQueue = queuedApprovalActions.toList(),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(approvalActionStatus = "Queued retry failed: ${error.localizedMessage ?: error}", queuedApprovalActions = queuedApprovalActions.size, queuedApprovalActionQueue = queuedApprovalActions.toList())
                    }
                }
        }
    }

    fun copyQueuedApprovalActionDetails(queued: HermesQueuedApprovalAction) {
        val details = buildString {
            appendLine("Approval ID: ${queued.approvalId}")
            appendLine("Decision: ${userFacingApprovalVerb(queued.action)} (wire: ${queued.action})")
            appendLine("Queued at: ${queued.createdAt}")
            appendLine("Note: ${queued.note ?: "(none)"}")
        }
        val clipboard = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Hermes Courier queued approval details", details))
        _uiState.update { it.copy(approvalActionStatus = "Queued approval details copied to clipboard") }
    }

    fun dismissQueuedApprovalAction(queued: HermesQueuedApprovalAction): HermesQueuedApprovalAction? {
        val removed = removeQueuedApprovalAction(queued)
        if (removed == null) {
            _uiState.update { it.copy(approvalActionStatus = "Queued approval was already removed") }
            return null
        }
        _uiState.update {
            it.copy(
                approvalActionStatus = "Dismissed queued ${userFacingApprovalVerb(queued.action)} for ${queued.approvalId}",
                queuedApprovalActions = queuedApprovalActions.size,
                queuedApprovalActionQueue = queuedApprovalActions.toList(),
            )
        }
        return removed
    }

    fun restoreQueuedApprovalAction(queued: HermesQueuedApprovalAction) {
        if (queuedApprovalActions.contains(queued)) {
            _uiState.update { it.copy(approvalActionStatus = "Queued approval already present", queuedApprovalActions = queuedApprovalActions.size, queuedApprovalActionQueue = queuedApprovalActions.toList()) }
            return
        }
        queuedApprovalActions.addFirst(queued)
        persistQueuedApprovalActions()
        _uiState.update {
            it.copy(
                approvalActionStatus = "Restored dismissed ${userFacingApprovalVerb(queued.action)} for ${queued.approvalId}",
                queuedApprovalActions = queuedApprovalActions.size,
                queuedApprovalActionQueue = queuedApprovalActions.toList(),
            )
        }
    }

    fun shareEnrollmentQr() {
        viewModelScope.launch {
            val payload = _uiState.value.enrollmentQrPayload
            if (payload.isBlank()) {
                _uiState.update { it.copy(enrollmentStatus = "No enrollment QR payload available to share") }
                return@launch
            }
            runCatching {
                val bitmap = BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, 1024, 1024)
                val shareFile = File(applicationContext.cacheDir, "hermes-enrollment-qr.png")
                FileOutputStream(shareFile).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                val uri = FileProvider.getUriForFile(
                    applicationContext,
                    "${applicationContext.packageName}.fileprovider",
                    shareFile,
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(applicationContext.contentResolver, "Enrollment QR", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(shareIntent, "Share enrollment QR").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                applicationContext.startActivity(chooser)
            }.onFailure { error ->
                _uiState.update { it.copy(enrollmentStatus = "Unable to share enrollment QR: ${error.localizedMessage ?: error}") }
            }
        }
    }

    fun approveApproval(approvalId: String, note: String? = null) {
        submitApprovalAction(approvalId = approvalId, action = "approve", note = note)
    }

    fun rejectApproval(approvalId: String, note: String? = null) {
        submitApprovalAction(approvalId = approvalId, action = "deny", note = note)
    }

    fun sendConversationMessage(message: String) {
        val trimmed = message.trim()
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(
                    conversationActionStatus = "Type a message before sending",
                    conversationActionError = "Type a message before sending",
                    conversationActionState = HermesConversationActionState.Failed,
                )
            }
            return
        }
        viewModelScope.launch {
            val activeSessionId = _uiState.value.activeSessionId
            val optimisticEvent = HermesConversationEvent(
                eventId = "local-${System.currentTimeMillis()}",
                author = "You",
                body = trimmed,
                timestamp = "now",
                sessionId = activeSessionId,
            )
            _uiState.update { state ->
                state.copy(
                    conversationEvents = state.conversationEvents.upsertConversationEvent(optimisticEvent),
                    conversationActionStatus = "Sending instruction to Hermes…",
                    conversationActionError = null,
                    conversationActionState = HermesConversationActionState.Sending,
                )
            }
            val session = currentSession ?: run {
                _uiState.update { state ->
                    state.copy(
                        conversationActionStatus = "Connect to a live gateway before sending",
                        conversationActionError = "No authenticated session is available yet.",
                        conversationActionState = HermesConversationActionState.Failed,
                    )
                }
                return@launch
            }
            val liveClientResult = runCatching { HermesGatewayClientFactory.create(applicationContext) }
            val liveClient = liveClientResult.getOrNull()
            if (liveClient == null) {
                val detail = liveClientResult.exceptionOrNull()?.localizedMessage
                    ?: liveClientResult.exceptionOrNull()?.toString()
                    ?: "Unable to create live gateway client"
                _uiState.update { state ->
                    state.copy(
                        conversationActionStatus = "Live gateway unavailable; cannot send: $detail",
                        conversationActionError = detail,
                        conversationActionState = HermesConversationActionState.Failed,
                    )
                }
                return@launch
            }
            runCatching {
                liveClient.submitConversationMessage(session, trimmed, activeSessionId)
            }.onSuccess { echoedEvent ->
                _uiState.update { state ->
                    state.copy(
                        conversationEvents = echoedEvent?.let { state.conversationEvents.upsertConversationEvent(it) } ?: state.conversationEvents,
                        conversationActionStatus = when {
                            liveClient is DemoHermesGatewayClient -> "Instruction recorded in demo mode"
                            echoedEvent != null -> "Instruction sent to Hermes"
                            else -> "Instruction accepted by the gateway"
                        },
                        conversationActionError = null,
                        conversationActionState = HermesConversationActionState.Sent,
                    )
                }
            }.onFailure { error ->
                val detail = error.localizedMessage ?: error.toString()
                _uiState.update { state ->
                    state.copy(
                        conversationActionStatus = "Saved locally; live send failed: $detail",
                        conversationActionError = detail,
                        conversationActionState = HermesConversationActionState.Failed,
                    )
                }
            }
        }
    }

    private fun submitApprovalAction(approvalId: String, action: String, note: String?) {
        viewModelScope.launch {
            val session = currentSession ?: run {
                queueApprovalAction(approvalId, action, note, reason = "No authenticated session available; queued locally")
                return@launch
            }
            val liveClient = runCatching { HermesGatewayClientFactory.create(applicationContext) }.getOrNull()
            if (liveClient == null) {
                queueApprovalAction(approvalId, action, note, reason = "Live gateway unavailable; queued locally")
                return@launch
            }
            runCatching {
                liveClient.submitApprovalAction(session, approvalId, action, note)
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        approvalActionStatus = approvalActionMessage(result),
                        queuedApprovalActions = queuedApprovalActions.size,
                        queuedApprovalActionQueue = queuedApprovalActions.toList(),
                    )
                }
                refresh()
            }.onFailure {
                queueApprovalAction(approvalId, action, note, reason = "Offline; approval action queued for retry")
            }
        }
    }

    private suspend fun loadFromGateway(
        client: HermesGatewayClient,
        connectionMode: String,
        connectionDetail: String,
    ): HermesCourierUiState {
        val session = client.bootstrap(deviceIdentity)
        currentSession = session
        val dashboard = client.fetchDashboard(session)
        val sessions = client.fetchSessions(session)
        val approvals = client.fetchApprovals(session)
        val activeSessionId = _uiState.value.activeSessionId
        val conversation = client.fetchConversation(session, activeSessionId)
        startRealtime(client, session)
        loadLibraryQuietly(client, session)
        val settings = HermesGatewayConfiguration.from(applicationContext).toSettings()
        val queuedCount = queuedApprovalActions.size
        val verificationResults = if (client is DemoHermesGatewayClient) {
            defaultVerificationResults(
                reason = "Demo fallback data source in use. Live endpoint verification not executed in refresh().",
                status = "demo",
            )
        } else {
            _uiState.value.endpointVerificationResults
        }
        // Do NOT overwrite streamStatus / realtimeReconnectCountdown here. `startRealtime`
        // above already registered the WebSocket listener, and its async `onStatus` callback
        // is the single source of truth for realtime liveness. Forcing "Realtime stream
        // connected" from the REST-bootstrap path lied on gateways that immediately reject
        // the WS upgrade with `events_unavailable` / `supported: false`. The updated
        // streamStatus (either "Realtime stream connected" on real opens, or
        // "Realtime unsupported by gateway (polling fallback: ...)" on 426) is preserved
        // by reading `_uiState.value` at the moment of return.
        return _uiState.value.copy(
            bootstrapState = "Secure gateway ready",
            authStatus = "Session ${session.sessionId} authenticated through ${session.gatewayUrl}",
            gatewayConnectionMode = connectionMode,
            gatewayConnectionDetail = connectionDetail,
            dashboard = dashboard,
            sessions = sessions,
            approvals = approvals,
            conversationEvents = conversation,
            gatewaySettings = settings,
            deviceFingerprint = deviceIdentity.publicKeyFingerprint,
            enrollmentStatus = enrollmentStatus(settings),
            courierPairingStatus = pairingStatusFromTokenStore(),
            enrollmentQrPayload = enrollmentPayload(settings),
            queuedApprovalActions = queuedCount,
            queuedApprovalActionQueue = queuedApprovalActions.toList(),
            endpointVerificationResults = verificationResults,
            verificationMode = if (client is DemoHermesGatewayClient) "Demo fallback (explicit)" else _uiState.value.verificationMode,
        )
    }

    private fun startRealtime(client: HermesGatewayClient, session: com.hermescourier.android.domain.model.HermesAuthSession) {
        realtimeHandle?.close()
        realtimeHandle = client.connectRealtime(
            session = session,
            onStatus = { status ->
                _uiState.update { it.copy(streamStatus = status) }
                updateReconnectCountdown(status)
                handleApprovalPollFallbackForStreamStatus(status, client)
                if (status.contains("connected", ignoreCase = true) && client !is DemoHermesGatewayClient) {
                    viewModelScope.launch { flushQueuedApprovalActions(client, session) }
                }
            },
            onEnvelope = onEnvelope@{ envelope ->
                val envelopeId = envelope.eventId ?: "${envelope.type}:${envelope.eventTimestamp}:${envelope.conversation?.eventId ?: ""}:${envelope.approvalResult?.approvalId ?: ""}:${envelope.sessionControlResult?.sessionId ?: ""}"
                if (seenRealtimeEventIds.contains(envelopeId)) {
                    _uiState.update { it.copy(streamStatus = "Realtime duplicate ignored: ${envelope.type}") }
                    return@onEnvelope
                }
                seenRealtimeEventIds.add(envelopeId)
                if (seenRealtimeEventIds.size > 200) {
                    seenRealtimeEventIds.remove(seenRealtimeEventIds.first())
                }
                val reconciledQueuedAction = envelope.approvalResult?.let { result ->
                    removeQueuedApprovalActionForResult(result)
                }
                val uiBeforeNotification = _uiState.value
                _uiState.update { state ->
                    val incomingEvent = envelope.conversation
                    val belongsToActive = when {
                        incomingEvent == null -> false
                        // Event is untagged (pre-Phase-3 servers or optimistic echoes):
                        // apply it regardless to stay backwards compatible.
                        incomingEvent.sessionId == null -> true
                        state.activeSessionId == null -> false
                        else -> incomingEvent.sessionId == state.activeSessionId
                    }
                    val updatedConversation = if (belongsToActive && incomingEvent != null) {
                        state.conversationEvents.upsertConversationEvent(incomingEvent)
                    } else {
                        state.conversationEvents
                    }
                    state.copy(
                        dashboard = envelope.dashboard ?: state.dashboard,
                        sessions = envelope.sessions ?: state.sessions,
                        approvals = envelope.approvals ?: state.approvals,
                        conversationEvents = updatedConversation,
                        queuedApprovalActions = queuedApprovalActions.size,
                        queuedApprovalActionQueue = queuedApprovalActions.toList(),
                        streamStatus = "Realtime event: ${envelope.type}",
                        approvalActionStatus = when {
                            envelope.approvalResult != null && reconciledQueuedAction != null -> {
                                "Live stream confirmed ${userFacingApprovalVerb(envelope.approvalResult.action)} for ${envelope.approvalResult.approvalId}"
                            }
                            envelope.approvalResult != null -> approvalActionMessage(envelope.approvalResult)
                            else -> state.approvalActionStatus
                        },
                        sessionControlStatus = envelope.sessionControlResult?.let { result ->
                            "Server confirmed ${result.action} for ${result.sessionId}: ${result.status}"
                        } ?: state.sessionControlStatus,
                    )
                }
                operatorNotifications.onRealtimeEnvelope(envelope, uiBeforeNotification)
            },
        )
    }

    private fun handleApprovalPollFallbackForStreamStatus(
        status: String,
        client: HermesGatewayClient,
    ) {
        if (status.contains("polling fallback", ignoreCase = true)) {
            startApprovalPollFallbackIfNeeded(client)
            return
        }
        if (status.contains("Realtime stream connected", ignoreCase = true)) {
            approvalPollFallbackJob?.cancel()
            approvalPollFallbackJob = null
        }
    }

    private fun startApprovalPollFallbackIfNeeded(client: HermesGatewayClient) {
        if (approvalPollFallbackJob?.isActive == true) return
        if (client is DemoHermesGatewayClient) return
        approvalPollFallbackJob = viewModelScope.launch {
            while (isActive) {
                val auth = currentSession
                if (auth == null) {
                    delay(15_000)
                    continue
                }
                val liveClient = liveClientOrNull()
                if (liveClient == null || liveClient is DemoHermesGatewayClient) {
                    delay(30_000)
                    continue
                }
                val previous = _uiState.value.approvals
                runCatching { liveClient.fetchApprovals(auth) }
                    .onSuccess { list ->
                        operatorNotifications.onApprovalListPolled(list, previous)
                        _uiState.update { it.copy(approvals = list) }
                    }
                delay(90_000)
            }
        }
    }

    private fun List<HermesConversationEvent>.upsertConversationEvent(event: HermesConversationEvent): List<HermesConversationEvent> {
        // Primary-key dedupe by eventId. LazyColumn uses eventId as its key, so letting a
        // duplicate in here would crash with "Key was already used" on the next measure pass
        // (observed in real device validation when the realtime stream re-delivered an event
        // that was already returned by the session-scoped REST fetch).
        val idIndex = indexOfFirst { it.eventId == event.eventId }
        if (idIndex >= 0) {
            return toMutableList().apply { this[idIndex] = event }
        }
        // Secondary match: optimistic echo (eventId starts with "local-") → replace when the
        // server echoes the same message back with its real eventId.
        val normalizedAuthor = event.author.trim().lowercase()
        val normalizedBody = event.body.trim().lowercase()
        val optimisticIndex = indexOfFirst {
            it.eventId.startsWith("local-") &&
                it.author.trim().lowercase() == normalizedAuthor &&
                it.body.trim().lowercase() == normalizedBody
        }
        if (optimisticIndex >= 0) {
            return toMutableList().apply { this[optimisticIndex] = event }
        }
        return this + event
    }

    private suspend fun flushQueuedApprovalActions(client: HermesGatewayClient, session: com.hermescourier.android.domain.model.HermesAuthSession?) {
        if (session == null || client is DemoHermesGatewayClient || queuedApprovalActions.isEmpty()) {
            _uiState.update { it.copy(queuedApprovalActions = queuedApprovalActions.size) }
            return
        }
        while (queuedApprovalActions.isNotEmpty()) {
            val queued = queuedApprovalActions.first()
            runCatching {
                client.submitApprovalAction(session, queued.approvalId, queued.action, queued.note)
            }.onSuccess { result ->
                removeQueuedApprovalAction(queued)
                persistQueuedApprovalActions()
                _uiState.update {
                    it.copy(
                        approvalActionStatus = "Flushed queued ${userFacingApprovalVerb(result.action)} for ${result.approvalId}: ${result.status}",
                        queuedApprovalActions = queuedApprovalActions.size,
                        queuedApprovalActionQueue = queuedApprovalActions.toList(),
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        approvalActionStatus = "Queued approval action still pending: ${error.localizedMessage ?: error}",
                        queuedApprovalActions = queuedApprovalActions.size,
                        queuedApprovalActionQueue = queuedApprovalActions.toList(),
                    )
                }
                return
            }
        }
    }

    private fun removeQueuedApprovalActionForResult(result: HermesApprovalActionResult): HermesQueuedApprovalAction? {
        val index = queuedApprovalActions.indexOfFirst { queued ->
            queuedApprovalActionMatchesResult(queued, result)
        }
        if (index >= 0) {
            val removed = queuedApprovalActions.removeAt(index)
            persistQueuedApprovalActions()
            return removed
        }
        return null
    }

    private fun removeQueuedApprovalAction(queued: HermesQueuedApprovalAction): HermesQueuedApprovalAction? {
        val index = queuedApprovalActions.indexOfFirst { it == queued }
        if (index >= 0) {
            val removed = queuedApprovalActions.removeAt(index)
            persistQueuedApprovalActions()
            return removed
        }
        return null
    }

    private fun updateReconnectCountdown(status: String) {
        val match = Regex("Realtime reconnecting in (\\d+)s", RegexOption.IGNORE_CASE).find(status)
        if (match != null) {
            val seconds = match.groupValues[1].toIntOrNull() ?: return
            reconnectCountdownJob?.cancel()
            reconnectCountdownJob = viewModelScope.launch {
                for (remaining in seconds downTo 1) {
                    val progress = 1f - (remaining.toFloat() / seconds.toFloat())
                    _uiState.update { it.copy(realtimeReconnectCountdown = "Reconnect retry in ${remaining}s", realtimeReconnectProgress = progress) }
                    delay(1000)
                }
                _uiState.update { it.copy(realtimeReconnectCountdown = "Retrying now", realtimeReconnectProgress = 1f) }
            }
        } else if (status.contains("connected", ignoreCase = true)) {
            reconnectCountdownJob?.cancel()
            reconnectCountdownJob = null
            _uiState.update { it.copy(realtimeReconnectCountdown = "Connected", realtimeReconnectProgress = 0f) }
        } else if (status.contains("disconnected", ignoreCase = true) || status.contains("error", ignoreCase = true)) {
            reconnectCountdownJob?.cancel()
            reconnectCountdownJob = null
            _uiState.update { it.copy(realtimeReconnectCountdown = "Reconnect now", realtimeReconnectProgress = 0f) }
        } else {
            reconnectCountdownJob?.cancel()
            reconnectCountdownJob = null
            _uiState.update { it.copy(realtimeReconnectCountdown = "Reconnect now", realtimeReconnectProgress = 0f) }
        }
    }

    private fun queueApprovalAction(approvalId: String, action: String, note: String?, reason: String) {
        queuedApprovalActions.addLast(
            HermesQueuedApprovalAction(
                approvalId = approvalId,
                action = action,
                note = note,
                createdAt = System.currentTimeMillis(),
            )
        )
        persistQueuedApprovalActions()
        _uiState.update {
            it.copy(
                approvalActionStatus = reason,
                queuedApprovalActions = queuedApprovalActions.size,
                queuedApprovalActionQueue = queuedApprovalActions.toList(),
            )
        }
    }

    private suspend fun syncSettingsFromDisk() {
        val loadedSettings = HermesGatewayConfiguration.from(applicationContext).toSettings()
        _uiState.update {
            it.copy(
                gatewaySettings = loadedSettings,
                deviceFingerprint = deviceIdentity.publicKeyFingerprint,
                enrollmentStatus = enrollmentStatus(loadedSettings),
                courierPairingStatus = pairingStatusFromTokenStore(),
                enrollmentQrPayload = enrollmentPayload(loadedSettings),
                queuedApprovalActions = queuedApprovalActions.size,
                queuedApprovalActionQueue = queuedApprovalActions.toList(),
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
            deviceFingerprint = deviceFingerprint,
            enrollmentStatus = enrollmentStatus(settings),
            courierPairingStatus = pairingStatusFromTokenStore(),
            enrollmentQrPayload = enrollmentPayload(settings),
            gatewayConnectionMode = "Unknown",
            gatewayConnectionDetail = "No gateway check has run yet",
            queuedApprovalActions = queuedApprovalActions.size,
            queuedApprovalActionQueue = queuedApprovalActions.toList(),
        )
    }

    private fun enrollmentStatus(settings: HermesGatewaySettings): String = when {
        settings.certificatePath.isBlank() -> "No certificate imported yet"
        settings.certificatePassword.isBlank() -> "Certificate imported; password required for mTLS enrollment"
        else -> "Certificate bundle enrolled and ready"
    }

    private fun enrollmentPayload(settings: HermesGatewaySettings): String {
        val payload = HermesEnrollmentPayload(
            gatewayUrl = settings.baseUrl,
            deviceId = deviceIdentity.deviceId,
            publicKeyFingerprint = deviceIdentity.publicKeyFingerprint,
            appVersion = deviceIdentity.appVersion,
            issuedAt = Instant.now().toString(),
        )
        return Uri.Builder()
            .scheme("hermes-courier-enroll")
            .authority("gateway")
            .appendQueryParameter("gatewayUrl", payload.gatewayUrl)
            .appendQueryParameter("deviceId", payload.deviceId)
            .appendQueryParameter("publicKeyFingerprint", payload.publicKeyFingerprint)
            .appendQueryParameter("appVersion", payload.appVersion)
            .appendQueryParameter("issuedAt", payload.issuedAt)
            .build()
            .toString()
    }

    private fun parseEnrollmentPayload(payload: String): HermesEnrollmentPayload? {
        return parseHermesEnrollmentPayload(payload = payload)
    }

    private suspend fun persistPairedBearerToken(gatewayUrl: String, bearerToken: String?) {
        val token = bearerToken?.trim().orEmpty()
        if (token.isBlank()) return
        EncryptedHermesTokenStore(applicationContext).save(
            HermesAuthSession(
                sessionId = "paired-bearer-${System.currentTimeMillis()}",
                accessToken = token,
                refreshToken = "",
                expiresAt = "paired-via-qr",
                gatewayUrl = gatewayUrl,
                mtlsRequired = false,
                scope = listOf("paired-bearer"),
            )
        )
    }

    private suspend fun liveClientOrNull(): HermesGatewayClient? {
        if (!hasPairedBearerToken()) return null
        return liveClientOrNull()
    }

    private suspend fun hasPairedBearerToken(): Boolean = runCatching {
        EncryptedHermesTokenStore(applicationContext).load()?.accessToken?.isNotBlank() == true
    }.getOrDefault(false)

    private fun pairingStatusFromTokenStore(): String {
        val hasToken = runCatching {
            runBlocking { EncryptedHermesTokenStore(applicationContext).load() }
        }.getOrNull()?.accessToken?.isNotBlank() == true
        return if (hasToken) {
            "Bearer pairing configured (manual token entry not required)"
        } else {
            "No paired bearer token configured"
        }
    }

    private suspend fun refreshPairingBackendStatus() {
        val configuration = HermesGatewayConfiguration.from(applicationContext)
        val transport = runCatching {
            OkHttpHermesGatewayTransport(
                baseUrl = configuration.baseUrl,
                client = HermesOkHttpClientFactory.create(configuration),
            )
        }.getOrElse { error ->
            _uiState.update {
                it.copy(
                    pairingBackendStatus = "Pairing backend status unavailable",
                    pairingBackendDetail = "Unable to build status transport: ${error.localizedMessage ?: error}",
                    pairingUnavailableReasons = emptyList(),
                )
            }
            return
        }
        runCatching {
            JSONObject(transport.get(HermesApiPaths.PAIRING_STATUS))
        }.onSuccess { json ->
            val pairingMode = json.optString("pairingMode", "unknown")
            val tokenBacked = json.optBoolean("tokenBackedPairingAvailable", false)
            val qrAvailable = json.optBoolean("qrPairingAvailable", false)
            val postScanBootstrap = json.optBoolean("postScanBootstrapAvailable", false)
            val unavailableReasons = mutableListOf<String>().apply {
                val reasons = json.optJSONArray("unavailableReasons")
                if (reasons != null) {
                    for (i in 0 until reasons.length()) {
                        val reason = reasons.optString(i).trim()
                        if (reason.isNotBlank()) add(reason)
                    }
                }
            }
            _uiState.update {
                it.copy(
                    pairingBackendStatus = if (tokenBacked && qrAvailable && pairingMode == "token-only") {
                        "Pairing backend ready for token-only QR flow"
                    } else {
                        "Pairing backend reports limited availability"
                    },
                    pairingBackendDetail = "mode=$pairingMode, tokenBackedPairingAvailable=$tokenBacked, qrPairingAvailable=$qrAvailable, postScanBootstrapAvailable=$postScanBootstrap",
                    pairingUnavailableReasons = unavailableReasons,
                )
            }
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    pairingBackendStatus = "Pairing backend status unavailable",
                    pairingBackendDetail = error.localizedMessage ?: error.toString(),
                    pairingUnavailableReasons = emptyList(),
                )
            }
        }
    }

    private fun persistQueuedApprovalActions() {
        val json = JSONArray().apply {
            queuedApprovalActions.forEach { action ->
                put(
                    JSONObject().apply {
                        put("approvalId", action.approvalId)
                        put("action", action.action)
                        put("note", action.note)
                        put("createdAt", action.createdAt)
                    }
                )
            }
        }
        queuedActionsFile.writeText(json.toString())
    }

    private fun loadQueuedApprovalActions() {
        if (!queuedActionsFile.exists()) return
        runCatching {
            val json = JSONArray(queuedActionsFile.readText())
            var migrated = false
            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)
                val rawAction = item.getString("action")
                val normalizedAction = migrateQueuedApprovalAction(rawAction)
                if (rawAction != normalizedAction) migrated = true
                queuedApprovalActions.addLast(
                    HermesQueuedApprovalAction(
                        approvalId = item.getString("approvalId"),
                        action = normalizedAction,
                        note = item.optString("note").takeIf { it.isNotBlank() },
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
            }
            if (migrated) persistQueuedApprovalActions()
        }
    }

    private fun approvalActionMessage(result: HermesApprovalActionResult): String =
        "${userFacingApprovalVerb(result.action)} approval ${result.approvalId}: ${result.status}"

    private fun defaultVerificationResults(reason: String, status: String): List<HermesEndpointVerificationResult> = listOf(
        HermesEndpointVerificationResult("auth/bootstrap", status, reason),
        HermesEndpointVerificationResult("dashboard", status, reason),
        HermesEndpointVerificationResult("sessions list", status, reason),
        HermesEndpointVerificationResult("session detail", status, reason),
        HermesEndpointVerificationResult("session-control pause", status, reason),
        HermesEndpointVerificationResult("session-control resume", status, reason),
        HermesEndpointVerificationResult("session-control terminate", status, reason),
        HermesEndpointVerificationResult("approvals", status, reason),
        HermesEndpointVerificationResult("conversation list", status, reason),
        HermesEndpointVerificationResult("conversation send", status, reason),
        HermesEndpointVerificationResult("realtime/events", status, reason),
    )

    private fun appVersionName(): String = runCatching {
        val packageInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
        packageInfo.versionName ?: "unknown"
    }.getOrDefault("unknown")

    override fun onCleared() {
        approvalPollFallbackJob?.cancel()
        approvalPollFallbackJob = null
        realtimeHandle?.close()
        super.onCleared()
    }
}
