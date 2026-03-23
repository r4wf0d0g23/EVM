package com.evefrontier.vault

import android.app.Application
import io.fusionauth.mobilesdk.AuthorizationConfiguration
import io.fusionauth.mobilesdk.AuthorizationManager

class EVMApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize AuthorizationManager at app start so state survives
        // the browser handoff pause/resume cycle.
        AuthorizationManager.initialize(
            AuthorizationConfiguration.fromResources(this, R.raw.fusionauth_config)
        )
    }
}
