package com.tdc.aichat

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope

class MainActivity : AppCompatActivity() {
    lateinit var webView: WebView
    private lateinit var bridge: JsBridge
    private lateinit var configManager: ConfigManager
    private lateinit var convManager: ConversationManager

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { bridge.onImagePicked(it) } }

    fun pickImageForWeb() {
        pickImageLauncher.launch("image/*")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configManager = ConfigManager(this)
        convManager = ConversationManager(this)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        setContentView(webView)

        bridge = JsBridge(this, configManager, convManager, lifecycleScope)
        webView.addJavascriptInterface(bridge, "native")

        webView.loadUrl("file:///android_asset/chat.html")
    }

    override fun onResume() {
        super.onResume()
        // Notify WebView to refresh config status
        webView.evaluateJavascript("checkConfig()", null)
    }

    // Called from JsBridge to open Settings
    fun openSettingsActivity() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}
