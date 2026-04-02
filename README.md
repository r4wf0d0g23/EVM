# EVE Vault Mobile

Android mobile wallet for **EVE Frontier** — a Capacitor wrapper around the official [EVE Vault](https://github.com/evefrontier/evevault) web app with a native Android OAuth layer using Chrome Custom Tabs.

> **Hackathon submission** — EVE Frontier Builder Week 1 / March 2026  
> Category: Tooling & Infrastructure

---

## What It Does

EVE Vault is the official Sui wallet and authentication layer for EVE Frontier. It exists as a Chrome browser extension and web app — but **not as a mobile app**.

This project wraps the EVE Vault web app in a native Android shell, adding:

- **Native OAuth login** via Chrome Custom Tab — opens a secure system browser for CCP authentication
- **Deep-link callback handling** (`evefrontier://callback`) — catches the OAuth return and injects the `id_token` back into the WebView via JavaScript bridge
- **Secure token storage** via `@capacitor/preferences` (Android Keystore-backed)
- **Full wallet UI** — balance, send SUI, transaction history, all from the official EVE Vault web app
- **CradleOS integration** — the mobile wallet communicates with CradleOS dApps via `window.postMessage`, the same protocol the Chrome extension uses
- **Server toggle** — switch between Stillness (production) and Utopia (test) servers in-app
- **In-app update notifications** — banner appears when a newer version is available on GitHub

---

## Current Status

| Feature | Status |
|---|---|
| App loads and displays EVE Vault UI | ✅ Working |
| Chrome Custom Tab OAuth launch | ✅ Working |
| Auth request reaches CCP's FusionAuth server | ✅ Working |
| `evefrontier://callback` deep-link registered in app | ✅ Working |
| Server toggle (Stillness / Utopia) | ✅ Working |
| In-app update checker | ✅ Working |
| APK builds clean (~8.3MB debug) | ✅ Working |
| OAuth redirect completes | ⏳ **Pending CCP config** |
| Token injected into WebView | ⏳ Depends on redirect |
| Full wallet functionality post-login | ⏳ Depends on redirect |

**Remaining blocker:** CCP needs to register `evefrontier://callback` as a valid redirect URI on their FusionAuth instances:
- Stillness production: client `583ebc6d-abd8-4057-8c77-78405628e42d` on `auth.evefrontier.com`
- Utopia test: client `00d3ce5b-4cab-4970-a9dc-e122fc1d30ce` on `test.auth.evefrontier.com`

The auth request reaches their server successfully — the Chrome Custom Tab opens the CCP login page. The only failure point is the redirect URI validation on CCP's side.

---

## Architecture

```
Android App (com.evefrontier.vault)
│
├── MainActivity.java       — Capacitor host + JS bridge + token injection + server toggle
├── LoginActivity.kt        — Launches Chrome Custom Tab for CCP OAuth
├── TokenActivity.kt        — Catches evefrontier://callback deep link, forwards token
├── AuthWebViewClient.java  — Intercepts auth requests in WebView
├── ServerConfig.java       — Stillness/Utopia server configuration
│
└── WebView (Capacitor)
    └── EVE Vault web app   — Official evevault/apps/web build
```

**Auth flow:**
1. User taps LOGIN → `MainActivity` JS intercepts → launches `LoginActivity`
2. `LoginActivity` → Chrome Custom Tab → CCP FusionAuth login page
3. User authenticates → CCP redirects to `evefrontier://callback?code=...`
4. Android routes deep link to `TokenActivity`
5. `TokenActivity` extracts params, sends to `MainActivity` via Intent
6. `MainActivity` injects token into WebView via JS bridge
7. WebView processes token → zkLogin flow continues normally

---

## Download & Install

Download `EVE-Vault-debug.apk` from this repo and sideload:

1. Enable **Settings → Developer Options → Install unknown apps**
2. Tap the downloaded APK file

Or build from source (see below).

---

## Build Instructions

### Prerequisites
- Java 21+
- Node.js 22+
- Android SDK (build-tools 34+)

### Build APK
```bash
git clone https://github.com/r4wf0d0g23/EVM.git
cd EVM
npm install && npm install -D typescript

# Stub the web dist (assets are already committed)
mkdir -p ../evevault/apps/web/dist
cp -r android/app/src/main/assets/public/. ../evevault/apps/web/dist/

# Sync Capacitor
npx cap sync android

# Set SDK path
echo "sdk.dir=/path/to/android/sdk" > android/local.properties

# Build
cd android && ./gradlew assembleDebug
# Output: android/app/build/outputs/apk/debug/app-debug.apk
```

---

## Releases

New releases are published to [GitHub Releases](https://github.com/r4wf0d0g23/EVM/releases). The app checks for updates on launch and shows a banner if a newer version is available.

CI automatically builds and attaches the APK when a new release is published.

---

## Project Structure

```
EVM/
├── android/
│   └── app/src/main/
│       ├── AndroidManifest.xml         — evefrontier://callback intent filter
│       ├── java/com/evefrontier/vault/
│       │   ├── MainActivity.java       — Entry point, JS bridge, server toggle
│       │   ├── LoginActivity.kt        — Chrome Custom Tab OAuth launcher
│       │   ├── TokenActivity.kt        — OAuth callback handler
│       │   ├── AuthWebViewClient.java  — WebView request interceptor
│       │   └── ServerConfig.java       — Stillness/Utopia config
│       └── res/raw/
│           └── fusionauth_config.json
├── .github/workflows/
│   └── build-apk.yml   — CI: builds APK on push, attaches to releases
├── capacitor.config.ts
├── package.json
├── EVE-Vault-debug.apk — Latest debug build
└── README.md
```

---

## Server Configuration

| Server | Auth URL | Client ID |
|---|---|---|
| Stillness (production) | `auth.evefrontier.com` | `583ebc6d-abd8-4057-8c77-78405628e42d` |
| Utopia (test) | `test.auth.evefrontier.com` | `00d3ce5b-4cab-4970-a9dc-e122fc1d30ce` |

Toggle between servers using the pill buttons in the app. Selection persists across restarts.

---

## Dependencies

- **Capacitor 8** — Web-to-native bridge
- **`@capacitor/preferences`** — Secure storage (Android Keystore-backed)
- **`androidx.browser`** — Chrome Custom Tabs for OAuth
- **`androidx.webkit`** — WebView compatibility
- EVE Vault web app (`evevault/apps/web`) — Official CCP wallet UI

---

## Roadmap

- [ ] CCP registers `evefrontier://callback` → login completes end-to-end
- [ ] Enoki configured with Utopia client ID for zkLogin
- [ ] Release build + signing
- [ ] iOS wrapper
- [ ] Biometric lock for wallet access
- [ ] Push notifications for incoming transactions
