package com.klin.read.ui.design

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * KlinRead's Material 3 Expressive styling.
 *
 * Why this is hand-built rather than using `MaterialExpressiveTheme`: in
 * material3 1.4.0 (the newest version this project can use) that API — together
 * with `MotionScheme` and `ExperimentalMaterial3ExpressiveApi` — is marked
 * `internal`, so application code cannot call it. It only became public in
 * 1.5.0-alpha, which in turn demands AGP 9.1+ and compileSdk 37. Rather than
 * force a toolchain jump for a visual style, the three things that actually make
 * "Expressive" read as Expressive are applied directly:
 *
 *   1. a saturated, seeded colour scheme (falling back to the wallpaper palette
 *      on Android 12+),
 *   2. a much rounder shape scale than the M3 defaults,
 *   3. springy, spatial motion instead of linear transitions.
 *
 * Behaviour stays byte-for-byte on the stable dependency set.
 */

// ---- Brand palette, seeded from the launcher icon pink ----------------------

private val Pink40 = Color(0xFF8E2A5E)
private val Pink80 = Color(0xFFFFB0D0)
private val Navy = Color(0xFF3A0021)
private val OnPink = Color(0xFFFFFFFF)

/**
 * Expressive shape scale.
 *
 * The signature move of the style is generous corner radii: where Material 3
 * defaults to 4/8/12/16/28dp, these are roughly double at the small end so cards,
 * sheets, and buttons all read as pill-like.
 */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

/**
 * Motion tokens.
 *
 * Expressive motion is spatial and spring-based: things overshoot slightly and
 * settle, rather than easing to a stop. These are the public substitutes for the
 * `MotionScheme` values the internal API would have supplied.
 */
object Motion {
    /** For elements that move across the screen (sheets, page changes). */
    fun <T> spatial(): androidx.compose.animation.core.SpringSpec<T> =
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

    /** For elements that resize or fade in place; no overshoot. */
    fun <T> effects(): androidx.compose.animation.core.SpringSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

    /** Emphasised easing for one-shot reveals. */
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

private fun lightScheme(): ColorScheme = lightColorScheme(
    primary = Pink40,
    onPrimary = OnPink,
    primaryContainer = Color(0xFFFFD9E7),
    onPrimaryContainer = Navy,

    secondary = Color(0xFF8A5069),
    onSecondary = OnPink,
    secondaryContainer = Color(0xFFFFD9E4),
    onSecondaryContainer = Color(0xFF37081F),

    tertiary = Color(0xFF7A5A00),
    onTertiary = OnPink,
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF261A00),

    background = Color(0xFFFFF0F5),
    onBackground = Color(0xFF22191C),
    surface = Color(0xFFFFF8F9),
    onSurface = Color(0xFF22191C),
    surfaceVariant = Color(0xFFF3DDE3),
    onSurfaceVariant = Color(0xFF524348),
    outline = Color(0xFF847379),
    outlineVariant = Color(0xFFD6C2C7),
    error = Color(0xFFB3261E),
    onError = OnPink,
)

private fun darkScheme(): ColorScheme = darkColorScheme(
    primary = Pink80,
    onPrimary = Color(0xFF3A0021),
    primaryContainer = Color(0xFF5C1039),
    onPrimaryContainer = Color(0xFFFFD9E7),

    secondary = Color(0xFFFFB0C9),
    onSecondary = Color(0xFF521D33),
    secondaryContainer = Color(0xFF6C3349),
    onSecondaryContainer = Color(0xFFFFD9E4),

    tertiary = Color(0xFFE9C349),
    onTertiary = Color(0xFF3E2E00),
    tertiaryContainer = Color(0xFF584400),
    onTertiaryContainer = Color(0xFFFFE08A),

    background = Color(0xFF2A0A18),
    onBackground = Color(0xFFF0DEE2),
    surface = Color(0xFF1E1114),
    onSurface = Color(0xFFF0DEE2),
    surfaceVariant = Color(0xFF524348),
    onSurfaceVariant = Color(0xFFD6C2C7),
    outline = Color(0xFF9E8C91),
    outlineVariant = Color(0xFF524348),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/**
 * Slightly heavier weights than the M3 defaults. Expressive type leans bold, and
 * the article text of a reader benefits from the extra presence at small sizes.
 */
private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Whether the app is currently rendering its dark palette. */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun KlinReadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Follow the Android 12+ wallpaper palette instead of the seeded pink. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> darkScheme()
        else -> lightScheme()
    }

    val appColors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = scheme,
        shapes = ExpressiveShapes,
        typography = AppTypography
    ) {
        CompositionLocalProvider(
            LocalColors provides appColors,
            LocalDarkTheme provides darkTheme,
            content = content
        )
    }
}
