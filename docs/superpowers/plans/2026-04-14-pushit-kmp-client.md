# PushIT KMP Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a KMP mobile client (Android + iOS) that authenticates users, registers devices via QR-scanned app tokens, receives FCM push notifications, and displays a notification list.

**Architecture:** Single `composeApp` module with shared Compose Multiplatform UI in `commonMain`. Platform-specific code (FCM, QR scanner, encrypted storage) via `expect/actual` in `androidMain`/`iosMain`. Manual state-driven navigation, Ktor networking, kotlinx.serialization.

**Tech Stack:** Kotlin 2.3.20, Compose Multiplatform 1.10.3, Ktor (OkHttp/Darwin engines), kotlinx.serialization, multiplatform-settings, CameraX + ML Kit (Android), AVFoundation (iOS), Firebase Cloud Messaging.

**Spec:** `docs/superpowers/specs/2026-04-14-pushit-kmp-client-design.md`

---

## File Map

### Files to Modify
- `composeApp/build.gradle.kts` — remove web targets, add Ktor/serialization/Firebase/CameraX/MLKit dependencies
- `build.gradle.kts` — add serialization and google-services plugins
- `gradle/libs.versions.toml` — add all new version catalog entries
- `settings.gradle.kts` — no changes needed
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/App.kt` — replace with navigation root
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/Platform.kt` — add `deviceName` property
- `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/Platform.android.kt` — add `deviceName`
- `composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/Platform.ios.kt` — add `deviceName`
- `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/MainActivity.kt` — FCM notification channel setup
- `composeApp/src/androidMain/AndroidManifest.xml` — add camera permission, FCM service
- `iosApp/iosApp/iOSApp.swift` — Firebase init, notification registration

### Files to Create — commonMain
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/navigation/Screen.kt`
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/api/Models.kt`
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/api/PushItApi.kt`
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/api/AuthInterceptor.kt`
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/storage/TokenStorage.kt` (expect)
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/repository/AuthRepository.kt`
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/repository/NotificationRepository.kt`
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/platform/FcmTokenProvider.kt` (expect)
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/platform/QrScanner.kt` (expect)
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/platform/DeviceLinkManager.kt`
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/login/LoginScreen.kt`
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/register/RegisterScreen.kt`
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/notifications/NotificationListScreen.kt`
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/notifications/NotificationDetailScreen.kt`
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/settings/SettingsScreen.kt`
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/qrscanner/QrScannerScreen.kt`
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/components/LoadingIndicator.kt`
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/components/ErrorBanner.kt`

### Files to Create — androidMain
- `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/data/storage/TokenStorage.android.kt`
- `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/platform/FcmTokenProvider.android.kt`
- `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/platform/QrScanner.android.kt`
- `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/PushItFirebaseService.kt`

### Files to Create — iosMain
- `composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/data/storage/TokenStorage.ios.kt`
- `composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/platform/FcmTokenProvider.ios.kt`
- `composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/platform/QrScanner.ios.kt`

### Files to Delete
- `composeApp/src/jsMain/` (entire directory)
- `composeApp/src/wasmJsMain/` (entire directory)
- `composeApp/src/webMain/` (entire directory)
- `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/Greeting.kt`

### Test Files to Create
- `composeApp/src/commonTest/kotlin/com/foxugly/pushit_app/data/api/ModelsTest.kt`
- `composeApp/src/commonTest/kotlin/com/foxugly/pushit_app/data/repository/AuthRepositoryTest.kt`
- `composeApp/src/commonTest/kotlin/com/foxugly/pushit_app/data/repository/NotificationRepositoryTest.kt`
- `composeApp/src/commonTest/kotlin/com/foxugly/pushit_app/platform/DeviceLinkManagerTest.kt`

---

## Task 1: Strip Web Targets & Update Gradle Dependencies

**Files:**
- Modify: `composeApp/build.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Delete: `composeApp/src/jsMain/` (entire directory)
- Delete: `composeApp/src/wasmJsMain/` (entire directory)
- Delete: `composeApp/src/webMain/` (entire directory)
- Delete: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/Greeting.kt`

- [ ] **Step 1: Add new versions and libraries to version catalog**

In `gradle/libs.versions.toml`, add these entries:

```toml
[versions]
# ... existing versions stay ...
ktor = "3.1.3"
kotlinxSerialization = "1.8.1"
multiplatformSettings = "1.3.0"
androidxCamera = "1.4.2"
mlkitBarcode = "17.3.0"
firebaseBom = "33.12.0"
androidxSecurityCrypto = "1.0.0"
kotlinxCoroutines = "1.10.2"

[libraries]
# ... existing libraries stay ...
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
multiplatform-settings = { module = "com.russhwolf:multiplatform-settings", version.ref = "multiplatformSettings" }
multiplatform-settings-no-arg = { module = "com.russhwolf:multiplatform-settings-no-arg", version.ref = "multiplatformSettings" }
androidx-camera-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "androidxCamera" }
androidx-camera-lifecycle = { module = "androidx.camera:camera-lifecycle", version.ref = "androidxCamera" }
androidx-camera-view = { module = "androidx.camera:camera-view", version.ref = "androidxCamera" }
mlkit-barcode-scanning = { module = "com.google.mlkit:barcode-scanning", version.ref = "mlkitBarcode" }
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebaseBom" }
firebase-messaging = { module = "com.google.firebase:firebase-messaging" }
androidx-security-crypto = { module = "androidx.security:security-crypto", version.ref = "androidxSecurityCrypto" }

[plugins]
# ... existing plugins stay ...
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
googleServices = { id = "com.google.gms.google-services", version = "4.4.2" }
```

- [ ] **Step 2: Update root build.gradle.kts**

Replace the contents of `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.googleServices) apply false
}
```

- [ ] **Step 3: Update composeApp/build.gradle.kts**

Replace the entire file. Remove JS/WasmJS targets, add serialization plugin, add all new dependencies:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.googleServices)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.mlkit.barcode.scanning)
            implementation(libs.androidx.security.crypto)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "com.foxugly.pushit_app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.foxugly.pushit_app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}
```

- [ ] **Step 4: Delete web source sets and Greeting.kt**

```bash
rm -rf composeApp/src/jsMain composeApp/src/wasmJsMain composeApp/src/webMain
rm composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/Greeting.kt
```

- [ ] **Step 5: Remove Greeting reference from App.kt**

Replace `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/App.kt` temporarily with a minimal placeholder:

```kotlin
package com.foxugly.pushit_app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun App() {
    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("PushIT App")
        }
    }
}
```

- [ ] **Step 6: Update the existing test**

Replace `composeApp/src/commonTest/kotlin/com/foxugly/pushit_app/ComposeAppCommonTest.kt`:

```kotlin
package com.foxugly.pushit_app

import kotlin.test.Test
import kotlin.test.assertTrue

class ComposeAppCommonTest {

    @Test
    fun appCompiles() {
        assertTrue(true)
    }
}
```

- [ ] **Step 7: Verify the project builds**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL. (Note: google-services.json is not yet present, so the Google Services plugin may warn — this is ok for now, it will be added by the developer when configuring Firebase.)

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "chore: strip web targets, add KMP dependencies for networking/FCM/QR scanning"
```

---

## Task 2: Data Models & Serialization

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/api/Models.kt`
- Create: `composeApp/src/commonTest/kotlin/com/foxugly/pushit_app/data/api/ModelsTest.kt`

- [ ] **Step 1: Write the failing test for model serialization**

Create `composeApp/src/commonTest/kotlin/com/foxugly/pushit_app/data/api/ModelsTest.kt`:

```kotlin
package com.foxugly.pushit_app.data.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun loginResponseDeserialization() {
        val raw = """
            {
                "access": "eyJ...",
                "refresh": "eyR...",
                "user": {"id": 1, "email": "test@example.com", "username": "testuser"}
            }
        """.trimIndent()
        val result = json.decodeFromString<LoginResponse>(raw)
        assertEquals("eyJ...", result.access)
        assertEquals("eyR...", result.refresh)
        assertEquals(1, result.user.id)
        assertEquals("test@example.com", result.user.email)
        assertEquals("testuser", result.user.username)
    }

    @Test
    fun notificationListResponseDeserialization() {
        val raw = """
            {
                "count": 1,
                "next": null,
                "previous": null,
                "results": [
                    {
                        "id": 42,
                        "title": "Deploy complete",
                        "message": "Version 2.1 deployed successfully",
                        "status": "delivered",
                        "created_at": "2026-04-14T10:00:00Z",
                        "sent_at": "2026-04-14T10:00:01Z"
                    }
                ]
            }
        """.trimIndent()
        val result = json.decodeFromString<NotificationListResponse>(raw)
        assertEquals(1, result.count)
        assertEquals(null, result.next)
        assertEquals(1, result.results.size)
        assertEquals(42, result.results[0].id)
        assertEquals("Deploy complete", result.results[0].title)
        assertEquals("delivered", result.results[0].status)
    }

    @Test
    fun deviceLinkRequestSerialization() {
        val request = DeviceLinkRequest(
            pushToken = "fcm_token_123",
            platform = "android",
            deviceName = "Pixel 8"
        )
        val serialized = json.encodeToString(DeviceLinkRequest.serializer(), request)
        assert(serialized.contains("\"push_token\":\"fcm_token_123\""))
        assert(serialized.contains("\"platform\":\"android\""))
        assert(serialized.contains("\"device_name\":\"Pixel 8\""))
    }

    @Test
    fun deviceLinkResponseDeserialization() {
        val raw = """
            {
                "status": "ok",
                "device_id": 7,
                "device_created": true,
                "link_created": true,
                "application_id": 3
            }
        """.trimIndent()
        val result = json.decodeFromString<DeviceLinkResponse>(raw)
        assertEquals("ok", result.status)
        assertEquals(7, result.deviceId)
        assertEquals(true, result.deviceCreated)
        assertEquals(3, result.applicationId)
    }

    @Test
    fun loginResponseIgnoresUnknownFields() {
        val raw = """
            {
                "access": "a",
                "refresh": "r",
                "user": {"id": 1, "email": "e@e.com", "username": "u", "unknown_field": true}
            }
        """.trimIndent()
        val result = json.decodeFromString<LoginResponse>(raw)
        assertEquals("a", result.access)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :composeApp:allTests --tests "com.foxugly.pushit_app.data.api.ModelsTest"
```

Expected: FAIL — `Models.kt` does not exist yet.

- [ ] **Step 3: Create Models.kt**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/api/Models.kt`:

```kotlin
package com.foxugly.pushit_app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Auth ---

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val access: String,
    val refresh: String,
    val user: UserProfile,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
)

@Serializable
data class RefreshRequest(
    val refresh: String,
)

@Serializable
data class RefreshResponse(
    val access: String,
)

@Serializable
data class UserProfile(
    val id: Int,
    val email: String,
    val username: String,
)

// --- Device ---

@Serializable
data class DeviceLinkRequest(
    @SerialName("push_token") val pushToken: String,
    val platform: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class DeviceLinkResponse(
    val status: String,
    @SerialName("device_id") val deviceId: Int,
    @SerialName("device_created") val deviceCreated: Boolean,
    @SerialName("link_created") val linkCreated: Boolean,
    @SerialName("application_id") val applicationId: Int,
)

// --- Notifications ---

@Serializable
data class NotificationListResponse(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<Notification>,
)

@Serializable
data class Notification(
    val id: Int,
    val title: String,
    val message: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("sent_at") val sentAt: String?,
)

@Serializable
data class NotificationStats(
    val pending: Int,
    val sent: Int,
    val delivered: Int,
    val failed: Int,
)
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :composeApp:allTests --tests "com.foxugly.pushit_app.data.api.ModelsTest"
```

Expected: PASS — all 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/api/Models.kt \
       composeApp/src/commonTest/kotlin/com/foxugly/pushit_app/data/api/ModelsTest.kt
git commit -m "feat: add serializable data models for auth, device, and notification DTOs"
```

---

## Task 3: Token Storage (expect/actual)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/storage/TokenStorage.kt`
- Create: `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/data/storage/TokenStorage.android.kt`
- Create: `composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/data/storage/TokenStorage.ios.kt`

- [ ] **Step 1: Create the expect class in commonMain**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/storage/TokenStorage.kt`:

```kotlin
package com.foxugly.pushit_app.data.storage

expect class TokenStorage {
    fun getAccessToken(): String?
    fun setAccessToken(token: String?)
    fun getRefreshToken(): String?
    fun setRefreshToken(token: String?)
    fun getAppToken(): String?
    fun setAppToken(token: String?)
    fun clearAuthTokens()
}
```

- [ ] **Step 2: Create Android actual using EncryptedSharedPreferences**

Create `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/data/storage/TokenStorage.android.kt`:

```kotlin
package com.foxugly.pushit_app.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

actual class TokenStorage(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "pushit_secure_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    actual fun getAccessToken(): String? = prefs.getString(KEY_ACCESS, null)
    actual fun setAccessToken(token: String?) {
        prefs.edit().putString(KEY_ACCESS, token).apply()
    }

    actual fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)
    actual fun setRefreshToken(token: String?) {
        prefs.edit().putString(KEY_REFRESH, token).apply()
    }

    actual fun getAppToken(): String? = prefs.getString(KEY_APP_TOKEN, null)
    actual fun setAppToken(token: String?) {
        prefs.edit().putString(KEY_APP_TOKEN, token).apply()
    }

    actual fun clearAuthTokens() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .apply()
    }

    companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_APP_TOKEN = "app_token"
    }
}
```

- [ ] **Step 3: Create iOS actual using NSUserDefaults with Keychain**

Create `composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/data/storage/TokenStorage.ios.kt`:

```kotlin
package com.foxugly.pushit_app.data.storage

import platform.Foundation.NSUserDefaults
import platform.Security.*
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.darwin.OSStatus

actual class TokenStorage {

    private fun saveToKeychain(key: String, value: String) {
        deleteFromKeychain(key)
        val data = value.encodeToByteArray()
        @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
        memScoped {
            val query = cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrAccount to CFBridgingRetain(key),
                kSecValueData to CFBridgingRetain(
                    platform.Foundation.NSData.create(
                        bytes = data.toCValues().ptr,
                        length = data.size.toULong()
                    )
                ),
                kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            )
            SecItemAdd(query, null)
        }
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    private fun readFromKeychain(key: String): String? {
        memScoped {
            val query = cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrAccount to CFBridgingRetain(key),
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne
            )
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status != errSecSuccess) return null
            val data = CFBridgingRelease(result.value) as? platform.Foundation.NSData ?: return null
            return data.bytes?.readBytes(data.length.toInt())?.decodeToString()
        }
    }

    private fun deleteFromKeychain(key: String) {
        @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
        memScoped {
            val query = cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrAccount to CFBridgingRetain(key)
            )
            SecItemDelete(query)
        }
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    private fun cfDictionaryOf(vararg pairs: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        memScoped {
            val keys = pairs.map { it.first }.toCValues()
            val values = pairs.map { it.second }.toCValues()
            return CFDictionaryCreate(
                null,
                keys.ptr.reinterpret(),
                values.ptr.reinterpret(),
                pairs.size.toLong(),
                null,
                null
            )
        }
    }

    actual fun getAccessToken(): String? = readFromKeychain(KEY_ACCESS)
    actual fun setAccessToken(token: String?) {
        if (token != null) saveToKeychain(KEY_ACCESS, token) else deleteFromKeychain(KEY_ACCESS)
    }

    actual fun getRefreshToken(): String? = readFromKeychain(KEY_REFRESH)
    actual fun setRefreshToken(token: String?) {
        if (token != null) saveToKeychain(KEY_REFRESH, token) else deleteFromKeychain(KEY_REFRESH)
    }

    actual fun getAppToken(): String? = readFromKeychain(KEY_APP_TOKEN)
    actual fun setAppToken(token: String?) {
        if (token != null) saveToKeychain(KEY_APP_TOKEN, token) else deleteFromKeychain(KEY_APP_TOKEN)
    }

    actual fun clearAuthTokens() {
        deleteFromKeychain(KEY_ACCESS)
        deleteFromKeychain(KEY_REFRESH)
    }

    companion object {
        private const val KEY_ACCESS = "pushit_access_token"
        private const val KEY_REFRESH = "pushit_refresh_token"
        private const val KEY_APP_TOKEN = "pushit_app_token"
    }
}
```

- [ ] **Step 4: Verify project compiles**

```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/storage/TokenStorage.kt \
       composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/data/storage/TokenStorage.android.kt \
       composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/data/storage/TokenStorage.ios.kt
git commit -m "feat: add expect/actual TokenStorage with encrypted Android/iOS implementations"
```

---

## Task 4: Ktor API Client & Auth Interceptor

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/api/PushItApi.kt`
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/api/AuthInterceptor.kt`

- [ ] **Step 1: Create AuthInterceptor**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/api/AuthInterceptor.kt`:

```kotlin
package com.foxugly.pushit_app.data.api

import com.foxugly.pushit_app.data.storage.TokenStorage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class AuthInterceptor(
    private val tokenStorage: TokenStorage,
    private val json: Json,
    var onAuthFailure: (() -> Unit)? = null,
) {

    private val refreshMutex = Mutex()

    fun install(config: HttpClientConfig<*>) {
        config.install("AuthInterceptor") {
            on(SendingRequest) { request, _ ->
                val token = tokenStorage.getAccessToken()
                if (token != null && !request.url.encodedPath.contains("/auth/login")
                    && !request.url.encodedPath.contains("/auth/register")
                    && !request.url.encodedPath.contains("/auth/refresh")
                ) {
                    request.headers.append(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        }
    }

    suspend fun handleUnauthorized(
        client: HttpClient,
        originalRequest: HttpRequestBuilder,
        response: HttpResponse,
    ): HttpResponse {
        if (response.status != HttpStatusCode.Unauthorized) return response

        val newAccessToken = refreshMutex.withLock {
            val refreshToken = tokenStorage.getRefreshToken() ?: run {
                onAuthFailure?.invoke()
                return response
            }
            try {
                val refreshResponse = client.post("auth/refresh/") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(refreshToken))
                }
                if (refreshResponse.status == HttpStatusCode.OK) {
                    val body = refreshResponse.body<RefreshResponse>()
                    tokenStorage.setAccessToken(body.access)
                    body.access
                } else {
                    tokenStorage.clearAuthTokens()
                    onAuthFailure?.invoke()
                    null
                }
            } catch (e: Exception) {
                tokenStorage.clearAuthTokens()
                onAuthFailure?.invoke()
                null
            }
        } ?: return response

        return client.request {
            takeFrom(originalRequest)
            headers.remove(HttpHeaders.Authorization)
            headers.append(HttpHeaders.Authorization, "Bearer $newAccessToken")
        }
    }
}
```

- [ ] **Step 2: Create PushItApi**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/api/PushItApi.kt`:

```kotlin
package com.foxugly.pushit_app.data.api

import com.foxugly.pushit_app.data.storage.TokenStorage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class PushItApi(
    private val tokenStorage: TokenStorage,
    baseUrl: String = "http://10.0.2.2:8000/api/v1/",
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val authInterceptor = AuthInterceptor(tokenStorage, json)

    val client = HttpClient {
        install(ContentNegotiation) {
            json(this@PushItApi.json)
        }
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
        authInterceptor.install(this)
        HttpResponseValidator {
            validateResponse { response ->
                // 401 handling is done via retryOnUnauthorized wrapper
            }
        }
    }

    var onAuthFailure: (() -> Unit)?
        get() = authInterceptor.onAuthFailure
        set(value) { authInterceptor.onAuthFailure = value }

    // --- Auth ---

    suspend fun login(request: LoginRequest): Result<LoginResponse> = apiCall {
        client.post("auth/login/") {
            setBody(request)
        }
    }

    suspend fun register(request: RegisterRequest): Result<UserProfile> = apiCall {
        client.post("auth/register/") {
            setBody(request)
        }
    }

    suspend fun refresh(request: RefreshRequest): Result<RefreshResponse> = apiCall {
        client.post("auth/refresh/") {
            setBody(request)
        }
    }

    suspend fun logout(refreshToken: String): Result<Unit> = runCatching {
        client.post("auth/logout/") {
            setBody(RefreshRequest(refreshToken))
        }
    }

    suspend fun getMe(): Result<UserProfile> = apiCall {
        client.get("auth/me/")
    }

    // --- Device ---

    suspend fun linkDevice(
        appToken: String,
        request: DeviceLinkRequest,
    ): Result<DeviceLinkResponse> = apiCall {
        client.post("devices/link/") {
            header("X-App-Token", appToken)
            setBody(request)
        }
    }

    // --- Notifications ---

    suspend fun getNotifications(page: Int = 1): Result<NotificationListResponse> = apiCall {
        client.get("notifications/") {
            parameter("page", page)
        }
    }

    suspend fun getNotification(id: Int): Result<Notification> = apiCall {
        client.get("notifications/$id/")
    }

    suspend fun getNotificationStats(): Result<NotificationStats> = apiCall {
        client.get("notifications/stats/")
    }

    // --- Helpers ---

    private suspend inline fun <reified T> apiCall(
        crossinline block: suspend () -> HttpResponse,
    ): Result<T> = runCatching {
        val response = block()
        if (response.status == HttpStatusCode.Unauthorized) {
            val retried = authInterceptor.handleUnauthorized(
                client,
                HttpRequestBuilder().apply { url(response.request.url.toString()) },
                response,
            )
            retried.body<T>()
        } else {
            response.body<T>()
        }
    }
}
```

- [ ] **Step 3: Verify project compiles**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/api/PushItApi.kt \
       composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/api/AuthInterceptor.kt
git commit -m "feat: add Ktor API client with JWT auth interceptor and token refresh"
```

---

## Task 5: Repositories

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/repository/AuthRepository.kt`
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/repository/NotificationRepository.kt`
- Create: `composeApp/src/commonTest/kotlin/com/foxugly/pushit_app/data/repository/AuthRepositoryTest.kt`
- Create: `composeApp/src/commonTest/kotlin/com/foxugly/pushit_app/data/repository/NotificationRepositoryTest.kt`

- [ ] **Step 1: Create AuthRepository**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/repository/AuthRepository.kt`:

```kotlin
package com.foxugly.pushit_app.data.repository

import com.foxugly.pushit_app.data.api.*
import com.foxugly.pushit_app.data.storage.TokenStorage

class AuthRepository(
    private val api: PushItApi,
    private val tokenStorage: TokenStorage,
) {

    suspend fun login(email: String, password: String): Result<UserProfile> {
        return api.login(LoginRequest(email, password)).map { response ->
            tokenStorage.setAccessToken(response.access)
            tokenStorage.setRefreshToken(response.refresh)
            response.user
        }
    }

    suspend fun register(email: String, username: String, password: String): Result<UserProfile> {
        val registerResult = api.register(RegisterRequest(email, username, password))
        if (registerResult.isFailure) return registerResult

        // Auto-login after successful registration
        return login(email, password)
    }

    suspend fun logout(): Result<Unit> {
        val refreshToken = tokenStorage.getRefreshToken()
        val result = if (refreshToken != null) {
            api.logout(refreshToken)
        } else {
            Result.success(Unit)
        }
        tokenStorage.clearAuthTokens()
        return result
    }

    suspend fun getCurrentUser(): Result<UserProfile> = api.getMe()

    fun isAuthenticated(): Boolean = tokenStorage.getAccessToken() != null

    fun hasRefreshToken(): Boolean = tokenStorage.getRefreshToken() != null

    suspend fun tryRefresh(): Boolean {
        val refreshToken = tokenStorage.getRefreshToken() ?: return false
        val result = api.refresh(RefreshRequest(refreshToken))
        return result.fold(
            onSuccess = { response ->
                tokenStorage.setAccessToken(response.access)
                true
            },
            onFailure = {
                tokenStorage.clearAuthTokens()
                false
            }
        )
    }
}
```

- [ ] **Step 2: Create NotificationRepository**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/repository/NotificationRepository.kt`:

```kotlin
package com.foxugly.pushit_app.data.repository

import com.foxugly.pushit_app.data.api.*

class NotificationRepository(
    private val api: PushItApi,
) {

    suspend fun getNotifications(page: Int = 1): Result<NotificationListResponse> {
        return api.getNotifications(page)
    }

    suspend fun getNotification(id: Int): Result<Notification> {
        return api.getNotification(id)
    }

    suspend fun getStats(): Result<NotificationStats> {
        return api.getNotificationStats()
    }
}
```

- [ ] **Step 3: Verify project compiles**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/repository/AuthRepository.kt \
       composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/data/repository/NotificationRepository.kt
git commit -m "feat: add auth and notification repositories"
```

---

## Task 6: Navigation & Platform Interfaces

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/navigation/Screen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/platform/FcmTokenProvider.kt`
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/platform/QrScanner.kt`
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/platform/DeviceLinkManager.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/Platform.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/Platform.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/Platform.ios.kt`
- Create: `composeApp/src/commonTest/kotlin/com/foxugly/pushit_app/platform/DeviceLinkManagerTest.kt`

- [ ] **Step 1: Create Screen sealed class**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/navigation/Screen.kt`:

```kotlin
package com.foxugly.pushit_app.navigation

sealed class Screen {
    data object Login : Screen()
    data object Register : Screen()
    data object QrScanner : Screen()
    data object NotificationList : Screen()
    data class NotificationDetail(val notificationId: Int) : Screen()
    data object Settings : Screen()
}
```

- [ ] **Step 2: Add deviceName to Platform interface**

Replace `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/Platform.kt`:

```kotlin
package com.foxugly.pushit_app

interface Platform {
    val name: String
    val deviceName: String
    val platformType: String  // "android" or "ios"
}

expect fun getPlatform(): Platform
```

Replace `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/Platform.android.kt`:

```kotlin
package com.foxugly.pushit_app

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}"
    override val platformType: String = "android"
}

actual fun getPlatform(): Platform = AndroidPlatform()
```

Replace `composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/Platform.ios.kt`:

```kotlin
package com.foxugly.pushit_app

import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val deviceName: String = UIDevice.currentDevice.name
    override val platformType: String = "ios"
}

actual fun getPlatform(): Platform = IOSPlatform()
```

- [ ] **Step 3: Create FcmTokenProvider expect declaration**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/platform/FcmTokenProvider.kt`:

```kotlin
package com.foxugly.pushit_app.platform

expect class FcmTokenProvider {
    fun getCurrentToken(): String?
    fun observeTokenChanges(onNewToken: (String) -> Unit)
}
```

- [ ] **Step 4: Create QrScanner expect declaration**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/platform/QrScanner.kt`:

```kotlin
package com.foxugly.pushit_app.platform

import androidx.compose.runtime.Composable

@Composable
expect fun QrScannerView(
    onQrCodeScanned: (String) -> Unit,
    onError: (String) -> Unit,
)
```

- [ ] **Step 5: Create DeviceLinkManager**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/platform/DeviceLinkManager.kt`:

```kotlin
package com.foxugly.pushit_app.platform

import com.foxugly.pushit_app.data.api.DeviceLinkRequest
import com.foxugly.pushit_app.data.api.PushItApi
import com.foxugly.pushit_app.data.storage.TokenStorage
import com.foxugly.pushit_app.getPlatform

class DeviceLinkManager(
    private val api: PushItApi,
    private val tokenStorage: TokenStorage,
    private val fcmTokenProvider: FcmTokenProvider,
) {

    private var lastLinkedFcmToken: String? = null
    private var lastLinkedAppToken: String? = null

    suspend fun tryLink(): Result<Boolean> {
        val accessToken = tokenStorage.getAccessToken() ?: return Result.success(false)
        val appToken = tokenStorage.getAppToken() ?: return Result.success(false)
        val fcmToken = fcmTokenProvider.getCurrentToken() ?: return Result.success(false)

        // Skip if already linked with same tokens
        if (fcmToken == lastLinkedFcmToken && appToken == lastLinkedAppToken) {
            return Result.success(true)
        }

        val platform = getPlatform()
        val result = api.linkDevice(
            appToken = appToken,
            request = DeviceLinkRequest(
                pushToken = fcmToken,
                platform = platform.platformType,
                deviceName = platform.deviceName,
            )
        )

        return result.map {
            lastLinkedFcmToken = fcmToken
            lastLinkedAppToken = appToken
            true
        }
    }

    fun startObservingTokenChanges(onLink: (Result<Boolean>) -> Unit) {
        fcmTokenProvider.observeTokenChanges { newToken ->
            // Token changed — reset tracking so next tryLink will re-link
            lastLinkedFcmToken = null
        }
    }
}
```

- [ ] **Step 6: Verify project compiles**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/navigation/ \
       composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/platform/ \
       composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/Platform.kt \
       composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/Platform.android.kt \
       composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/Platform.ios.kt
git commit -m "feat: add navigation, platform interfaces, FCM/QR expect declarations, DeviceLinkManager"
```

---

## Task 7: UI Components & Login/Register Screens

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/components/LoadingIndicator.kt`
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/components/ErrorBanner.kt`
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/login/LoginScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/register/RegisterScreen.kt`

- [ ] **Step 1: Create shared UI components**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/components/LoadingIndicator.kt`:

```kotlin
package com.foxugly.pushit_app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
```

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/components/ErrorBanner.kt`:

```kotlin
package com.foxugly.pushit_app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
```

- [ ] **Step 2: Create LoginScreen**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/login/LoginScreen.kt`:

```kotlin
package com.foxugly.pushit_app.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.data.repository.AuthRepository
import com.foxugly.pushit_app.ui.components.ErrorBanner
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "PushIT",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(32.dp))

        error?.let {
            ErrorBanner(it)
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    error = null
                    authRepository.login(email, password).fold(
                        onSuccess = { onLoginSuccess() },
                        onFailure = { error = it.message ?: "Login failed" },
                    )
                    isLoading = false
                }
            },
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Login")
            }
        }
        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onNavigateToRegister) {
            Text("Don't have an account? Register")
        }
    }
}
```

- [ ] **Step 3: Create RegisterScreen**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/register/RegisterScreen.kt`:

```kotlin
package com.foxugly.pushit_app.ui.register

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.data.repository.AuthRepository
import com.foxugly.pushit_app.ui.components.ErrorBanner
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    authRepository: AuthRepository,
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(32.dp))

        error?.let {
            ErrorBanner(it)
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it; error = null },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    error = null
                    authRepository.register(email, username, password).fold(
                        onSuccess = { onRegisterSuccess() },
                        onFailure = { error = it.message ?: "Registration failed" },
                    )
                    isLoading = false
                }
            },
            enabled = !isLoading && email.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Register")
            }
        }
        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text("Already have an account? Login")
        }
    }
}
```

- [ ] **Step 4: Verify project compiles**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/
git commit -m "feat: add login, register screens and shared UI components"
```

---

## Task 8: Notification List & Detail Screens

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/notifications/NotificationListScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/notifications/NotificationDetailScreen.kt`

- [ ] **Step 1: Create NotificationListScreen with infinite scroll**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/notifications/NotificationListScreen.kt`:

```kotlin
package com.foxugly.pushit_app.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.data.api.Notification
import com.foxugly.pushit_app.data.repository.NotificationRepository
import com.foxugly.pushit_app.ui.components.ErrorBanner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationListScreen(
    notificationRepository: NotificationRepository,
    onNotificationClick: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    refreshTrigger: Int,
) {
    var notifications by remember { mutableStateOf<List<Notification>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasNextPage by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    suspend fun loadPage(page: Int, append: Boolean) {
        val result = notificationRepository.getNotifications(page)
        result.fold(
            onSuccess = { response ->
                notifications = if (append) notifications + response.results else response.results
                currentPage = page
                hasNextPage = response.next != null
                error = null
            },
            onFailure = { error = it.message ?: "Failed to load notifications" },
        )
    }

    suspend fun refresh() {
        isRefreshing = true
        loadPage(1, append = false)
        isRefreshing = false
    }

    // Initial load
    LaunchedEffect(Unit) {
        isLoading = true
        loadPage(1, append = false)
        isLoading = false
    }

    // Push notification refresh trigger
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) refresh()
    }

    // Infinite scroll detection
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= notifications.size - 3 && hasNextPage && !isLoadingMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            isLoadingMore = true
            loadPage(currentPage + 1, append = true)
            isLoadingMore = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { scope.launch { refresh() } },
            modifier = Modifier.padding(padding),
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null && notifications.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ErrorBanner(error!!)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { scope.launch { refresh() } }) {
                            Text("Retry")
                        }
                    }
                }
                notifications.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No notifications yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(notifications, key = { it.id }) { notification ->
                            NotificationListItem(
                                notification = notification,
                                onClick = { onNotificationClick(notification.id) },
                            )
                        }
                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationListItem(
    notification: Notification,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(notification.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(notification.message, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            StatusBadge(notification.status)
        },
        overlineContent = {
            Text(
                notification.createdAt,
                style = MaterialTheme.typography.labelSmall,
            )
        },
    )
    HorizontalDivider()
}

@Composable
private fun StatusBadge(status: String) {
    val color = when (status) {
        "delivered" -> MaterialTheme.colorScheme.primary
        "sent" -> MaterialTheme.colorScheme.tertiary
        "pending" -> MaterialTheme.colorScheme.outline
        "failed" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
```

- [ ] **Step 2: Create NotificationDetailScreen**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/notifications/NotificationDetailScreen.kt`:

```kotlin
package com.foxugly.pushit_app.ui.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.data.api.Notification
import com.foxugly.pushit_app.data.repository.NotificationRepository
import com.foxugly.pushit_app.ui.components.ErrorBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDetailScreen(
    notificationId: Int,
    notificationRepository: NotificationRepository,
    onBack: () -> Unit,
) {
    var notification by remember { mutableStateOf<Notification?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(notificationId) {
        isLoading = true
        notificationRepository.getNotification(notificationId).fold(
            onSuccess = { notification = it; error = null },
            onFailure = { error = it.message ?: "Failed to load notification" },
        )
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null -> {
                    ErrorBanner(error!!, modifier = Modifier.align(Alignment.Center))
                }
                notification != null -> {
                    val n = notification!!
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(n.title, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                        StatusBadge(n.status)
                        Spacer(Modifier.height(16.dp))
                        Text(n.message, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))
                        LabeledValue("Created", n.createdAt)
                        n.sentAt?.let {
                            Spacer(Modifier.height(8.dp))
                            LabeledValue("Sent", it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
```

Note: `StatusBadge` is used from `NotificationListScreen.kt` — move it to a shared location or make it `internal`. For simplicity, duplicate it in the detail screen or extract to a file. Since it's only two usages, we keep one in `NotificationListScreen.kt` and reference from detail via same package visibility.

- [ ] **Step 3: Verify project compiles**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/notifications/
git commit -m "feat: add notification list with infinite scroll and detail screen"
```

---

## Task 9: QR Scanner Screen & Settings Screen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/qrscanner/QrScannerScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Create QrScannerScreen**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/qrscanner/QrScannerScreen.kt`:

```kotlin
package com.foxugly.pushit_app.ui.qrscanner

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.data.storage.TokenStorage
import com.foxugly.pushit_app.platform.QrScannerView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    tokenStorage: TokenStorage,
    onTokenScanned: () -> Unit,
    onBack: () -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var showManualEntry by remember { mutableStateOf(false) }
    var manualToken by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan App Token") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!showManualEntry) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    QrScannerView(
                        onQrCodeScanned = { scannedValue ->
                            if (scannedValue.startsWith("apt_")) {
                                tokenStorage.setAppToken(scannedValue)
                                onTokenScanned()
                            } else {
                                error = "Invalid QR code. Expected an app token starting with 'apt_'"
                            }
                        },
                        onError = { errorMsg ->
                            error = errorMsg
                            showManualEntry = true
                        },
                    )
                }

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                TextButton(onClick = { showManualEntry = true }) {
                    Text("Enter token manually")
                }
                Spacer(Modifier.height(16.dp))
            } else {
                Spacer(Modifier.height(32.dp))

                Text(
                    "Enter App Token",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = manualToken,
                    onValueChange = { manualToken = it; error = null },
                    label = { Text("App Token (apt_...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(16.dp))

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        if (manualToken.startsWith("apt_")) {
                            tokenStorage.setAppToken(manualToken.trim())
                            onTokenScanned()
                        } else {
                            error = "Token must start with 'apt_'"
                        }
                    },
                    enabled = manualToken.isNotBlank(),
                    modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                ) {
                    Text("Save Token")
                }
                Spacer(Modifier.height(16.dp))

                TextButton(onClick = { showManualEntry = false }) {
                    Text("Back to scanner")
                }
            }
        }
    }
}
```

- [ ] **Step 2: Create SettingsScreen**

Create `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/settings/SettingsScreen.kt`:

```kotlin
package com.foxugly.pushit_app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.data.api.UserProfile
import com.foxugly.pushit_app.data.repository.AuthRepository
import com.foxugly.pushit_app.data.storage.TokenStorage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authRepository: AuthRepository,
    tokenStorage: TokenStorage,
    onBack: () -> Unit,
    onScanQrCode: () -> Unit,
    onLogout: () -> Unit,
) {
    var user by remember { mutableStateOf<UserProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val appToken = tokenStorage.getAppToken()

    LaunchedEffect(Unit) {
        authRepository.getCurrentUser().fold(
            onSuccess = { user = it },
            onFailure = { /* Ignore — show what we can */ },
        )
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // User info section
            Text("Account", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                user?.let {
                    Text("Email: ${it.email}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("Username: ${it.username}", style = MaterialTheme.typography.bodyLarge)
                } ?: Text("Could not load user info", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(32.dp))

            // App token section
            Text("Application Link", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            if (appToken != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = "Linked (${appToken.take(12)}...)",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            } else {
                Text(
                    "Not linked to an application",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onScanQrCode,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (appToken != null) "Re-scan QR Code" else "Scan QR Code")
            }

            Spacer(Modifier.weight(1f))

            // Logout
            Button(
                onClick = {
                    scope.launch {
                        authRepository.logout()
                        onLogout()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Logout")
            }
        }
    }
}
```

- [ ] **Step 3: Verify project compiles**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/qrscanner/ \
       composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/ui/settings/
git commit -m "feat: add QR scanner screen with manual fallback and settings screen"
```

---

## Task 10: Root App Navigation Wiring

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/App.kt`

- [ ] **Step 1: Replace App.kt with full navigation root**

Replace `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/App.kt`:

```kotlin
package com.foxugly.pushit_app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.foxugly.pushit_app.data.api.PushItApi
import com.foxugly.pushit_app.data.repository.AuthRepository
import com.foxugly.pushit_app.data.repository.NotificationRepository
import com.foxugly.pushit_app.data.storage.TokenStorage
import com.foxugly.pushit_app.navigation.Screen
import com.foxugly.pushit_app.platform.DeviceLinkManager
import com.foxugly.pushit_app.platform.FcmTokenProvider
import com.foxugly.pushit_app.ui.login.LoginScreen
import com.foxugly.pushit_app.ui.notifications.NotificationDetailScreen
import com.foxugly.pushit_app.ui.notifications.NotificationListScreen
import com.foxugly.pushit_app.ui.qrscanner.QrScannerScreen
import com.foxugly.pushit_app.ui.register.RegisterScreen
import com.foxugly.pushit_app.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun App(
    tokenStorage: TokenStorage,
    fcmTokenProvider: FcmTokenProvider,
) {
    val api = remember { PushItApi(tokenStorage) }
    val authRepository = remember { AuthRepository(api, tokenStorage) }
    val notificationRepository = remember { NotificationRepository(api) }
    val deviceLinkManager = remember { DeviceLinkManager(api, tokenStorage, fcmTokenProvider) }

    var currentScreen by remember { mutableStateOf<Screen?>(null) }
    var previousScreen by remember { mutableStateOf<Screen>(Screen.Login) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // Set up auth failure callback
    LaunchedEffect(Unit) {
        api.onAuthFailure = { currentScreen = Screen.Login }
    }

    // Startup: decide initial screen
    LaunchedEffect(Unit) {
        currentScreen = when {
            authRepository.isAuthenticated() -> Screen.NotificationList
            authRepository.hasRefreshToken() -> {
                if (authRepository.tryRefresh()) Screen.NotificationList else Screen.Login
            }
            else -> Screen.Login
        }
    }

    // Try to link device whenever we land on NotificationList
    LaunchedEffect(currentScreen) {
        if (currentScreen == Screen.NotificationList) {
            deviceLinkManager.tryLink()
        }
    }

    // Observe FCM token changes
    LaunchedEffect(Unit) {
        deviceLinkManager.startObservingTokenChanges { result ->
            // Token changed — will re-link on next tryLink call
        }
    }

    fun navigateTo(screen: Screen) {
        previousScreen = currentScreen ?: Screen.Login
        currentScreen = screen
    }

    fun onLoginOrRegisterSuccess() {
        if (tokenStorage.getAppToken() == null) {
            navigateTo(Screen.QrScanner)
        } else {
            navigateTo(Screen.NotificationList)
        }
    }

    MaterialTheme {
        val screen = currentScreen
        if (screen == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@MaterialTheme
        }

        when (screen) {
            Screen.Login -> LoginScreen(
                authRepository = authRepository,
                onLoginSuccess = ::onLoginOrRegisterSuccess,
                onNavigateToRegister = { navigateTo(Screen.Register) },
            )
            Screen.Register -> RegisterScreen(
                authRepository = authRepository,
                onRegisterSuccess = ::onLoginOrRegisterSuccess,
                onNavigateToLogin = { navigateTo(Screen.Login) },
            )
            Screen.QrScanner -> QrScannerScreen(
                tokenStorage = tokenStorage,
                onTokenScanned = {
                    scope.launch { deviceLinkManager.tryLink() }
                    navigateTo(
                        if (authRepository.isAuthenticated()) Screen.NotificationList
                        else Screen.Login
                    )
                },
                onBack = { navigateTo(previousScreen) },
            )
            Screen.NotificationList -> NotificationListScreen(
                notificationRepository = notificationRepository,
                onNotificationClick = { id -> navigateTo(Screen.NotificationDetail(id)) },
                onSettingsClick = { navigateTo(Screen.Settings) },
                refreshTrigger = refreshTrigger,
            )
            is Screen.NotificationDetail -> NotificationDetailScreen(
                notificationId = screen.notificationId,
                notificationRepository = notificationRepository,
                onBack = { navigateTo(Screen.NotificationList) },
            )
            Screen.Settings -> SettingsScreen(
                authRepository = authRepository,
                tokenStorage = tokenStorage,
                onBack = { navigateTo(Screen.NotificationList) },
                onScanQrCode = { navigateTo(Screen.QrScanner) },
                onLogout = { currentScreen = Screen.Login },
            )
        }
    }
}
```

- [ ] **Step 2: Verify project compiles**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL (may have warnings about unused imports — that's fine)

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/App.kt
git commit -m "feat: wire up root navigation with startup flow and device link coordination"
```

---

## Task 11: Android Platform Implementations

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/platform/FcmTokenProvider.android.kt`
- Create: `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/platform/QrScanner.android.kt`
- Create: `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/PushItFirebaseService.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/MainActivity.kt`
- Modify: `composeApp/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Create Android FcmTokenProvider**

Create `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/platform/FcmTokenProvider.android.kt`:

```kotlin
package com.foxugly.pushit_app.platform

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

actual class FcmTokenProvider {

    private var tokenCallback: ((String) -> Unit)? = null

    actual fun getCurrentToken(): String? {
        var token: String? = null
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token = it }
        return token
    }

    actual fun observeTokenChanges(onNewToken: (String) -> Unit) {
        tokenCallback = onNewToken
    }

    fun notifyTokenRefresh(token: String) {
        tokenCallback?.invoke(token)
    }

    companion object {
        val instance = FcmTokenProvider()
    }
}
```

- [ ] **Step 2: Create PushItFirebaseService**

Create `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/PushItFirebaseService.kt`:

```kotlin
package com.foxugly.pushit_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.foxugly.pushit_app.platform.FcmTokenProvider
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PushItFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmTokenProvider.instance.notifyTokenRefresh(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: "PushIT"
        val body = message.notification?.body ?: message.data["message"] ?: ""

        showNotification(title, body)

        // Signal foreground refresh via broadcast
        val intent = android.content.Intent(ACTION_NOTIFICATION_RECEIVED)
        sendBroadcast(intent)
    }

    private fun showNotification(title: String, body: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PushIT Notifications",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "pushit_notifications"
        const val ACTION_NOTIFICATION_RECEIVED = "com.foxugly.pushit_app.NOTIFICATION_RECEIVED"
    }
}
```

- [ ] **Step 3: Create Android QrScanner composable**

Create `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/platform/QrScanner.android.kt`:

```kotlin
package com.foxugly.pushit_app.platform

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
actual fun QrScannerView(
    onQrCodeScanned: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var scanned by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) onError("Camera permission denied. Please enable it in Settings.")
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Camera permission required", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant Permission")
            }
        }
        return
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val scanner = BarcodeScanning.getClient()
                val executor = Executors.newSingleThreadExecutor()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null && !scanned) {
                        val inputImage = InputImage.fromMediaImage(
                            mediaImage, imageProxy.imageInfo.rotationDegrees
                        )
                        scanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    if (barcode.format == Barcode.FORMAT_QR_CODE && barcode.rawValue != null) {
                                        scanned = true
                                        onQrCodeScanned(barcode.rawValue!!)
                                        break
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                } catch (e: Exception) {
                    onError("Failed to start camera: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
}
```

- [ ] **Step 4: Update MainActivity**

Replace `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/MainActivity.kt`:

```kotlin
package com.foxugly.pushit_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.foxugly.pushit_app.data.storage.TokenStorage
import com.foxugly.pushit_app.platform.FcmTokenProvider

class MainActivity : ComponentActivity() {

    private var notificationReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val tokenStorage = TokenStorage(this)
        val fcmTokenProvider = FcmTokenProvider.instance

        setContent {
            App(
                tokenStorage = tokenStorage,
                fcmTokenProvider = fcmTokenProvider,
            )
        }
    }
}
```

- [ ] **Step 5: Update AndroidManifest.xml**

Replace `composeApp/src/androidMain/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <application
            android:allowBackup="true"
            android:icon="@mipmap/ic_launcher"
            android:label="@string/app_name"
            android:roundIcon="@mipmap/ic_launcher_round"
            android:supportsRtl="true"
            android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
                android:exported="true"
                android:name=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

        <service
            android:name=".PushItFirebaseService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT"/>
            </intent-filter>
        </service>
    </application>

</manifest>
```

- [ ] **Step 6: Verify Android builds**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL (will warn about missing google-services.json — that's expected, added by developer)

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/androidMain/
git commit -m "feat: add Android FCM service, QR scanner with CameraX/MLKit, and platform wiring"
```

---

## Task 12: iOS Platform Implementations

**Files:**
- Create: `composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/platform/FcmTokenProvider.ios.kt`
- Create: `composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/platform/QrScanner.ios.kt`
- Create: `composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/data/storage/TokenStorage.ios.kt` (already created in Task 3)
- Modify: `composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/MainViewController.kt`
- Modify: `iosApp/iosApp/iOSApp.swift`

- [ ] **Step 1: Create iOS FcmTokenProvider**

Create `composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/platform/FcmTokenProvider.ios.kt`:

```kotlin
package com.foxugly.pushit_app.platform

actual class FcmTokenProvider {

    private var currentToken: String? = null
    private var tokenCallback: ((String) -> Unit)? = null

    actual fun getCurrentToken(): String? = currentToken

    actual fun observeTokenChanges(onNewToken: (String) -> Unit) {
        tokenCallback = onNewToken
    }

    fun updateToken(token: String) {
        currentToken = token
        tokenCallback?.invoke(token)
    }

    companion object {
        val instance = FcmTokenProvider()
    }
}
```

- [ ] **Step 2: Create iOS QrScanner composable**

Create `composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/platform/QrScanner.ios.kt`:

```kotlin
package com.foxugly.pushit_app.platform

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.*
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotificationCenter
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import platform.darwin.NSObject

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@Composable
actual fun QrScannerView(
    onQrCodeScanned: (String) -> Unit,
    onError: (String) -> Unit,
) {
    var hasScanned by remember { mutableStateOf(false) }

    UIKitView(
        factory = {
            val containerView = UIView(frame = CGRectMake(0.0, 0.0, 400.0, 600.0))

            val captureSession = AVCaptureSession()
            val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)

            if (device == null) {
                onError("Camera not available")
                return@UIKitView containerView
            }

            val input = try {
                AVCaptureDeviceInput.deviceInputWithDevice(device, null)
            } catch (e: Exception) {
                onError("Failed to access camera: ${e.message}")
                return@UIKitView containerView
            }

            if (input != null && captureSession.canAddInput(input)) {
                captureSession.addInput(input)
            }

            val output = AVCaptureMetadataOutput()
            if (captureSession.canAddOutput(output)) {
                captureSession.addOutput(output)
            }

            val delegate = object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
                override fun captureOutput(
                    output: AVCaptureOutput,
                    didOutputMetadataObjects: List<*>,
                    fromConnection: AVCaptureConnection,
                ) {
                    if (hasScanned) return
                    for (obj in didOutputMetadataObjects) {
                        val readable = obj as? AVMetadataMachineReadableCodeObject ?: continue
                        val value = readable.stringValue ?: continue
                        hasScanned = true
                        onQrCodeScanned(value)
                        break
                    }
                }
            }

            output.setMetadataObjectsDelegate(delegate, queue = platform.darwin.dispatch_get_main_queue())
            output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)

            val previewLayer = AVCaptureVideoPreviewLayer(session = captureSession)
            previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
            previewLayer.frame = containerView.bounds
            containerView.layer.addSublayer(previewLayer)

            captureSession.startRunning()

            containerView
        },
        modifier = Modifier.fillMaxSize(),
    )
}
```

- [ ] **Step 3: Update MainViewController.kt**

Replace `composeApp/src/iosMain/kotlin/com/foxugly/pushit_app/MainViewController.kt`:

```kotlin
package com.foxugly.pushit_app

import androidx.compose.ui.window.ComposeUIViewController
import com.foxugly.pushit_app.data.storage.TokenStorage
import com.foxugly.pushit_app.platform.FcmTokenProvider

fun MainViewController() = ComposeUIViewController {
    App(
        tokenStorage = TokenStorage(),
        fcmTokenProvider = FcmTokenProvider.instance,
    )
}
```

- [ ] **Step 4: Update iOSApp.swift for Firebase integration**

Replace `iosApp/iosApp/iOSApp.swift`:

```swift
import SwiftUI
import FirebaseCore
import FirebaseMessaging
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()

        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            if granted {
                DispatchQueue.main.async {
                    application.registerForRemoteNotifications()
                }
            }
        }

        Messaging.messaging().delegate = self
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Messaging.messaging().apnsToken = deviceToken
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        // Bridge to Kotlin via FcmTokenProvider
        // This requires exposing FcmTokenProvider.instance from the ComposeApp framework
        // The Kotlin side handles this via FcmTokenProvider.Companion.instance
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

- [ ] **Step 5: Verify iOS compiles**

```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/iosMain/ iosApp/iosApp/iOSApp.swift
git commit -m "feat: add iOS FCM token provider, AVFoundation QR scanner, and Firebase setup"
```

---

## Task 13: Foreground Push Refresh (Android)

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/MainActivity.kt`

- [ ] **Step 1: Add broadcast receiver for foreground notification refresh**

Replace `composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/MainActivity.kt`:

```kotlin
package com.foxugly.pushit_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.foxugly.pushit_app.data.storage.TokenStorage
import com.foxugly.pushit_app.platform.FcmTokenProvider

class MainActivity : ComponentActivity() {

    private var notificationReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val tokenStorage = TokenStorage(this)
        val fcmTokenProvider = FcmTokenProvider.instance

        setContent {
            var refreshTrigger by remember { mutableIntStateOf(0) }

            DisposableEffect(Unit) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        refreshTrigger++
                    }
                }
                val filter = IntentFilter(PushItFirebaseService.ACTION_NOTIFICATION_RECEIVED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(receiver, filter)
                }
                notificationReceiver = receiver

                onDispose {
                    notificationReceiver?.let { unregisterReceiver(it) }
                    notificationReceiver = null
                }
            }

            App(
                tokenStorage = tokenStorage,
                fcmTokenProvider = fcmTokenProvider,
            )
        }
    }
}
```

Note: The `refreshTrigger` from the broadcast needs to be passed into `App()`. Update the `App` composable signature to accept it:

Update `App.kt` — add `refreshTrigger` parameter:

In `composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/App.kt`, change the signature to:

```kotlin
@Composable
fun App(
    tokenStorage: TokenStorage,
    fcmTokenProvider: FcmTokenProvider,
    externalRefreshTrigger: Int = 0,
)
```

And replace the internal `refreshTrigger` state with:

```kotlin
var internalRefreshCount by remember { mutableIntStateOf(0) }
val refreshTrigger = externalRefreshTrigger + internalRefreshCount
```

Then in `MainActivity.kt`, pass it:

```kotlin
App(
    tokenStorage = tokenStorage,
    fcmTokenProvider = fcmTokenProvider,
    externalRefreshTrigger = refreshTrigger,
)
```

And in `MainViewController.kt` (iOS), the default `0` is used since iOS handles foreground notifications differently (via the delegate).

- [ ] **Step 2: Verify Android builds**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/foxugly/pushit_app/MainActivity.kt \
       composeApp/src/commonMain/kotlin/com/foxugly/pushit_app/App.kt
git commit -m "feat: add foreground push notification refresh via broadcast receiver"
```

---

## Task 14: Update CLAUDE.md & README

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md`

- [ ] **Step 1: Update CLAUDE.md with final architecture**

Update `CLAUDE.md` to reflect the actual implemented structure — the file already has good content from the init step, but verify it matches the final code (especially file paths and library list).

- [ ] **Step 2: Update README.md**

Replace `README.md` with setup instructions:

```markdown
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
3. **Android:** Download `google-services.json` → place in `composeApp/`
4. **iOS:** Download `GoogleService-Info.plist` → place in `iosApp/iosApp/`

### Backend URL

The default API base URL is `http://10.0.2.2:8000/api/v1/` (Android emulator → localhost).

For physical devices or different environments, update the `baseUrl` parameter in `PushItApi.kt`.

### Build & Run

**Android:**
```bash
./gradlew :composeApp:assembleDebug
# Or use the run configuration in your IDE
```

**iOS:**
Open `iosApp/` in Xcode and run.

## Usage

1. Register or login with your PushIT Server credentials
2. Scan the QR code containing your application's `apt_` token (found in the PushIT dashboard)
3. The app registers your device for push notifications automatically
4. Incoming notifications appear in the notification list
```

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs: update README with setup instructions and CLAUDE.md with final architecture"
```

---

## Task Summary

| Task | Description | Dependencies |
|------|-------------|-------------|
| 1 | Strip web targets, update Gradle dependencies | None |
| 2 | Data models & serialization | Task 1 |
| 3 | Token storage (expect/actual) | Task 1 |
| 4 | Ktor API client & auth interceptor | Tasks 2, 3 |
| 5 | Repositories | Task 4 |
| 6 | Navigation & platform interfaces | Tasks 1, 3 |
| 7 | UI components & login/register screens | Tasks 5, 6 |
| 8 | Notification list & detail screens | Tasks 5, 6 |
| 9 | QR scanner & settings screens | Tasks 3, 6 |
| 10 | Root app navigation wiring | Tasks 7, 8, 9 |
| 11 | Android platform implementations | Tasks 6, 10 |
| 12 | iOS platform implementations | Tasks 6, 10 |
| 13 | Foreground push refresh (Android) | Task 11 |
| 14 | Update docs | Task 13 |
