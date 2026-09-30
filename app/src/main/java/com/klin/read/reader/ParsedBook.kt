package com.klin.read.reader

/**
 * A parsed book, held in memory while reading.
 *
 * Text is kept as one string plus chapter offsets rather than a string per
 * chapter; slicing on demand avoids duplicating the whole book.
 */
data class ParsedBook(
    val title: String,
    val text: String,
    val chapters: List<Chapter>
) {
    val charCount: Int get() = text.length

    /** Rough progress in 0f..1f, used for the shelf indicator. */
    fun progressAt(charOffset: Int): Float {
        if (text.isEmpty()) return 0f
        return (charOffset.toFloat() / text.length).coerceIn(0f, 1f)
    }
}

/**
 * A chapter boundary within [ParsedBook.text].
 *
 * [start] is inclusive and [end] exclusive, so the body is
 * `text.substring(start, end)`.
 */
data class Chapter(
    val title: String,
    val start: Int,
    val end: Int
)
