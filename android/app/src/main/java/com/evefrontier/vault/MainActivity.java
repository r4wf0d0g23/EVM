package com.evefrontier.vault;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.WebViewListener;

public class MainActivity extends BridgeActivity {

    // Redirect URI registered with CCP (Chrome extension)
    static final String CHROME_REDIRECT = "https://lbmfdkobfnkfobfahpekbaaombpnafah.chromiumapp.org/";
    // Redirect URI EVE Vault web app uses
    private static final String LOCAL_CALLBACK = "https://localhost/callback";

    // Match test.auth.evefrontier.com (Utopia) — same server EVE Vault web app is built against
    private static final String OIDC_METADATA =
        "{\"issuer\":\"https://test.auth.evefrontier.com\","
        + "\"authorization_endpoint\":\"https://test.auth.evefrontier.com/oauth2/authorize\","
        + "\"token_endpoint\":\"https://test.auth.evefrontier.com/oauth2/token\","
        + "\"userinfo_endpoint\":\"https://test.auth.evefrontier.com/oauth2/userinfo\","
        + "\"jwks_uri\":\"https://test.auth.evefrontier.com/.well-known/jwks.json\","
        + "\"response_types_supported\":[\"code\"],"
        + "\"subject_types_supported\":[\"public\"],"
        + "\"id_token_signing_alg_values_supported\":[\"RS256\"]}";

    private static final String AUTH_INTERCEPTOR_JS =
        "(function() {"
        + "  if (location.hostname !== 'localhost') return;"
        + "  if (window.__authPatched) return; window.__authPatched = true;"
        + "  console.log('[EVM] Auth interceptor installed');"
        + "  var MOCK_META = " + OIDC_METADATA + ";"
        + "  var CHROME_RDR = 'https://lbmfdkobfnkfobfahpekbaaombpnafah.chromiumapp.org/';"
        + "  var LOCAL_CB   = 'https://localhost/callback';"
        + "  var _fetch = window.fetch;"
        + "  window.fetch = function(resource, options) {"
        + "    var url = typeof resource === 'string' ? resource"
        + "              : (resource && resource.url ? resource.url : '');"
        // Mock OIDC discovery (CCP returns text/plain, we need application/json)
        + "    if ((url.indexOf('auth.evefrontier.com') !== -1 || url.indexOf('test.auth.evefrontier.com') !== -1)"
        + "        && url.indexOf('.well-known/openid-configuration') !== -1) {"
        + "      console.log('[EVM] Returning mock OIDC metadata');"
        + "      return Promise.resolve(new Response(JSON.stringify(MOCK_META), {"
        + "        status: 200, headers: {'Content-Type': 'application/json'}"
        + "      }));"
        + "    }"
        // Fix token exchange: redirect_uri in POST body must match what was used in auth
        // (we swapped localhost→chromiumapp.org in the auth request, so swap here too)
        + "    if (url.indexOf('auth.evefrontier.com/oauth2/token') !== -1"
        + "        && options && options.body) {"
        + "      var body = options.body;"
        + "      var bodyStr = (typeof body === 'string') ? body : '';"
        + "      if (!bodyStr && body && typeof body.toString === 'function') bodyStr = body.toString();"
        + "      var fixed = bodyStr.replace("
        + "        encodeURIComponent(LOCAL_CB),"
        + "        encodeURIComponent(CHROME_RDR)"
        + "      );"
        + "      if (fixed !== bodyStr) {"
        + "        console.log('[EVM] Fixed redirect_uri in token exchange');"
        + "        options = Object.assign({}, options, { body: fixed });"
        + "      }"
        + "    }"
        + "    return _fetch.call(this, resource, options);"
        + "  };"
        + "})();";

    private volatile boolean loginInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getBridge().addWebViewListener(new WebViewListener() {
            @Override
            public void onPageStarted(WebView webView) {
                String url = webView.getUrl();
                if (url == null) {
                    webView.evaluateJavascript(AUTH_INTERCEPTOR_JS, null);
                    return;
                }

                // When EVE Vault navigates to auth — intercept, rewrite redirect_uri,
                // open in LoginActivity WebView which can catch chromiumapp.org
                if ((url.contains("auth.evefrontier.com/oauth2/authorize") || url.contains("test.auth.evefrontier.com/oauth2/authorize"))
                        && url.contains("redirect_uri=")) {
                    String encodedLocal  = android.net.Uri.encode(LOCAL_CALLBACK);
                    String encodedChrome = android.net.Uri.encode(CHROME_REDIRECT);
                    String fixed = url.replace(encodedLocal, encodedChrome);
                    if (!fixed.equals(url) && !loginInProgress) {
                        loginInProgress = true;
                        android.util.Log.i("MainActivity", "[EVM] Launching LoginActivity with rewritten auth URL");
                        webView.stopLoading();
                        Intent authIntent = new Intent(MainActivity.this, LoginActivity.class);
                        authIntent.putExtra("auth_url", fixed);
                        startActivity(authIntent);
                    }
                } else {
                    webView.evaluateJavascript(AUTH_INTERCEPTOR_JS, null);
                }
            }

            @Override
            public void onPageLoaded(WebView webView) {
                webView.evaluateJavascript("window.__authPatched = false;", null);
                webView.evaluateJavascript(AUTH_INTERCEPTOR_JS, null);
            }
        });

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        loginInProgress = false;
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        // Handle callback URL forwarded from LoginActivity
        String callbackUrl = intent.getStringExtra(LoginActivity.EXTRA_CALLBACK_URL);
        if (callbackUrl != null) {
            android.util.Log.i("MainActivity", "[EVM] Loading callback URL: " + callbackUrl);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    getBridge().getWebView().loadUrl(callbackUrl);
                } catch (Exception ignored) {}
            }, 800);
        }
    }
}
