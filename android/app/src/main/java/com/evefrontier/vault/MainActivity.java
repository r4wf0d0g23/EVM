package com.evefrontier.vault;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    // JS injected after page load to intercept the LOGIN button click
    private static final String LOGIN_INTERCEPTOR_JS =
        "(function() {" +
        "  if (window.__nativeAuthPatched) return;" +
        "  window.__nativeAuthPatched = true;" +
        "  document.addEventListener('click', function(e) {" +
        "    var el = e.target;" +
        "    for (var i = 0; i < 5 && el; i++, el = el.parentElement) {" +
        "      if (el.tagName === 'BUTTON') {" +
        "        var txt = (el.textContent || '').trim().toLowerCase();" +
        "        if (txt === 'login' || txt === 'sign in' || txt === 'log in') {" +
        "          e.stopPropagation();" +
        "          e.preventDefault();" +
        "          if (window.NativeAuth) { window.NativeAuth.requestLogin(); }" +
        "          return;" +
        "        }" +
        "      }" +
        "    }" +
        "  }, true);" +
        "})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Delay setup until Capacitor bridge is initialized
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
            // Add JS bridge — safe to call multiple times
            webView.removeJavascriptInterface("NativeAuth");
            webView.addJavascriptInterface(new NativeAuthBridge(), "NativeAuth");
            // Inject interceptor
            webView.evaluateJavascript(LOGIN_INTERCEPTOR_JS, null);
        } catch (Exception e) {
            // Retry after another second if not ready
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
            try {
                getBridge().getWebView().evaluateJavascript(finalJs, null);
            } catch (Exception ignored) {}
        }, 1000);
    }

    class NativeAuthBridge {
        @JavascriptInterface
        public void requestLogin() {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
        }
    }
}
