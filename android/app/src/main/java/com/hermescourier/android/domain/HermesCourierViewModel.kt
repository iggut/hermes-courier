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
import com.hermescourier.android.domain.model.HermesCourierUiState
import com.hermescourier.android.domain.model.HermesDeviceIdentity
import com.hermescourier.android.domain.model.HermesEnrollmentPayload
import com.hermescourier.android.domain.model.HermesGatewaySettings
import com.hermescourier.android.domain.model.HermesQueuedApprovalAction
import com.hermescourier.android.domain.model.migrateQueuedApprovalAction
import com.hermescourier.android.domain.model.userFacingApprovalVerb
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private var reconnectCountdownJob: Job? = null
    private val queuedApprovalActions = ArrayDeque<HermesQueuedApprovalAction>()
    private val queuedActionsFile = File(applicationContext.filesDir, "hermes-queued-approval-actions.json")

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<HermesCourierUiState> = _uiState.asStateFlow()

    private val deviceIdentity = HermesDeviceIdentity(
        deviceId = "android-courier-${android.os.Build.MODEL.lowercase().replace(' ', '-')}",
        platform = "android",
        appVersion = "0.1.0",
        publicKeyFingerprint = deviceFingerprint,
    )

    init {
        loadQueuedApprovalActions()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            reconnectCountdownJob?.cancel()
            reconnectCountdownJob = null
            syncSettingsFromDisk()
            _uiState.update {
                it.copy(
                    bootstrapState = "Negotiating secure gateway",
                    authStatus = "Requesting device challenge",
                    realtimeReconnectProgress = 0f,
                    realtimeReconnectCountdown = "Reconnect now",
                )
            }
            val liveClient = HermesGatewayClientFactory.createOrNull(applicationContext)
            if (liveClient == null) {
                _uiState.update {
                    it.copy(
                        bootstrapState = "Gateway unavailable",
                        authStatus = "Using offline-safe sample data because secure gateway initialization failed",
                        streamStatus = "Demo realtime stream active",
                    )
                }
                runCatching {
                    loadFromGateway(fallbackGatewayClient)
                }.onSuccess { fallbackState ->
                    _uiState.value = fallbackState.copy(
                        bootstrapState = "Demo fallback active",
                        authStatus = "Using offline-safe sample data",
                        streamStatus = "Demo realtime stream active",
                    )
                }.onFailure { fallbackError ->
                    _uiState.update {
                        it.copy(
                            bootstrapState = "Gateway unavailable",
                            authStatus = fallbackError.localizedMessage ?: fallbackError.toString(),
                            streamStatus = "Realtime stream unavailable",
                        )
                    }
                }
                return@launch
            }
            runCatching {
                loadFromGateway(liveClient)
            }.onSuccess { state ->
                _uiState.value = state
                flushQueuedApprovalActions(liveClient, currentSession)
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
                            authStatus = fallbackError.localizedMessage ?: fallbackError.toString(),
                            streamStatus = "Realtime stream unavailable",
                        )
                    }
                }
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

    fun applyEnrollmentQr(payload: String) {
        val parsed = parseEnrollmentPayload(payload)
        if (parsed == null) {
            _uiState.update { it.copy(enrollmentStatus = "Enrollment QR could not be parsed") }
            return
        }
        val updatedSettings = _uiState.value.gatewaySettings.copy(baseUrl = parsed.gatewayUrl)
        _uiState.update {
            it.copy(
                gatewaySettings = updatedSettings,
                enrollmentStatus = "Enrollment QR scanned for ${parsed.gatewayUrl}",
                enrollmentQrPayload = enrollmentPayload(updatedSettings),
            )
        }
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

    private suspend fun loadFromGateway(client: HermesGatewayClient): HermesCourierUiState {
        val session = client.bootstrap(deviceIdentity)
        currentSession = session
        val dashboard = client.fetchDashboard(session)
        val sessions = client.fetchSessions(session)
        val approvals = client.fetchApprovals(session)
        val conversation = client.fetchConversation(session)
        startRealtime(client, session)
        val settings = HermesGatewayConfiguration.from(applicationContext).toSettings()
        val queuedCount = queuedApprovalActions.size
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
            enrollmentQrPayload = enrollmentPayload(settings),
            queuedApprovalActions = queuedCount,
            queuedApprovalActionQueue = queuedApprovalActions.toList(),
            streamStatus = "Realtime stream connected",
            realtimeReconnectCountdown = "Connected",
        )
    }

    private fun startRealtime(client: HermesGatewayClient, session: com.hermescourier.android.domain.model.HermesAuthSession) {
        realtimeHandle?.close()
        realtimeHandle = client.connectRealtime(
            session = session,
            onStatus = { status ->
                _uiState.update { it.copy(streamStatus = status) }
                updateReconnectCountdown(status)
                if (status.contains("connected", ignoreCase = true) && client !is DemoHermesGatewayClient) {
                    viewModelScope.launch { flushQueuedApprovalActions(client, session) }
                }
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
            enrollmentQrPayload = enrollmentPayload(settings),
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
        val uri = runCatching { Uri.parse(payload) }.getOrNull() ?: return null
        if (uri.scheme != "hermes-courier-enroll") return null
        val gatewayUrl = uri.getQueryParameter("gatewayUrl") ?: return null
        return HermesEnrollmentPayload(
            gatewayUrl = gatewayUrl,
            deviceId = uri.getQueryParameter("deviceId") ?: deviceIdentity.deviceId,
            publicKeyFingerprint = uri.getQueryParameter("publicKeyFingerprint") ?: deviceIdentity.publicKeyFingerprint,
            appVersion = uri.getQueryParameter("appVersion") ?: deviceIdentity.appVersion,
            issuedAt = uri.getQueryParameter("issuedAt") ?: Instant.now().toString(),
        )
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

    override fun onCleared() {
        realtimeHandle?.close()
        super.onCleared()
    }
}
