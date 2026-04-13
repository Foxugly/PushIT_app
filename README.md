# PushIT App

Kotlin Multiplatform mobile client for PushIT Server — receives push notifications via Firebase Cloud Messaging.

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
3. **Android:** Download `google-services.json` and place it in `composeApp/` (replacing the placeholder)
4. **iOS:** Download `GoogleService-Info.plist` and place it in `iosApp/iosApp/`. Add Firebase iOS SDK via CocoaPods or Swift Package Manager in Xcode.

### Backend URL

The default API base URL is `http://10.0.2.2:8000/api/v1/` (Android emulator to host localhost).

For physical devices or different environments, update the `baseUrl` parameter in `PushItApi.kt`.

### Build & Run

**Android:**
```bash
./gradlew :composeApp:assembleDebug
# Or use the run configuration in your IDE
```

**iOS:**
Open `iosApp/` in Xcode and run.

**Tests:**
```bash
./gradlew :composeApp:testDebugUnitTest
```

## Usage

1. Register or login with your PushIT Server credentials
2. Scan the QR code containing your application's `apt_` token (found in the PushIT dashboard)
3. The app registers your device for push notifications automatically
4. Incoming notifications appear in the notification list

## Architecture

Single `composeApp` KMP module with shared Compose Multiplatform UI. Platform-specific code (FCM, QR scanner, encrypted storage) via `expect/actual` in `androidMain`/`iosMain`. State-driven navigation, Ktor networking, kotlinx.serialization.
