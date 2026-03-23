package com.evefrontier.vault

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// No longer used — token exchange is handled in LoginActivity after
// intercepting the chromiumapp.org redirect in our WebView.
// Kept as a stub to avoid manifest/intent-filter breakage.
class TokenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
