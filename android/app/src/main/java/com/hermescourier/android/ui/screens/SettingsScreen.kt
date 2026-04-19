package com.hermescourier.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesCourierUiState

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    uiState: HermesCourierUiState,
    onGatewayUrlChange: (String) -> Unit,
    onCertificatePasswordChange: (String) -> Unit,
    onImportCertificate: (Uri) -> Unit,
    onSaveSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    val certificatePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportCertificate)
    }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Configure the secure gateway endpoint and import the device certificate bundle.")

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
                Text(text = "Gateway connection", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = uiState.gatewaySettings.baseUrl,
                    onValueChange = onGatewayUrlChange,
                    label = { Text(text = "Gateway URL") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.gatewaySettings.certificatePassword,
                    onValueChange = onCertificatePasswordChange,
                    label = { Text(text = "PKCS#12 password") },
                    singleLine = true,
                )
                Text(text = "Certificate path: ${if (uiState.gatewaySettings.certificatePath.isBlank()) "None" else uiState.gatewaySettings.certificatePath}")
                Text(text = "Enrollment: ${uiState.enrollmentStatus}", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { certificatePicker.launch(arrayOf("application/x-pkcs12", "application/x-pkcs7-mime", "*/*")) }) {
                    Text(text = "Import certificate bundle")
                }
                Button(onClick = onSaveSettings) {
                    Text(text = "Save secure settings")
                }
                Button(onClick = onRefresh) {
                    Text(text = "Reconnect gateway")
                }
            }
        }

        Card {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                Text(text = "Enrollment fingerprint", style = MaterialTheme.typography.titleMedium)
                Text(text = uiState.deviceFingerprint)
                Text(
                    text = "Use this fingerprint to enroll the mobile device with your Hermes gateway before importing the issued certificate.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
