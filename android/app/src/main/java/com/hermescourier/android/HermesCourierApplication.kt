package com.hermescourier.android

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.hermescourier.android.logging.CrashLogWriter
import com.hermescourier.android.notifications.HermesNotificationChannels

class HermesCourierApplication : Application() {
    override fun onCreate() {
        CrashLogWriter.install(this)
        super.onCreate()
        HermesNotificationChannels.ensureCreated(this)

        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(this))
            }
            // Bootstrap the embedded backend server on a background thread
            // to avoid blocking the main UI thread with the server loop
            Thread {
                try {
                    val py = Python.getInstance()
                    val backendModule = py.getModule("backend")
                    backendModule.callAttr("start_server")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
            // In a production app we might want to log this or notify the user
            // that the embedded backend failed to start.
        }
    }
}
