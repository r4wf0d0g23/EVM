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

    static final String CHROME_REDIRECT = "https://lbmfdkobfnkfobfahpekbaaombpnafah.chromiumapp.org/";
    static final String LOCAL_CALLBACK = "https://localhost/callback";

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
        + "    if (url.indexOf('evefrontier.com/oauth2/token') !== -1 && options && options.body) {"
        + "      var rawBody = options.body;"
        + "      var bodyStr = typeof rawBody === 'string' ? rawBody"
        + "                  : (rawBody instanceof URLSearchParams ? rawBody.toString() : '');"
        + "      var fixed = bodyStr.replace(encodeURIComponent('https://localhost/callback'),"
        + "                                  encodeURIComponent('https://lbmfdkobfnkfobfahpekbaaombpnafah.chromiumapp.org/'));"
        + "      if (fixed !== bodyStr) {"
        + "        console.log('[EVM] Fixed redirect_uri in token exchange');"
        + "        options = Object.assign({}, options, {body: fixed});"
        + "      }"
        + "    }"
        // Cache wallet address from Enoki and inject CradleOS button
        + "    if (url.indexOf('enoki.mystenlabs.com/v1/zklogin') !== -1 && url.indexOf('zkp') === -1) {"
        + "      return _fetch.call(this, resource, options).then(function(resp) {"
        + "        var clone = resp.clone();"
        + "        clone.json().then(function(j) {"
        + "          if (j && j.data && j.data.address) {"
        + "            window.__evmWalletAddr = j.data.address;"
        + "            if (window.EVMNative) window.EVMNative.cacheWalletAddress(j.data.address);"
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
        + "    if (document.getElementById('evm-cradleos-btn') || !document.body) return;"
        + "    var btn = document.createElement('button');"
        + "    btn.id = 'evm-cradleos-btn';"
        + "    btn.textContent = '\\u26A1 CradleOS';"
        + "    btn.style.cssText = 'position:fixed;bottom:80px;right:16px;z-index:9999;"
        + "background:#C64F05;color:#FFFFD6;border:none;padding:10px 18px;"
        + "font-size:14px;font-weight:bold;cursor:pointer;border-radius:4px;';"
        + "    btn.onclick = function() {"
        + "      if (window.EVMNative) window.EVMNative.openCradleOS();"
        + "    };"
        + "    document.body.appendChild(btn);"
        + "    console.log('[EVM] CradleOS button injected');"
        + "  }"
        // Inject button if localStorage has session data (returning user)
        + "  try {"
        + "    var stored = Object.keys(localStorage).filter(function(k) { return k.includes('oidc')||k.includes('evevault'); });"
        + "    if (stored.length > 0) { setTimeout(injectCradleOSButton, 3000); }"
        + "  } catch(e) {}"
        + "})();";

    private volatile String cachedIdToken = null;
    private volatile String cachedWalletAddress = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getBridge().getWebView().addJavascriptInterface(new NativeAuthBridge(), "EVMNative");
        getBridge().getWebView().setWebViewClient(new AuthWebViewClient(getBridge()));

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

        // Re-inject CradleOS button on page load if wallet address is cached
        getBridge().addWebViewListener(new WebViewListener() {
            @Override
            public void onPageLoaded(WebView webView) {
                if (cachedWalletAddress != null) {
                    String js = "(function(){"
                        + "if(!document.getElementById('evm-cradleos-btn')&&document.body){"
                        + "var b=document.createElement('button');"
                        + "b.id='evm-cradleos-btn';"
                        + "b.textContent='\\u26A1 CradleOS';"
                        + "b.style.cssText='position:fixed;bottom:80px;right:16px;z-index:9999;"
                        + "background:#C64F05;color:#FFFFD6;border:none;padding:10px 18px;"
                        + "font-size:14px;font-weight:bold;cursor:pointer;border-radius:4px;';"
                        + "b.onclick=function(){if(window.EVMNative)window.EVMNative.openCradleOS();};"
                        + "document.body.appendChild(b);"
                        + "}})()";
                    webView.evaluateJavascript(js, null);
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

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        if (intent.hasExtra("id_token")) {
            cachedIdToken = intent.getStringExtra("id_token");
        }
        if (intent.hasExtra("wallet_address")) {
            cachedWalletAddress = intent.getStringExtra("wallet_address");
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

    /** Read wallet state from localStorage and launch CradleOS */
    private void launchCradleOS() {
        final String addr = cachedWalletAddress;
        final String token = cachedIdToken;
        runOnUiThread(() ->
            getBridge().getWebView().evaluateJavascript(
                "(function(){"
                + "var addr='',tok='';"
                + "try{var s=JSON.parse(localStorage.getItem('evevault:auth')||'{}');var u=(s.state||s).user||{};addr=u.profile&&u.profile.sui_address||'';tok=u.id_token||'';}catch(e){}"
                + "try{for(var k in localStorage){if(k.startsWith('oidc.user:')){try{var u=JSON.parse(localStorage[k]);tok=tok||u.id_token||'';addr=addr||u.profile&&u.profile.sui_address||'';}catch(e){}}}}catch(e){}"
                + "try{var jj=JSON.parse(localStorage.getItem('evevault:jwts')||'{}');for(var n in jj){tok=tok||jj[n].id_token||'';}}catch(e){}"
                + "return JSON.stringify({addr:addr,tok:tok});"
                + "})()",
                result -> {
                    String finalAddr = addr != null ? addr : "";
                    String finalToken = token != null ? token : "";
                    try {
                        String clean = result.replaceAll("^\"|\"$","").replace("\\\"","\"").replace("\\\\","\\");
                        org.json.JSONObject obj = new org.json.JSONObject(clean);
                        String a = obj.optString("addr","");
                        String t = obj.optString("tok","");
                        if (!a.isEmpty()) finalAddr = a;
                        if (!t.isEmpty()) finalToken = t;
                    } catch (Exception ignored) {}
                    android.util.Log.i("MainActivity","[EVM] CradleOS addr="+finalAddr+" hasToken="+!finalToken.isEmpty());
                    final String la = finalAddr, lt = finalToken;
                    runOnUiThread(() -> {
                        Intent i = new Intent(MainActivity.this, CradleOSActivity.class);
                        if (!lt.isEmpty()) i.putExtra(CradleOSActivity.EXTRA_ID_TOKEN, lt);
                        if (!la.isEmpty()) i.putExtra(CradleOSActivity.EXTRA_WALLET_ADDRESS, la);
                        startActivity(i);
                    });
                }
            )
        );
    }

    class NativeAuthBridge {
        @JavascriptInterface
        public void cacheWalletAddress(String address) {
            cachedWalletAddress = address;
        }

        @JavascriptInterface
        public void openCradleOS() {
            // @JavascriptInterface runs on background thread — post to main handler
            new Handler(Looper.getMainLooper()).post(() -> launchCradleOS());
        }
    }
}
