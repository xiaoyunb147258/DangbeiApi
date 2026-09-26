package dev.dangbei2api

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dev.dangbei2api.store.SettingsStore
import dev.dangbei2api.store.TokenStore
import dev.dangbei2api.util.Logger
import dev.dangbei2api.util.NetUtil
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var settings: SettingsStore
    private lateinit var tokenStore: TokenStore

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val loginLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val token = result.data?.getStringExtra(LoginActivity.EXTRA_TOKEN).orEmpty()
                if (token.isNotEmpty()) {
                    tokenStore.token = token
                    tokenStore.savedAt = System.currentTimeMillis()
                    Logger.log("token 已保存（${token.take(8)}…）")
                    webView.post { webView.evaluateJavascript("window.onTokenSaved && window.onTokenSaved()", null) }
                }
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        tokenStore = TokenStore.get(this)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        webView.webViewClient = WebViewClient()

        webView.addJavascriptInterface(Bridge(), "DangbeiNative")
        webView.loadUrl("file:///android_asset/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        requestNotificationPermission()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {
            // 悬浮窗权限非必需，静默跳过（不强制跳设置）
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startService(action: String) {
        try {
            val svc = Intent(this, GatewayService::class.java).apply { this.action = action }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc)
            } else {
                startService(svc)
            }
        } catch (e: Exception) {
            Logger.log("服务操作失败：${e.message}")
        }
    }

    /**
     * 暴露给前端的 JS 桥。方法在 JS 里通过 window.DangbeiNative.xxx() 调用。
     */
    inner class Bridge {

        @JavascriptInterface
        fun startGateway() = startService(GatewayService.ACTION_START)

        @JavascriptInterface
        fun stopGateway() = startService(GatewayService.ACTION_STOP)

        @JavascriptInterface
        fun restartGateway() = startService(GatewayService.ACTION_RESTART)

        @JavascriptInterface
        fun openLogin() {
            runOnUiThread {
                loginLauncher.launch(Intent(this@MainActivity, LoginActivity::class.java))
            }
        }

        @JavascriptInterface
        fun getStatus(): String {
            val running = GatewayService.running
            val port = if (running) GatewayService.currentPort else settings.port
            val obj = JSONObject().apply {
                put("running", running)
                put("port", port)
                put("ip", NetUtil.getLocalIp())
                put("hasToken", tokenStore.hasToken())
                put("tokenPreview", if (tokenStore.hasToken()) tokenStore.token.take(8) + "…" + tokenStore.token.takeLast(4) else "")
                put("requestCount", dev.dangbei2api.http.GatewayServer.requestCount())
                put("baseUrl", "http://${NetUtil.getLocalIp()}:$port/v1")
            }
            return obj.toString()
        }

        @JavascriptInterface
        fun getSettings(): String {
            val obj = JSONObject().apply {
                put("port", settings.port)
                put("apiKey", settings.apiKey)
                put("autoStart", settings.autoStart)
                put("useSearch", settings.useSearch)
                put("defaultModel", settings.defaultModel)
            }
            return obj.toString()
        }

        @JavascriptInterface
        fun saveSettings(json: String) {
            try {
                val j = JSONObject(json)
                if (j.has("port")) settings.port = j.optInt("port", 9980)
                if (j.has("apiKey")) settings.apiKey = j.optString("apiKey", "")
                if (j.has("autoStart")) settings.autoStart = j.optBoolean("autoStart", true)
                if (j.has("useSearch")) settings.useSearch = j.optBoolean("useSearch", false)
                if (j.has("defaultModel")) settings.defaultModel = j.optString("defaultModel", "glm-5")
                Logger.log("设置已保存")
            } catch (e: Exception) {
                Logger.log("设置保存失败：${e.message}")
            }
        }

        @JavascriptInterface
        fun getModels(): String {
            val arr = JSONArray()
            for (m in dev.dangbei2api.model.Models.ALL) {
                arr.put(JSONObject().apply {
                    put("id", m.id)
                    put("label", m.label)
                    put("think", m.supportThink)
                })
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun getLogs(): String {
            val arr = JSONArray()
            Logger.recent().forEach { arr.put(it) }
            return arr.toString()
        }

        @JavascriptInterface
        fun clearLogs() = Logger.clear()

        @JavascriptInterface
        fun clearToken() {
            tokenStore.clear()
            Logger.log("token 已清除")
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
