package com.evefrontier.vault;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private static final String FUSIONAUTH_URL = "test.auth.evefrontier.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupJsBridge();
        setupWebViewInterception();
        handleAuthResult(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAuthResult(intent);
    }

    // JS to inject on every page load — intercepts the login flow at the source
    private static final String LOGIN_INTERCEPTOR_JS =
        "(function() {" +
        "  var _origAssign = window.location.__defineSetter__ ? null : null;" +
        // Override window.location.href setter to catch redirect attempts
        "  var _origHref = Object.getOwnPropertyDescriptor(window.location.__proto__, 'href') || " +
        "                  Object.getOwnPropertyDescriptor(Location.prototype, 'href');" +
        "  if (_origHref && _origHref.set) {" +
        "    Object.defineProperty(window.location, 'href', {" +
        "      set: function(url) {" +
        "        if (url && url.indexOf('test.auth.evefrontier.com') !== -1) {" +
        "          window.NativeAuth && window.NativeAuth.requestLogin();" +
        "          return;" +
        "        }" +
        "        _origHref.set.call(window.location, url);" +
        "      }," +
        "      get: function() { return _origHref.get.call(window.location); }," +
        "      configurable: true" +
        "    });" +
        "  }" +
        // Override window.location.replace
        "  var _origReplace = window.location.replace.bind(window.location);" +
        "  window.location.replace = function(url) {" +
        "    if (url && url.indexOf('test.auth.evefrontier.com') !== -1) {" +
        "      window.NativeAuth && window.NativeAuth.requestLogin();" +
        "      return;" +
        "    }" +
        "    _origReplace(url);" +
        "  };" +
        // Override window.open
        "  var _origOpen = window.open.bind(window);" +
        "  window.open = function(url, target, features) {" +
        "    if (url && url.indexOf('test.auth.evefrontier.com') !== -1) {" +
        "      window.NativeAuth && window.NativeAuth.requestLogin();" +
        "      return null;" +
        "    }" +
        "    return _origOpen(url, target, features);" +
        "  };" +
        // Also patch fetch/XHR redirect — listen for login button click
        "  document.addEventListener('click', function(e) {" +
        "    var btn = e.target && e.target.closest('button');" +
        "    if (btn) {" +
        "      var txt = btn.textContent && btn.textContent.trim().toLowerCase();" +
        "      if (txt === 'login' || txt === 'sign in' || txt === 'log in') {" +
        "        setTimeout(function() {" +
        "          window.NativeAuth && window.NativeAuth.requestLogin();" +
        "        }, 50);" +
        "      }" +
        "    }" +
        "  }, true);" +
        "})();";

    private void setupJsBridge() {
        WebView webView = getBridge().getWebView();
        webView.addJavascriptInterface(new NativeAuthBridge(), "NativeAuth");
    }

    private void setupWebViewInterception() {
        WebView webView = getBridge().getWebView();
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Inject interceptor JS after every page load
                view.evaluateJavascript(LOGIN_INTERCEPTOR_JS, null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.contains(FUSIONAUTH_URL)) {
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.contains(FUSIONAUTH_URL)) {
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, url);
            }
        });
    }

    private void handleAuthResult(Intent intent) {
        if (intent == null) return;
        boolean authSuccess = intent.getBooleanExtra("auth_success", false);
        String idToken = intent.getStringExtra("id_token");
        String authError = intent.getStringExtra("auth_error");

        if (intent.hasExtra("auth_success")) {
            if (authSuccess && idToken != null && !idToken.isEmpty()) {
                String escaped = idToken.replace("\\", "\\\\").replace("'", "\\'");
                String js = String.format(
                    "window.postMessage({__from:'Eve Vault',type:'auth_success',token:{id_token:'%s'}}, '*');",
                    escaped
                );
                getBridge().getWebView().post(() ->
                    getBridge().getWebView().evaluateJavascript(js, null)
                );
            } else {
                String errMsg = authError != null ? authError.replace("'", "\\'") : "Authentication failed";
                String js = String.format(
                    "window.postMessage({__from:'Eve Vault',type:'auth_error',error:'%s'}, '*');",
                    errMsg
                );
                getBridge().getWebView().post(() ->
                    getBridge().getWebView().evaluateJavascript(js, null)
                );
            }
        }
    }

    class NativeAuthBridge {
        @JavascriptInterface
        public void requestLogin() {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
        }
    }
}
