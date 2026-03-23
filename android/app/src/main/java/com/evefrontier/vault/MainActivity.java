package com.evefrontier.vault;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.WebViewListener;

public class MainActivity extends BridgeActivity {

    private static final String OIDC_METADATA =
        "{\"issuer\":\"https://auth.evefrontier.com\","
        + "\"authorization_endpoint\":\"https://auth.evefrontier.com/oauth2/authorize\","
        + "\"token_endpoint\":\"https://auth.evefrontier.com/oauth2/token\","
        + "\"userinfo_endpoint\":\"https://auth.evefrontier.com/oauth2/userinfo\","
        + "\"jwks_uri\":\"https://auth.evefrontier.com/.well-known/jwks.json\","
        + "\"response_types_supported\":[\"code\"],"
        + "\"subject_types_supported\":[\"public\"],"
        + "\"id_token_signing_alg_values_supported\":[\"RS256\"]}";

    private static final String AUTH_INTERCEPTOR_JS =
        "(function() {"
        + "  if (window.__authPatched) return; window.__authPatched = true;"
        + "  console.log('[EVM] Auth interceptor installed');"
        + "  var MOCK_META = " + OIDC_METADATA + ";"
        + "  var _fetch = window.fetch;"
        + "  window.fetch = function(resource, options) {"
        + "    var url = typeof resource === 'string' ? resource : (resource && resource.url ? resource.url : '');"
        + "    if (url.indexOf('auth.evefrontier.com') !== -1 && url.indexOf('.well-known/openid-configuration') !== -1) {"
        + "      console.log('[EVM] Returning mock OIDC metadata');"
        + "      return Promise.resolve(new Response(JSON.stringify(MOCK_META), {"
        + "        status: 200, headers: {'Content-Type': 'application/json'}"
        + "      }));"
        + "    }"
        + "    return _fetch.apply(this, arguments);"
        + "  };"
        // Only trigger native login on explicit button click — not on background fetches
        + "  document.addEventListener('click', function(e) {"
        + "    var el = e.target;"
        + "    for (var i = 0; i < 8 && el; i++, el = el.parentElement) {"
        + "      if (el.tagName === 'BUTTON' || el.getAttribute('role') === 'button') {"
        + "        var t = (el.textContent || '').trim().toLowerCase();"
        + "        if (t === 'login' || t === 'sign in' || t === 'connect') {"
        + "          console.log('[EVM] Login button clicked, launching native auth');"
        + "          e.stopImmediatePropagation(); e.preventDefault();"
        + "          if (window.NativeAuth) window.NativeAuth.requestLogin();"
        + "          return false;"
        + "        }"
        + "      }"
        + "    }"
        + "  }, true);"
        + "})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register JS interface immediately after bridge init
        getBridge().getWebView().addJavascriptInterface(new NativeAuthBridge(), "NativeAuth");

        // Register WebViewListener via Bridge (correct API for Capacitor 8)
        getBridge().addWebViewListener(new WebViewListener() {
            @Override
            public void onPageStarted(WebView webView) {
                // Inject before any app JS runs — no more race condition
                webView.evaluateJavascript(AUTH_INTERCEPTOR_JS, null);
            }

            @Override
            public void onPageLoaded(WebView webView) {
                // Re-apply on each page load (SPA navigations reset window.__authPatched)
                webView.evaluateJavascript("window.__authPatched = false;", null);
                webView.evaluateJavascript(AUTH_INTERCEPTOR_JS, null);
            }
        });

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
            js = "window.postMessage({__from:'Eve Vault',type:'auth_success',token:{id_token:'" + esc + "'}}, '*');";
        } else {
            String e2 = (err != null ? err : "Auth failed").replace("'", "\\'");
            js = "window.postMessage({__from:'Eve Vault',type:'auth_error',error:'" + e2 + "'}, '*');";
        }

        final String finalJs = js;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                getBridge().getWebView().evaluateJavascript(finalJs, null);
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try { getBridge().getWebView().evaluateJavascript(finalJs, null); }
                    catch (Exception ignored) {}
                }, 1000);
            }
        }, 800);
    }

    private volatile boolean loginInProgress = false;

    @Override
    public void onResume() {
        super.onResume();
        // Reset login flag when returning from browser/LoginActivity
        loginInProgress = false;
    }

    class NativeAuthBridge {
        @JavascriptInterface
        public void requestLogin() {
            if (loginInProgress) return;
            loginInProgress = true;
            runOnUiThread(() ->
                startActivity(new Intent(MainActivity.this, LoginActivity.class))
            );
        }
    }
}
