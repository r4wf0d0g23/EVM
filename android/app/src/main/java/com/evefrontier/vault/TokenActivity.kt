package com.evefrontier.vault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * Handles the evefrontier://callback deep link after CCP redirects back from OAuth.
 *
 * Flow:
 * 1. Chrome Custom Tab (launched by LoginActivity) completes OAuth and CCP redirects to
 *    evefrontier://callback?code=...&state=...
 * 2. Android routes the deep link here via the intent-filter in AndroidManifest.
 * 3. We extract the query params, rebuild as https://localhost/callback, and
 *    forward to MainActivity so the Capacitor WebView can complete the OIDC exchange.
 */
class TokenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data: Uri? = intent?.data
        if (data == null) {
            Log.e("TokenActivity", "No deep link data in intent")
            finish()
            return
        }

        Log.i("TokenActivity", "Deep link received: $data")

        // Rebuild params onto localhost/callback so EVE Vault's OIDC client can consume them
        val builder = Uri.parse("https://localhost/callback").buildUpon()
        for (param in data.queryParameterNames) {
            builder.appendQueryParameter(param, data.getQueryParameter(param))
        }
        val callbackUrl = builder.build().toString()
        Log.i("TokenActivity", "Forwarding callback URL to MainActivity: $callbackUrl")

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("callback_url", callbackUrl)
        }
        startActivity(mainIntent)
        finish()
    }
}
