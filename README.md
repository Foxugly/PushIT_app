# PushIT App

Kotlin Multiplatform mobile client for PushIT Server — receives push notifications via Firebase Cloud Messaging.

> ⚠️ **Known issues & roadmap:** [`BACKLOG.md`](BACKLOG.md) tracks the prioritised audit backlog
> (P0 → P3). Notably: iOS push is not yet wired (FCM token bridge + `GoogleService-Info.plist`),
> and the Android `release` build is not yet signed/minified.

## Platforms

- Android (Jetpack Compose)
- iOS (SwiftUI + Compose Multiplatform)

## Setup

### Prerequisites

- Android Studio / IntelliJ IDEA with KMP plugin
- Xcode (for iOS)
- A running PushIT Server instance
- Firebase project (shared with the backend)

### Firebase Configuration

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select the same project used by your PushIT Server
3. **Android:** Download `google-services.json` and place it in `androidApp/`. This file is **git-ignored** (it holds the Firebase API key) — provide your own; it is never committed.
4. **iOS:** Download `GoogleService-Info.plist` and place it in `iosApp/iosApp/` (also git-ignored). Add the Firebase iOS SDK via CocoaPods or Swift Package Manager in Xcode.

### Backend URL

`MainActivity` selects the API base URL off `BuildConfig.DEBUG`: debug builds use `http://10.0.2.2:8000/api/v1/`
(Android emulator → host localhost), release builds use `https://pushit-api.foxugly.com/api/v1/`. Override by
editing the `DEV_API_BASE_URL` / `PROD_API_BASE_URL` constants in `MainActivity`, or pass a different `apiBaseUrl` into `App()`.

### Build & Run

**Android:**
```bash
./gradlew :androidApp:assembleDebug
# Or use the run configuration in your IDE
```

**iOS:**
Open `iosApp/` in Xcode and run.

**Tests:**
```bash
./gradlew :composeApp:testAndroidHostTest
```

## Usage

1. Register or login with your PushIT Server credentials
2. Scan the QR code containing your application's `apt_` token (found in the PushIT dashboard)
3. The app registers your device for push notifications automatically
4. Incoming notifications appear in the notification list

## Architecture

Single `composeApp` KMP module with shared Compose Multiplatform UI. Platform-specific code (FCM, QR scanner, encrypted storage) via `expect/actual` in `androidMain`/`iosMain`. State-driven navigation, Ktor networking, kotlinx.serialization.
