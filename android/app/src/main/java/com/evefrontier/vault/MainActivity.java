package com.evefrontier.vault;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private static final String LOGIN_INTERCEPTOR_JS = "(function() {"
        + "  if (window.__nativeAuthPatched) return;"
        + "  window.__nativeAuthPatched = true;"
        + "  function isAuth(u) { return u && u.indexOf('auth.evefrontier.com') !== -1; }"
        + "  try {"
        + "    var lp = Location.prototype;"
        + "    var hd = Object.getOwnPropertyDescriptor(lp, 'href');"
        + "    if (hd && hd.set) {"
        + "      Object.defineProperty(lp, 'href', {"
        + "        get: hd.get,"
        + "        set: function(u) { if (isAuth(u)) { window.NativeAuth && window.NativeAuth.requestLogin(); return; } hd.set.call(this, u); },"
        + "        configurable: true"
        + "      });"
        + "    }"
        + "  } catch(e) {}"
        + "  try {"
        + "    var oa = window.location.assign.bind(window.location);"
        + "    var or = window.location.replace.bind(window.location);"
        + "    window.location.assign = function(u) { if (isAuth(u)) { window.NativeAuth && window.NativeAuth.requestLogin(); return; } oa(u); };"
        + "    window.location.replace = function(u) { if (isAuth(u)) { window.NativeAuth && window.NativeAuth.requestLogin(); return; } or(u); };"
        + "  } catch(e) {}"
        + "  var of2 = window.fetch;"
        + "  window.fetch = function(u, opts) {"
        + "    var url = typeof u === 'string' ? u : (u && u.url) || '';"
        + "    if (isAuth(url)) { window.NativeAuth && window.NativeAuth.requestLogin(); return Promise.resolve(new Response('{}', {status:200})); }"
        + "    return of2.apply(this, arguments);"
        + "  };"
        + "  document.addEventListener('click', function(e) {"
        + "    var el = e.target;"
        + "    for (var i = 0; i < 8 && el; i++, el = el.parentElement) {"
        + "      if (el.tagName === 'BUTTON' || el.tagName === 'A') {"
        + "        var t = (el.textContent || '').trim().toLowerCase();"
        + "        if (t === 'login' || t === 'sign in') {"
        + "          e.stopImmediatePropagation();"
        + "          e.preventDefault();"
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
        new Handler(Looper.getMainLooper()).postDelayed(this::setupNativeAuth, 800);
        handleAuthResult(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAuthResult(intent);
    }

    private void setupNativeAuth() {
        try {
            WebView webView = getBridge().getWebView();
            webView.removeJavascriptInterface("NativeAuth");
            webView.addJavascriptInterface(new NativeAuthBridge(), "NativeAuth");
            webView.evaluateJavascript(LOGIN_INTERCEPTOR_JS, null);
        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).postDelayed(this::setupNativeAuth, 1000);
        }
    }

    private void handleAuthResult(Intent intent) {
        if (intent == null || !intent.hasExtra("auth_success")) return;
        boolean authSuccess = intent.getBooleanExtra("auth_success", false);
        String idToken = intent.getStringExtra("id_token");
        String authError = intent.getStringExtra("auth_error");
        String js;
        if (authSuccess && idToken != null && !idToken.isEmpty()) {
            String escaped = idToken.replace("\\", "\\\\").replace("'", "\\'");
            js = "window.postMessage({__from:'Eve Vault',type:'auth_success',token:{id_token:'" + escaped + "'}}, '*');";
        } else {
            String err = (authError != null ? authError : "Authentication failed").replace("'", "\\'");
            js = "window.postMessage({__from:'Eve Vault',type:'auth_error',error:'" + err + "'}, '*');";
        }
        final String finalJs = js;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { getBridge().getWebView().evaluateJavascript(finalJs, null); } catch (Exception ignored) {}
        }, 1000);
    }

    class NativeAuthBridge {
        @JavascriptInterface
        public void requestLogin() {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
        }
    }
}
