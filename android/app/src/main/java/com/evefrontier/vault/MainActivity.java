package com.evefrontier.vault;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.WebViewListener;
import java.util.List;

public class MainActivity extends BridgeActivity {

    // Redirect URI registered with CCP (Chrome extension)
    static final String CHROME_REDIRECT = "https://lbmfdkobfnkfobfahpekbaaombpnafah.chromiumapp.org/";
    // Redirect URI EVE Vault web app uses internally
    static final String LOCAL_CALLBACK = "https://localhost/callback";

    // Only mock OIDC discovery — let EVE Vault's auth flow navigate normally.
    // Navigation is intercepted at the native layer via AuthWebViewClient.
    private static final String AUTH_INTERCEPTOR_JS =
        "(function() {"
        + "  if (location.hostname !== 'localhost') return;"
        + "  if (window.__authPatched) return; window.__authPatched = true;"
        + "  console.log('[EVM] Auth interceptor installed');"
        + "  var _fetch = window.fetch;"
        + "  window.fetch = function(resource, options) {"
        + "    var url = typeof resource === 'string' ? resource"
        + "              : (resource && resource.url ? resource.url : '');"
        + "    if (url.indexOf('.well-known/openid-configuration') !== -1) {"
        + "      var base = url.indexOf('test.auth.evefrontier.com') !== -1"
        + "        ? 'https://test.auth.evefrontier.com'"
        + "        : 'https://auth.evefrontier.com';"
        + "      var meta = JSON.stringify({"
        + "        issuer: base,"
        + "        authorization_endpoint: base+'/oauth2/authorize',"
        + "        token_endpoint: base+'/oauth2/token',"
        + "        userinfo_endpoint: base+'/oauth2/userinfo',"
        + "        jwks_uri: base+'/.well-known/jwks.json',"
        + "        response_types_supported: ['code'],"
        + "        subject_types_supported: ['public'],"
        + "        id_token_signing_alg_values_supported: ['RS256']"
        + "      });"
        + "      console.log('[EVM] Mock OIDC metadata for: ' + base);"
        + "      return Promise.resolve(new Response(meta,"
        + "        {status:200, headers:{'Content-Type':'application/json'}}));"
        + "    }"
        // Fix token exchange redirect_uri to match what was used in auth request
        + "    if (url.indexOf('evefrontier.com/oauth2/token') !== -1 && options && options.body) {"
        + "      var rawBody = options.body;"
        + "      var bodyStr;"
        + "      if (typeof rawBody === 'string') {"
        + "        bodyStr = rawBody;"
        + "      } else if (rawBody instanceof URLSearchParams) {"
        + "        bodyStr = rawBody.toString();"
        + "      } else {"
        + "        bodyStr = '';"
        + "      }"
        + "      var fixed = bodyStr.replace(encodeURIComponent(LOCAL_CB), encodeURIComponent(CHROME_RDR));"
        + "      if (fixed !== bodyStr) {"
        + "        console.log('[EVM] Fixed redirect_uri in token exchange');"
        + "        options = Object.assign({}, options, {body: fixed});"
        + "      }"
        + "    }"
        // Log Enoki responses to diagnose zkLogin address lookup failures
        + "    if (url.indexOf('enoki.mystenlabs.com') !== -1) {"
        + "      return _fetch.call(this, resource, options).then(function(resp) {"
        + "        var clone = resp.clone();"
        + "        clone.json().then(function(j) {"
        + "          console.log('[EVM] Enoki response: ' + JSON.stringify(j).substring(0,200));"
        + "        }).catch(function(e) {"
        + "          console.log('[EVM] Enoki response parse error: ' + e);"
        + "        });"
        + "        return resp;"
        + "      });"
        + "    }"
        + "    return _fetch.call(this, resource, options);"
        + "  };"
        + "  var LOCAL_CB = 'https://localhost/callback';"
        + "  var CHROME_RDR = 'https://lbmfdkobfnkfobfahpekbaaombpnafah.chromiumapp.org/';"
        + "})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Replace Capacitor's WebViewClient with our custom one that intercepts OAuth URLs.
        // SAFE: Capacitor serves localhost assets via shouldInterceptRequest() (not
        // shouldOverrideUrlLoading), so our override doesn't break asset loading.
        // We call super.shouldOverrideUrlLoading() for all other URLs.
        getBridge().getWebView().setWebViewClient(new AuthWebViewClient(getBridge()));

        // Inject OIDC discovery mock at document-start
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                getBridge().getWebView(),
                AUTH_INTERCEPTOR_JS,
                java.util.Collections.singleton("https://localhost")
            );
        } else {
            getBridge().addWebViewListener(new WebViewListener() {
                @Override
                public void onPageStarted(WebView webView) {
                    webView.evaluateJavascript(AUTH_INTERCEPTOR_JS, null);
                }
            });
        }

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
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String callbackUrl = intent.getStringExtra(LoginActivity.EXTRA_CALLBACK_URL);
        if (callbackUrl != null) {
            android.util.Log.i("MainActivity", "[EVM] Loading callback from intent: " + callbackUrl);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { getBridge().getWebView().loadUrl(callbackUrl); }
                catch (Exception ignored) {}
            }, 800);
        }
    }
}
