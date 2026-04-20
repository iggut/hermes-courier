package com.hermescourier.android

import android.app.Application
import com.hermescourier.android.logging.CrashLogWriter

class HermesCourierApplication : Application() {
    override fun onCreate() {
        CrashLogWriter.install(this)
        super.onCreate()
    }
}
