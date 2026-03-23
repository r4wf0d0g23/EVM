# EVE Vault Mobile

Android mobile wallet for **EVE Frontier** — built as a Capacitor wrapper around the official [EVE Vault](https://github.com/evefrontier/evevault) web app with a native Android OAuth layer via the FusionAuth Android SDK.

> **Hackathon submission** — EVE Frontier Builder Week 1 / March 2026  
> Category: Tooling & Infrastructure

---

## What It Does

EVE Vault is the official Sui wallet and authentication layer for EVE Frontier. It exists as a Chrome browser extension and web app — but **not as a mobile app**.

This project wraps the EVE Vault web app in a native Android shell, adding:

- **Native OAuth login** via the FusionAuth Android SDK — opens a system browser (Chrome Custom Tab) for authentication, not a WebView redirect
- **Deep-link callback** handling (`evefrontier://callback`) — catches the auth return and injects the `id_token` back into the WebView via JavaScript bridge
- **Secure token storage** via `@capacitor/preferences` (Android Keystore-backed)
- **Full wallet UI** — balance, send SUI, transaction history, all from the official EVE Vault web app
- **CradleOS integration** — the mobile wallet communicates with CradleOS dApps via `window.postMessage`, the same protocol the Chrome extension uses

---

## Current Status

| Feature | Status |
|---|---|
| App loads and displays EVE Vault UI | ✅ Working |
| FusionAuth credentials baked into build | ✅ Working |
| Auth request reaches CCP's FusionAuth server | ✅ Working |
| Native LoginActivity launches | ✅ Working |
| APK builds clean (8.6MB debug) | ✅ Working |
| OAuth redirect completes | ⏳ **Pending CCP config** |
| Token injected into WebView | ⏳ Depends on redirect |
| Full wallet functionality post-login | ⏳ Depends on redirect |

**One remaining blocker:** CCP needs to register `evefrontier://callback` as a valid redirect URI for OAuth client `00d3ce5b-4cab-4970-a9dc-e122fc1d30ce` on their FusionAuth test instance. The auth request reaches their server successfully but returns `invalid_redirect_uri` for the current redirect of `https://localhost/callback`.

---

## Architecture

```
Android App (com.evefrontier.vault)
│
├── MainActivity.java           — Capacitor host + JS bridge + token injection
├── LoginActivity.kt            — FusionAuth SDK: launches system browser OAuth
├── TokenActivity.kt            — Catches evefrontier://callback, extracts id_token
│
└── WebView (Capacitor)
    └── EVE Vault web app       — Official evevault/apps/web build
        ├── Wallet UI
        ├── Transaction history
        └── Send SUI
```

**Auth flow:**
1. User taps LOGIN → `MainActivity` intercepts → launches `LoginActivity`
2. `LoginActivity` → FusionAuth SDK → Chrome Custom Tab → CCP auth page
3. User authenticates → FusionAuth redirects to `evefrontier://callback`
4. Android catches deep link → `TokenActivity` → extracts `id_token`
5. `TokenActivity` → starts `MainActivity` with token extras
6. `MainActivity` → injects `window.postMessage({type:'auth_success', token:{id_token}})` into WebView
7. WebView receives token → zkLogin flow continues normally

**CradleOS dApp integration:**
The injected `postMessage` format matches what the EVE Vault Chrome extension emits (`__from: 'Eve Vault'`), so CradleOS and other dApps that integrate with EVE Vault work without modification.

---

## Build Instructions

### Prerequisites
- Android Studio (or command-line Android SDK)
- Java 21+
- Node.js 22+
- The `evevault` repo alongside this one (or a pre-built web dist)

### One-time setup
```bash
# Clone alongside evevault
git clone https://github.com/evefrontier/evevault.git
git clone https://github.com/r4wf0d0g23/EVM.git eve-vault-mobile

# Build the EVE Vault web app with auth credentials
cd evevault/apps/web
VITE_FUSION_SERVER_URL=https://test.auth.evefrontier.com \
VITE_FUSIONAUTH_CLIENT_ID=<client-id> \
VITE_ENOKI_API_KEY=<enoki-key> \
npx vite build

# Update capacitor.config.ts if needed
# webDir: '../evevault/apps/web/dist'

# Install Capacitor dependencies
cd ../../eve-vault-mobile
npm install

# Sync web assets into Android
npx cap sync android
```

### Build APK
```bash
cd android
./gradlew assembleDebug
# Output: android/app/build/outputs/apk/debug/app-debug.apk
```

### Install on device
```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

Or download `EVE-Vault-debug.apk` from this repo and sideload:
- Enable **Settings → Developer Options → Install unknown apps**
- Tap the downloaded APK

---

## Google OAuth Setup (for production)

CCP's EVE Frontier auth uses **FusionAuth** with **Google sign-in** via zkLogin. For the OAuth callback to work on Android:

1. CCP must register `evefrontier://callback` as an allowed redirect URI for the OAuth client
2. The FusionAuth Android SDK handles the rest via AppAuth-Android (Chrome Custom Tab)

For development/testing, the redirect URI can also be registered as:
- `com.evefrontier.vault:/oauth2redirect` (alternative Android scheme)

---

## Configuration

### `android/app/src/main/res/raw/fusionauth_config.json`
```json
{
  "fusionAuthUrl": "https://test.auth.evefrontier.com",
  "clientId": "00d3ce5b-4cab-4970-a9dc-e122fc1d30ce"
}
```

### `capacitor.config.ts`
```typescript
const config: CapacitorConfig = {
  appId: 'com.evefrontier.vault',
  appName: 'EVE Vault',
  webDir: '../evevault/apps/web/dist',
  // ...
};
```

---

## Project Structure

```
eve-vault-mobile/
├── android/                    — Android project (Capacitor-generated)
│   └── app/src/main/
│       ├── AndroidManifest.xml — Deep-link intent filter for evefrontier://callback
│       ├── java/com/evefrontier/vault/
│       │   ├── MainActivity.java   — Entry point, JS bridge, token handler
│       │   ├── LoginActivity.kt    — FusionAuth OAuth launcher
│       │   └── TokenActivity.kt    — OAuth callback handler
│       └── res/raw/
│           └── fusionauth_config.json
├── capacitor.config.ts         — Capacitor configuration
├── package.json
├── EVE-Vault-debug.apk         — Latest debug build
└── README.md
```

---

## Dependencies

- **Capacitor 8** — Web-to-native bridge
- **`@capacitor/preferences`** — Secure storage (Android Keystore)
- **`fusionauth-android-sdk:0.2.0`** — FusionAuth OAuth (wraps AppAuth-Android)
- **`kotlinx-coroutines-android`** — Async auth handling
- EVE Vault web app (`evevault/apps/web`) — Official CCP wallet UI

---

## Relation to EVE Vault

This project does **not** modify the EVE Vault source. It wraps the official `apps/web` build as-is. The web app's existing authentication, wallet management, and transaction signing all work unchanged — we only add:

1. A native Android container
2. A native OAuth login that bypasses the WebView redirect limitation
3. A `postMessage` bridge to pass the auth token from native to web

---

## Roadmap

- [ ] CCP registers `evefrontier://callback` redirect URI → login completes end-to-end
- [ ] Release build + signing (Play Store submission)
- [ ] iOS wrapper (same approach, Capacitor supports both)
- [ ] Biometric lock for wallet access
- [ ] Push notification support for incoming transactions
- [ ] WalletConnect integration for third-party dApp connections
