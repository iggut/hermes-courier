package com.hermescourier.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.hermescourier.android.domain.model.HermesEndpointVerificationResult
import com.hermescourier.android.domain.model.HermesCourierUiState
import com.hermescourier.android.domain.model.HermesQueuedApprovalAction
import com.hermescourier.android.domain.model.userFacingApprovalVerb
import com.hermescourier.android.ui.components.CompactStatusStrip
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    uiState: HermesCourierUiState,
    onGatewayUrlChange: (String) -> Unit,
    onCertificatePasswordChange: (String) -> Unit,
    onImportCertificate: (Uri) -> Unit,
    onEnrollmentQrScanned: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onRefresh: () -> Unit,
    onTestLiveGateway: () -> Unit,
    onFlushQueuedActions: () -> Unit,
    onReconnectRealtime: () -> Unit,
    onShareEnrollmentQr: () -> Unit,
    onCopyEnrollmentQrPayload: () -> Unit,
    onRetryQueuedApprovalAction: (HermesQueuedApprovalAction) -> Unit,
    onCopyQueuedApprovalActionDetails: (HermesQueuedApprovalAction) -> Unit,
    onDismissQueuedApprovalAction: (HermesQueuedApprovalAction) -> HermesQueuedApprovalAction?,
    onRestoreQueuedApprovalAction: (HermesQueuedApprovalAction) -> Unit,
) {
    val certificatePicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImportCertificate) }
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result: ScanIntentResult ->
        result.contents?.let(onEnrollmentQrScanned)
    }

    val qrBitmap = remember(uiState.enrollmentQrPayload) {
        runCatching {
            BarcodeEncoder().encodeBitmap(uiState.enrollmentQrPayload, BarcodeFormat.QR_CODE, 512, 512)
        }.getOrNull()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var verificationExpanded by rememberSaveable { mutableStateOf(false) }
    var queuedExpanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompactStatusStrip(
                uiState = uiState,
                onReconnect = onReconnectRealtime,
                onRetryQueued = onFlushQueuedActions,
            )

            SetupChecklistCard(
                uiState = uiState,
                onScanEnrollmentQr = { qrScanner.launch(com.journeyapps.barcodescanner.ScanOptions()) },
                onTestLiveGateway = onTestLiveGateway,
                onReconnectRealtime = onReconnectRealtime,
            )

            SettingsSectionCard(title = "Connection") {
                KeyValueRow(
                    label = "Backend",
                    value = "Embedded Chaquopy Backend (${uiState.gatewaySettings.baseUrl})",
                )
                Text(
                    text = liveGatewayConnectionSummary(uiState),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = uiState.gatewaySettings.certificatePassword,
                    onValueChange = onCertificatePasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "PKCS#12 password (required)") },
                    singleLine = true,
                )
                Text(
                    text = "Required to enroll the imported PKCS#12 bundle and use it for mTLS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KeyValueRow(
                    label = "Certificate",
                    value = uiState.gatewaySettings.certificatePath.ifBlank { "Not imported" },
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            certificatePicker.launch(
                                arrayOf(
                                    "application/x-pkcs12",
                                    "application/pkcs12",
                                    "application/octet-stream",
                                ),
                            )
                        },
                    ) {
                        Text(text = "Import certificate")
                    }
                }
                KeyValueRow(label = "Enrollment", value = uiState.enrollmentStatus)
                KeyValueRow(label = "Pairing", value = uiState.courierPairingStatus)
                if (uiState.pairingUnavailableReasons.isNotEmpty()) {
                    Text(
                        text = "Reasons: ${uiState.pairingUnavailableReasons.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                KeyValueRow(
                    label = "Mode",
                    value = "${uiState.gatewayConnectionMode} · ${uiState.realtimeReconnectCountdown}",
                )
                LinearProgressIndicator(
                    progress = { uiState.realtimeReconnectProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
                KeyValueRow(label = "Verification", value = uiState.verificationMode)

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Button(onClick = onSaveSettings) { Text("Save") }
                    OutlinedButton(onClick = onTestLiveGateway) { Text("Live test") }
                    OutlinedButton(onClick = onRefresh) { Text("Refresh") }
                    OutlinedButton(onClick = onReconnectRealtime) { Text("Reconnect") }
                }
            }

            SettingsSectionCard(
                title = "Verification report",
                subtitle = verificationSummaryLine(uiState.endpointVerificationResults),
                trailing = {
                    TextButton(onClick = { verificationExpanded = !verificationExpanded }) {
                        Text(text = if (verificationExpanded) "Hide" else "Details")
                    }
                },
            ) {
                if (uiState.endpointVerificationResults.isEmpty()) {
                    Text(
                        text = "No verification report yet. Run a live test to populate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (verificationExpanded) {
                    uiState.endpointVerificationResults.forEach { result ->
                        Text(
                            text = "${result.endpoint}: ${result.status.uppercase()} — ${result.reason}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            SettingsSectionCard(title = "Device enrollment") {
                Text(
                    text = "Scan the Hermes WebUI pairing QR to import pairing settings. Token-only and certificate modes are both supported; sharing this QR remains available for fallback workflows.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Enrollment QR",
                        modifier = Modifier
                            .size(180.dp)
                            .padding(top = 4.dp),
                    )
                }
                Text(
                    text = uiState.enrollmentQrPayload,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Button(onClick = { qrScanner.launch(com.journeyapps.barcodescanner.ScanOptions()) }) {
                        Text(text = "Scan QR")
                    }
                    OutlinedButton(onClick = onShareEnrollmentQr) { Text(text = "Share QR") }
                    OutlinedButton(onClick = onCopyEnrollmentQrPayload) { Text(text = "Copy payload") }
                }
            }

            SettingsSectionCard(
                title = "Queued approval actions",
                subtitle = queuedApprovalsSummary(uiState),
                trailing = if (uiState.queuedApprovalActionQueue.isNotEmpty()) {
                    {
                        TextButton(onClick = { queuedExpanded = !queuedExpanded }) {
                            Text(text = if (queuedExpanded) "Hide" else "Details")
                        }
                    }
                } else null,
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Button(onClick = onFlushQueuedActions) { Text("Flush now") }
                }
                if (uiState.queuedApprovalActionQueue.isEmpty()) {
                    Text(
                        text = "No queued approval actions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (queuedExpanded) {
                    uiState.queuedApprovalActionQueue.forEach { queued ->
                        QueuedActionItem(
                            queued = queued,
                            onCopyDetails = { onCopyQueuedApprovalActionDetails(queued) },
                            onRetry = { onRetryQueuedApprovalAction(queued) },
                            onDismiss = {
                                val dismissed = onDismissQueuedApprovalAction(queued)
                                if (dismissed != null) {
                                    coroutineScope.launch {
                                        val snackbarResult = snackbarHostState.showSnackbar(
                                            message = "Dismissed ${userFacingApprovalVerb(dismissed.action)} for ${dismissed.approvalId}",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (snackbarResult == SnackbarResult.ActionPerformed) {
                                            onRestoreQueuedApprovalAction(dismissed)
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
                if (uiState.approvalActionStatus.isNotBlank() && uiState.approvalActionStatus != "idle") {
                    Text(
                        text = "Last action: ${uiState.approvalActionStatus}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsSectionCard(title = "Secure bootstrap") {
                Text(
                    text = "Import the PKCS#12 certificate bundle from storage and persist it inside the app sandbox.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        certificatePicker.launch(arrayOf("application/x-pkcs12", "application/octet-stream", "*/*"))
                    },
                ) {
                    Text(text = "Import certificate")
                }
            }

            Spacer(modifier = Modifier.size(8.dp))
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(colors = CardDefaults.cardColors()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupChecklistCard(
    uiState: HermesCourierUiState,
    onScanEnrollmentQr: () -> Unit,
    onTestLiveGateway: () -> Unit,
    onReconnectRealtime: () -> Unit,
) {
    val hasGatewayUrl = uiState.gatewaySettings.baseUrl.isNotBlank()
    val hasPairingToken = uiState.courierPairingStatus.contains("configured", ignoreCase = true)
    val isLiveConnected = uiState.gatewayConnectionMode.contains("live", ignoreCase = true) &&
        !uiState.gatewayConnectionMode.contains("demo", ignoreCase = true) &&
        !uiState.bootstrapState.contains("Demo fallback", ignoreCase = true)

    val checks = listOf(
        Triple("Gateway URL", hasGatewayUrl, "Enter a reachable HTTPS base URL"),
        Triple("Paired bearer token", hasPairingToken, "Scan the WebUI pairing QR"),
        Triple("Live gateway connected", isLiveConnected, "Run live test after pairing"),
    )
    val completed = checks.count { it.second }

    SettingsSectionCard(
        title = "Setup",
        subtitle = "$completed/${checks.size} ready · ${liveGatewayConnectionSummary(uiState)}",
    ) {
        checks.forEach { (label, done, hint) ->
            ChecklistRow(label = label, done = done, hint = hint)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(onClick = onScanEnrollmentQr) { Text("Scan QR") }
            OutlinedButton(onClick = onTestLiveGateway) { Text("Live test") }
            OutlinedButton(onClick = onReconnectRealtime) { Text("Reconnect") }
        }
    }
}

@Composable
private fun ChecklistRow(label: String, done: Boolean, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (done) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (done) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = if (done) "done" else "pending",
                tint = if (done) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (done) FontWeight.Normal else FontWeight.SemiBold,
            )
            if (!done) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueuedActionItem(
    queued: HermesQueuedApprovalAction,
    onCopyDetails: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${userFacingApprovalVerb(queued.action).uppercase()} · ${queued.approvalId}",
                style = MaterialTheme.typography.titleSmall,
            )
            queued.note?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "Queued at ${queued.createdAt}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onRetry) { Text("Retry") }
                TextButton(onClick = onCopyDetails) { Text("Copy") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

private fun queuedApprovalsSummary(uiState: HermesCourierUiState): String {
    val count = uiState.queuedApprovalActionQueue.size
    return when {
        count == 0 -> "Nothing queued. Offline approvals would appear here."
        count == 1 -> "1 action queued offline."
        else -> "$count actions queued offline."
    }
}

private fun verificationSummaryLine(results: List<HermesEndpointVerificationResult>): String {
    if (results.isEmpty()) return "Per-endpoint verification has not run yet."
    val s = verificationSummary(results)
    return "OK ${s.ok} · Unsupported ${s.unsupported} · Drift ${s.drift} · Failed ${s.failed} · Skipped ${s.skipped}"
}

/**
 * One-line, production-oriented summary: demo vs live, paired-but-down, auth vs network vs success.
 */
internal fun liveGatewayConnectionSummary(ui: HermesCourierUiState): String {
    val mode = ui.gatewayConnectionMode
    val detail = ui.gatewayConnectionDetail
    val auth = ui.authStatus
    val hasToken = ui.courierPairingStatus.contains("configured", ignoreCase = true)
    val demo = mode.contains("Demo fallback", ignoreCase = true) ||
        ui.bootstrapState.contains("Demo fallback", ignoreCase = true) ||
        auth.contains("offline-safe sample", ignoreCase = true)
    if (demo) {
        return "Mode: sample data (not your live gateway)."
    }
    if (mode.contains("Live gateway", ignoreCase = true) && ui.streamStatus.contains("connected", ignoreCase = true)) {
        return "Mode: live — connected over ${ui.gatewaySettings.baseUrl}."
    }
    if (detail.contains(" 401", ignoreCase = false) || detail.contains("401:", ignoreCase = true) || auth.contains("401", ignoreCase = true)) {
        return "Live: bearer auth failed (token rejected, expired, or blocked)."
    }
    if (hasToken && (detail.contains("Unable to resolve host", ignoreCase = true) ||
            detail.contains("Failed to connect", ignoreCase = true) ||
            detail.contains("Connection refused", ignoreCase = true) ||
            detail.contains("ETIMEDOUT", ignoreCase = true) ||
            detail.contains("timeout", ignoreCase = true) ||
            detail.contains("Network is unreachable", ignoreCase = true) ||
            detail.contains("ENETUNREACH", ignoreCase = true) ||
            detail.contains("ECONNREFUSED", ignoreCase = true))
    ) {
        return "Paired, but the gateway is unreachable (DNS, network, or Tailscale path)."
    }
    if (hasToken && mode.contains("unavailable", ignoreCase = true)) {
        return "Paired, but the gateway is not responding — see messages below."
    }
    if (hasToken) {
        return "Paired; use “Live test” to confirm HTTPS reachability."
    }
    return "Not paired: scan the WebUI pairing QR."
}

private data class VerificationSummary(
    val ok: Int,
    val unsupported: Int,
    val drift: Int,
    val failed: Int,
    val skipped: Int,
)

private fun verificationSummary(results: List<HermesEndpointVerificationResult>): VerificationSummary {
    var ok = 0
    var unsupported = 0
    var drift = 0
    var failed = 0
    var skipped = 0
    results.forEach { result ->
        when (result.status.lowercase()) {
            "ok" -> ok += 1
            "unsupported" -> unsupported += 1
            "drift" -> drift += 1
            "failed" -> failed += 1
            "skipped" -> skipped += 1
        }
    }
    return VerificationSummary(ok, unsupported, drift, failed, skipped)
}
