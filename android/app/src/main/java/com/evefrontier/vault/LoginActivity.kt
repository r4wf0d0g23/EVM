package com.evefrontier.vault

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val CHROME_REDIRECT = "https://lbmfdkobfnkfobfahpekbaaombpnafah.chromiumapp.org/"
        private const val LOCAL_CALLBACK  = "https://localhost/callback"
        const val EXTRA_CALLBACK_URL      = "callback_url"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val authUrl = intent.getStringExtra("auth_url") ?: run {
            Log.e("LoginActivity", "No auth_url provided")
            finish()
            return
        }

        Log.i("LoginActivity", "Loading auth in WebView: $authUrl")

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (url.startsWith(CHROME_REDIRECT)) {
                        Log.i("LoginActivity", "Intercepted chromiumapp.org redirect: $url")
                        // Build the localhost/callback URL with the same params
                        val callbackUrl = buildCallbackUrl(url)
                        Log.i("LoginActivity", "Forwarding to: $callbackUrl")
                        val result = Intent(this@LoginActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra(EXTRA_CALLBACK_URL, callbackUrl)
                        }
                        startActivity(result)
                        finish()
                        return true
                    }
                    return false
                }
            }
            loadUrl(authUrl)
        }

        setContentView(webView)
    }

    private fun buildCallbackUrl(chromiumUrl: String): String {
        val uri = Uri.parse(chromiumUrl)
        val builder = Uri.parse(LOCAL_CALLBACK).buildUpon()
        for (param in uri.queryParameterNames) {
            builder.appendQueryParameter(param, uri.getQueryParameter(param))
        }
        return builder.build().toString()
    }
}
