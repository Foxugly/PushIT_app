package com.foxugly.pushit_app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Fleet brand theme — mirrors the Foxugly / TrainingManager / QuizOnline web
 * charte (emerald accent over slate neutrals, navy brand header) so the native
 * app reads as part of the same family. Light-only today: the web fronts are
 * light, and the baked-navy Foxugly logo only reads on a light surface.
 *
 * The palette is mapped onto Material 3 roles so every existing
 * `MaterialTheme.colorScheme.*` consumer (buttons, status badges, focus rings)
 * picks it up without touching call sites. The navy brand header isn't a
 * Material role, so it's applied explicitly via [pushItTopAppBarColors].
 */

// Emerald accent (web `--accent` / `--accent-strong`). The strong shade is the
// button fill — white-on-#059669 clears the contrast bar that #10b981 fails.
private val EmeraldStrong = Color(0xFF059669)
private val Emerald = Color(0xFF10B981)
private val EmeraldContainer = Color(0xFFD1FAE5) // emerald-100
private val OnEmeraldContainer = Color(0xFF065F46) // emerald-800

// Slate neutrals (web text + surfaces).
private val Slate900 = Color(0xFF0F172A)
private val Slate800 = Color(0xFF1E293B)
private val Slate500 = Color(0xFF64748B)
private val Slate300 = Color(0xFFCBD5E1)
private val Slate200 = Color(0xFFE2E8F0)
private val Slate50 = Color(0xFFF8FAFC)

// Sky accent for the "sent" (in-transit) status badge.
private val Sky = Color(0xFF0EA5E9)
private val SkyContainer = Color(0xFFE0F2FE)
private val OnSkyContainer = Color(0xFF075985)

// Foxugly brand navy — same hue as the baked-in logo, used for the app's top
// bar to echo the web topmenu's navy header.
val BrandNavy = Color(0xFF1B1A30)

private val PushItLightColors = lightColorScheme(
    primary = EmeraldStrong,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = Emerald,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = OnEmeraldContainer,
    tertiary = Sky,
    onTertiary = Color.White,
    tertiaryContainer = SkyContainer,
    onTertiaryContainer = OnSkyContainer,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate800,
    surfaceVariant = Slate50,
    onSurfaceVariant = Slate500,
    outline = Slate300,
    outlineVariant = Slate200,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
)

/** Applies the fleet brand palette to the whole app. */
@Composable
fun PushItTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PushItLightColors, content = content)
}

/**
 * Navy brand colors for the top app bar (white title + icons over [BrandNavy]),
 * echoing the web topmenu. Apply via `TopAppBar(colors = pushItTopAppBarColors())`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun pushItTopAppBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(
        containerColor = BrandNavy,
        titleContentColor = Color.White,
        navigationIconContentColor = Color.White,
        actionIconContentColor = Color.White,
    )
