package com.evefrontier.vault;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Add JS bridge for native auth
        setupJsBridge();
        // Handle token if passed from TokenActivity
        handleAuthResult(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAuthResult(intent);
    }

    private void setupJsBridge() {
        // Bridge allows WebView to request native login
        // WebView JS calls: window.NativeAuth.requestLogin()
        WebView webView = getBridge().getWebView();
        webView.addJavascriptInterface(new NativeAuthBridge(), "NativeAuth");
    }

    private void handleAuthResult(Intent intent) {
        if (intent == null) return;
        boolean authSuccess = intent.getBooleanExtra("auth_success", false);
        String idToken = intent.getStringExtra("id_token");
        String authError = intent.getStringExtra("auth_error");

        if (intent.hasExtra("auth_success")) {
            if (authSuccess && idToken != null && !idToken.isEmpty()) {
                // Inject token into WebView
                String js = String.format(
                    "window.postMessage({__from:'NativeAuth',type:'auth_success',token:{id_token:'%s'}}, '*');",
                    idToken
                );
                getBridge().getWebView().post(() ->
                    getBridge().getWebView().evaluateJavascript(js, null)
                );
            } else {
                String js = String.format(
                    "window.postMessage({__from:'NativeAuth',type:'auth_error',error:'%s'}, '*');",
                    authError != null ? authError.replace("'", "\\'") : "Authentication failed"
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
