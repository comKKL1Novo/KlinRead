package com.klin.read.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A monochrome palette, matching the app icon.
 *
 * Everything is black, white or a neutral grey; `accent` is simply the strongest
 * ink rather than a colour. The one exception is `danger`, which stays red so
 * destructive actions are not mistaken for ordinary ones.
 *
 * Keeping the UI greyscale also stops the chrome competing with book covers and
 * reading text for attention.
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

val LightColors = AppColors(
    canvas = Color(0xFFF5F5F3),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFEFEFED),
    divider = Color(0xFFE0E0DE),
    ink = Color(0xFF0A0A0A),
    inkMuted = Color(0xFF6B6B69),
    inkFaint = Color(0xFF9C9C99),
    accent = Color(0xFF0A0A0A),
    accentInk = Color(0xFFFFFFFF),
    danger = Color(0xFF9B3B34)
)

val DarkColors = AppColors(
    canvas = Color(0xFF0A0A0A),
    surface = Color(0xFF161616),
    surfaceMuted = Color(0xFF222222),
    divider = Color(0xFF303030),
    ink = Color(0xFFF2F2F0),
    inkMuted = Color(0xFF9E9E9B),
    inkFaint = Color(0xFF6B6B68),
    accent = Color(0xFFF2F2F0),
    accentInk = Color(0xFF0A0A0A),
    danger = Color(0xFFCC7B72)
)

val LocalColors = compositionLocalOf { LightColors }

/** Spacing scale, so paddings stay consistent instead of ad-hoc. */
object Space {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 14.dp
    val lg: Dp = 20.dp
    val xl: Dp = 28.dp
}

@Composable
fun rememberAppColors(dark: Boolean = isSystemInDarkTheme()): AppColors =
    if (dark) DarkColors else LightColors
