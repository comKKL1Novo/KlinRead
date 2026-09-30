package com.klin.read.ui.music

import com.klin.read.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Music list filtering.
 *
 * These pin down a real bug: search used to be a plain function the screen called
 * during composition, reading the query state directly. Because that read was not
 * observable, the list never recomposed and typing in the box appeared to do
 * nothing. The matching rules themselves are tested here; the reactivity comes
 * from the query being part of a `combine` flow.
 */
class MusicFilterTest {

    private fun track(title: String) = Track(uri = "uri:$title", title = title)

    private val library = listOf(
        track("不遗憾 李荣浩-千斤#2pFz0B"),
        track("M500003Nl6PA0BESMS"),
        track("Bohemian Rhapsody")
    )

    @Test
    fun `blank query returns the whole library`() {
        assertEquals(library, filterTracks(library, ""))
    }

    @Test
    fun `whitespace-only query is treated as blank`() {
        // Otherwise a stray space would hide every track.
        assertEquals(library, filterTracks(library, "   "))
    }

    @Test
    fun `matches a Chinese title substring`() {
        val result = filterTracks(library, "不遗憾")
        assertEquals(1, result.size)
        assertEquals("不遗憾 李荣浩-千斤#2pFz0B", result.first().title)
    }

    @Test
    fun `matches ignoring case`() {
        // Lower-case input must still find an upper-case title.
        val lower = filterTracks(library, "bohemian")
        val upper = filterTracks(library, "BOHEMIAN")
        assertEquals(1, lower.size)
        assertEquals(lower, upper)
    }

    @Test
    fun `matches a substring in the middle of a title`() {
        val result = filterTracks(library, "Nl6PA")
        assertEquals(1, result.size)
        assertEquals("M500003Nl6PA0BESMS", result.first().title)
    }

    @Test
    fun `query is trimmed before matching`() {
        assertEquals(1, filterTracks(library, "  不遗憾  ").size)
    }

    @Test
    fun `no match returns an empty list rather than the full library`() {
        // Returning everything on a failed search was the visible symptom users
        // reported: the box looked broken because the list never changed.
        assertTrue(filterTracks(library, "zzz-no-such-track").isEmpty())
    }

    @Test
    fun `searching an empty library is safe`() {
        assertTrue(filterTracks(emptyList(), "anything").isEmpty())
    }
}
