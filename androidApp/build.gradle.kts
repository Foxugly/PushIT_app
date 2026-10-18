import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

// Release signing is provided out-of-band — via a git-ignored keystore.properties
// at the repo root, or env vars (CI). The keystore itself is NEVER committed. When
// nothing is configured the release build still runs R8 but stays unsigned (so the
// minified APK can be validated without a keystore).
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun releaseProp(name: String): String? = keystoreProperties.getProperty(name) ?: System.getenv(name)
val hasReleaseSigning = releaseProp("RELEASE_STORE_FILE") != null

// Sentry DSN (project pushit-app, org foxugly-srl, de.sentry.io): Gradle property
// -PPUSHIT_SENTRY_DSN=… or env var PUSHIT_SENTRY_DSN (CI secret). Empty ⇒ the app
// does not initialise Sentry (local / PR builds).
val sentryDsn: String = (findProperty("PUSHIT_SENTRY_DSN") as String?)
    ?.takeIf { it.isNotBlank() }
    ?: System.getenv("PUSHIT_SENTRY_DSN").orEmpty()

// Versioning. versionName: gradle.properties `pushit.versionName` (single source).
// versionCode must strictly increase on every Play upload; resolved in order:
//   1. -PPUSHIT_VERSION_CODE / env PUSHIT_VERSION_CODE (explicit override),
//   2. env GITHUB_RUN_NUMBER (CI — the release workflow's run counter, monotonic),
//   3. `git rev-list --count HEAD` (local builds),
//   4. 1 (no git available).
// Only AABs built by .github/workflows/release.yml go to Play, so the CI counter
// is the one that matters; local codes are for side-loaded test builds.
val appVersionName: String = (findProperty("pushit.versionName") as String?) ?: "0.0.0"
fun envOrProp(name: String): String? =
    (findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: System.getenv(name)?.takeIf { it.isNotBlank() }
val gitCommitCount: Int? = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toIntOrNull()
}.getOrNull()
val appVersionCode: Int =
    envOrProp("PUSHIT_VERSION_CODE")?.toIntOrNull()
        ?: envOrProp("GITHUB_RUN_NUMBER")?.toIntOrNull()
        ?: gitCommitCount
        ?: 1

android {
    namespace = "com.foxugly.pushit_app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.foxugly.pushit_app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // See "Versioning" above: never hard-code these.
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "SENTRY_DSN", "\"${sentryDsn.replace("\"", "")}\"")
    }
    buildFeatures {
        // BuildConfig.DEBUG is read in MainActivity to enable HTTP logging only in
        // debug builds.
        buildConfig = true
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseProp("RELEASE_STORE_FILE")!!)
                storePassword = releaseProp("RELEASE_STORE_PASSWORD")
                keyAlias = releaseProp("RELEASE_KEY_ALIAS")
                keyPassword = releaseProp("RELEASE_KEY_PASSWORD")
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            // R8: shrink + obfuscate. Keep rules in proguard-rules.pro cover
            // kotlinx.serialization (the API DTOs) + Ktor + the FCM service.
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.uiToolingPreview)
    implementation(platform(libs.firebase.bom))
    implementation(libs.sentry.android.core)
    debugImplementation(libs.compose.uiTooling)
}
