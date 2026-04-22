package com.hermescourier.android

import android.app.Application
import com.hermescourier.android.logging.CrashLogWriter
import com.hermescourier.android.notifications.HermesNotificationChannels

class HermesCourierApplication : Application() {
    override fun onCreate() {
        CrashLogWriter.install(this)
        super.onCreate()
        HermesNotificationChannels.ensureCreated(this)
    }
}
