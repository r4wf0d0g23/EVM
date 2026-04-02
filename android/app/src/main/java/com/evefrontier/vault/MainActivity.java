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

    static final String CHROME_REDIRECT       = "https://lbmfdkobfnkfobfahpekbaaombpnafah.chromiumapp.org/";
    static final String EVEFRONTIER_CALLBACK   = "evefrontier://callback";
    static final String LOCAL_CALLBACK         = "https://localhost/callback";

    /** Build the auth interceptor JS dynamically based on selected server */
    private String buildAuthInterceptorJs() {
        String selectedAuth = ServerConfig.getAuthUrl(this);
        String buildAuth = ServerConfig.getBuildAuthUrl();
        String selectedClientId = ServerConfig.getClientId(this);
        String buildClientId = ServerConfig.getBuildClientId();
        String selectedServer = ServerConfig.getSelectedServer(this);
        return AUTH_INTERCEPTOR_JS_TEMPLATE
            .replace("__SELECTED_AUTH__", selectedAuth)
            .replace("__BUILD_AUTH__", buildAuth)
            .replace("__SELECTED_CLIENT_ID__", selectedClientId)
            .replace("__BUILD_CLIENT_ID__", buildClientId)
            .replace("__SELECTED_SERVER__", selectedServer);
    }

    private static final String AUTH_INTERCEPTOR_JS_TEMPLATE =
        "(function() {"
        + "  if (location.hostname !== 'localhost') return;"
        + "  if (window.__authPatched) return; window.__authPatched = true;"
        + "  var selectedAuth = '__SELECTED_AUTH__';"
        + "  var buildAuth = '__BUILD_AUTH__';"
        + "  var selectedClientId = '__SELECTED_CLIENT_ID__';"
        + "  var buildClientId = '__BUILD_CLIENT_ID__';"
        + "  var selectedServer = '__SELECTED_SERVER__';"
        + "  console.log('[EVM] Auth interceptor installed (server=' + selectedServer + ')');"
        + "  var _fetch = window.fetch;"
        + "  window.fetch = function(resource, options) {"
        + "    var url = typeof resource === 'string' ? resource"
        + "              : (resource && resource.url ? resource.url : '');"
        // OIDC discovery: always return metadata for the SELECTED server
        + "    if (url.indexOf('.well-known/openid-configuration') !== -1) {"
        + "      var meta = JSON.stringify({"
        + "        issuer: selectedAuth,"
        + "        authorization_endpoint: selectedAuth+'/oauth2/authorize',"
        + "        token_endpoint: selectedAuth+'/oauth2/token',"
        + "        userinfo_endpoint: selectedAuth+'/oauth2/userinfo',"
        + "        jwks_uri: selectedAuth+'/.well-known/jwks.json',"
        + "        response_types_supported: ['code'],"
        + "        subject_types_supported: ['public'],"
        + "        id_token_signing_alg_values_supported: ['RS256']"
        + "      });"
        + "      console.log('[EVM] Mock OIDC metadata for: ' + selectedAuth);"
        + "      return Promise.resolve(new Response(meta,"
        + "        {status:200, headers:{'Content-Type':'application/json'}}));"
        + "    }"
        // Token exchange: fix redirect_uri AND rewrite auth server URL if needed
        + "    if (url.indexOf('evefrontier.com/oauth2/token') !== -1 && options && options.body) {"
        + "      if (selectedAuth !== buildAuth) {"
        + "        url = url.replace(buildAuth, selectedAuth);"
        + "        if (typeof resource === 'string') resource = url;"
        + "        console.log('[EVM] Rewrote token endpoint to: ' + selectedAuth);"
        + "      }"
        + "      var rawBody = options.body;"
        + "      var bodyStr = typeof rawBody === 'string' ? rawBody"
        + "                  : (rawBody instanceof URLSearchParams ? rawBody.toString() : '');"
        // Fix redirect_uri
        + "      var fixed = bodyStr.replace(encodeURIComponent('https://localhost/callback'),"
        + "                                  encodeURIComponent('evefrontier://callback'));"
        // Fix client_id if build differs from selected
        + "      if (selectedClientId !== buildClientId) {"
        + "        fixed = fixed.replace(buildClientId, selectedClientId);"
        + "        console.log('[EVM] Rewrote client_id in token exchange');"
        + "      }"
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
        // Fix Enoki API: add Bearer prefix to Authorization header (API changed format)
        + "    if (url.indexOf('enoki.mystenlabs.com') !== -1 && options && options.headers) {"
        + "      var h = options.headers;"
        + "      if (h.Authorization && !h.Authorization.startsWith('Bearer ')) {"
        + "        h = Object.assign({}, h, {Authorization: 'Bearer ' + h.Authorization});"
        + "        options = Object.assign({}, options, {headers: h});"
        + "        console.log('[EVM] Fixed Enoki Authorization header');"
        + "      }"
        + "    }"
        + "    return _fetch.call(this, resource, options);"
        + "  };"
        // Inject server selector — inline pill toggle, EVE Frontier themed
        + "  function injectServerSelector() {"
        + "    if (document.getElementById('evm-server-toggle') || !document.body) return;"
        + "    var wrap = document.createElement('div');"
        + "    wrap.id = 'evm-server-toggle';"
        + "    wrap.style.cssText = 'position:fixed;bottom:140px;left:50%;transform:translateX(-50%);"
        + "z-index:9999;display:flex;align-items:center;gap:0;border-radius:20px;"
        + "border:1px solid #555;overflow:hidden;background:#1a1a2e;';"
        + "    function mkBtn(label, val) {"
        + "      var b = document.createElement('button');"
        + "      b.textContent = label;"
        + "      b.dataset.val = val;"
        + "      b.style.cssText = 'border:none;padding:7px 18px;font-size:12px;"
        + "font-weight:bold;cursor:pointer;text-transform:uppercase;letter-spacing:1px;"
        + "transition:background .2s,color .2s;';"
        + "      if (val === selectedServer) {"
        + "        b.style.background = '#C64F05'; b.style.color = '#FFFFD6';"
        + "      } else {"
        + "        b.style.background = 'transparent'; b.style.color = '#888';"
        + "      }"
        + "      b.onclick = function() {"
        + "        if (val !== selectedServer && window.EVMNative) {"
        + "          selectedServer = val;"
        + "          var btns = document.querySelectorAll('#evm-server-toggle button');"
        + "          btns.forEach(function(btn) {"
        + "            if (btn.dataset.val === val) {"
        + "              btn.style.background = '#C64F05'; btn.style.color = '#FFFFD6';"
        + "              btn.style.fontWeight = 'bold';"
        + "            } else {"
        + "              btn.style.background = 'transparent'; btn.style.color = '#888';"
        + "              btn.style.fontWeight = 'normal';"
        + "            }"
        + "          });"
        + "          window.EVMNative.setServer(val);"
        + "        }"
        + "      };"
        + "      return b;"
        + "    }"
        + "    wrap.appendChild(mkBtn('Stillness', 'stillness'));"
        + "    wrap.appendChild(mkBtn('Utopia', 'utopia'));"
        + "    document.body.appendChild(wrap);"
        + "    console.log('[EVM] Server toggle injected (current=' + selectedServer + ')');"
        + "  }"
        + "  setTimeout(injectServerSelector, 500);"
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
        + "  var APP_VERSION = '1.1';"
        + "  function checkForUpdate() {"
        + "    fetch('https://api.github.com/repos/r4wf0d0g23/EVM/releases/latest')"
        + "      .then(function(r) { return r.json(); })"
        + "      .catch(function() { return null; })"
        + "      .then(function(data) {"
        + "        if (!data || !data.tag_name) return;"
        + "        var latest = data.tag_name.replace(/^v/, '');"
        + "        if (latest === APP_VERSION) return;"
        + "        if (document.getElementById('evm-update-banner')) return;"
        + "        var banner = document.createElement('div');"
        + "        banner.id = 'evm-update-banner';"
        + "        banner.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:99999;"
        + "background:#C64F05;color:#FFFFD6;text-align:center;padding:10px 16px;"
        + "font-size:13px;font-weight:bold;cursor:pointer;letter-spacing:0.5px;';"
        + "        banner.textContent = '\\u25B2 Update available (v' + latest + ') \\u2014 tap to download';"
        + "        banner.onclick = function() {"
        + "          window.open('https://github.com/r4wf0d0g23/EVM/releases/latest', '_blank');"
        + "        };"
        + "        document.body.insertBefore(banner, document.body.firstChild);"
        + "        console.log('[EVM] Update banner shown: v' + latest);"
        + "      });"
        + "  }"
        + "  setTimeout(checkForUpdate, 3000);"
        + "})();";
    private volatile String cachedIdToken = null;
    private volatile String cachedWalletAddress = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getBridge().getWebView().addJavascriptInterface(new NativeAuthBridge(), "EVMNative");
        getBridge().getWebView().setWebViewClient(new AuthWebViewClient(getBridge()));

        // Always rebuild interceptorJs fresh on each page load so server toggle takes effect
        getBridge().addWebViewListener(new WebViewListener() {
            @Override
            public void onPageStarted(WebView webView) {
                webView.evaluateJavascript(buildAuthInterceptorJs(), null);
            }
        });

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
                // Log all keys so we can see what's stored
                + "var allKeys=[];try{for(var k in localStorage)allKeys.push(k);}catch(e){}"
                + "console.log('[EVM] LS keys: '+allKeys.join('|'));"
                // evevault:auth Zustand store
                + "try{"
                + "  var s=JSON.parse(localStorage.getItem('evevault:auth')||'{}');"
                + "  var st=s.state!=null?s.state:s;"
                + "  var u=st.user||{};"
                + "  var p=u.profile||{};"
                + "  addr=p.sui_address||'';"
                + "  tok=u.id_token||'';"
                + "  console.log('[EVM] evevault:auth: addr='+addr+' tok='+!!tok);"
                + "}catch(e){console.log('[EVM] evevault:auth err: '+e);}"
                // oidc.user: keys
                + "try{for(var k in localStorage){if(k.indexOf('oidc.user:')===0){try{"
                + "  var u=JSON.parse(localStorage.getItem(k));"
                + "  var p=u.profile||{};"
                + "  tok=tok||u.id_token||'';"
                + "  addr=addr||p.sui_address||p.ccp_owned_wallet_address||'';"
                + "}catch(e){}}}}catch(e){}"
                // evevault:jwts
                + "try{"
                + "  var jj=JSON.parse(localStorage.getItem('evevault:jwts')||'{}');"
                + "  console.log('[EVM] evevault:jwts: '+Object.keys(jj).join(','));"
                + "  for(var n in jj)tok=tok||jj[n].id_token||'';"
                + "}catch(e){}"
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
            new Handler(Looper.getMainLooper()).post(() -> launchCradleOS());
        }

        @JavascriptInterface
        public String getServer() {
            return ServerConfig.getSelectedServer(MainActivity.this);
        }

        @JavascriptInterface
        public void setServer(String server) {
            android.util.Log.i("MainActivity", "[EVM] Server switched to: " + server);
            ServerConfig.setSelectedServer(MainActivity.this, server);
            // Reload the page with new interceptor config
            new Handler(Looper.getMainLooper()).post(() -> {
                getBridge().getWebView().loadUrl("https://localhost");
            });
        }
    }
}
