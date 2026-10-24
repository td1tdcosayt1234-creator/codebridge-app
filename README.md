# CodeBridge (Android)

Server-based Android client of the CodeBridge dashboard (`dash.roun.sryze.cc`).

## Features
- **Tasks / History / Profile / Earn** — the dashboard pages inside a tuned WebView
- Native top app bar, vector bottom navigation with pill highlight, URL-synced tabs
- Pull-to-refresh, branded splash screen
- **Earn is mobile-only**: the web dashboard refuses earn calls (server UA gate) —
  this app appends `CodeBridgeApp/1.0` to its user agent, unlocking `/api/earn`
- Earn flow = native **AdMob rewarded ad** (via a `window.open` hook on the earn
  page's sponsor link) + the dashboard's own timer → `POST /api/earn` verification

## Build

```powershell
# requires: Android SDK 34, JAVA_HOME with JDK 17
.\build.ps1        # gradle assembleRelease (signed) -> ..\codebridge apk\codebridge.apk
```

Signed with `keys/codebridge.keystore` (gitignored — keep a private backup).
Latest release APK: see this repo's **Releases**.

## AdMob
| Item | Value |
|------|-------|
| App ID | `ca-app-pub-3123261757380037~5789907866` |
| Rewarded unit | `ca-app-pub-3123261757380037/5119738300` |

Currently `USE_TEST = true` in `MainActivity.java` (Google mandates test ads until
the AdMob app review finishes). After approval flip to `false`, bump
`versionCode`/`versionName` in `app/build.gradle.kts`, rebuild, re-release.

## Toolchain pin
Gradle wrapper **8.10.2** + AGP **8.5.2** + `play-services-ads:23.6.0`
(choco Gradle 9.x is NOT compatible with AGP 8.x).
