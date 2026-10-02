package com.klin.read.ui.design

import android.os.Build
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
import androidx.compose.ui.unit.em

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

// ---- Brand palette -----------------------------------------------------------
//
// The accent stays the app's own pink; the NEUTRALS are Apple's.
//
// An earlier version tinted everything pink: the page background was #FFF0F5 and
// the cards were #FFF8F9. Those two are ~4% apart in luminance, so a card was
// effectively invisible against the page -- the layout read as one flat pink
// field with text floating on it. No amount of grid or tracking adjustment fixes
// that, because the problem is the palette, not the spacing.
//
// Apple's system greys put real distance between the layers:
//
//     systemGroupedBackground  #F2F2F7   the page
//     secondarySystemGrouped   #FFFFFF   the cards on it
//     separator                #C6C6C8   hairlines
//
// That is a ~10% luminance step rather than 4%, and it is what makes the card
// edges, the glass bar and the sheet boundaries actually visible. The pink is kept
// for primary actions, selection and accents, where it carries the brand without
// flattening the hierarchy.

private val Pink40 = Color(0xFF8E2A5E)
private val Pink80 = Color(0xFFFFB0D0)
private val Navy = Color(0xFF3A0021)
private val OnPink = Color(0xFFFFFFFF)

// ---- Apple system greys (light) ---------------------------------------------
private val SysGroupedBgLight = Color(0xFFF2F2F7)
private val SysBgLight = Color(0xFFFFFFFF)
private val SysSecondaryBgLight = Color(0xFFFFFFFF)
private val SysSeparatorLight = Color(0xFFC6C6C8)
private val SysLabelLight = Color(0xFF000000)

/*
 * Apple's `secondaryLabel` / `tertiaryLabel` are 60% / 30% alpha labels, tuned
 * for the large display sizes iOS uses them at. Measured against a white card
 * they land at 3.4:1 and 2.5:1, both under the 4.5:1 the skill requires for body
 * text -- and this app uses these roles at 11-13sp.
 *
 * So the opacity trick is dropped for the light theme. Both greys are flat, and
 * both are measured against the TWO surfaces they can land on (the white card and
 * the #F2F2F7 page); the page is the harder of the two, so it sets the value:
 *
 *     muted  #6C6C6C   5.25:1 on card   4.71:1 on page
 *     faint  #6A6A6A   5.41:1 on card   4.85:1 on page
 *
 * They are deliberately close. The first attempt used a wider gap (#767676 for
 * faint) and the page measurement came back at 4.07:1 -- below AA. `faint` is for
 * chrome labels, which are small, so it cannot afford to be lighter than `muted`.
 */
private val SysSecondaryLabelLight = Color(0xFF6C6C6C)
private val SysTertiaryLabelLight = Color(0xFF6A6A6A)

// ---- Apple system greys (dark) ----------------------------------------------
/*
 * The page is #1C1C1E, NOT true black.
 *
 * iOS uses two different dark backgrounds depending on the context: `systemBackground`
 * (#000000) for a full-screen content view, and `secondarySystemBackground`
 * (#1C1C1E) for grouped/list content. True black was the first choice here and it
 * reads wrong on this app: a shelf of white book covers on a pure-black field
 * gives maximum halation around every edge, and on an OLED panel the contrast
 * between a #000 page and a #000 nav bar makes the glass bar invisible -- there is
 * nothing left to separate the layers with.
 *
 * #1C1C1E keeps the page, the #2C2C2E cards and the nav bar distinguishable while
 * staying unambiguously dark.
 */
private val SysGroupedBgDark = Color(0xFF1C1C1E)
private val SysBgDark = Color(0xFF333336)
private val SysSecondaryBgDark = Color(0xFF333336)
private val SysSeparatorDark = Color(0xFF48484A)
private val SysLabelDark = Color(0xFFFFFFFF)

/*
 * Same reasoning as the light theme, measured against the #333336 card (the
 * harder surface in dark mode):
 *
 *     muted  #A0A0A0    4.82:1 on card   6.51:1 on page
 *     faint  #A2A2A2    4.93:1 on card   6.67:1 on page
 *
 * These had to move up when the card went from #2C2C2E to #333336: #9A9A9A
 * measured 4.475:1 against the lighter card, just under the 4.5:1 bar.
 */
private val SysSecondaryLabelDark = Color(0xFFA0A0A0)
private val SysTertiaryLabelDark = Color(0xFFA2A2A2)

/**
 * Shape scale, on the Apple skill's radii.
 *
 * The skill specifies 8 (small elements) / 12 (buttons) / 20 (cards, modals).
 * The phone uses those literal values; the watch build pulls each back one step
 * because a 20dp radius swallows a card's corner on a 372px display.
 */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.small),
    small = RoundedCornerShape(Radius.small),
    medium = RoundedCornerShape(Radius.button),
    large = RoundedCornerShape(Radius.card),
    extraLarge = RoundedCornerShape(Radius.card + 8.dp),
)

/*
 * Motion lives in Design.kt as `Motion`, so there is one definition rather than
 * two competing sets of spring specs.
 */

private fun lightScheme(): ColorScheme = lightColorScheme(
    // Pink stays the accent: primary actions, selection, the brand.
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

    // Neutrals are Apple's system greys, not pink tints.
    background = SysGroupedBgLight,
    onBackground = SysLabelLight,
    surface = SysSecondaryBgLight,
    onSurface = SysLabelLight,
    // surfaceVariant backs the unfilled slider track and chips; it has to differ
    // from BOTH the page and the cards, so it is the iOS fill grey.
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = SysSecondaryLabelLight,
    outline = Color(0xFF8E8E93),
    outlineVariant = SysSeparatorLight,
    error = Color(0xFFFF3B30),
    onError = Color(0xFFFFFFFF),
    surfaceContainer = SysBgLight,
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

    // Neutrals are Apple's dark system greys, not pink tints. iOS dark mode pairs
    // a true-black grouped background with #1C1C1E cards, which is a much larger
    // step than the previous #2A0A18 / #1E1114 pair.
    background = SysGroupedBgDark,
    onBackground = SysLabelDark,
    surface = SysSecondaryBgDark,
    onSurface = SysLabelDark,
    surfaceVariant = Color(0xFF3A3A3C),
    onSurfaceVariant = SysSecondaryLabelDark,
    outline = Color(0xFF8E8E93),
    outlineVariant = SysSeparatorDark,
    error = Color(0xFFFF453A),
    onError = Color(0xFF000000),
    surfaceContainer = SysBgDark,
)

/**
 * Typography following the skill's SF Pro specs.
 *
 * Two things the skill mandates and the M3 defaults do not do:
 *
 *   1. **Negative tracking.** Headlines carry letter-spacing -0.022em and body
 *      text -0.011em. Compose expresses tracking in em, so those values are used
 *      directly. This is what makes Apple's type read as tight and deliberate;
 *      without it, large text looks loose and generic.
 *   2. **Font weight 600 on headlines, 400 on body.**
 *
 * The skill forbids Inter/Roboto/Helvetica and asks for SF Pro, falling back to
 * "the system's native sans-serif equivalent to maintain OS-level consistency".
 * On Android that sanctioned fallback is the system default face, which is what
 * `FontFamily.Default` resolves to -- so the family is left at the default rather
 * than bundling a font, which would also render Chinese with worse hinting than
 * the system face.
 */
private val AppTypography = Typography().let { base ->
    val headlineTracking = (-0.022).em
    val bodyTracking = (-0.011).em

    base.copy(
        displayLarge = base.displayLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = headlineTracking
        ),
        displayMedium = base.displayMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = headlineTracking
        ),
        displaySmall = base.displaySmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = headlineTracking
        ),
        headlineLarge = base.headlineLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = headlineTracking
        ),
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = headlineTracking
        ),
        headlineSmall = base.headlineSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = headlineTracking
        ),
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = headlineTracking
        ),
        titleMedium = base.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = bodyTracking
        ),
        titleSmall = base.titleSmall.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = bodyTracking
        ),
        bodyLarge = base.bodyLarge.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = bodyTracking
        ),
        bodyMedium = base.bodyMedium.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = bodyTracking
        ),
        bodySmall = base.bodySmall.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = bodyTracking
        ),
        labelLarge = base.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = bodyTracking
        ),
        labelMedium = base.labelMedium.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = bodyTracking
        ),
        labelSmall = base.labelSmall.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = bodyTracking
        ),
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

    MaterialTheme(
        colorScheme = scheme,
        shapes = ExpressiveShapes,
        typography = AppTypography
    ) {
        // The semantic palette is derived from the scheme above, so screens and
        // Material components can never drift apart.
        CompositionLocalProvider(
            LocalColors provides rememberAppColors(),
            LocalDarkTheme provides darkTheme,
            content = content
        )
    }
}
