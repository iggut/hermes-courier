
package com.hermescourier.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hermescourier.android.ui.theme.HermesCourierTheme
import com.hermescourier.android.ui.HermesCourierApp

class MainActivity : ComponentActivity() {
    private var pendingEnrollmentPayload: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingEnrollmentPayload = intent?.dataString
        enableEdgeToEdge()
        setContent {
            HermesCourierTheme {
                HermesCourierApp(
                    initialEnrollmentPayload = pendingEnrollmentPayload,
                    onInitialEnrollmentPayloadConsumed = { pendingEnrollmentPayload = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val payload = intent.dataString ?: return
        pendingEnrollmentPayload = payload
        setContent {
            HermesCourierTheme {
                HermesCourierApp(
                    initialEnrollmentPayload = pendingEnrollmentPayload,
                    onInitialEnrollmentPayloadConsumed = { pendingEnrollmentPayload = null },
                )
            }
        }
    }
}
