package com.klin.read.reader

import com.klin.read.ui.reader.paragraphIndexAt
import com.klin.read.ui.reader.paragraphOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Round-trip tests for the reading position on a multi-chapter EPUB.
 *
 * These exist because the shelf's progress bar kept reading wrong for EPUB while
 * the TXT path looked right, and every attempt to explain it from the code was a
 * guess. The suspicion was that the two parsers disagree about what a chapter's
 * `start` means: `EpubParser` accumulates `text.length` as it appends bodies, while
 * `ChapterSplitter` derives offsets from the source string. If they disagree, the
 * offset saved for a paragraph does not point at that paragraph, and the ratio
 * against the whole book is wrong by however much they diverge.
 *
 * The positions are produced by the same helpers the ViewModel uses, so a pass here
 * means the shelf and the reader agree for this book shape.
 */
class EpubPositionRoundTripTest {

    /** Builds a real 12-chapter EPUB: container, OPF, NCX, one XHTML per chapter. */
    private fun buildEpub(chapterCount: Int = 12): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            put("mimetype", "application/epub+zip")

            val manifest = (1..chapterCount).joinToString("\n") {
                """<item id="c$it" href="ch$it.xhtml" media-type="application/xhtml+xml"/>"""
            }
            val spine = (1..chapterCount).joinToString("\n") { """<itemref idref="c$it"/>""" }
            val navPoints = (1..chapterCount).joinToString("\n") {
                """<navPoint id="n$it" playOrder="$it"><navLabel><text>第${it}章 标题$it</text></navLabel><content src="ch$it.xhtml"/></navPoint>"""
            }

            put(
                "META-INF/container.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""
            )

            put(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>测试书</dc:title><dc:creator>t</dc:creator>
    <dc:language>zh</dc:language><dc:identifier id="id">urn:uuid:t</dc:identifier>
  </metadata>
  <manifest><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
$manifest</manifest>
  <spine toc="ncx">
$spine
  </spine>
</package>"""
            )

            put(
                "OEBPS/toc.ncx",
                """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="urn:uuid:t"/></head>
  <docTitle><text>测试书</text></docTitle>
  <navMap>
$navPoints
  </navMap>
</ncx>"""
            )

            for (i in 1..chapterCount) {
                val body = (1..30).joinToString("") {
                    "<p>第${i}章第${it}段正文内容，用来让这一章足够长，可以滚动多屏并检验位置记录。</p>"
                }
                put(
                    "OEBPS/ch$i.xhtml",
                    """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>第${i}章 标题$i</title></head>
<body><h1>第${i}章 标题$i</h1>$body</body></html>"""
                )
            }
        }
        return out.toByteArray()
    }

    /** Every chapter's declared range must cover the text without gaps or overlap. */
    @Test
    fun epubChapterRangesTileTheWholeText() {
        val book = EpubParser.parse(buildEpub().inputStream(), "测试书")

        assertEquals(12, book.chapters.size)
        assertEquals("first chapter must start at 0", 0, book.chapters.first().start)

        book.chapters.forEachIndexed { i, ch ->
            assertTrue("chapter $i start ${ch.start} must be >= 0", ch.start >= 0)
            assertTrue(
                "chapter $i end ${ch.end} must not exceed text length ${book.text.length}",
                ch.end <= book.text.length
            )
            assertTrue("chapter $i must not be empty", ch.end > ch.start)
        }

        book.chapters.zipWithNext().forEachIndexed { i, (a, b) ->
            assertEquals(
                "chapter $i ends at ${a.end} but chapter ${i + 1} starts at ${b.start}; " +
                    "the saved offset would land in the wrong chapter",
                a.end,
                b.start
            )
        }
    }

    /**
     * The offset saved for a paragraph must, when read back, name that same
     * paragraph. This is the round trip the shelf depends on.
     */
    @Test
    fun savedOffsetRoundTripsToTheSameParagraph() {
        val book = EpubParser.parse(buildEpub().inputStream(), "测试书")

        // Probe the middle of the book, where any accumulated drift would be worst.
        listOf(0 to 0, 3 to 10, 6 to 29, 11 to 15).forEach { (chapterIndex, paragraphIndex) ->
            val chapter = book.chapters[chapterIndex]
            val offset = paragraphOffset(book.text, chapter, paragraphIndex)
            val back = paragraphIndexAt(book.text, chapter, offset)

            // Paragraph 0 is always a legitimate answer for an offset at the very
            // start of a chapter, but every other case must be exact.
            if (paragraphIndex > 0) {
                assertEquals(
                    "chapter $chapterIndex paragraph $paragraphIndex -> offset $offset -> paragraph $back",
                    paragraphIndex,
                    back
                )
            }
        }
    }

    /** The shelf's ratio must land near the chapter's true share of the book. */
    @Test
    fun progressRatioTracksRealPosition() {
        val book = EpubParser.parse(buildEpub().inputStream(), "测试书")
        val lastChapter = book.chapters.lastIndex

        book.chapters.forEachIndexed { index, chapter ->
            val offset = paragraphOffset(book.text, chapter, 0)
            val ratio = offset.toFloat() / book.text.length

            val expectedLow = chapter.start.toFloat() / book.text.length
            val expectedHigh = chapter.end.toFloat() / book.text.length
            assertTrue(
                "chapter $index ratio $ratio outside its own range [$expectedLow, $expectedHigh]",
                ratio >= expectedLow - 0.001f && ratio <= expectedHigh + 0.001f
            )
        }

        val lastOffset = paragraphOffset(book.text, book.chapters[lastChapter], 0)
        assertTrue(
            "the final chapter must read as near-finished, was $lastOffset of ${book.text.length}",
            lastOffset.toFloat() / book.text.length > 0.85f
        )
    }
}


