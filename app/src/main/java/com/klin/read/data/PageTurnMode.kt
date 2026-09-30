package com.klin.read.data

/**
 * How the reading surface advances from one screen to the next.
 *
 * The names follow what Chinese reading apps call these modes, since that is the
 * vocabulary the screens use.
 *
 * COVER and SIMULATION were removed: neither felt right in practice, and a
 * half-working page-curl is worse than not offering one.
 */
enum class PageTurnMode {
    /** Continuous vertical scrolling. */
    SCROLL,

    /** Horizontal slide: the next page pushes the current one aside. */
    SLIDE
}

/** Preset text sizes, so the reader can offer one-tap choices as well as a slider. */
enum class FontFamilyChoice { SYSTEM, SERIF, SANS }