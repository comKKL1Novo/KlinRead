package com.klin.read.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Apple UI Design System – Verified: 8pt Grid, SF Pro Typography,
 * Material-Depth, Natural Spring Motion
 *
 * Tokens follow the apple-ui-design skill: an 8pt spacing grid, SF Pro type
 * specs, semantic glass materials, and iOS spring motion. Where the skill
 * specifies web values (px, CSS), the Android equivalents are used and the
 * deviation is noted at the token.
 */

/**
 * Semantic colour names, resolved from the Material 3 scheme.
 *
 * This used to be a standalone palette with its own hardcoded light/dark hex
 * values, which meant the app carried a second colour system in parallel with
 * Material 3. Every colour below is now a role from [MaterialTheme.colorScheme],
 * so there is one source of truth for colour and a theme change cannot leave one
 * screen behind.
 *
 * The glass fields are new: the skill requires elevated surfaces to carry BOTH
 * an rgba fill and a blur, so the material has to be a token rather than
 * something each card improvises.
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
    val danger: Color,

    /** Fill for an elevated surface. Translucent by definition. */
    val glassFill: Color,
    /** Hairline around an elevated surface: 0.5dp, per the skill. */
    val glassBorder: Color,
    /** Drop shadow under an elevated surface. */
    val glassShadow: Color,
    /** Fill for recessed/search inputs: rgba(0,0,0,0.05) light / white 0.1 dark. */
    val fieldFill: Color
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
    val dark = LocalDarkTheme.current
    return AppColors(
        canvas = s.background,
        surface = s.surface,
        surfaceMuted = s.surfaceVariant,
        divider = s.outlineVariant,
        ink = s.onSurface,
        // Two measured greys, both clearing 4.5:1 against the card AND the page;
        // see the label constants in KlinReadTheme. An earlier version stacked an
        // extra alpha reduction on top of Apple's secondaryLabel and dropped faint
        // text to ~2.5:1, which is the invisible-text bug class this project has
        // already hit once.
        inkMuted = s.onSurfaceVariant,
        inkFaint = s.onSurfaceVariant,
        accent = s.primary,
        accentInk = s.onPrimary,
        danger = s.error,

        // The skill's material values, mapped onto this app's surface roles:
        //   light  rgba(255,255,255,0.72) + 0.5px rgba(0,0,0,0.1)
        //   dark   rgba(28,28,30,0.7)     + 0.5px rgba(255,255,255,0.15)
        //
        // The fill is OPAQUE rather than 72% here, which is a deliberate departure
        // from the skill. A translucent white over a #F2F2F7 page resolves to
        // ~#FBFBFC, cutting the card/page luminance step from 1.12:1 to about
        // 1.03:1 -- i.e. it undoes the layer separation the neutral palette was
        // chosen for, and the card edges stop reading. The skill's transparency
        // exists to let a backdrop blur show through; below API 31 there is no
        // blur, so transparency only costs contrast. The card therefore keeps the
        // skill's *colour* (white) at full strength, and the border plus shadow
        // supply the elevation cue.
        glassFill = s.surface,
        // Border and shadow carry the elevation, since the fill is now opaque.
        // Apple's `separator` (#C6C6C8) is the light-mode hairline; the previous
        // 10%-black resolved to a lighter grey than the page in some places.
        glassBorder = if (dark) {
            Color.White.copy(alpha = 0.15f)
        } else {
            Color(0xFFC6C6C8)
        },
        glassShadow = Color.Black.copy(alpha = if (dark) 0.40f else 0.10f),
        // Apple's `tertiarySystemFill` / `secondarySystemFill`. A 5% black over
        // #F2F2F7 was so close to the page that the search field and the chips had
        // no visible boundary; these values give them one.
        //
        // In dark mode this has to sit ABOVE the card (#333336), not below it: the
        // first value here was #3A3A3C, which was lighter than the card and made a
        // search field read as raised instead of recessed. #48484A restores the
        // intended direction.
        fieldFill = if (dark) {
            Color(0xFF48484A)
        } else {
            Color(0xFFE5E5EA)
        }
    )
}

/**
 * Spacing scale on the skill's 8pt grid.
 *
 * The skill mandates multiples of 8. `xs` is the single sanctioned half-step (4dp)
 * for optical nudges inside a component; everything structural is 8 and up. This
 * matches the phone's existing 4/8/16/24/32/48 ladder, which already conformed.
 */
object Space {
    /** Half-step, for optical nudges only. */
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
}

/**
 * Corner radii, straight from the skill: buttons 12, cards/modals 20, small
 * elements 8. The phone uses these literal values -- the watch build pulls them
 * back one step because a 20dp radius eats a card on a 372px screen.
 */
object Radius {
    /** Badges, tags, chips. */
    val small: Dp = 8.dp
    /** Buttons and inputs. */
    val button: Dp = 12.dp
    /** Cards, sheets, modals. */
    val card: Dp = 20.dp
}

/**
 * Motion tokens.
 *
 * The skill asks for `300ms cubic-bezier(0.25,0.1,0.25,1)` for standard
 * transitions and an overshooting `cubic-bezier(0.4,0,0.2,1.4)` for modals. Both
 * are reproduced; Compose expresses the overshoot as a spring, which is the
 * platform-native equivalent and stays interruptible.
 */
object Motion {
    /** Standard transition: the skill's cubic-bezier(0.25,0.1,0.25,1), 300ms. */
    val standard: androidx.compose.animation.core.Easing =
        androidx.compose.animation.core.CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    /** Standard duration in ms, for tween specs. */
    const val STANDARD_MS: Int = 300

    /** For elements that move across the screen (sheets, page changes). */
    fun <T> spatial(): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(
            dampingRatio = 0.75f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        )

    /** Spring pop for modals and sheets: slight overshoot, then settle. */
    fun <T> pop(): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(
            dampingRatio = 0.55f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        )

    /** For elements that resize or fade in place; no overshoot. */
    fun <T> effects(): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(
            dampingRatio = 1f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        )

    /** Emphasised easing for one-shot reveals. */
    val emphasized: androidx.compose.animation.core.Easing =
        androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // ---- Interaction scales, straight from the skill ------------------------

    /** Pressed state. `scale(0.96)` in the skill. */
    const val PRESSED_SCALE: Float = 0.96f

    /** Hover/pointed state. `scale(1.02)` in the skill. */
    const val HOVER_SCALE: Float = 1.02f
}

/**
 * Minimum touch target.
 *
 * The skill sets 44px (the iOS standard); Android's own guidance is 48dp. The
 * skill's 44 is used so both platforms agree and the value is traceable to the
 * spec.
 */
val MinTouchTarget: Dp = 44.dp

/**
 * Structural clearances.
 *
 * These are NOT spacing-scale values: they are distances that depend on the app's
 * own chrome, so they cannot be expressed as a step on the 8pt ladder. Naming them
 * here keeps the number in one place instead of repeated as a literal in every
 * screen (the same 104.dp appeared in two files, and 140.dp in three).
 */
object Clearance {
    /** Scroll content must clear the floating bottom bar. */
    val bottomBar: Dp = 104.dp
    /** Bottom padding for a LazyColumn whose last item sits above the bar. */
    val listBottom: Dp = 140.dp
    /** Reserve for the shelf's pinned import button plus the bar. */
    val shelfBottom: Dp = 170.dp
}

/**
 * Fixed component geometry.
 *
 * Round and icon sizes that the skill does not specify. Grouped so a change to,
 * say, the nav-bar indicator height is one edit rather than four.
 */
object Size {
    /** Bottom-bar selection indicator. */
    val navIndicatorWidth: Dp = 52.dp
    val navIndicatorHeight: Dp = 30.dp
    val navIcon: Dp = 20.dp
    /** Reader sheet drag handle. */
    val sheetHandleWidth: Dp = 34.dp
    val sheetHandleHeight: Dp = 4.dp
    /** Progress bars (import, chapter, book). */
    val progressBarHeight: Dp = 4.dp
    val progressBarThin: Dp = 3.dp
    /** Shelf book cover in the list. */
    val coverWidth: Dp = 62.dp
    val coverHeight: Dp = 88.dp
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
