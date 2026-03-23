package com.evefrontier.vault

import android.app.Application

class EVMApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Auth is now handled directly via AppAuth (net.openid:appauth)
        // with hardcoded endpoints — no SDK init needed here.
    }
}
