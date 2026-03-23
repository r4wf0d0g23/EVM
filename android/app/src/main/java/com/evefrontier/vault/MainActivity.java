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

    // Redirect URI registered with CCP (Chrome extension) — accepted by test.auth.evefrontier.com
    static final String CHROME_REDIRECT = "https://lbmfdkobfnkfobfahpekbaaombpnafah.chromiumapp.org/";
    // Redirect URI EVE Vault web app uses internally
    private static final String LOCAL_CALLBACK = "https://localhost/callback";

    // OIDC metadata for test.auth.evefrontier.com (Utopia)
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
        // ── OIDC discovery mock ──────────────────────────────────────────────
        + "  var _fetch = window.fetch;"
        + "  window.fetch = function(resource, options) {"
        + "    var url = typeof resource === 'string' ? resource"
        + "              : (resource && resource.url ? resource.url : '');"
        + "    if (url.indexOf('evefrontier.com') !== -1"
        + "        && url.indexOf('.well-known/openid-configuration') !== -1) {"
        + "      console.log('[EVM] Mock OIDC metadata');"
        + "      return Promise.resolve(new Response(JSON.stringify(MOCK_META),"
        + "        {status:200, headers:{'Content-Type':'application/json'}}));"
        + "    }"
        // Fix token exchange redirect_uri to match chromiumapp.org
        + "    if (url.indexOf('evefrontier.com/oauth2/token') !== -1 && options && options.body) {"
        + "      var body = typeof options.body === 'string' ? options.body : '';"
        + "      var fixed = body.replace(encodeURIComponent(LOCAL_CB), encodeURIComponent(CHROME_RDR));"
        + "      if (fixed !== body) {"
        + "        console.log('[EVM] Fixed redirect_uri in token exchange');"
        + "        options = Object.assign({}, options, {body: fixed});"
        + "      }"
        + "    }"
        + "    return _fetch.call(this, resource, options);"
        + "  };"
        // ── Intercept auth navigation BEFORE WebView loads it ────────────────
        // Patch location.assign
        + "  try {"
        + "    var _assign = window.location.assign.bind(window.location);"
        + "    window.location.assign = function(u) {"
        + "      if (String(u).indexOf('oauth2/authorize') !== -1) {"
        + "        console.log('[EVM] Caught location.assign to auth');"
        + "        if (window.NativeAuth) window.NativeAuth.startAuth(String(u)); return;"
        + "      } _assign(u);"
        + "    };"
        + "  } catch(e) {}"
        // Patch location.replace
        + "  try {"
        + "    var _replace = window.location.replace.bind(window.location);"
        + "    window.location.replace = function(u) {"
        + "      if (String(u).indexOf('oauth2/authorize') !== -1) {"
        + "        console.log('[EVM] Caught location.replace to auth');"
        + "        if (window.NativeAuth) window.NativeAuth.startAuth(String(u)); return;"
        + "      } _replace(u);"
        + "    };"
        + "  } catch(e) {}"
        // Patch Location.prototype.href setter
        + "  try {"
        + "    var locProto = Object.getPrototypeOf(window.location);"
        + "    var hrefDesc = Object.getOwnPropertyDescriptor(locProto, 'href');"
        + "    if (hrefDesc && hrefDesc.set) {"
        + "      var _origSet = hrefDesc.set;"
        + "      Object.defineProperty(locProto, 'href', {"
        + "        get: hrefDesc.get,"
        + "        set: function(v) {"
        + "          if (String(v).indexOf('oauth2/authorize') !== -1) {"
        + "            console.log('[EVM] Caught location.href = auth');"
        + "            if (window.NativeAuth) window.NativeAuth.startAuth(String(v)); return;"
        + "          }"
        + "          _origSet.call(window.location, v);"
        + "        },"
        + "        configurable: true, enumerable: true"
        + "      });"
        + "    }"
        + "  } catch(e) { console.log('[EVM] href patch failed: ' + e); }"
        + "})();";

    private volatile boolean loginInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register NativeAuth bridge
        getBridge().getWebView().addJavascriptInterface(new NativeAuthBridge(), "NativeAuth");

        getBridge().addWebViewListener(new WebViewListener() {
            @Override
            public void onPageStarted(WebView webView) {
                webView.evaluateJavascript(AUTH_INTERCEPTOR_JS, null);
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
        String callbackUrl = intent.getStringExtra(LoginActivity.EXTRA_CALLBACK_URL);
        if (callbackUrl != null) {
            android.util.Log.i("MainActivity", "[EVM] Loading callback: " + callbackUrl);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { getBridge().getWebView().loadUrl(callbackUrl); }
                catch (Exception ignored) {}
            }, 800);
        }
    }

    class NativeAuthBridge {
        @JavascriptInterface
        public void startAuth(String authUrl) {
            if (loginInProgress) return;
            // Rewrite redirect_uri: localhost/callback → chromiumapp.org
            String encodedLocal  = android.net.Uri.encode(LOCAL_CALLBACK);
            String encodedChrome = android.net.Uri.encode(CHROME_REDIRECT);
            String fixed = authUrl.replace(encodedLocal, encodedChrome);
            android.util.Log.i("MainActivity", "[EVM] startAuth called, launching LoginActivity");
            loginInProgress = true;
            Intent authIntent = new Intent(MainActivity.this, LoginActivity.class);
            authIntent.putExtra("auth_url", fixed);
            runOnUiThread(() -> startActivity(authIntent));
        }
    }
}
