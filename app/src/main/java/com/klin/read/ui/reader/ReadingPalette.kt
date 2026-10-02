package com.klin.read.ui.reader

import androidx.compose.ui.graphics.Color
import com.klin.read.data.ReaderTheme

/**
 * Reading-surface colours.
 *
 * Kept separate from the app palette because the reading surface follows the
 * user's chosen theme even when the rest of the app uses the system theme.
 *
 * The values are intentionally literal rather than Material roles: the reader must
 * be able to render a sepia page while the rest of the app is in dark mode, and a
 * Material role cannot express that. The literals are therefore a deliberate
 * exception to the "no hardcoded colours in UI" rule, confined to this file.
 */
data class ReadingPalette(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val divider: Color
)

/**
 * The palette as raw ARGB longs.
 *
 * Separate from [ReadingPalette] so the colour values and their contrast can be
 * verified by the JVM unit tests. `Color` is a Compose type whose constructor
 * lives in the Android artifact, which is not on this project's unit-test
 * classpath -- a test that touches `Color` fails to load at all. Keeping the
 * numbers here means the contrast assertions actually run instead of silently
 * never executing.
 */
internal data class ReadingPaletteArgb(
    val background: Long,
    val text: Long,
    val secondary: Long,
    val divider: Long
)

/*
 * Contrast audit.
 *
 * Every value in these palettes was originally chosen by eye and never measured.
 * Two of the four roles were wrong, and both were wrong in the same direction --
 * too faint:
 *
 *   `secondary` is used for real content (chapter-end prompts, reader chrome
 *   labels, search placeholders), so WCAG 2.1 AA body text (4.5:1) applies. The
 *   original greys fell short -- the worst measured ~3.3:1. Reselecting them put
 *   the three themes at 5.4:1 / 7.3:1 / 5.1:1.
 *
 *   `divider` is not a decorative rule: it is the slider track and the sheet drag
 *   handle, so it is a non-text UI component and needs 3:1. The originals
 *   measured ~1.2:1 -- effectively invisible, which is a real usability bug on a
 *   control the user is meant to grab. They now measure 3.3:1 / 3.6:1 / 3.4:1.
 *
 * `text` already cleared AA on all three themes and is unchanged (16.6 / 10.7 /
 * 9.4:1).
 *
 * `ReadingPaletteContrastTest` asserts all of these and prints the measured
 * ratios, so this comment cannot drift from the code.
 */
internal fun paletteArgbFor(theme: ReaderTheme): ReadingPaletteArgb = when (theme) {
    ReaderTheme.LIGHT -> ReadingPaletteArgb(
        background = 0xFFFCFBF9,
        text = 0xFF1C1B19,
        secondary = 0xFF6B6760,
        divider = 0xFF8F8A82
    )
    ReaderTheme.DARK -> ReadingPaletteArgb(
        background = 0xFF141414,
        text = 0xFFC9C5BF,
        secondary = 0xFFA8A39B,
        divider = 0xFF6E6E6E
    )
    ReaderTheme.SEPIA -> ReadingPaletteArgb(
        background = 0xFFF2E8D5,
        text = 0xFF43382A,
        secondary = 0xFF6B5F49,
        divider = 0xFF8A7A5C
    )
}

fun paletteFor(theme: ReaderTheme): ReadingPalette {
    val a = paletteArgbFor(theme)
    return ReadingPalette(
        background = Color(a.background),
        text = Color(a.text),
        secondary = Color(a.secondary),
        divider = Color(a.divider)
    )
}

/**
 * WCAG 2.1 relative-luminance contrast ratio between two ARGB colours.
 *
 * Re-exported from `ui.design` so the reading palette's tests and the app
 * palette's tests share one implementation; two copies would eventually disagree.
 */
internal fun contrastRatio(argbA: Long, argbB: Long): Float =
    com.klin.read.ui.design.contrastRatio(argbA, argbB)
