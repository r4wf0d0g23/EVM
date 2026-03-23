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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.concurrent.thread

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val AUTH_ENDPOINT = "https://auth.evefrontier.com/oauth2/authorize"
        private const val TOKEN_ENDPOINT = "https://auth.evefrontier.com/oauth2/token"
        private const val CLIENT_ID = "583ebc6d-abd8-4057-8c77-78405628e42d"
        // Use the redirect URI already registered for the Chrome extension
        private const val REDIRECT_URI = "https://lbmfdkobfnkfobfahpekbaaombpnafah.chromiumapp.org/"
    }

    private lateinit var webView: WebView
    private lateinit var codeVerifier: String

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Build PKCE code verifier + challenge
        codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val state = generateRandom()
        val nonce = generateRandom()

        val authUrl = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", "openid profile email offline_access")
            .appendQueryParameter("state", state)
            .appendQueryParameter("nonce", nonce)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()

        Log.i("LoginActivity", "Loading auth URL in WebView: $AUTH_ENDPOINT")

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (url.startsWith(REDIRECT_URI)) {
                        Log.i("LoginActivity", "Intercepted redirect: $url")
                        handleRedirect(Uri.parse(url))
                        return true
                    }
                    return false
                }
            }
            loadUrl(authUrl)
        }

        setContentView(webView)
    }

    private fun handleRedirect(uri: Uri) {
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")

        if (error != null) {
            Log.e("LoginActivity", "Auth error from server: $error - ${uri.getQueryParameter("error_description")}")
            returnToMain(false, null, error)
            return
        }

        if (code == null) {
            Log.e("LoginActivity", "No code in redirect")
            returnToMain(false, null, "No authorization code received")
            return
        }

        Log.i("LoginActivity", "Got auth code, exchanging for token...")
        exchangeToken(code)
    }

    private fun exchangeToken(code: String) {
        thread {
            try {
                val client = OkHttpClient()
                val body = FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", REDIRECT_URI)
                    .add("client_id", CLIENT_ID)
                    .add("code_verifier", codeVerifier)
                    .build()

                val request = Request.Builder()
                    .url(TOKEN_ENDPOINT)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody == null) {
                    Log.e("LoginActivity", "Token exchange failed: ${response.code} $responseBody")
                    returnToMain(false, null, "Token exchange failed: ${response.code}")
                    return@thread
                }

                val json = JSONObject(responseBody)
                val idToken = json.optString("id_token")
                Log.i("LoginActivity", "Token exchange success, idToken present: ${idToken.isNotEmpty()}")
                returnToMain(true, idToken.ifEmpty { null }, null)

            } catch (e: Exception) {
                Log.e("LoginActivity", "Token exchange exception: ${e.message}")
                returnToMain(false, null, e.message ?: "Token exchange failed")
            }
        }
    }

    private fun returnToMain(success: Boolean, idToken: String?, error: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("auth_success", success)
            if (idToken != null) putExtra("id_token", idToken)
            if (error != null) putExtra("auth_error", error)
        }
        startActivity(intent)
        finish()
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun generateRandom(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
