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

    // Full OIDC metadata mock — authorization_endpoint is a custom scheme
    // so the JS OIDC client won't navigate there, it'll just try to
    // redirect the WebView — we catch that via NativeAuth.requestLogin()
    private static final String OIDC_METADATA =
        "{\"issuer\":\"https://auth.evefrontier.com\","
        + "\"authorization_endpoint\":\"https://auth.evefrontier.com/oauth2/authorize\","
        + "\"token_endpoint\":\"https://auth.evefrontier.com/oauth2/token\","
        + "\"userinfo_endpoint\":\"https://auth.evefrontier.com/oauth2/userinfo\","
        + "\"jwks_uri\":\"https://auth.evefrontier.com/.well-known/jwks.json\","
        + "\"response_types_supported\":[\"code\"],"
        + "\"subject_types_supported\":[\"public\"],"
        + "\"id_token_signing_alg_values_supported\":[\"RS256\"]}";

    // Injected at document-start (onPageStarted) so it runs before any app JS
    private static final String AUTH_INTERCEPTOR_JS =
        "(function() {"
        + "  if (window.__authPatched) return; window.__authPatched = true;"
        + "  console.log('[EVM] Auth interceptor installed');"
        + "  var MOCK_META = " + OIDC_METADATA + ";"
        + "  var _fetch = window.fetch;"
        + "  window.fetch = function(resource, options) {"
        + "    var url = typeof resource === 'string' ? resource : (resource && resource.url ? resource.url : '');"
        + "    if (url.indexOf('auth.evefrontier.com') !== -1) {"
        + "      console.log('[EVM] Intercepted fetch: ' + url);"
        + "      if (url.indexOf('.well-known/openid-configuration') !== -1) {"
        + "        console.log('[EVM] Returning mock OIDC metadata');"
        + "        return Promise.resolve(new Response(JSON.stringify(MOCK_META), {"
        + "          status: 200,"
        + "          headers: {'Content-Type': 'application/json'}"
        + "        }));"
        + "      }"
        + "      // Any other auth.evefrontier.com fetch → launch native login"
        + "      console.log('[EVM] Triggering native login from fetch intercept');"
        + "      if (window.NativeAuth) window.NativeAuth.requestLogin();"
        + "      return new Promise(function() {}); // hang so app doesn't process response"
        + "    }"
        + "    return _fetch.apply(this, arguments);"
        + "  };"
        + "  // Also intercept XHR for any library that uses it"
        + "  var _open = XMLHttpRequest.prototype.open;"
        + "  XMLHttpRequest.prototype.open = function(method, url) {"
        + "    if (typeof url === 'string' && url.indexOf('auth.evefrontier.com') !== -1) {"
        + "      console.log('[EVM] Intercepted XHR: ' + url);"
        + "      if (window.NativeAuth) window.NativeAuth.requestLogin();"
        + "      // Redirect to a no-op to prevent real network call"
        + "      return _open.call(this, method, 'about:blank');"
        + "    }"
        + "    return _open.apply(this, arguments);"
        + "  };"
        + "  // Click interceptor for LOGIN button"
        + "  document.addEventListener('click', function(e) {"
        + "    var el = e.target;"
        + "    for (var i = 0; i < 8 && el; i++, el = el.parentElement) {"
        + "      if (el.tagName === 'BUTTON' || el.role === 'button') {"
        + "        var t = (el.textContent || '').trim().toLowerCase();"
        + "        if (t === 'login' || t === 'sign in' || t === 'connect') {"
        + "          console.log('[EVM] Login button clicked, launching native auth');"
        + "          e.stopImmediatePropagation();"
        + "          e.preventDefault();"
        + "          if (window.NativeAuth) window.NativeAuth.requestLogin();"
        + "          return false;"
        + "        }"
        + "      }"
        + "    }"
        + "  }, true);"
        + "})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Register WebView listener BEFORE super.onCreate() so it's in place when Bridge initializes
        addWebViewListener(new WebViewListener() {
            @Override
            public void onPageStarted(WebView webView, String url, android.graphics.Bitmap favicon) {
                // Inject auth interceptor at document-start, before any app JS runs
                webView.evaluateJavascript(AUTH_INTERCEPTOR_JS, null);
            }

            @Override
            public void onPageLoaded(WebView webView) {
                // Re-inject on each page load to survive SPA navigation
                webView.addJavascriptInterface(new NativeAuthBridge(), "NativeAuth");
                webView.evaluateJavascript(AUTH_INTERCEPTOR_JS, null);
            }
        });

        super.onCreate(savedInstanceState);
        handleAuthResult(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAuthResult(intent);
    }

    private void handleAuthResult(Intent intent) {
        if (intent == null || !intent.hasExtra("auth_success")) return;
        boolean ok = intent.getBooleanExtra("auth_success", false);
        String token = intent.getStringExtra("id_token");
        String err = intent.getStringExtra("auth_error");

        String js;
        if (ok && token != null && !token.isEmpty()) {
            String esc = token.replace("\\", "\\\\").replace("'", "\\'");
            js = "window.postMessage({"
               + "__from:'Eve Vault',"
               + "type:'auth_success',"
               + "token:{id_token:'" + esc + "'}"
               + "}, '*');";
        } else {
            String e2 = (err != null ? err : "Auth failed").replace("'", "\\'");
            js = "window.postMessage({"
               + "__from:'Eve Vault',"
               + "type:'auth_error',"
               + "error:'" + e2 + "'"
               + "}, '*');";
        }

        final String finalJs = js;
        // Wait for WebView to be ready, then inject token
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                getBridge().getWebView().evaluateJavascript(finalJs, null);
            } catch (Exception ignored) {
                // WebView not ready yet, try again shortly
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try { getBridge().getWebView().evaluateJavascript(finalJs, null); }
                    catch (Exception e2ignored) {}
                }, 1000);
            }
        }, 800);
    }

    // Registered in onPageLoaded — also set up early via addJavascriptInterface after bridge ready
    class NativeAuthBridge {
        @JavascriptInterface
        public void requestLogin() {
            runOnUiThread(() -> startActivity(
                new Intent(MainActivity.this, LoginActivity.class)
            ));
        }
    }
}
