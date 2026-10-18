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

`AuthInterceptor` (Ktor client plugin) attaches `Bearer` token to requests for the API host only (never another origin), except `/auth/*`. On 401, `apiCall` calls `AuthInterceptor.refreshIfNeeded()` (single refresh under `refreshMutex`, with a guard that skips re-refreshing when a concurrent call already rotated the token) and then **replays the original request lambda** — preserving the verb and body, with the fresh token re-attached. If the server rejects the refresh token (401/403), tokens are cleared and `onAuthFailure` navigates to Login; a transport error or 5xx on refresh keeps the session (offline must not sign out). Logout unlinks the device from its apps (`devices/unlink-app/`, best effort) before revoking the refresh token. Transport failures (timeout/offline) surface as `NetworkException`, distinct from HTTP `ApiException`.

### Startup Flow (in App.kt)

1. Check `TokenStorage` for valid access token → `NotificationList`
2. Else try refresh token → on success `NotificationList`, on failure `Login`
3. After reaching `NotificationList`, `DeviceLinkManager.tryLink()` registers the FCM token with the backend via `POST /devices/link/` (requires app token from QR scan)

### Backend API

Base URL: `https://pushit-api.foxugly.com/api/v1/` (prod-only). Passed into `PushItApi` from `App()`.

- JWT auth: login, register, refresh, logout, me
- Device registration: `POST /devices/link/` with `X-App-Token` header
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

## Gradle

Uses version catalogs (`gradle/libs.versions.toml`). Configuration cache and build caching enabled. Firebase BOM is used for Android Firebase dependency management.
