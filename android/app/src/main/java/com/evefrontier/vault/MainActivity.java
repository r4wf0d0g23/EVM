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
        // Cache wallet address from Enoki response + inject CradleOS button
        + "    if (url.indexOf('enoki.mystenlabs.com/v1/zklogin') !== -1 && url.indexOf('zkp') === -1) {"
        + "      return _fetch.call(this, resource, options).then(function(resp) {"
        + "        var clone = resp.clone();"
        + "        clone.json().then(function(j) {"
        + "          if (j && j.data && j.data.address && window.EVMNative) {"
        + "            window.EVMNative.cacheWalletAddress(j.data.address);"
        + "            console.log('[EVM] Cached wallet address: ' + j.data.address);"
        + "            setTimeout(injectCradleOSButton, 2000);"
        + "          }"
        + "        }).catch(function(){});"
        + "        return resp;"
        + "      });"
        + "    }"
        + "    return _fetch.call(this, resource, options);"
        + "  };"
        + "  function injectCradleOSButton() {"
        + "    if (document.getElementById('evm-cradleos-btn')) return;"
        + "    if (!document.body) { setTimeout(injectCradleOSButton, 500); return; }"
        + "    var btn = document.createElement('button');"
        + "    btn.id = 'evm-cradleos-btn';"
        + "    btn.textContent = '\\u26A1 CradleOS';"
        + "    btn.style.cssText = 'position:fixed;bottom:80px;right:16px;z-index:9999;'"
        + "      + 'background:#C64F05;color:#FFFFD6;border:none;padding:10px 18px;'"
        + "      + 'font-size:14px;font-weight:bold;cursor:pointer;border-radius:4px;';"
        + "    btn.onclick = function() {"
        + "      if (window.EVMNative) window.EVMNative.openCradleOS();"
        + "      else console.log('[EVM] EVMNative not found');"
        + "    };"
        + "    document.body.appendChild(btn);"
        + "    console.log('[EVM] CradleOS button injected');"
        + "  }"
        // Also try injecting button on session storage check (already logged in)
        + "  try {"
        + "    var stored = Object.keys(localStorage).filter(function(k) { return k.includes('oidc') || k.includes('evevault'); });"
        + "    if (stored.length > 0) { setTimeout(injectCradleOSButton, 3000); }"
        + "  } catch(e) {}"
        + "  var LOCAL_CB = 'https://localhost/callback';"
        + "  var CHROME_RDR = 'https://lbmfdkobfnkfobfahpekbaaombpnafah.chromiumapp.org/';"
        + "})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register bridge for CradleOS launcher
        getBridge().getWebView().addJavascriptInterface(new NativeAuthBridge(), "EVMNative");

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

        // Re-inject CradleOS button on page load if wallet address is known
        getBridge().addWebViewListener(new WebViewListener() {
            @Override
            public void onPageLoaded(WebView webView) {
                if (cachedWalletAddress != null) {
                    webView.evaluateJavascript(
                        "(function() { if (!document.getElementById('evm-cradleos-btn') && document.body) {"
                        + "  var b = document.createElement('button');"
                        + "  b.id = 'evm-cradleos-btn';"
                        + "  b.textContent = '\\u26A1 CradleOS';"
                        + "  b.style.cssText = 'position:fixed;bottom:80px;right:16px;z-index:9999;"
                        + "    background:#C64F05;color:#FFFFD6;border:none;padding:10px 18px;"
                        + "    font-size:14px;font-weight:bold;cursor:pointer;border-radius:4px;';"
                        + "  b.onclick = function() { if(window.EVMNative) window.EVMNative.openCradleOS(); };"
                        + "  document.body.appendChild(b);"
                        + "} })()", null
                    );
                }
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
    }

    // Cached after successful auth — used to bridge into CradleOS
    private volatile String cachedIdToken = null;
    private volatile String cachedWalletAddress = null;

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        // Cache id_token from auth result (comes from LoginActivity via intent extra)
        if (intent.hasExtra("id_token")) {
            cachedIdToken = intent.getStringExtra("id_token");
            android.util.Log.i("MainActivity", "[EVM] Cached id_token for CradleOS bridge");
        }
        if (intent.hasExtra("wallet_address")) {
            cachedWalletAddress = intent.getStringExtra("wallet_address");
        }
        // Also cache the token via a JS call after the WebView is ready, so the
        // Enoki fetch interceptor can pick it up when the wallet address is returned
        if (cachedIdToken != null) {
            final String token = cachedIdToken;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    String escaped = token.replace("\\", "\\\\").replace("'", "\\'");
                    getBridge().getWebView().evaluateJavascript(
                        "window.__evmCachedToken = '" + escaped + "';", null
                    );
                } catch (Exception ignored) {}
            }, 1200);
        }

        String callbackUrl = intent.getStringExtra(LoginActivity.EXTRA_CALLBACK_URL);
        if (callbackUrl != null) {
            android.util.Log.i("MainActivity", "[EVM] Loading callback from intent: " + callbackUrl);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { getBridge().getWebView().loadUrl(callbackUrl); }
                catch (Exception ignored) {}
            }, 800);
        }
    }

    class NativeAuthBridge {
        @JavascriptInterface
        public void cacheWalletAddress(String address) {
            cachedWalletAddress = address;
            android.util.Log.i("MainActivity", "[EVM] Wallet address cached: " + address);
        }

        @JavascriptInterface
        public void openCradleOS() {
            // Read wallet address and token from the WebView's JS state before launching
            final String addr = cachedWalletAddress;
            final String token = cachedIdToken;
            getBridge().getWebView().evaluateJavascript(
                "(function() {"
                + "  try {"
                + "    var addr = '', tok = '';"
                + "    var debugKeys = [];"
                // Log all localStorage keys for debugging
                + "    try { for(var k in localStorage) { if(k.includes('vault')||k.includes('oidc')||k.includes('eve')) { debugKeys.push(k); } } } catch(e){}"
                + "    console.log('[EVM] localStorage keys: ' + debugKeys.join(', '));"
                // Try evevault:auth Zustand store (user object has profile.sui_address)
                + "    try { var s=JSON.parse(localStorage.getItem('evevault:auth')||'{}'); var st=s.state||s; var u=st.user||{}; addr=addr||u.profile?.sui_address||''; tok=tok||u.id_token||''; console.log('[EVM] evevault:auth addr='+addr); } catch(e){ console.log('[EVM] evevault:auth err:'+e); }"
                // Try all OIDC user keys
                + "    try { for (var k in localStorage) { if (k.startsWith('oidc.user:')) { try { var u=JSON.parse(localStorage[k]); tok=tok||u.id_token||''; addr=addr||u.profile?.sui_address||''; console.log('[EVM] oidc key '+k+' addr='+addr); } catch(e){} } } } catch(e){}"
                // Try evevault:jwts network JWT store
                + "    try { var jj=JSON.parse(localStorage.getItem('evevault:jwts')||'{}'); for(var net in jj){var jt=jj[net]; tok=tok||jt.id_token||''; } } catch(e){}"
                // Try evevault:network Zustand store for chain context
                + "    try { var ns=JSON.parse(localStorage.getItem('evevault:network')||'{}'); console.log('[EVM] network store: '+JSON.stringify(ns).substring(0,100)); } catch(e){}"
                + "    return JSON.stringify({addr:addr, tok:tok});"
                + "  } catch(e) { return '{}'; }"
                + "})()",
                result -> {
                    String finalAddr = addr;
                    String finalToken = token;
                    try {
                        // result is a JSON string wrapped in quotes, need to parse
                        String clean = result.replaceAll("^\"|\"$", "").replace("\\\"", "\"").replace("\\\\", "\\");
                        org.json.JSONObject obj = new org.json.JSONObject(clean);
                        String jsAddr = obj.optString("addr", "");
                        String jsTok = obj.optString("tok", "");
                        if (!jsAddr.isEmpty()) finalAddr = jsAddr;
                        if (!jsTok.isEmpty()) finalToken = jsTok;
                        android.util.Log.i("MainActivity", "[EVM] CradleOS launch addr=" + finalAddr + " hasToken=" + !finalToken.isEmpty());
                    } catch (Exception e) {
                        android.util.Log.w("MainActivity", "[EVM] Could not parse JS wallet state: " + e.getMessage());
                    }
                    final String launchAddr = finalAddr;
                    final String launchToken = finalToken;
                    runOnUiThread(() -> {
                        android.util.Log.i("MainActivity", "[EVM] Opening CradleOS");
                        Intent i = new Intent(MainActivity.this, CradleOSActivity.class);
                        if (launchToken != null && !launchToken.isEmpty()) i.putExtra(CradleOSActivity.EXTRA_ID_TOKEN, launchToken);
                        if (launchAddr != null && !launchAddr.isEmpty()) i.putExtra(CradleOSActivity.EXTRA_WALLET_ADDRESS, launchAddr);
                        startActivity(i);
                    });
                }
            );
        }
    }
}
