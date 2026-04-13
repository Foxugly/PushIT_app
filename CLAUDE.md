# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PushIT App — a Kotlin Multiplatform (KMP) mobile client for the PushIT Server (Django REST API managing push notifications via Firebase Cloud Messaging). Targets Android and iOS using Compose Multiplatform for shared UI.

Package: `com.foxugly.pustit_app`

## Build Commands

```bash
# Android
./gradlew :composeApp:assembleDebug          # Build debug APK
./gradlew :composeApp:installDebug           # Build and install on connected device/emulator

# iOS — open iosApp/ in Xcode and run from there

# Tests
./gradlew :composeApp:allTests               # Run all tests
./gradlew :composeApp:testDebugUnitTest      # Android unit tests only
```

On Windows, use `.\gradlew.bat` instead of `./gradlew`.

## Architecture

Single-module KMP project (`composeApp`) with shared Compose UI. No separate `shared` library module.

- **commonMain** — all shared code: Ktor networking, data models, ViewModels, Compose UI screens
- **androidMain** — Android platform specifics: `MainActivity`, Firebase `FirebaseMessagingService`, CameraX + ML Kit QR scanner, `EncryptedSharedPreferences` for token storage
- **iosMain** — iOS platform specifics: `MainViewController`, FCM token handling, AVFoundation QR scanner, Keychain for token storage
- **iosApp/** — SwiftUI entry point that hosts the Compose view via `ComposeView` (UIViewControllerRepresentable)

### Platform Boundaries (expect/actual)

Cross-platform abstractions use `expect/actual` declarations in `commonMain` with implementations in `androidMain` and `iosMain`:
- `TokenStorage` — encrypted key-value storage (JWT tokens, app token)
- `QrScanner` — camera-based QR code scanning composable
- `FcmTokenProvider` — FCM token retrieval and observation

### Navigation

State-driven manual navigation using `sealed class Screen` + `when` in the root composable. No navigation library.

### Backend API

Base URL: `http://127.0.0.1:8000/api/v1/` (PushIT Server)

- JWT auth: login, register, refresh, logout, me
- Device registration: `POST /devices/link/` with `X-App-Token` header
- Notifications: paginated list, detail, stats (read-only)

### Key Libraries

- **Ktor** — HTTP client (KMP-compatible)
- **kotlinx.serialization** — JSON serialization
- **multiplatform-settings** — encrypted token storage
- **Compose Multiplatform 1.10.3** / **Kotlin 2.3.20**

## Gradle

Uses version catalogs (`gradle/libs.versions.toml`). Configuration cache and build caching enabled.
