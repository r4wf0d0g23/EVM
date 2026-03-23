package com.evefrontier.vault

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import io.fusionauth.mobilesdk.AuthorizationConfiguration
import io.fusionauth.mobilesdk.AuthorizationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TokenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Re-initialize here to ensure state is available even if the process
        // was killed during the browser handoff. This is the fix for:
        //   W AppAuth: No stored state - unable to handle response
        AuthorizationManager.initialize(
            AuthorizationConfiguration.fromResources(this, R.raw.fusionauth_config)
        )

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val state = AuthorizationManager.oAuth(this@TokenActivity)
                    .handleRedirect(intent)
                val idToken = state.idToken
                Log.i("TokenActivity", "Auth success, idToken present: ${idToken != null}")

                val returnIntent = Intent(this@TokenActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("id_token", idToken ?: "")
                    putExtra("auth_success", true)
                }
                startActivity(returnIntent)
            } catch (e: Exception) {
                Log.e("TokenActivity", "Auth failed: ${e.message}")
                val failIntent = Intent(this@TokenActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("auth_success", false)
                    putExtra("auth_error", e.message ?: "Unknown error")
                }
                startActivity(failIntent)
            } finally {
                finish()
            }
        }
    }
}
