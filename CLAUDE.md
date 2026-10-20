# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PushIT App — a Kotlin Multiplatform (KMP) mobile client for the PushIT Server (Django REST API managing push notifications via Firebase Cloud Messaging). Targets Android and iOS using Compose Multiplatform for shared UI.

Package: `com.foxugly.pushit_app`

> **Open work / known issues:** see [`BACKLOG.md`](BACKLOG.md) — prioritised audit backlog
> (P0 → P3) covering security, networking/API, architecture/UI, platform code, and build/tests.
> Check it before starting a task and tick items off as you go.

## Build Commands

```bash
# Android (the runnable app lives in the :androidApp module; :composeApp is a library)
./gradlew :androidApp:assembleDebug          # Build debug APK
./gradlew :androidApp:installDebug           # Build and install on connected device/emulator

# iOS — open iosApp/ in Xcode and run from there

# Tests (host/JVM — runs commonMain + androidMain tests, no Mac needed)
./gradlew :composeApp:testAndroidHostTest    # Unit tests
./gradlew :composeApp:allTests               # All targets incl. iOS (needs macOS for the iOS targets)
```

On Windows, use `.\gradlew.bat` instead of `./gradlew`.

### Testing on Windows (important)

The iOS test targets (`iosSimulatorArm64Test`, …) are **Kotlin/Native Apple targets** that can only
link/run on **macOS**. On Windows they fail. So:

- **Do NOT** run `:composeApp:test` (ambiguous) or `:composeApp:allTests` (drags in iOS) — both fail.
- **DO** run **`.\gradlew.bat :composeApp:testAndroidHostTest`** — it runs the whole `commonTest`
  suite on the JVM (no Mac, no emulator needed). This is the canonical local + CI test task.
- **In the IDE**: the green-arrow gutter on a `commonTest` class may default to an iOS target or
  `allTests` and fail. Either pick the **[android]** target when prompted, or run the test from the
  **Gradle tool window** → `composeApp → Tasks → verification → testAndroidHostTest`.

## Architecture

Two-module project (since the `androidApp`/`composeApp` split): **`:composeApp`** is a KMP **library**
(`com.android.kotlin.multiplatform.library`) holding all shared code + Compose UI for Android & iOS;
**`:androidApp`** is the runnable Android application (`MainActivity`). The iOS app is `iosApp/` (Xcode).

- **commonMain** — all shared code: Ktor networking, data models, repositories, Compose UI screens
- **androidMain** — Android platform specifics: `MainActivity`, `PushItFirebaseService`, CameraX + ML Kit QR scanner, `EncryptedSharedPreferences` token storage
- **iosMain** — iOS platform specifics: `MainViewController`, FCM token handling, QR scanner (manual entry fallback), NSUserDefaults token storage
- **iosApp/** — SwiftUI entry point that hosts the Compose view via `ComposeView` (UIViewControllerRepresentable)

### Dependency Wiring

No DI framework. Dependencies are manually constructed in `App.kt`:
`TokenStorage` → `TokenStorageStore` (the `TokenStore` interface seam, so the data layer is fakeable in tests) → `PushItApi` → `AuthRepository` / `NotificationRepository` / `DeviceLinkManager`.
Platform-specific `TokenStorage` and `FcmTokenProvider` are created by the platform entry point (`MainActivity` / `MainViewController`) and passed into `App()`. The app is prod-only: `MainActivity` passes the fixed prod `apiBaseUrl` (`https://pushit-api.foxugly.com/api/v1/`) and `enableHttpLogging = BuildConfig.DEBUG` into `App()`. There is no local/dev backend or runtime backend switch.

### Platform Boundaries (expect/actual)

Cross-platform abstractions use `expect/actual` declarations in `commonMain` with implementations in `androidMain` and `iosMain`:
- `TokenStorage` — encrypted key-value storage (JWT access/refresh tokens + app token)
- `QrScanner` — camera-based QR code scanning composable
- `FcmTokenProvider` — FCM token retrieval and observation

### Navigation

State-driven manual navigation in `App.kt` using `sealed class Screen` + `when`. No navigation library. Screen state is held via `mutableStateOf<Screen?>` — `null` means loading (startup auth check in progress).

### Auth Flow

`AuthInterceptor` (Ktor client plugin) attaches `Bearer` token to requests for the API host only (never another origin), except `/auth/*`. On 401, `apiCall` calls `AuthInterceptor.refreshIfNeeded()` (single refresh under `refreshMutex`, with a guard that skips re-refreshing when a concurrent call already rotated the token) and then **replays the original request lambda** — preserving the verb and body, with the fresh token re-attached. If the server rejects the refresh token (401/403), tokens are cleared, `onAuthFailure` calls `SessionViewModel.expireSession()` (Login + a discreet "session expired" note, **no error dialog**) and the failed call surfaces `SessionExpiredException`, which `errorText` maps to `null`; a transport error or 5xx on refresh keeps the session (offline must not sign out) — OPERATIONS.md §3.25. `login` and `refresh` themselves never go through the 401→refresh→replay path. Logout unlinks the device from its apps (`devices/unlink-app/`, best effort) before revoking the refresh token. Transport failures (timeout/offline) surface as `NetworkException`, distinct from HTTP `ApiException`.

### Startup Flow (in App.kt)

1. Check `TokenStorage` for valid access token → `NotificationList`
2. Else try refresh token → on success (or transient failure: offline/5xx, session kept) `NotificationList`; refused (401/403) → tokens cleared, `Login` with the discreet session-expired note
3. After reaching `NotificationList`, `DeviceLinkManager.syncAuthenticatedDevice()` identifies the device
   (`POST /devices/identify/`) and, if it is linked to no app yet and an enrolment code is stored, links it
   (`POST /devices/link/`). A stored code the server refuses (403 `app_token_*`) is forgotten, not retried.

### Backend API

Base URL: `https://pushit-api.foxugly.com/api/v1/` (prod-only). Passed into `PushItApi` from `App()`.

- JWT auth: login, refresh, logout, me (+ `auth/email/resend/`). No in-app registration: sign-up is
  web-only (captcha). Login 403 `email_not_verified` → "confirm your e-mail" + resend action; 429 → too many attempts.
- Logout (`auth/logout/`) needs a valid access token AND the refresh token in the body: on 401 the app
  refreshes first, then replays with the (rotated) refresh token so it really gets blacklisted.
- Device registration: `POST /devices/link/` with the enrolment code in the **`app_token` JSON body field**
  (no header). Only `apk_` enrolment codes are accepted by the scanner; an `apt_` value is a *send* token
  and is refused with an explanation.
- Enrolment-code refusals (`app_token_*` codes; 403 — older servers sent 401) are never a session
  problem: `apiCall` does **not** refresh/replay on them, and the UI shows a translated message.
- FCM token rotation: the last token registered with the server is persisted (`TokenStore.lastPushToken`).
  When FCM rotates it, identify (and link) send the new `push_token` plus `previous_push_token` = the old
  one; the server moves the existing Device to the new token, keeping all its app links and inbox history.
  The stored token is updated only after the server accepted it, and cleared on logout/account deletion.
- Notifications: paginated list, detail, stats (read-only)

All API calls return `Result<T>` via a shared `apiCall` helper.

### Key Libraries

- **Ktor** — HTTP client with `AuthInterceptor` plugin for JWT
- **kotlinx.serialization** — JSON serialization (`@Serializable` data classes in `Models.kt`)
- **multiplatform-settings** — cross-platform key-value storage (Android uses `EncryptedSharedPreferences`, iOS uses `NSUserDefaults`)
- **Compose Multiplatform 1.10.3** / **Kotlin 2.3.10**

## Crash reporting (Sentry)

Android only, via `io.sentry:sentry-android-core`, initialised in `androidApp/.../PushItApplication.kt`
(before `MainActivity.installCrashLogger()`, so the chain is our logger → Sentry → platform handler).

- **DSN source:** Gradle property `-PPUSHIT_SENTRY_DSN=…` or env var `PUSHIT_SENTRY_DSN` → `BuildConfig.SENTRY_DSN`.
  In CI it comes from the **repo secret `PUSHIT_SENTRY_DSN`** (release workflow). Empty ⇒ Sentry is **not
  initialised** (local, PR and debug builds without the variable). Never commit the DSN.
- **Sentry project to create (once, by an admin):** `pushit-app`, org **`foxugly-srl`**, region
  **`de.sentry.io`**, platform Android. Its DSN goes into the GitHub secret above.
- Privacy: `sendDefaultPii=false`, no screenshots / view hierarchy / tap breadcrumbs, `beforeSend` drops
  user + request, `beforeBreadcrumb` drops HTTP breadcrumbs and anything that looks like a JWT, Bearer
  header, enrolment code or token/password. Auto-init is disabled in the manifest.
- iOS: no crash reporting yet (sentry-kotlin-multiplatform needs the Cocoa SDK wired into the Xcode
  project, which does not build from Windows — see the iOS blockers in `BACKLOG.md`).

## Release pipeline

`.github/workflows/release.yml` (manual `workflow_dispatch` or tag `v*`) runs the host tests, builds
the **signed AAB** (`:androidApp:bundleRelease`), verifies it with `jarsigner` and uploads it (+
`mapping.txt`) as a workflow artifact. **No Play upload** — the operator uploads it in Play Console.
It fails up front, listing what is missing, unless these **repository secrets** exist:
`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`,
`GOOGLE_SERVICES_JSON` (raw or base64; the CI placeholder is refused), `PUSHIT_SENTRY_DSN`.
Signing reads `RELEASE_STORE_FILE` / `RELEASE_*` env vars (same names as `keystore.properties`).
Operator checklist: `PUBLICATION_PLAY_STORE.md`.

## Versioning

- **versionName** (Android) / **MARKETING_VERSION** (iOS): single source = `pushit.versionName` in
  `gradle.properties`. Bump it there for a release; keep `MARKETING_VERSION` in
  `iosApp/Configuration/Config.xcconfig` equal to it (or pass
  `MARKETING_VERSION=$(grep '^pushit.versionName=' gradle.properties | cut -d= -f2)` to `xcodebuild`).
- **versionCode** (Android) is derived in `androidApp/build.gradle.kts`: `PUSHIT_VERSION_CODE`
  (property/env override) → `GITHUB_RUN_NUMBER` (CI) → `git rev-list --count HEAD` (local) → 1.
  Only the AAB built by `.github/workflows/release.yml` is uploaded to Play, so the release workflow's
  run counter is what increases from one upload to the next. If a code ever collides (e.g. a manual
  upload), re-run the workflow or set `PUSHIT_VERSION_CODE` higher.
- **CURRENT_PROJECT_VERSION** (iOS build number) stays `1` in `Config.xcconfig` and is overridden at
  build time: `xcodebuild … CURRENT_PROJECT_VERSION=$GITHUB_RUN_NUMBER` (CI) or
  `agvtool new-version -all <n>` before archiving in Xcode. It must increase for every TestFlight upload.

## Gradle

Uses version catalogs (`gradle/libs.versions.toml`). Configuration cache and build caching enabled. Firebase BOM is used for Android Firebase dependency management.
