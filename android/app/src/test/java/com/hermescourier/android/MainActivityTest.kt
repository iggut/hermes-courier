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
import android.content.pm.ApplicationInfo
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityTest {

    @Test
    fun testOnReceivedSslError_DebugBuild_Blocked() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.applicationInfo.flags = activity.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE

        val webViewClient = activity.createWebViewClient()
        val mockHandler = mock<SslErrorHandler>()
        val mockError = mock<SslError>()
        val webView = mock<WebView>()

        webViewClient.onReceivedSslError(webView, mockHandler, mockError)

        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertNotNull("Dialog should be shown to inform of the error", dialog)

        val shadowDialog = org.robolectric.Shadows.shadowOf(dialog)
        assertEquals("SSL Certificate Error", shadowDialog.title)

        verify(mockHandler).cancel()
        verify(mockHandler, never()).proceed()
    }

    @Test
    fun testOnReceivedSslError_ReleaseBuild_Blocked() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.applicationInfo.flags = activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()

        val webViewClient = activity.createWebViewClient()
        val mockHandler = mock<SslErrorHandler>()
        val mockError = mock<SslError>()
        val webView = mock<WebView>()

        ShadowDialog.reset()

        webViewClient.onReceivedSslError(webView, mockHandler, mockError)

        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertNotNull("Dialog should be shown to inform of the error", dialog)

        verify(mockHandler).cancel()
        verify(mockHandler, never()).proceed()
    }
}
