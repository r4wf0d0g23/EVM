package com.evefrontier.vault;

import android.content.Intent;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Extends BridgeWebViewClient to intercept OAuth authorize navigations at the native layer.
 *
 * KEY INSIGHT: Capacitor serves localhost assets via shouldInterceptRequest() — NOT
 * shouldOverrideUrlLoading(). So overriding shouldOverrideUrlLoading here is safe and
 * does not break asset serving.
 *
 * This fires for ALL WebView navigations regardless of JS timing or Location object
 * overridability — it's the definitive interception point.
 */
public class AuthWebViewClient extends BridgeWebViewClient {

    public AuthWebViewClient(Bridge bridge) {
        super(bridge);
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();

        // Rewrite auth server URLs if selected server differs from build
        if (url.contains("evefrontier.com/oauth2/token")) {
            String selectedAuth = ServerConfig.getAuthUrl(view.getContext());
            String buildAuth = ServerConfig.getBuildAuthUrl();
            if (!selectedAuth.equals(buildAuth) && url.contains(buildAuth)) {
                url = url.replace(buildAuth, selectedAuth);
                android.util.Log.i("AuthWebViewClient", "[EVM] Rewrote token endpoint URL to: " + selectedAuth);
            }
        }

        // Intercept Enoki API calls to fix Authorization header (add Bearer prefix)
        if (url.contains("api.enoki.mystenlabs.com")) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod(request.getMethod());

                // Copy all headers, fixing Authorization
                Map<String, String> headers = request.getRequestHeaders();
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    String key = entry.getKey();
                    String val = entry.getValue();
                    if (key.equalsIgnoreCase("Authorization") && !val.startsWith("Bearer ")) {
                        val = "Bearer " + val;
                        android.util.Log.i("AuthWebViewClient", "[EVM] Fixed Enoki Bearer header");
                    }
                    conn.setRequestProperty(key, val);
                }

                conn.connect();
                int code = conn.getResponseCode();
                android.util.Log.i("AuthWebViewClient", "[EVM] Enoki response: " + code + " " + conn.getResponseMessage());
                String contentType = conn.getContentType();
                String mimeType = contentType != null ? contentType.split(";")[0].trim() : "application/json";
                String encoding = "utf-8";

                InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                // Log first bytes of response for debugging
                if (stream != null) {
                    java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                    byte[] tmp = new byte[4096];
                    int len;
                    while ((len = stream.read(tmp)) != -1) buf.write(tmp, 0, len);
                    byte[] body = buf.toByteArray();
                    String bodyStr = new String(body, "UTF-8");
                    android.util.Log.i("AuthWebViewClient", "[EVM] Enoki body: " + bodyStr.substring(0, Math.min(300, bodyStr.length())));
                    stream = new java.io.ByteArrayInputStream(body);
                }
                Map<String, String> responseHeaders = new HashMap<>();
                for (Map.Entry<String, java.util.List<String>> h : conn.getHeaderFields().entrySet()) {
                    if (h.getKey() != null && !h.getValue().isEmpty()) {
                        responseHeaders.put(h.getKey(), h.getValue().get(0));
                    }
                }
                // Allow CORS
                responseHeaders.put("Access-Control-Allow-Origin", "*");

                return new WebResourceResponse(mimeType, encoding, code,
                    conn.getResponseMessage() != null ? conn.getResponseMessage() : "OK",
                    responseHeaders, stream);
            } catch (Exception e) {
                android.util.Log.e("AuthWebViewClient", "[EVM] Enoki proxy error: " + e.getMessage());
            }
        }

        return super.shouldInterceptRequest(view, request);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();

        // Intercept logout and strip the invalid post_logout_redirect_uri
        if (url.contains("oauth2/logout")) {
            Uri uri = Uri.parse(url);
            Uri.Builder fixed = uri.buildUpon().clearQuery();
            for (String param : uri.getQueryParameterNames()) {
                if (!param.equals("post_logout_redirect_uri")) {
                    fixed.appendQueryParameter(param, uri.getQueryParameter(param));
                }
            }
            String fixedUrl = fixed.build().toString();
            android.util.Log.i("AuthWebViewClient", "[EVM] Logout — stripped post_logout_redirect_uri");
            // Load the logout URL without the invalid redirect
            view.loadUrl(fixedUrl);
            return true;
        }

        if (url.contains("oauth2/authorize") && url.contains("redirect_uri=")) {
            // Rewrite redirect_uri: localhost/callback → evefrontier://callback
            String encodedLocal    = Uri.encode(MainActivity.LOCAL_CALLBACK);
            String encodedDeepLink = Uri.encode(MainActivity.EVEFRONTIER_CALLBACK);
            String fixed = url.replace(encodedLocal, encodedDeepLink);

            // Rewrite auth server if build server differs from selected
            String selectedAuth = ServerConfig.getAuthUrl(view.getContext());
            String buildAuth = ServerConfig.getBuildAuthUrl();
            if (!selectedAuth.equals(buildAuth)) {
                fixed = fixed.replace(buildAuth, selectedAuth);
            }

            // Rewrite client_id if build differs from selected
            String selectedClientId = ServerConfig.getClientId(view.getContext());
            String buildClientId = ServerConfig.getBuildClientId();
            if (!selectedClientId.equals(buildClientId)) {
                fixed = fixed.replace(buildClientId, selectedClientId);
            }

            android.util.Log.i("AuthWebViewClient", "[EVM] Intercepted auth URL, launching LoginActivity");
            view.stopLoading();

            Intent authIntent = new Intent(view.getContext(), LoginActivity.class);
            authIntent.putExtra("auth_url", fixed);
            view.getContext().startActivity(authIntent);
            return true;
        }

        // After logout CCP may redirect to evefrontier.com — send back to localhost
        if (url.contains("evefrontier.com") && !url.contains("oauth2/")) {
            android.util.Log.i("AuthWebViewClient", "[EVM] Post-logout redirect, returning to localhost");
            view.loadUrl("https://localhost");
            return true;
        }

        // Handle evefrontier://callback deep link — this should now be routed to TokenActivity
        // by the Android OS, but intercept here as a safety net.
        if (url.startsWith(MainActivity.EVEFRONTIER_CALLBACK)) {
            android.util.Log.i("AuthWebViewClient", "[EVM] Caught evefrontier://callback in WebView (unexpected)");
            Uri uri = Uri.parse(url);
            Uri.Builder builder = Uri.parse(MainActivity.LOCAL_CALLBACK).buildUpon();
            for (String param : uri.getQueryParameterNames()) {
                builder.appendQueryParameter(param, uri.getQueryParameter(param));
            }
            String callbackUrl = builder.build().toString();
            android.util.Log.i("AuthWebViewClient", "[EVM] Loading callback: " + callbackUrl);
            view.loadUrl(callbackUrl);
            return true;
        }

        return super.shouldOverrideUrlLoading(view, request);
    }
}
