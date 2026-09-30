package com.klin.read.reader

/**
 * Splits plain text into chapters by recognising common Chinese chapter headings.
 *
 * Web-novel exports vary, so this accepts several shapes in one pass:
 *
 *   第一章 标题
 *   第1章 标题
 *   第 12 章：标题
 *   第十二回 标题
 *   楔子 / 序章 / 尾声 / 后记
 *
 * One pass anchored on "第…章/回/节" plus a few standalone titles is simpler and
 * more predictable than a chain of per-style regexes.
 */
object ChapterSplitter {

    /**
     * Chapter heading: optional leading whitespace, then "第", digits or CJK
     * numerals, then a unit character, optionally followed by a title.
     */
    private val NUMBERED = Regex(
        """^\s{0,8}第\s*[0-9０-９一二三四五六七八九十百千零两]{1,12}\s*[章回节卷篇集]\s*[:：.、]?\s*.*$"""
    )

    /**
     * Standalone section titles carrying no number.
     *
     * The trailing group is deliberately restrictive: the title must be the whole
     * line, or be followed by a separator. Allowing arbitrary trailing text would
     * make any body line starting with 引子 or 序 match.
     */
    private val STANDALONE = Regex(
        """^\s{0,8}(楔子|序章|序言|序|引子|前言|尾声|终章|后记|番外|大结局)\s*[:：.、]?\s*$"""
    )

    /** Headings longer than this are almost certainly body text that matched by luck. */
    private const val MAX_HEADING_LENGTH = 40

    /** Below this many chapters the split is not worth using. */
    private const val MIN_CHAPTERS = 2

    /**
     * Returns chapter bounds for [text], or a single "全文" chapter when no
     * headings are found, so the reader always has something to show.
     */
    fun split(text: String): List<Chapter> {
        val starts = findHeadingStarts(text)

        if (starts.size < MIN_CHAPTERS) {
            return listOf(Chapter(title = "全文", start = 0, end = text.length))
        }

        val chapters = ArrayList<Chapter>(starts.size + 1)

        // Anything before the first heading becomes a preface, so no text is
        // silently dropped.
        val firstOffset = starts.first().first
        if (firstOffset > 0 && text.substring(0, firstOffset).isNotBlank()) {
            chapters += Chapter(title = "前言", start = 0, end = firstOffset)
        }

        starts.forEachIndexed { index, (offset, title) ->
            val end = if (index + 1 < starts.size) starts[index + 1].first else text.length
            chapters += Chapter(title = title, start = offset, end = end)
        }

        return chapters
    }

    /** Pairs of (offset of heading start, cleaned heading title). */
    private fun findHeadingStarts(text: String): List<Pair<Int, String>> {
        val result = ArrayList<Pair<Int, String>>()
        var lineStart = 0

        while (lineStart <= text.length) {
            val newline = text.indexOf('\n', lineStart)
            val lineEnd = if (newline == -1) text.length else newline

            val trimmed = text.substring(lineStart, lineEnd).trim().trimEnd('\r')

            // No "preceded by a blank line" requirement: most Chinese TXT exports
            // run the heading straight after the previous paragraph, so demanding
            // a blank line would reject nearly every real file. isHeading()
            // carries the filtering instead.
            if (isHeading(trimmed)) {
                result += lineStart to trimmed
            }

            if (newline == -1) break
            lineStart = newline + 1
        }

        return result
    }

    private fun isHeading(line: String): Boolean {
        if (line.isEmpty() || line.length > MAX_HEADING_LENGTH) return false
        // Headings should not look like sentences.
        if (line.endsWith("。") || line.endsWith("，") ||
            line.endsWith("！") || line.endsWith("？")
        ) {
            return false
        }
        return NUMBERED.matches(line) || STANDALONE.matches(line)
    }
}
