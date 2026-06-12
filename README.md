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

#### Securing the Firebase API key (GCP)

A Firebase **Android** API key is **not a secret** — it ships inside every distributed APK and is
extractable. Real protection comes from **restricting it in Google Cloud**, not from keeping it private.
So treat the key as public, but lock it down in the console:

In [Google Cloud Console](https://console.cloud.google.com) → project **`pushit-dcf8a`** →
**APIs & Services → Credentials** → open the *“Android key (auto created by Firebase)”* (the one in your
`google-services.json`):

1. **Application restrictions → Android apps** — add `applicationId` + the signing **SHA-1**:
   - Package: `com.foxugly.pushit_app`
   - Get a SHA-1 with `keytool`:
     - debug: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`
     - release: `keytool -list -v -keystore <release.jks> -alias <alias>`
   - ⚠️ The **debug** keystore SHA-1 is **per developer machine** (each `~/.android/debug.keystore` is unique)
     — add one entry per dev machine. The **release** SHA-1 is what matters for the published app, so add it
     once release signing is set up (otherwise FCM breaks in production).
2. **API restrictions → Restrict key** — allow only what FCM needs:
   **Firebase Installations API** (issues the FCM token) + **Firebase Cloud Messaging API** (+ Cloud Messaging).
3. **Save.** Rotating the key is unnecessary if it never reached a public remote — restriction is the fix.

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
