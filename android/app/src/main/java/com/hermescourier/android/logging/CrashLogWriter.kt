package com.hermescourier.android.logging

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CrashLogWriter {
    private const val TAG = "CrashLogWriter"
    private const val PUBLIC_SUBDIR = "Hermes Courier"
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)
        .withZone(ZoneId.systemDefault())

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val appContext = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching {
                    val report = buildReport(appContext, thread.name, throwable)
                    val location = writeToDownloads(appContext, report)
                    Log.e(TAG, "Wrote crash log to $location")
                }.onFailure { loggingError ->
                    Log.e(TAG, "Failed to write crash log", loggingError)
                }
                previous?.uncaughtException(thread, throwable)
                    ?: run {
                        android.os.Process.killProcess(android.os.Process.myPid())
                        kotlin.system.exitProcess(10)
                    }
            }
            installed = true
        }
    }

    private fun buildReport(context: Context, threadName: String, throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        val packageManager = context.packageManager
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()
        val versionName = packageInfo?.versionName ?: "unknown"
        val versionCode = when {
            packageInfo == null -> "unknown"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> packageInfo.longVersionCode.toString()
            else -> @Suppress("DEPRECATION") packageInfo.versionCode.toString()
        }
        return buildString {
            appendLine("Hermes Courier crash report")
            appendLine("Timestamp: ${timestampFormatter.format(Instant.now())}")
            appendLine("Thread: $threadName")
            appendLine("Package: ${context.packageName}")
            appendLine("Version name: $versionName")
            appendLine("Version code: $versionCode")
            if (packageInfo != null) {
                appendLine("App label: ${packageInfo.applicationInfo?.loadLabel(packageManager)}")
            }
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Fingerprint: ${Build.FINGERPRINT}")
            appendLine()
            appendLine(writer.toString())
        }
    }

    private fun writeToDownloads(context: Context, report: String): String {
        val fileName = "orderking-pos-observer-crash-${timestampFormatter.format(Instant.now())}.txt"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/$PUBLIC_SUBDIR")
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Unable to create downloads entry")
            resolver.openOutputStream(uri, "w")?.use { output ->
                output.write(report.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Unable to open downloads output stream")
            uri.toString()
        } else {
            val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), PUBLIC_SUBDIR)
                .apply { mkdirs() }
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { output ->
                output.write(report.toByteArray(Charsets.UTF_8))
            }
            file.absolutePath
        }
    }
}
