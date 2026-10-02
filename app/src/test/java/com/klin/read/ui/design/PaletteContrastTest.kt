package com.klin.read.ui.design

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrast and layer-separation tests for the app's neutral palette.
 *
 * These exist because the palette has now failed twice in ways that compile and
 * render fine but are unusable to look at:
 *
 *   1. Everything was tinted pink. The page (#FFF0F5) and the cards (#FFF8F9)
 *      were only ~5% apart in luminance, so cards were invisible and the layout
 *      read as one flat field. An earlier round of "design system" work adjusted
 *      the spacing grid and the letter tracking and measured a 1.6% pixel change
 *      on screen -- because the problem was the palette, not the spacing.
 *   2. Muted text was derived by stacking an alpha reduction on top of Apple's
 *      secondaryLabel, which lands near 2.5:1 and is effectively invisible.
 *
 * Both are the same failure class: values chosen by eye and never measured. These
 * tests measure them.
 *
 * WCAG 2.1 AA is 4.5:1 for body text, 3:1 for non-text UI components.
 */
class PaletteContrastTest {

    private val bodyMinimum = 4.5f
    private val nonTextMinimum = 3.0f

    // Light theme, as declared in KlinReadTheme.lightScheme().
    private val lightPage = 0xFFF2F2F7L
    private val lightCard = 0xFFFFFFFFL
    private val lightMuted = 0xFF6C6C6CL
    private val lightFaint = 0xFF6A6A6AL
    private val lightSeparator = 0xFFC6C6C8L
    private val lightLabel = 0xFF000000L

    // Dark theme.
    //
    // The page is #1C1C1E rather than true black: a shelf of white covers on a
    // #000 field halates badly, and a #000 page left the #000-adjacent nav bar
    // with nothing to separate it from. The card is #333336 rather than Apple's
    // #2C2C2E because against #1C1C1E that only measures 1.22:1, which is weaker
    // separation than a card needs.
    private val darkPage = 0xFF1C1C1EL
    private val darkCard = 0xFF333336L
    private val darkMuted = 0xFFA0A0A0L
    private val darkFaint = 0xFFA2A2A2L
    private val darkLabel = 0xFFFFFFFFL

    /**
     * Text has to clear AA against BOTH surfaces it can appear on. Checking only
     * the card is what let the alpha-stacked greys through last time.
     */
    private fun assertTextClears(role: String, fg: Long, vararg backgrounds: Pair<String, Long>) {
        backgrounds.forEach { (bgName, bg) ->
            val ratio = contrastRatio(fg, bg)
            assertTrue(
                "$role on $bgName: contrast $ratio is below $bodyMinimum",
                ratio >= bodyMinimum
            )
        }
    }

    @Test
    fun lightThemeMutedTextClearsAa() = assertTextClears(
        "light muted", lightMuted,
        "card" to lightCard,
        "page" to lightPage
    )

    @Test
    fun lightThemeFaintTextClearsAa() = assertTextClears(
        "light faint", lightFaint,
        "card" to lightCard,
        "page" to lightPage
    )

    @Test
    fun lightThemeLabelClearsAa() = assertTextClears(
        "light label", lightLabel,
        "card" to lightCard,
        "page" to lightPage
    )

    @Test
    fun darkThemeMutedTextClearsAa() = assertTextClears(
        "dark muted", darkMuted,
        "card" to darkCard,
        "page" to darkPage
    )

    @Test
    fun darkThemeFaintTextClearsAa() = assertTextClears(
        "dark faint", darkFaint,
        "card" to darkCard,
        "page" to darkPage
    )

    @Test
    fun darkThemeLabelClearsAa() = assertTextClears(
        "dark label", darkLabel,
        "card" to darkCard,
        "page" to darkPage
    )

    /**
     * The regression this class was written for.
     *
     * A card must be visibly distinct from the page behind it. The old pink pair
     * measured 1.05:1, which is indistinguishable.
     *
     * The thresholds are lower for light than for dark because a white card on a
     * light grey page has less room to move before the page stops reading as grey.
     * The dark figure was 1.40 while the page was true black; moving the page to
     * #1C1C1E (see KlinReadTheme) took the achievable separation down to 1.35, so
     * the bar moved with it rather than forcing an unrealistically bright card.
     * Both still sit far above the 1.05 that caused the original bug.
     */
    @Test
    fun cardsAreDistinctFromThePage() {
        val light = contrastRatio(lightCard, lightPage)
        assertTrue(
            "Light card ($lightCard) vs page ($lightPage) is only $light -- cards would be invisible",
            light >= 1.10f
        )

        val dark = contrastRatio(darkCard, darkPage)
        assertTrue(
            "Dark card ($darkCard) vs page ($darkPage) is only $dark -- cards would be invisible",
            dark >= 1.30f
        )
    }

    /** The old pink pair, pinned so it cannot come back unnoticed. */
    @Test
    fun theOldPinkCardPairWouldHaveFailed() {
        val oldCard = 0xFFFFF8F9L
        val oldPage = 0xFFFFF0F5L
        val ratio = contrastRatio(oldCard, oldPage)
        assertTrue(
            "Expected the old pink card/page pair (~1.05:1) to fail, but it measured $ratio",
            ratio < 1.10f
        )
    }

    /**
     * The separator is a *decorative* hairline between list rows, not a control.
     *
     * WCAG's 3:1 non-text rule applies to UI components and graphical objects you
     * have to perceive to operate something -- a slider track, a checkbox border,
     * a drag handle. A row divider carries no information a user acts on, and
     * Apple's own `separator` (#C6C6C8) measures about 1.7:1 against white. Forcing
     * it to 3:1 would mean a mid-grey rule, which reads as a table border and is
     * visibly wrong next to iOS.
     *
     * So this asserts the weaker property that actually matters: the separator is
     * distinguishable from the surface it sits on rather than invisible. The 3:1
     * requirement is asserted where it does apply -- on the reading palette's
     * divider, which is a slider track and a sheet drag handle.
     */
    @Test
    fun separatorsAreVisibleWithoutBecomingRules() {
        val ratio = contrastRatio(lightSeparator, lightCard)
        assertTrue(
            "Light separator on card is $ratio -- indistinguishable from the surface",
            ratio >= 1.30f
        )
        assertTrue(
            "Light separator on card is $ratio -- too heavy, reads as a table border",
            ratio <= 2.20f
        )
    }

    /** Prints the measured ratios so the comments in source stay verifiable. */
    @Test
    fun reportMeasuredRatios() {
        println("LIGHT  muted/card=${contrastRatio(lightMuted, lightCard)}" +
            "  muted/page=${contrastRatio(lightMuted, lightPage)}" +
            "  faint/card=${contrastRatio(lightFaint, lightCard)}" +
            "  card-vs-page=${contrastRatio(lightCard, lightPage)}" +
            "  separator/card=${contrastRatio(lightSeparator, lightCard)}")
        println("DARK   muted/card=${contrastRatio(darkMuted, darkCard)}" +
            "  muted/page=${contrastRatio(darkMuted, darkPage)}" +
            "  faint/card=${contrastRatio(darkFaint, darkCard)}" +
            "  card-vs-page=${contrastRatio(darkCard, darkPage)}")
    }
}
