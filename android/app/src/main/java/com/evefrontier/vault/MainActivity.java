package com.evefrontier.vault;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    // OIDC discovery mock — authorization_endpoint points to our custom scheme
    // When the JS OIDC client tries to redirect to evefrontier://authorize, our shouldOverrideUrlLoading catches it
    private static final String OIDC_METADATA =
        "{\"issuer\":\"https://auth.evefrontier.com\","
        + "\"authorization_endpoint\":\"evefrontier://authorize\","
        + "\"token_endpoint\":\"https://auth.evefrontier.com/oauth2/token\","
        + "\"userinfo_endpoint\":\"https://auth.evefrontier.com/oauth2/userinfo\","
        + "\"jwks_uri\":\"https://auth.evefrontier.com/.well-known/jwks.json\","
        + "\"response_types_supported\":[\"code\"],"
        + "\"subject_types_supported\":[\"public\"],"
        + "\"id_token_signing_alg_values_supported\":[\"RS256\"]}";

    // Injected after page load — intercepts OIDC discovery fetch and button clicks
    private static final String AUTH_INTERCEPTOR_JS = "(function() {"
        + "  if (window.__authPatched) return; window.__authPatched = true;"
        + "  var MOCK_META = '" + OIDC_METADATA.replace("'", "\\'") + "';"
        + "  var _f = window.fetch;"
        + "  window.fetch = function(u, o) {"
        + "    var url = typeof u === 'string' ? u : '';"
        + "    if (url.indexOf('auth.evefrontier.com') !== -1) {"
        + "      if (url.indexOf('openid-configuration') !== -1) {"
        + "        return Promise.resolve(new Response(MOCK_META, {status:200, headers:{'Content-Type':'application/json'}}));"
        + "      }"
        + "      window.NativeAuth && window.NativeAuth.requestLogin();"
        + "      return Promise.resolve(new Response('{}', {status:200, headers:{'Content-Type':'application/json'}}));"
        + "    }"
        + "    return _f.apply(this, arguments);"
        + "  };"
        + "  document.addEventListener('click', function(e) {"
        + "    var el = e.target;"
        + "    for (var i=0; i<6 && el; i++, el=el.parentElement) {"
        + "      if (el.tagName==='BUTTON') {"
        + "        var t=(el.textContent||'').trim().toLowerCase();"
        + "        if (t==='login'||t==='sign in') {"
        + "          e.stopImmediatePropagation(); e.preventDefault();"
        + "          window.NativeAuth && window.NativeAuth.requestLogin();"
        + "          return false;"
        + "        }"
        + "      }"
        + "    }"
        + "  }, true);"
        + "})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inject bridge and interceptor after Capacitor initializes
        new Handler(Looper.getMainLooper()).postDelayed(this::setupBridge, 500);
        handleAuthResult(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAuthResult(intent);
    }

    private void setupBridge() {
        try {
            WebView webView = getBridge().getWebView();
            webView.removeJavascriptInterface("NativeAuth");
            webView.addJavascriptInterface(new NativeAuthBridge(), "NativeAuth");
            webView.evaluateJavascript(AUTH_INTERCEPTOR_JS, null);
        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).postDelayed(this::setupBridge, 1000);
        }
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
        final String fjs = js;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { getBridge().getWebView().evaluateJavascript(fjs, null); } catch (Exception ignored) {}
        }, 1200);
    }

    class NativeAuthBridge {
        @JavascriptInterface
        public void requestLogin() {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
        }
    }
}
