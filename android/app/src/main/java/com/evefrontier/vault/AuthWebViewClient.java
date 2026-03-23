package com.evefrontier.vault;

import android.content.Intent;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;

/**
 * Extends BridgeWebViewClient to intercept OAuth authorize navigations at the native layer.
 *
 * KEY INSIGHT: Capacitor serves localhost assets via shouldInterceptRequest() — NOT
 * shouldOverrideUrlLoading(). So overriding shouldOverrideUrlLoading here is safe and
 * does not break asset serving.
 *
 * This fires for ALL WebView navigations regardless of JS timing or Location object
 * overridability — it's the definitive interception point.
 */
public class AuthWebViewClient extends BridgeWebViewClient {

    public AuthWebViewClient(Bridge bridge) {
        super(bridge);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();

        // Intercept logout and strip the invalid post_logout_redirect_uri
        if (url.contains("oauth2/logout")) {
            Uri uri = Uri.parse(url);
            Uri.Builder fixed = uri.buildUpon().clearQuery();
            for (String param : uri.getQueryParameterNames()) {
                if (!param.equals("post_logout_redirect_uri")) {
                    fixed.appendQueryParameter(param, uri.getQueryParameter(param));
                }
            }
            String fixedUrl = fixed.build().toString();
            android.util.Log.i("AuthWebViewClient", "[EVM] Logout — stripped post_logout_redirect_uri");
            // Load the logout URL without the invalid redirect
            view.loadUrl(fixedUrl);
            return true;
        }

        if (url.contains("oauth2/authorize") && url.contains("redirect_uri=")) {
            // Rewrite redirect_uri: localhost/callback → chromiumapp.org
            String encodedLocal  = Uri.encode(MainActivity.LOCAL_CALLBACK);
            String encodedChrome = Uri.encode(MainActivity.CHROME_REDIRECT);
            String fixed = url.replace(encodedLocal, encodedChrome);

            android.util.Log.i("AuthWebViewClient", "[EVM] Intercepted auth URL, launching LoginActivity");
            view.stopLoading();

            Intent authIntent = new Intent(view.getContext(), LoginActivity.class);
            authIntent.putExtra("auth_url", fixed);
            view.getContext().startActivity(authIntent);
            return true;
        }

        // After logout CCP may redirect to evefrontier.com — send back to localhost
        if (url.contains("evefrontier.com") && !url.contains("oauth2/")) {
            android.util.Log.i("AuthWebViewClient", "[EVM] Post-logout redirect, returning to localhost");
            view.loadUrl("https://localhost");
            return true;
        }

        // Handle chromiumapp.org callback — forward to localhost/callback
        if (url.startsWith(MainActivity.CHROME_REDIRECT)) {
            android.util.Log.i("AuthWebViewClient", "[EVM] Caught chromiumapp.org callback");
            Uri uri = Uri.parse(url);
            Uri.Builder builder = Uri.parse(MainActivity.LOCAL_CALLBACK).buildUpon();
            for (String param : uri.getQueryParameterNames()) {
                builder.appendQueryParameter(param, uri.getQueryParameter(param));
            }
            String callbackUrl = builder.build().toString();
            android.util.Log.i("AuthWebViewClient", "[EVM] Loading callback: " + callbackUrl);
            view.loadUrl(callbackUrl);
            return true;
        }

        return super.shouldOverrideUrlLoading(view, request);
    }
}
