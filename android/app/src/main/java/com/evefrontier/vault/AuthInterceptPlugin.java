package com.evefrontier.vault;

import android.content.Intent;
import android.net.Uri;
import com.getcapacitor.Plugin;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Capacitor plugin that intercepts OAuth authorize navigations at the native layer.
 * shouldOverrideLoad() is called by BridgeWebViewClient.shouldOverrideUrlLoading()
 * BEFORE the WebView loads the URL — guaranteed to catch window.open() and all
 * other navigation methods regardless of JS timing.
 */
@CapacitorPlugin(name = "AuthIntercept")
public class AuthInterceptPlugin extends Plugin {

    @Override
    public Boolean shouldOverrideLoad(Uri url) {
        String urlStr = url.toString();

        // Intercept any navigation to an OAuth authorize endpoint
        if (urlStr.contains("oauth2/authorize") && urlStr.contains("redirect_uri=")) {
            // Rewrite redirect_uri: localhost/callback → chromiumapp.org
            String encodedLocal  = Uri.encode(MainActivity.LOCAL_CALLBACK);
            String encodedChrome = Uri.encode(MainActivity.CHROME_REDIRECT);
            String fixed = urlStr.replace(encodedLocal, encodedChrome);

            android.util.Log.i("AuthInterceptPlugin", "[EVM] Intercepted auth URL, launching LoginActivity");

            Intent authIntent = new Intent(getActivity(), LoginActivity.class);
            authIntent.putExtra("auth_url", fixed);
            getActivity().startActivity(authIntent);

            // Return true = we handled it, don't load in WebView
            return true;
        }

        // Let everything else through
        return null;
    }
}
