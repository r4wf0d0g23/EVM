# EVE Vault — Android Mobile App

A Capacitor-based Android wrapper for the [EVE Frontier Vault](https://github.com/evefrontier/evevault) web app. This project packages the official EVE Vault web wallet as a native Android APK, enabling seamless integration with CradleOS dApps and Google OAuth deep-link callbacks.

---

## What This Is

**EVE Vault** is the official wallet and identity layer for EVE Frontier, built on the Sui blockchain. This mobile project wraps the web app (`apps/web`) in a native Android shell using [Capacitor](https://capacitorjs.com/), providing:

- Native Android packaging (APK/AAB)
- OAuth deep-link callback handling (`evefrontier://callback`)
- Secure storage via `@capacitor/preferences`
- `window.postMessage` bridge for CradleOS dApp integration
- PWA-grade offline caching preserved from the web build

---

## Architecture

```
eve-vault-mobile/
├── capacitor.config.ts     # Capacitor configuration
├── android/                # Native Android project (Gradle)
│   └── app/src/main/
│       ├── AndroidManifest.xml    # Deep-link intent filters
│       └── res/values/strings.xml # App name + URL scheme
└── ../evevault/apps/web/dist/     # Web app build output (referenced via webDir)
```

**How it works:**
1. The web app (`../evevault/apps/web`) is built with Vite → `dist/`
2. Capacitor copies that `dist/` into `android/app/src/main/assets/public/`
3. Android WebView loads the app via `https://localhost`
4. Deep links, storage, and other native APIs are bridged via the Capacitor plugin layer

---

## Prerequisites

- **Node.js** ≥ 18
- **Bun** ≥ 1.3 (for building the web app)
- **Android Studio** (Hedgehog or newer) with:
  - Android SDK 34
  - Android Build Tools 34.x
  - NDK (optional)
- **JDK** 17+

---

## Build Instructions

### 1. Install dependencies

```bash
# In this directory
npm install

# In the evevault monorepo (web app deps)
cd ../evevault && bun install
```

### 2. Build the web app

```bash
npm run build:web
# Builds ../evevault/apps/web → dist/
```

Ensure `../evevault/.env` exists with at minimum:
```env
VITE_GOOGLE_CLIENT_ID=your-google-client-id
VITE_SUI_NETWORK=testnet
VITE_ZK_PROOF_URL=https://prover.mystenlabs.com/v1
```

### 3. Sync web assets to Android

```bash
npm run sync
# Copies dist/ into android/app/src/main/assets/public/
```

### 4. Open in Android Studio

```bash
npm run open:android
```

Then **Build → Generate Signed Bundle/APK** to produce a release APK.

### One-command build

```bash
npm run build
# = build:web + sync
```

---

## Deep Link Setup (Google OAuth)

EVE Vault uses Google's OAuth OIDC flow. For Android, you must configure the OAuth redirect URI to use a custom scheme:

**Redirect URI:** `evefrontier://callback`

### Google Cloud Console setup

1. Go to [Google Cloud Console → Credentials](https://console.cloud.google.com/apis/credentials)
2. Edit your OAuth 2.0 Client ID
3. Under **Authorized redirect URIs**, add: `evefrontier://callback`
4. Set `VITE_GOOGLE_CLIENT_ID` in your `.env` to this client's ID

### Android intent filter

`AndroidManifest.xml` already includes the deep-link intent filter:

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="evefrontier" android:host="callback" />
</intent-filter>
```

When Google redirects to `evefrontier://callback?code=...`, Android intercepts it and routes it back to the EVE Vault activity. The web app's OIDC client handles the token exchange.

---

## CradleOS dApp Integration (window.postMessage)

EVE Vault integrates with CradleOS dApps via the standard `window.postMessage` API:

```javascript
// From a CradleOS dApp (running in the same WebView or a sub-iframe):
window.postMessage({
  type: 'eve-vault:request',
  action: 'sign-transaction',
  payload: { /* Sui transaction bytes */ }
}, '*');

// EVE Vault responds:
window.addEventListener('message', (event) => {
  if (event.data.type === 'eve-vault:response') {
    const { signature, publicKey } = event.data.payload;
    // proceed with signed tx
  }
});
```

The Capacitor WebView allows cross-origin `postMessage` within the same activity context, making EVE Vault a natural wallet provider for embedded dApps.

---

## Hackathon Context

This project was built for the **EVE Frontier hackathon** as part of the CradleOS submission. It demonstrates:

- Mobile-first wallet UX for the EVE Frontier universe
- Sui zkLogin integration via Google OAuth on Android
- Native deep-link OAuth callback handling
- CradleOS composability: EVE Vault as a portable signing module

The web app (`../evevault`) is the canonical implementation. This wrapper adds native Android packaging with zero divergence from the official web codebase.

---

## Project Structure

```
android/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/public/          ← Capacitor copies web dist here
│       ├── java/com/evefrontier/vault/
│       │   └── MainActivity.java
│       └── res/
│           ├── values/strings.xml
│           └── mipmap-*/            ← App icons
├── build.gradle
├── capacitor.settings.gradle
└── variables.gradle
```

---

## License

See `../evevault/LICENSE`
