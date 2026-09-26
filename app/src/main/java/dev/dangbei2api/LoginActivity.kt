package dev.dangbei2api

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 内置当贝登录页。用户在 WebView 登录 ai.dangbei.com 后点右上角「保存」，
 * 本页从 Cookie 提取 token 回传给主界面。
 *
 * 实测：登录凭证是 Cookie 里的 token=xxxx（不是 localStorage）。
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    companion object {
        const val EXTRA_TOKEN = "dangbei_token"
        const val LOGIN_URL = "https://ai.dangbei.com/chat"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#0E1116"))

        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = WebViewClient()

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        lp.topMargin = dp(56)
        root.addView(webView, lp)

        val bar = FrameLayout(this)
        bar.setBackgroundColor(Color.parseColor("#0E1116"))

        val title = TextView(this).apply {
            text = "登录当贝 AI"
            setTextColor(Color.parseColor("#E6EAF2"))
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
        }
        val tlp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        tlp.leftMargin = dp(16)
        bar.addView(title, tlp)

        val saveBtn = Button(this).apply {
            text = "保存"
            setTextColor(Color.parseColor("#0E1116"))
            setBackgroundColor(Color.parseColor("#8AAE5B"))
            setOnClickListener { extractToken() }
        }
        val blp = FrameLayout.LayoutParams(dp(80), dp(40))
        blp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        blp.rightMargin = dp(12)
        bar.addView(saveBtn, blp)

        root.addView(bar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
        ))

        setContentView(root)
        webView.loadUrl(LOGIN_URL)
    }

    private fun extractToken() {
        val all = CookieManager.getInstance().getCookie(LOGIN_URL) ?: ""
        val fromCookie = extractFromCookie(all)
        if (!fromCookie.isNullOrEmpty()) {
            finishWithToken(fromCookie)
            return
        }
        // 兜底：部分实现把 token 放在 localStorage 的 userInfo 里
        webView.evaluateJavascript(
            "(function(){try{" +
                "var m=document.cookie.match(/(?:^|;\\s*)token=([^;]+)/);" +
                "if(m)return m[1];" +
                "return '';}catch(e){return '';}})()"
        ) { value ->
            val v = value?.trim('"') ?: ""
            if (v.isNotEmpty()) {
                finishWithToken(v)
            } else {
                Toast.makeText(this, "未找到 token，请确认已在页面登录成功", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun extractFromCookie(cookie: String): String? {
        cookie.split(";").forEach { part ->
            val kv = part.trim().split("=")
            if (kv.size >= 2 && kv[0].trim() == "token") {
                return kv[1].trim()
            }
        }
        return null
    }

    private fun finishWithToken(token: String) {
        val data = Intent().putExtra(EXTRA_TOKEN, token)
        setResult(RESULT_OK, data)
        Toast.makeText(this, "已获取 token", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
