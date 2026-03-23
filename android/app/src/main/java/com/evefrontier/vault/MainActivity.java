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

    private void setupJsBridge() {
        WebView webView = getBridge().getWebView();
        webView.addJavascriptInterface(new NativeAuthBridge(), "NativeAuth");
    }

    private void setupWebViewInterception() {
        WebView webView = getBridge().getWebView();
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Intercept any navigation to FusionAuth — launch native auth instead
                if (url.contains(FUSIONAUTH_URL)) {
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    return true; // consume the navigation
                }
                // Let Capacitor handle all other URLs
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
