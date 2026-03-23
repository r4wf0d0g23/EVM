package com.evefrontier.vault;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

/**
 * CradleOS dApp browser with injected EVE Vault wallet bridge.
 *
 * Injects:
 * 1. A postMessage bridge that auto-responds to dapp_login requests with the stored id_token
 * 2. A minimal Wallet Standard "Eve Vault" registration so CradleOS detects the wallet
 */
public class CradleOSActivity extends AppCompatActivity {

    public static final String EXTRA_ID_TOKEN = "id_token";
    public static final String EXTRA_WALLET_ADDRESS = "wallet_address";

    // Wallet Standard icon (reuse EVE Vault icon placeholder)
    private static final String WALLET_ICON =
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAABHNCSVQICAgIfAhkiAAAAAlwSFlzAAAA7AAAAOwBeShxvQAAABl0RVh0U29mdHdhcmUAd3d3Lmlua3NjYXBlLm9yZ5vuPBoAAAMzSURBVFiF7ZdNaBNBFMd/m03SJqaNpFpRqSD4UdSD4EEPevCgF/EiCIIHQcGDePBLPHgQv0APInhQTx48iKIn8SCC4EFBEDyIB8GDgp+XSjVNm6bJJpvdzczszsxm3GS3TbYBhYJmYWFJHLJ/3pu3897MvDcIIYRoMLlv4RzwDTgG3ALmgA5gJ3ALmAJagBuAA2wBrcDvgKuqBpjNZrNQLuec87uu6/mu6/qe5/lVVfXm5+f/27f+gkajUYVhGIZhWIZhGIZhGIZhGIZhFBzHKTiO4ziO4ziOs/BbsixrWl1dnW/btnVd130cx3kA+H5Vld5JkiQJIQghSikkSZIkyb/o5oUQQogQQogQQogQQogQQojwLyFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCFECCH8CyFECCFECCFECCFECCFECCFECCH8AJTEZtqkAAAAASUVORK5CYII=";

    private static final String CRADLEOS_URL =
        "https://r4wf0d0g23.github.io/Reality_Anchor_Eve_Frontier_Hackathon_2026/";

    private WebView webView;
    private String idToken;
    private String walletAddress;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        idToken = getIntent().getStringExtra(EXTRA_ID_TOKEN);
        walletAddress = getIntent().getStringExtra(EXTRA_WALLET_ADDRESS);

        android.util.Log.i("CradleOSActivity", "[EVM] Opening CradleOS, hasToken=" + (idToken != null)
            + ", address=" + walletAddress);

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        // Inject bridge and Wallet Standard mock before page JS runs
        webView.addJavascriptInterface(new CradleOSBridge(), "EVMBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Inject immediately
                view.evaluateJavascript(buildInjectionScript(), null);
                // Re-inject after React mounts (500ms, 1.5s, 3s)
                view.postDelayed(() -> view.evaluateJavascript(buildReRegisterScript(), null), 500);
                view.postDelayed(() -> view.evaluateJavascript(buildReRegisterScript(), null), 1500);
                view.postDelayed(() -> view.evaluateJavascript(buildReRegisterScript(), null), 3000);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Stay within CradleOS domain
                if (url.startsWith("https://r4wf0d0g23.github.io") ||
                    url.startsWith("https://fullnode") ||
                    url.startsWith("https://graphql")) {
                    return false;
                }
                return true; // block external navigation
            }
        });

        setContentView(webView);
        webView.loadUrl(CRADLEOS_URL);
    }

    private String buildInjectionScript() {
        String safeToken = idToken != null
            ? idToken.replace("\\", "\\\\").replace("'", "\\'")
            : "";
        String safeAddr = walletAddress != null
            ? walletAddress.replace("\\", "\\\\").replace("'", "\\'")
            : "";

        return "(function() {"
            + "  if (window.__evmBridgeInstalled) return;"
            + "  window.__evmBridgeInstalled = true;"
            + "  console.log('[EVM] CradleOS bridge installed');"
            + ""
            + "  var STORED_TOKEN = '" + safeToken + "';"
            + "  var WALLET_ADDR  = '" + safeAddr + "';"
            + ""
            // ── postMessage bridge: respond to dapp_login ────────────────────
            + "  window.addEventListener('message', function(e) {"
            + "    var d = e.data || {};"
            + "    if (d.__to !== 'Eve Vault') return;"
            + "    console.log('[EVM] Received dapp_login request:', d.id);"
            // Try bridge fallback if token not in JS
            + "    if (!STORED_TOKEN && window.EVMBridge) { try { STORED_TOKEN = window.EVMBridge.getIdToken(); } catch(e) {} }"
            + "    if (STORED_TOKEN) {"
            + "      setTimeout(function() {"
            + "        window.postMessage({"
            + "          __from: 'Eve Vault',"
            + "          type: 'auth_success',"
            + "          id: d.id,"
            + "          token: { id_token: STORED_TOKEN }"
            + "        }, '*');"
            + "        console.log('[EVM] Sent auth_success to CradleOS');"
            + "      }, 100);"
            + "    } else {"
            + "      console.log('[EVM] No token — triggering native auth');"
            + "      if (window.EVMBridge) window.EVMBridge.requestAuth();"
            + "    }"
            + "  });"
            + ""
            // ── Wallet Standard: register mock Eve Vault wallet ──────────────
            + "  (function registerEveVaultWallet() {"
            + "    if (typeof window === 'undefined') return;"
            + "    var walletEvents = {};"
            + "    function emit(event) {"
            + "      var listeners = walletEvents[event] || [];"
            + "      listeners.forEach(function(fn) { try { fn(); } catch(e) {} });"
            + "    }"
            + "    var wallet = window.__evmWallet = {"
            + "      version: '1.0.0',"
            + "      name: 'Eve Vault',"
            + "      icon: '" + WALLET_ICON + "',"
            + "      chains: ['sui:testnet', 'sui:devnet'],"
            + "      accounts: [],"
            + "      features: {"
            + "        'standard:connect': {"
            + "          version: '1.0.0',"
            + "          connect: function() {"
            + "            console.log('[EVM] Wallet connect called');"
            // Try to get address from bridge (synchronous call)
            + "            var addr = WALLET_ADDR;"
            + "            try { if (!addr && window.EVMBridge) addr = window.EVMBridge.getWalletAddress(); } catch(e) {}"
            + "            console.log('[EVM] connect address: ' + addr);"
            + "            if (addr) {"
            + "              var acct = {"
            + "                address: addr,"
            + "                publicKey: new Uint8Array(32),"
            + "                chains: ['sui:testnet'],"
            + "                features: ['standard:connect','standard:disconnect','standard:events','sui:signTransaction','sui:signAndExecuteTransaction']"
            + "              };"
            + "              wallet.accounts = [acct];"
            + "              WALLET_ADDR = addr;"
            + "              emit('change');"
            + "              return Promise.resolve({ accounts: [acct] });"
            + "            }"
            + "            return Promise.resolve({ accounts: [] });"
            + "          }"
            + "        },"
            + "        'standard:disconnect': {"
            + "          version: '1.0.0',"
            + "          disconnect: function() {"
            + "            wallet.accounts = [];"
            + "            emit('change');"
            + "            return Promise.resolve();"
            + "          }"
            + "        },"
            + "        'standard:events': {"
            + "          version: '1.0.0',"
            + "          on: function(event, fn) {"
            + "            if (!walletEvents[event]) walletEvents[event] = [];"
            + "            walletEvents[event].push(fn);"
            + "            return function() {"
            + "              walletEvents[event] = (walletEvents[event] || []).filter(function(f) { return f !== fn; });"
            + "            };"
            + "          }"
            + "        },"
            + "        'sui:signTransaction': {"
            + "          version: '1.0.0',"
            + "          signTransaction: function() { return Promise.reject(new Error('Signing not supported in mobile')); }"
            + "        },"
            + "        'sui:signAndExecuteTransaction': {"
            + "          version: '1.0.0',"
            + "          signAndExecuteTransaction: function() { return Promise.reject(new Error('Signing not supported in mobile')); }"
            + "        }"
            + "      }"
            + "    };"
            // Wallet Standard registration — must match exact spec from @wallet-standard/wallet
            // The callback receives { register } and calls register(wallet)
            + "    var callback = function(api) { api.register(wallet); console.log('[EVM] Registered Eve Vault wallet'); };"
            // 1. Listen for app-ready (app fires this when it's ready to accept wallets)
            + "    window.addEventListener('wallet-standard:app-ready', function(e) {"
            + "      if (e.detail && typeof e.detail.register === 'function') {"
            + "        callback(e.detail);"
            + "      }"
            + "    });"
            // 2. Dispatch register-wallet event (app listens for this if already ready)
            + "    try {"
            + "      var evt = new Event('wallet-standard:register-wallet', { bubbles: false, cancelable: false, composed: false });"
            + "      Object.defineProperty(evt, 'detail', { value: callback, enumerable: true });"
            + "      window.dispatchEvent(evt);"
            + "      console.log('[EVM] Dispatched wallet-standard:register-wallet');"
            + "    } catch(e) { console.log('[EVM] dispatch error: ' + e); }"
            + "  })();"
            + "})();";
    }

    class CradleOSBridge {
        @JavascriptInterface
        public String getWalletAddress() {
            android.util.Log.i("CradleOSActivity", "[EVM] getWalletAddress: " + walletAddress);
            return walletAddress != null ? walletAddress : "";
        }

        @JavascriptInterface
        public String getIdToken() {
            return idToken != null ? idToken : "";
        }

        @JavascriptInterface
        public void requestAuth() {
            android.util.Log.i("CradleOSActivity", "[EVM] requestAuth called from CradleOS");
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /** Re-run wallet registration after React has had time to mount */
    private String buildReRegisterScript() {
        String safeAddr = walletAddress != null
            ? walletAddress.replace("\\", "\\\\").replace("'", "\\'") : "";
        return "(function() {"
            + "  if (!window.__evmWallet) return;"
            // Update address in case it changed
            + "  window.__evmWallet.accounts = window.__evmWallet.accounts.length === 0 && '" + safeAddr + "' ? [{"
            + "    address: '" + safeAddr + "',"
            + "    publicKey: new Uint8Array(32),"
            + "    chains: ['sui:testnet'],"
            + "    features: ['standard:connect','standard:disconnect','standard:events','sui:signTransaction','sui:signAndExecuteTransaction']"
            + "  }] : window.__evmWallet.accounts;"
            // Re-dispatch using correct Wallet Standard event format
            + "  try {"
            + "    var cb = function(api) { api.register(window.__evmWallet); };"
            + "    var evt = new Event('wallet-standard:register-wallet', { bubbles: false, cancelable: false, composed: false });"
            + "    Object.defineProperty(evt, 'detail', { value: cb, enumerable: true });"
            + "    window.dispatchEvent(evt);"
            + "  } catch(e) {}"
            + "  console.log('[EVM] Re-registered Eve Vault wallet, addr=" + safeAddr.substring(0, Math.min(10, safeAddr.length())) + "...');"
            + "})();";
    }
}
