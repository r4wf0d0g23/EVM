package com.evefrontier.vault

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

class LoginActivity : AppCompatActivity() {

    private lateinit var authService: AuthorizationService

    companion object {
        // Hardcoded endpoints — bypasses OIDC discovery entirely
        // CCP's .well-known/openid-configuration returns text/plain which breaks discovery
        private val AUTH_ENDPOINT = Uri.parse("https://auth.evefrontier.com/oauth2/authorize")
        private val TOKEN_ENDPOINT = Uri.parse("https://auth.evefrontier.com/oauth2/token")

        // Client ID from fusionauth_config.json
        private const val CLIENT_ID = "583ebc6d-abd8-4057-8c77-78405628e42d"
        private const val REDIRECT_URI = "evefrontier://callback"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authService = AuthorizationService(this)

        val serviceConfig = AuthorizationServiceConfiguration(
            AUTH_ENDPOINT,
            TOKEN_ENDPOINT
        )

        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI)
        )
            .setScope("openid profile email")
            .build()

        Log.i("LoginActivity", "Starting AppAuth authorization (explicit endpoints, no discovery)")

        val completionIntent = Intent(this, TokenActivity::class.java)
        val cancelIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        authService.performAuthorizationRequest(
            authRequest,
            PendingIntent.getActivity(this, 0, completionIntent, PendingIntent.FLAG_MUTABLE),
            PendingIntent.getActivity(this, 0, cancelIntent, PendingIntent.FLAG_MUTABLE)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        authService.dispose()
    }
}
