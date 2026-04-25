package com.hermescourier.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var settingsPanel: LinearLayout

    private val prefs by lazy { getSharedPreferences("hermes_courier", Context.MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { c ->
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // ── Build UI ──────────────────────────────────────────────────────
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0F0F14.toInt())
        }

        // Top bar: URL input + Go button
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(0xFF1A1A24.toInt())
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        urlBar = EditText(this).apply {
            hint = "http://your-server:8080"
            setTextColor(0xFFE0E0E0.toInt())
            setHintTextColor(0xFF808080.toInt())
            setBackgroundColor(0xFF2A2A3A.toInt())
            setPadding(16, 12, 16, 12)
            textSize = 14f
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = 8
            layoutParams = lp
        }

        val goButton = Button(this).apply {
            text = "Go"
            setOnClickListener { loadUrl() }
        }

        val settingsButton = Button(this).apply {
            text = "⚙"
            setOnClickListener { toggleSettings() }
        }

        topBar.addView(urlBar)
        topBar.addView(goButton)
        topBar.addView(settingsButton)
        root.addView(topBar)

        // Progress bar
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        root.addView(progressBar)

        // Settings panel (hidden by default)
        settingsPanel = buildSettingsPanel()
        settingsPanel.visibility = View.GONE
        root.addView(settingsPanel)

        // WebView
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progressBar.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar.visibility = View.GONE
                    // Update URL bar with actual URL
                    url?.let { urlBar.setText(it) }
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    // Allow self-signed certs for local/Tailscale servers
                    handler?.proceed()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)

        // URL bar enter key
        urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadUrl()
                true
            } else false
        }

        // Back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
                else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Load saved URL or show settings
        val savedUrl = prefs.getString("server_url", "")
        if (savedUrl.isNullOrBlank()) {
            settingsPanel.visibility = View.VISIBLE
        } else {
            urlBar.setText(savedUrl)
            webView.loadUrl(savedUrl)
        }
    }

    private fun loadUrl() {
        var url = urlBar.text.toString().trim()
        if (url.isBlank()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        prefs.edit().putString("server_url", url).apply()
        webView.loadUrl(url)
        settingsPanel.visibility = View.GONE
    }

    private fun toggleSettings() {
        settingsPanel.visibility = if (settingsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun buildSettingsPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xFF1A1A24.toInt())
        }

        panel.addView(TextView(this).apply {
            text = "Server Settings"
            setTextColor(0xFFE0E0E0.toInt())
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        panel.addView(TextView(this).apply {
            text = "\nEnter the URL of your hermes-webui server.\nExamples:\n  • http://192.168.1.100:8080\n  • https://your-server.tail12345.ts.net\n  • http://100.78.13.86:8080 (Tailscale)"
            setTextColor(0xFFA0A0B0.toInt())
            textSize = 12f
            setPadding(0, 8, 0, 16)
        })

        val saveButton = Button(this).apply {
            text = "Connect"
            setOnClickListener { loadUrl() }
        }
        panel.addView(saveButton)

        return panel
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
