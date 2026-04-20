package com.hermescourier.android.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.hermescourier.android.domain.model.HermesCourierUiState

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Configure the secure gateway endpoint, import the device certificate bundle, and enroll with QR-based provisioning.")

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
                Text(text = "Queued approvals: ${uiState.queuedApprovalActions}")
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
                Button(onClick = { qrScanner.launch(com.journeyapps.barcodescanner.ScanOptions()) }) {
                    Text(text = "Scan enrollment QR")
                }
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
                Button(onClick = onSaveSettings) {
                    Text(text = "Save settings")
                }
                Button(onClick = onRefresh) {
                    Text(text = "Refresh connection")
                }
            }
        }
    }
}
