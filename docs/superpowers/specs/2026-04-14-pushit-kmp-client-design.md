# PushIT KMP Mobile Client — Design Spec

## Overview

Kotlin Multiplatform mobile client for the PushIT Server (Django REST API). Receives push notifications via Firebase Cloud Messaging, registers devices with the backend, and displays notifications in a list. Targets Android and iOS with shared Compose Multiplatform UI.

Package: `com.foxugly.pushit_app`
Backend base URL: `http://127.0.0.1:8000/api/v1/`

## Scope

**In scope:** User auth (JWT), FCM device registration, notification list/detail, QR-based app token onboarding, settings screen.

**Out of scope:** Notification creation, quiet periods, templates, admin features, web target, offline caching.

---

## 1. Project Structure

Single `composeApp` module. No separate shared library module.

```
composeApp/src/
├── commonMain/kotlin/com/foxugly/pushit_app/
│   ├── App.kt                              # Root composable, navigation state
│   ├── navigation/
│   │   └── Screen.kt                       # sealed class Screen
│   ├── data/
│   │   ├── api/
│   │   │   ├── PushItApi.kt                # Ktor client, all endpoint calls
│   │   │   ├── AuthInterceptor.kt          # Token injection + 401 refresh logic
│   │   │   └── Models.kt                   # @Serializable DTOs
│   │   ├── storage/
│   │   │   └── TokenStorage.kt             # expect class
│   │   └── repository/
│   │       ├── AuthRepository.kt
│   │       └── NotificationRepository.kt
│   ├── ui/
│   │   ├── login/LoginScreen.kt
│   │   ├── register/RegisterScreen.kt
│   │   ├── notifications/NotificationListScreen.kt
│   │   ├── notifications/NotificationDetailScreen.kt
│   │   ├── settings/SettingsScreen.kt
│   │   └── components/                     # Shared UI components
│   └── platform/
│       ├── QrScanner.kt                    # expect composable
│       ├── FcmTokenProvider.kt             # expect class
│       └── DeviceLinkManager.kt            # Coordinates link call
│
├── androidMain/kotlin/com/foxugly/pushit_app/
│   ├── platform/
│   │   ├── QrScanner.android.kt            # CameraX + ML Kit
│   │   ├── FcmTokenProvider.android.kt
│   │   └── TokenStorage.android.kt         # EncryptedSharedPreferences
│   ├── PushItFirebaseService.kt            # FirebaseMessagingService
│   └── MainActivity.kt
│
├── iosMain/kotlin/com/foxugly/pushit_app/
│   ├── platform/
│   │   ├── QrScanner.ios.kt                # AVFoundation
│   │   ├── FcmTokenProvider.ios.kt
│   │   └── TokenStorage.ios.kt             # iOS Keychain
│   └── MainViewController.kt
```

### Platform Boundaries (expect/actual)

- **TokenStorage** — encrypted key-value storage for JWT access token, refresh token, and app token
- **QrScanner** — camera-based QR scanning composable, returns scanned string via callback
- **FcmTokenProvider** — get current FCM token + observe token changes

---

## 2. Data Models

### Request DTOs

```kotlin
@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val email: String, val username: String, val password: String)

@Serializable
data class RefreshRequest(val refresh: String)

@Serializable
data class DeviceLinkRequest(
    val push_token: String,
    val platform: String,       // "android" or "ios"
    val device_name: String     // obtained from device model (e.g., Build.MODEL on Android, UIDevice.name on iOS)
)
```

### Response DTOs

```kotlin
@Serializable
data class LoginResponse(
    val access: String,
    val refresh: String,
    val user: UserProfile
)

@Serializable
data class RefreshResponse(val access: String)

@Serializable
data class UserProfile(val id: Int, val email: String, val username: String)

@Serializable
data class DeviceLinkResponse(
    val status: String,
    val device_id: Int,
    val device_created: Boolean,
    val link_created: Boolean,
    val application_id: Int
)

@Serializable
data class NotificationListResponse(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<Notification>
)

@Serializable
data class Notification(
    val id: Int,
    val title: String,
    val message: String,
    val status: String,
    val created_at: String,
    val sent_at: String?
)

@Serializable
data class NotificationStats(
    val pending: Int,
    val sent: Int,
    val delivered: Int,
    val failed: Int
)
```

---

## 3. Networking

### Ktor Client

Single `HttpClient` instance with:
- `ContentNegotiation` plugin using `kotlinx.serialization` JSON (ignoreUnknownKeys = true)
- Custom `AuthInterceptor` plugin for token management

### AuthInterceptor Behavior

1. Injects `Authorization: Bearer <access_token>` on all authenticated requests
2. On 401 response: attempts `POST /auth/refresh/` with stored refresh token
3. Refresh succeeds → stores new access token, retries original request
4. Refresh fails → clears all JWT tokens, signals navigation to Login screen
5. Concurrent requests during refresh: queued and replayed after refresh completes

### API Endpoints

```kotlin
class PushItApi(private val client: HttpClient) {
    // Auth
    suspend fun login(request: LoginRequest): LoginResponse
    suspend fun register(request: RegisterRequest): UserProfile
    suspend fun refresh(request: RefreshRequest): RefreshResponse
    suspend fun logout(refreshToken: String)        // POST, 204
    suspend fun getMe(): UserProfile

    // Device
    suspend fun linkDevice(appToken: String, request: DeviceLinkRequest): DeviceLinkResponse

    // Notifications
    suspend fun getNotifications(page: Int = 1): NotificationListResponse
    suspend fun getNotification(id: Int): Notification
    suspend fun getNotificationStats(): NotificationStats
}
```

The `linkDevice` call adds `X-App-Token: <appToken>` header. All other authenticated calls use the Bearer token from `AuthInterceptor`.

---

## 4. Repository Layer

### AuthRepository

- `login(email, password)` → calls API, stores access + refresh tokens in TokenStorage, returns UserProfile
- `register(email, username, password)` → calls API, then auto-login
- `logout()` → calls API with refresh token, clears JWT tokens from storage (keeps app token)
- `getCurrentUser()` → calls `GET /auth/me/`
- `isAuthenticated()` → checks if access token exists in storage

### NotificationRepository

- `getNotifications(page)` → returns paginated results
- `getNotification(id)` → returns single notification
- `getStats()` → returns notification counts by status

Both repositories take `PushItApi` and `TokenStorage` as constructor parameters. No DI framework.

---

## 5. Navigation

### Screen Sealed Class

```kotlin
sealed class Screen {
    object Login : Screen()
    object Register : Screen()
    object QrScanner : Screen()
    object NotificationList : Screen()
    data class NotificationDetail(val notificationId: Int) : Screen()
    object Settings : Screen()
}
```

### Navigation Rules

- State-driven: root `App()` holds `currentScreen: MutableState<Screen>` and renders with `when`
- Login/Register: no back navigation between each other (just links)
- QrScanner: back returns to previous screen
- NotificationList → NotificationDetail: standard back
- NotificationList → Settings: gear icon in top bar
- Settings → QrScanner: "Scan QR Code" button
- Logout (Settings): clears JWT tokens, navigates to Login. App token is preserved.

---

## 6. App Startup & Onboarding

### Startup Flow

```
App launches
  → Read TokenStorage for existing JWT
  → Has valid access token? → NotificationList
  → Has refresh token only? → Try refresh
      → Success → NotificationList
      → Failure → Login
  → No tokens → Login
```

### Onboarding

Login and QR scan can happen in either order. The device link call fires when all three conditions are met:
1. Valid JWT (authenticated)
2. App token stored (QR scanned)
3. FCM token available

If any condition is missing, the app waits. `DeviceLinkManager` in commonMain observes all three signals and triggers `POST /devices/link/` when ready.

### QR Code Format

Plain text: `apt_xxxxxxxxxxxx`

The scanner validates that the scanned string starts with `apt_`. Invalid codes show an error and keep scanning.

Manual text entry fallback available via link below the camera preview.

---

## 7. FCM Integration

### Android

**`PushItFirebaseService`** (extends `FirebaseMessagingService`):
- `onNewToken(token)` → stores FCM token, triggers DeviceLinkManager
- `onMessageReceived(message)` → shows system notification via `NotificationCompat.Builder`, broadcasts foreground refresh event

Requires `google-services.json` in `composeApp/`.
Notification channel created at app startup in `MainActivity`.

### iOS

Setup in SwiftUI `iOSApp.swift`:
- `FirebaseApp.configure()`
- Register for remote notifications via `UNUserNotificationCenter`
- `Messaging.messaging().delegate` captures token refreshes
- Token and messages bridged to Kotlin via `FcmTokenProvider` actual implementation

Requires `GoogleService-Info.plist` in `iosApp/iosApp/`.

### Common Interface

```kotlin
expect class FcmTokenProvider {
    fun getCurrentToken(): String?
    fun observeTokenChanges(onNewToken: (String) -> Unit)
}
```

### DeviceLinkManager

```kotlin
class DeviceLinkManager(
    private val api: PushItApi,
    private val tokenStorage: TokenStorage,
    private val fcmTokenProvider: FcmTokenProvider
) {
    // Observes auth state + app token + FCM token
    // When all three non-null → POST /devices/link/
    // Re-triggers on FCM token change
    // Tracks lastLinkedToken to prevent duplicate calls
}
```

---

## 8. Screen Designs

### Login Screen
- Email + password text fields
- "Login" button → `POST /auth/login/`
- "Don't have an account? Register" link
- Error display for invalid credentials

### Register Screen
- Email, username, password fields
- "Register" button → `POST /auth/register/` then auto-login
- "Already have an account? Login" link
- Inline validation errors

### QR Scanner Screen
- Full-screen camera preview with scanning frame overlay
- Validates scanned string starts with `apt_`
- On success: stores token, shows feedback, pops back
- "Enter token manually" link at bottom as fallback

### Notification List Screen
- Top bar: app name + settings gear icon
- Pull-to-refresh
- Infinite scroll (auto-load next page near bottom)
- Each item: title, truncated message, color-coded status badge, timestamp
- Tap → NotificationDetail
- Empty state: "No notifications yet"
- Auto-refreshes when push notification arrives in foreground

### Notification Detail Screen
- Back arrow in top bar
- Full title, full message body
- Status badge
- Timestamps: created_at, sent_at

### Settings Screen
- Current user info (email, username)
- App token status: "Linked to application" or "Not linked"
- "Scan QR Code" button (always available to re-link)
- "Logout" button

### Theming
- Material 3 default color scheme
- No custom theme for MVP

---

## 9. Error Handling

### Network Errors
- All API calls return `Result<T>` — screens observe success/error states
- Connection failures: snackbar with "No connection — pull to refresh to retry"
- No automatic retry loops

### Token Expiry
- 401 → AuthInterceptor attempts one refresh
- Refresh fails → clear JWT, navigate to Login
- Concurrent requests queued during refresh, replayed after

### QR Scanner Errors
- Camera permission denied → message with "Open Settings" button
- Non-`apt_` scan → "Invalid QR code" error, keep scanning
- Camera unavailable → fall back to manual input

### Device Link Failures
- Network error → store intent to link, retry on next app open or FCM token refresh
- Invalid app token (4xx) → clear stored app token, prompt re-scan

### Empty/Offline States
- No notifications → centered "No notifications yet" message
- Not linked → Settings shows QR scan prompt
- No offline caching — list requires connectivity

---

## 10. Dependencies

### Common (version catalog)
- `io.ktor:ktor-client-core` + `ktor-client-content-negotiation` + `ktor-serialization-kotlinx-json`
- `org.jetbrains.kotlinx:kotlinx-serialization-json`
- `com.russhwolf:multiplatform-settings` + `multiplatform-settings-no-arg`
- Compose Multiplatform (already configured)
- `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` (already configured)

### Android-specific
- `io.ktor:ktor-client-okhttp` (Ktor engine)
- `com.google.firebase:firebase-messaging` (FCM)
- `com.google.mlkit:barcode-scanning` (QR scanner)
- `androidx.camera:camera-camera2` + `camera-lifecycle` + `camera-view` (CameraX)
- `androidx.security:security-crypto` (EncryptedSharedPreferences)
- Google Services Gradle plugin

### iOS-specific
- `io.ktor:ktor-client-darwin` (Ktor engine)
- Firebase iOS SDK (via CocoaPods or SPM, configured in Xcode)

### Gradle Plugins to Add
- `org.jetbrains.kotlin.plugin.serialization`
- `com.google.gms.google-services` (Android)
