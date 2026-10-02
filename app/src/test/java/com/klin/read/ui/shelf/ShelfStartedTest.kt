package com.klin.read.ui.shelf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the "has this book been opened" rule.
 *
 * Written because the rule was wrong twice, in ways that are easy to miss in
 * review and impossible to miss in use. Both kept a book the reader was actively
 * reading sitting in "未读", reported as "读了没反应":
 *
 *   1. It tested the chapter index alone, and chapter one is index 0.
 *   2. Widening it to "offset > 0 OR chapter > 0" still failed for the common
 *      case, because chapter one STARTS at offset 0 -- so opening a book and
 *      reading the whole first chapter leaves both signals at zero.
 *
 * Hence the explicit `opened` flag, which the reader sets on open. The numeric
 * signals remain as fallbacks so a position saved before the flag existed still
 * counts.
 */
class ShelfStartedTest {

    @Test
    fun aBookNeverOpenedIsNotStarted() {
        assertFalse(hasBeenStarted(opened = false, charOffset = 0, chapterIndex = 0))
    }

    /**
     * The regression this file exists for: chapter one, read to the end, is offset
     * 0 and index 0, so only the flag can report it.
     */
    @Test
    fun readingInsideChapterOneCountsAsStarted() {
        assertTrue(hasBeenStarted(opened = true, charOffset = 0, chapterIndex = 0))
    }

    /** Falls back to the offset, for a position saved before the flag existed. */
    @Test
    fun aSavedOffsetStillCountsAsStarted() {
        assertTrue(hasBeenStarted(opened = false, charOffset = 4200, chapterIndex = 0))
    }

    @Test
    fun beingPastChapterOneStillCountsAsStarted() {
        assertTrue(hasBeenStarted(opened = false, charOffset = 0, chapterIndex = 1))
    }

    @Test
    fun bothSignalsSetCountsAsStarted() {
        assertTrue(hasBeenStarted(opened = true, charOffset = 900, chapterIndex = 3))
    }

    /**
     * Pins the exact case that defeated the first fix, so a future refactor cannot
     * quietly drop the flag and reintroduce it.
     */
    @Test
    fun thePositionOnlyRuleWouldHaveFailedForChapterOne() {
        val positionOnly = (0 > 0) || (0 > 0) // charOffset, chapterIndex
        assertFalse(
            "Position-only signals cannot tell an unopened book from one open at chapter one",
            positionOnly
        )
        assertTrue(
            "The opened flag is what distinguishes them",
            hasBeenStarted(opened = true, charOffset = 0, chapterIndex = 0)
        )
    }
}
