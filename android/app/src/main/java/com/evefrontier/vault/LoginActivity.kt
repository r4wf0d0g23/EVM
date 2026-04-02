package com.evefrontier.vault

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent

class LoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CALLBACK_URL = "callback_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val authUrl = intent.getStringExtra("auth_url") ?: run {
            Log.e("LoginActivity", "No auth_url provided")
            finish()
            return
        }

        Log.i("LoginActivity", "Launching Chrome Custom Tab for auth: $authUrl")
        CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(authUrl))
    }

    override fun onResume() {
        super.onResume()
        // Finish so that if the user backs out of the Custom Tab, this activity closes.
        // The Custom Tab was already launched in onCreate; this is a trampoline activity.
        finish()
    }
}
