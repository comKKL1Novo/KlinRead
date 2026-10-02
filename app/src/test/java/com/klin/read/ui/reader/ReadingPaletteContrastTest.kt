package com.klin.read.ui.reader

import com.klin.read.data.ReaderTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrast regression tests for the reading surfaces.
 *
 * These exist because the previous values were picked by eye and two of the four
 * roles fell below their WCAG minimum. A colour is exactly the kind of value that
 * regresses silently: the code still compiles, the screen still renders, and the
 * text is merely hard to read.
 *
 * The tests work on raw ARGB longs rather than `Color` because `Color`'s
 * constructor lives in the Android Compose artifact, which is not on this
 * project's unit-test classpath. A contrast test that cannot run is worse than
 * none, because it looks like coverage.
 *
 * WCAG 2.1 AA for body text is 4.5:1; for non-text elements such as the hairline
 * divider and slider track it is 3:1.
 */
class ReadingPaletteContrastTest {

    private val bodyMinimum = 4.5f
    private val nonTextMinimum = 3.0f

    private fun check(theme: ReaderTheme) {
        val p = paletteArgbFor(theme)
        val textRatio = contrastRatio(p.text, p.background)
        val secondaryRatio = contrastRatio(p.secondary, p.background)

        assertTrue(
            "$theme: body text contrast $textRatio is below $bodyMinimum",
            textRatio >= bodyMinimum
        )
        assertTrue(
            "$theme: secondary text contrast $secondaryRatio is below $bodyMinimum",
            secondaryRatio >= bodyMinimum
        )
    }

    @Test
    fun lightThemeTextClearsWcagAa() = check(ReaderTheme.LIGHT)

    @Test
    fun darkThemeTextClearsWcagAa() = check(ReaderTheme.DARK)

    @Test
    fun sepiaThemeTextClearsWcagAa() = check(ReaderTheme.SEPIA)

    /** The divider is a slider track and drag handle, so 3:1 applies. */
    @Test
    fun dividersAreVisibleOnEveryTheme() {
        ReaderTheme.entries.forEach { theme ->
            val p = paletteArgbFor(theme)
            val ratio = contrastRatio(p.divider, p.background)
            assertTrue(
                "$theme: divider contrast $ratio is below $nonTextMinimum -- the track would be invisible",
                ratio >= nonTextMinimum
            )
        }
    }

    /**
     * Guards the specific bug this class was written for: the old secondary grey
     * on the old dark background.
     */
    @Test
    fun theOldDarkSecondaryGreyWouldHaveFailed() {
        val oldSecondary = 0xFF7A756EL
        val oldBackground = 0xFF141414L
        val ratio = contrastRatio(oldSecondary, oldBackground)
        assertTrue(
            "Expected the old pairing to fail the $bodyMinimum bar, but it measured $ratio",
            ratio < bodyMinimum
        )
    }

    /** Prints the measured ratios, so the figures in the source are verifiable. */
    @Test
    fun reportMeasuredRatios() {
        ReaderTheme.entries.forEach { theme ->
            val p = paletteArgbFor(theme)
            println(
                "$theme  text=${contrastRatio(p.text, p.background)}  " +
                    "secondary=${contrastRatio(p.secondary, p.background)}  " +
                    "divider=${contrastRatio(p.divider, p.background)}"
            )
        }
    }

    /**
     * Sanity check on the formula: black on white is the known 21:1 maximum.
     * Without this, a broken luminance function could make every other assertion
     * meaningless.
     */
    @Test
    fun formulaMatchesTheKnownExtremes() {
        val blackOnWhite = contrastRatio(0xFF000000L, 0xFFFFFFFFL)
        assertTrue(
            "Black on white should be ~21:1 but measured $blackOnWhite",
            blackOnWhite > 20.9f && blackOnWhite < 21.1f
        )
        val selfContrast = contrastRatio(0xFF808080L, 0xFF808080L)
        assertTrue(
            "A colour against itself should be 1:1 but measured $selfContrast",
            selfContrast > 0.99f && selfContrast < 1.01f
        )
    }
}
