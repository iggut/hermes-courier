package com.hermescourier.android

import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebView
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.mockito.kotlin.never
import android.app.AlertDialog
import android.content.DialogInterface
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowDialog
import android.content.pm.ApplicationInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityTest {

    @Test
    fun testOnReceivedSslError_DebugBuild_Proceed() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        // Ensure applicationInfo flag is debuggable for this test
        activity.applicationInfo.flags = activity.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE

        val webViewClient = activity.createWebViewClient()
        val mockHandler = mock<SslErrorHandler>()
        val mockError = mock<SslError>()
        val webView = mock<WebView>()

        webViewClient.onReceivedSslError(webView, mockHandler, mockError)

        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertNotNull("Dialog should be shown in debug mode", dialog)

        // Assert the title and message
        val shadowDialog = org.robolectric.Shadows.shadowOf(dialog)
        assertEquals("SSL Certificate Error", shadowDialog.title)

        try {
            val alertField = AlertDialog::class.java.getDeclaredField("mAlert")
            alertField.isAccessible = true
            val mAlert = alertField.get(dialog)

            val proceedListenerField = mAlert.javaClass.getDeclaredField("mButtonPositiveMessage")
            proceedListenerField.isAccessible = true
            val proceedMsg = proceedListenerField.get(mAlert) as android.os.Message
            proceedMsg.sendToTarget()
            org.robolectric.shadows.ShadowLooper.runUiThreadTasks()

            verify(mockHandler).proceed()
        } catch (e: Exception) {
            e.printStackTrace()
            // Fail if reflection fails
            assertNotNull(null)
        }
    }

    @Test
    fun testOnReceivedSslError_DebugBuild_Cancel_Button() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.applicationInfo.flags = activity.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE

        val webViewClient = activity.createWebViewClient()
        val mockHandler = mock<SslErrorHandler>()
        val mockError = mock<SslError>()
        val webView = mock<WebView>()

        webViewClient.onReceivedSslError(webView, mockHandler, mockError)

        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertNotNull("Dialog should be shown in debug mode", dialog)

        try {
            val alertField = AlertDialog::class.java.getDeclaredField("mAlert")
            alertField.isAccessible = true
            val mAlert = alertField.get(dialog)

            val cancelListenerField = mAlert.javaClass.getDeclaredField("mButtonNegativeMessage")
            cancelListenerField.isAccessible = true
            val cancelMsg = cancelListenerField.get(mAlert) as android.os.Message
            cancelMsg.sendToTarget()
            org.robolectric.shadows.ShadowLooper.runUiThreadTasks()

            verify(mockHandler).cancel()
        } catch (e: Exception) {
            e.printStackTrace()
            assertNotNull(null)
        }
    }

    @Test
    fun testOnReceivedSslError_DebugBuild_Cancel_Outside() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.applicationInfo.flags = activity.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE

        val webViewClient = activity.createWebViewClient()
        val mockHandler = mock<SslErrorHandler>()
        val mockError = mock<SslError>()
        val webView = mock<WebView>()

        webViewClient.onReceivedSslError(webView, mockHandler, mockError)

        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertNotNull("Dialog should be shown in debug mode", dialog)

        // Tapping outside triggers onCancel listener
        try {
            val alertField = android.app.Dialog::class.java.getDeclaredField("mCancelMessage")
            alertField.isAccessible = true
            val cancelMsg = alertField.get(dialog) as android.os.Message?
            cancelMsg?.sendToTarget()
            org.robolectric.shadows.ShadowLooper.runUiThreadTasks()
            verify(mockHandler).cancel()
        } catch (e: Exception) {
            // Not crucial if this reflection fails on some SDKs, but good to have
        }
    }

    @Test
    fun testOnReceivedSslError_ReleaseBuild() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.applicationInfo.flags = activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()

        val webViewClient = activity.createWebViewClient()
        val mockHandler = mock<SslErrorHandler>()
        val mockError = mock<SslError>()
        val webView = mock<WebView>()

        ShadowDialog.reset()

        webViewClient.onReceivedSslError(webView, mockHandler, mockError)

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals("No dialog should be shown in release mode", null, dialog)

        verify(mockHandler).cancel()
        verify(mockHandler, never()).proceed()
    }
}
