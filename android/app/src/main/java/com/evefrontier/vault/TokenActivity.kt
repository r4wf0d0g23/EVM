package com.evefrontier.vault

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.NoClientAuthentication

class TokenActivity : AppCompatActivity() {

    private lateinit var authService: AuthorizationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authService = AuthorizationService(this)

        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        when {
            response != null -> {
                Log.i("TokenActivity", "Auth response received, exchanging code for token")
                exchangeToken(response)
            }
            ex != null -> {
                Log.e("TokenActivity", "Auth error: ${ex.errorDescription ?: ex.message}")
                returnToMain(false, null, ex.errorDescription ?: "Auth cancelled")
            }
            else -> {
                Log.e("TokenActivity", "No response or error in intent")
                returnToMain(false, null, "No auth response received")
            }
        }
    }

    private fun exchangeToken(response: AuthorizationResponse) {
        val tokenRequest = response.createTokenExchangeRequest()

        authService.performTokenRequest(tokenRequest, NoClientAuthentication.INSTANCE) { tokenResponse, ex ->
            if (tokenResponse != null) {
                val idToken = tokenResponse.idToken
                Log.i("TokenActivity", "Token exchange success, idToken present: ${idToken != null}")
                returnToMain(true, idToken, null)
            } else {
                val err = ex?.errorDescription ?: ex?.message ?: "Token exchange failed"
                Log.e("TokenActivity", "Token exchange failed: $err")
                returnToMain(false, null, err)
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
        authService.dispose()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::authService.isInitialized) authService.dispose()
    }
}
