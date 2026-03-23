package com.evefrontier.vault;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.getcapacitor.BridgeActivity;
import java.io.ByteArrayInputStream;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Install WebViewClient BEFORE bridge loads to intercept navigations
        installAuthInterceptor();
        new Handler(Looper.getMainLooper()).postDelayed(this::addJsBridge, 1200);
        handleAuthResult(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAuthResult(intent);
    }

    private void installAuthInterceptor() {
        getBridge().getWebView().setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.contains("auth.evefrontier.com")) {
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    return true;
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.contains("auth.evefrontier.com")) {
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    return true;
                }
                return false;
            }

            // Intercept ALL requests including fetch() calls to auth.evefrontier.com
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.contains("auth.evefrontier.com")) {
                    // Launch native login and return a dummy response to stop the JS fetch
                    runOnUiThread(() -> startActivity(new Intent(MainActivity.this, LoginActivity.class)));
                    // Return empty JSON to satisfy the OIDC client and prevent the error
                    byte[] empty = "{}".getBytes();
                    return new WebResourceResponse("application/json", "UTF-8",
                        new ByteArrayInputStream(empty));
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
    }

    private void addJsBridge() {
        try {
            WebView webView = getBridge().getWebView();
            webView.removeJavascriptInterface("NativeAuth");
            webView.addJavascriptInterface(new NativeAuthBridge(), "NativeAuth");
        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).postDelayed(this::addJsBridge, 1000);
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
            String err = (authError != null ? authError : "Auth failed").replace("'", "\\'");
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
