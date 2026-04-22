
package com.hermescourier.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.hermescourier.android.ui.theme.HermesCourierTheme
import com.hermescourier.android.ui.HermesCourierApp

class MainActivity : ComponentActivity() {
    private var pendingEnrollmentPayload: String? = null
    private var pendingOperatorNavUri: Uri? = null

    private val postNotificationsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            postNotificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        captureRoutingIntents(intent)
        enableEdgeToEdge()
        setContent {
            HermesCourierTheme {
                HermesCourierApp(
                    initialEnrollmentPayload = pendingEnrollmentPayload,
                    onInitialEnrollmentPayloadConsumed = { pendingEnrollmentPayload = null },
                    initialOperatorNavUri = pendingOperatorNavUri,
                    onInitialOperatorNavUriConsumed = { pendingOperatorNavUri = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureRoutingIntents(intent)
        setContent {
            HermesCourierTheme {
                HermesCourierApp(
                    initialEnrollmentPayload = pendingEnrollmentPayload,
                    onInitialEnrollmentPayloadConsumed = { pendingEnrollmentPayload = null },
                    initialOperatorNavUri = pendingOperatorNavUri,
                    onInitialOperatorNavUriConsumed = { pendingOperatorNavUri = null },
                )
            }
        }
    }

    private fun captureRoutingIntents(intent: Intent?) {
        val uri = intent?.data ?: return
        when {
            uri.scheme.equals("hermes-courier-enroll", ignoreCase = true) -> {
                pendingEnrollmentPayload = uri.toString()
                pendingOperatorNavUri = null
            }
            uri.scheme.equals("hermes-courier", ignoreCase = true) &&
                uri.host.equals("nav", ignoreCase = true) -> {
                pendingOperatorNavUri = uri
            }
        }
    }
}
