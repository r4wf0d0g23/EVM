package com.evefrontier.vault

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import io.fusionauth.mobilesdk.AuthorizationManager
import io.fusionauth.mobilesdk.oauth.OAuthAuthorizeOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // AuthorizationManager is already initialized in EVMApplication,
        // but re-initialize here as a safety net in case this activity
        // is ever started in isolation.
        // (SDK treats this as a no-op if already initialized with same config.)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                AuthorizationManager.oAuth(this@LoginActivity)
                    .authorize(
                        Intent(this@LoginActivity, TokenActivity::class.java),
                        OAuthAuthorizeOptions(
                            redirectUri = "evefrontier://callback",
                            cancelIntent = Intent(this@LoginActivity, MainActivity::class.java)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                    )
            } catch (e: Exception) {
                Log.e("LoginActivity", "Authorize failed: ${e.message}")
                finish()
            }
        }
    }
}
