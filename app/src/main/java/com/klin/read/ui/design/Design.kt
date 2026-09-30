package com.klin.read.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semantic colour names, resolved from the Material 3 scheme.
 *
 * This used to be a standalone palette with its own hardcoded light/dark hex
 * values, which meant the app carried a second colour system in parallel with
 * Material 3 — the exact anti-pattern the design guidance calls out
 * ("use Material3 tokens, not hardcoded values"). Every colour below is now a
 * role from [MaterialTheme.colorScheme], so:
 *
 *   - there is one source of truth for colour,
 *   - dark mode and dynamic colour work without a second set of constants,
 *   - a theme change cannot leave one screen behind.
 *
 * The property names are kept because they read well at the call site and are
 * used across every screen; only their backing changes.
 */
data class AppColors(
    val canvas: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val divider: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val accent: Color,
    val accentInk: Color,
    val danger: Color
)

/**
 * Builds the semantic palette from the active Material 3 scheme.
 *
 * Read inside a composable, because it depends on [MaterialTheme]; there is no
 * longer any static Light/Dark constant to fall back to.
 */
@Composable
fun rememberAppColors(): AppColors {
    val s = MaterialTheme.colorScheme
    return AppColors(
        canvas = s.background,
        surface = s.surface,
        surfaceMuted = s.surfaceVariant,
        divider = s.outlineVariant,
        ink = s.onSurface,
        inkMuted = s.onSurfaceVariant,
        // Faint text still has to be legible: this is onSurface at reduced
        // emphasis rather than a fixed grey, so it tracks the theme.
        inkFaint = s.onSurfaceVariant.copy(alpha = 0.72f),
        accent = s.primary,
        accentInk = s.onPrimary,
        danger = s.error
    )
}

/**
 * Spacing scale on a strict 4/8dp rhythm.
 *
 * The guidance asks for a mathematical spacing system rather than ad-hoc
 * increments; every value here is a multiple of 4.
 */
object Space {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
}

/**
 * The semantic palette for the current theme.
 *
 * Populated by [KlinReadTheme]; reading it outside that theme is a programming
 * error, so the default throws rather than silently rendering light-mode values
 * on a dark background.
 */
val LocalColors = compositionLocalOf<AppColors> {
    error("LocalColors read outside KlinReadTheme")
}
