package com.hermescourier.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.hermescourier.android.domain.model.HermesEndpointVerificationResult
import com.hermescourier.android.domain.model.HermesCourierUiState
import com.hermescourier.android.domain.model.HermesQueuedApprovalAction
import com.hermescourier.android.domain.model.userFacingApprovalVerb
import kotlinx.coroutines.launch

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
    val certificatePicker = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportCertificate)
    }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Configure the secure gateway endpoint, enroll the device, and manage queued approvals from the companion app.")

        SetupReadinessCard(
            uiState = uiState,
            onScanEnrollmentQr = { qrScanner.launch(com.journeyapps.barcodescanner.ScanOptions()) },
            onTestLiveGateway = onTestLiveGateway,
            onReconnectRealtime = onReconnectRealtime,
        )

        Card(colors = CardDefaults.cardColors()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Connection", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = uiState.gatewaySettings.baseUrl,
                    onValueChange = onGatewayUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Gateway URL") },
                )
                OutlinedTextField(
                    value = uiState.gatewaySettings.certificatePassword,
                    onValueChange = onCertificatePasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "PKCS#12 password") },
                )
                Text(text = "Certificate path: ${uiState.gatewaySettings.certificatePath.ifBlank { "Not imported" }}")
                Text(text = "Enrollment status: ${uiState.enrollmentStatus}")
                Text(text = "Courier pairing: ${uiState.courierPairingStatus}")
                Text(text = "Gateway mode: ${uiState.gatewayConnectionMode} (${uiState.realtimeReconnectCountdown})")
                LinearProgressIndicator(
                    progress = uiState.realtimeReconnectProgress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(text = "Verification mode: ${uiState.verificationMode}")
                Button(onClick = onSaveSettings) { Text(text = "Save settings") }
                Button(onClick = onTestLiveGateway) { Text(text = "Test live gateway") }
                Button(onClick = onRefresh) { Text(text = "Refresh connection") }
                Button(onClick = onReconnectRealtime) { Text(text = "Reconnect realtime now") }
            }
        }

        Card(colors = CardDefaults.cardColors()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Live verification report", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Each endpoint/action is reported independently. Demo fallback is always explicit here.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (uiState.endpointVerificationResults.isEmpty()) {
                    Text(text = "No verification report yet.")
                } else {
                    val grouped = verificationSummary(uiState.endpointVerificationResults)
                    Text(
                        text = "OK ${grouped.ok} · Unsupported ${grouped.unsupported} · Drift ${grouped.drift} · Failed ${grouped.failed} · Skipped ${grouped.skipped}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    uiState.endpointVerificationResults.forEach { result ->
                        Text(
                            text = "${result.endpoint}: ${result.status.uppercase()} — ${result.reason}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Device enrollment QR", style = MaterialTheme.typography.titleMedium)
                Text(text = "Share this QR with the gateway or scan an enrollment QR to prefill the secure gateway address.")
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Enrollment QR",
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                Text(text = uiState.enrollmentQrPayload)
                Button(onClick = onShareEnrollmentQr) { Text(text = "Share enrollment QR") }
                Button(onClick = onCopyEnrollmentQrPayload) { Text(text = "Copy enrollment QR payload") }
                Button(onClick = { qrScanner.launch(com.journeyapps.barcodescanner.ScanOptions()) }) {
                    Text(text = "Scan enrollment QR")
                }
            }
        }

        Card(colors = CardDefaults.cardColors()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Queued approval actions", style = MaterialTheme.typography.titleMedium)
                Text(text = "Queued decisions are persisted locally until the live gateway is reachable again.")
                Button(onClick = onFlushQueuedActions) { Text(text = "Flush queued actions now") }
                if (uiState.queuedApprovalActionQueue.isEmpty()) {
                    Text(text = "No queued approval actions.")
                } else {
                    uiState.queuedApprovalActionQueue.forEach { queued ->
                        Card(colors = CardDefaults.cardColors()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = "${userFacingApprovalVerb(queued.action).uppercase()} • ${queued.approvalId}", style = MaterialTheme.typography.titleSmall)
                                queued.note?.let { Text(text = it) }
                                Text(text = "Queued at: ${queued.createdAt}", style = MaterialTheme.typography.labelSmall)
                                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onCopyQueuedApprovalActionDetails(queued) }) { Text(text = "Copy details") }
                                    Button(onClick = { onRetryQueuedApprovalAction(queued) }) { Text(text = "Retry now") }
                                    Button(onClick = {
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
                                    }) { Text(text = "Dismiss") }
                                }
                            }
                        }
                    }
                }
                Text(text = "Retry status: ${uiState.approvalActionStatus}")
            }
        }

        Card(colors = CardDefaults.cardColors()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Secure bootstrap", style = MaterialTheme.typography.titleMedium)
                Text(text = "Import the PKCS#12 certificate bundle from storage and persist it inside the app sandbox.")
                Button(onClick = {
                    certificatePicker.launch(arrayOf("application/x-pkcs12", "application/octet-stream", "*/*"))
                }) {
                    Text(text = "Import certificate bundle")
                }
            }
        }
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SetupReadinessCard(
    uiState: HermesCourierUiState,
    onScanEnrollmentQr: () -> Unit,
    onTestLiveGateway: () -> Unit,
    onReconnectRealtime: () -> Unit,
) {
    val hasGatewayUrl = uiState.gatewaySettings.baseUrl.isNotBlank()
    val hasCertificate = uiState.gatewaySettings.certificatePath.isNotBlank()
    val hasCertificatePassword = uiState.gatewaySettings.certificatePassword.isNotBlank()
    val hasPairingToken = uiState.courierPairingStatus.contains("configured", ignoreCase = true)
    val isLiveConnected = uiState.gatewayConnectionMode.contains("live", ignoreCase = true)

    val readiness = listOf(
        "Gateway URL" to hasGatewayUrl,
        "Certificate" to hasCertificate,
        "Certificate password" to hasCertificatePassword,
        "Pairing token" to hasPairingToken,
        "Live connection test" to isLiveConnected,
    )
    val completed = readiness.count { it.second }

    Card(colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Setup readiness", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "$completed/${readiness.size} checks complete",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            readiness.forEach { (label, done) ->
                Text(text = "${if (done) "OK" else "TODO"}  $label", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "Status: ${uiState.gatewayConnectionMode} · ${uiState.streamStatus}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScanEnrollmentQr) { Text("Scan pairing QR") }
                Button(onClick = onTestLiveGateway) { Text("Run live test") }
                Button(onClick = onReconnectRealtime) { Text("Reconnect") }
            }
        }
    }
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
