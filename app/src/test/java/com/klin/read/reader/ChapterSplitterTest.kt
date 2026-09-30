package com.klin.read.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chapter splitting tests.
 *
 * Two of these are regression tests for bugs that shipped once and are easy to
 * reintroduce, so they are called out explicitly below.
 */
class ChapterSplitterTest {

    // ── 基本识别 ────────────────────────────────────────────────────────────

    @Test
    fun `splits numbered chapters with arabic numerals`() {
        val text = """
            第一章 开始
            这是第一章的正文。

            第二章 继续
            这是第二章的正文。
        """.trimIndent()

        val chapters = ChapterSplitter.split(text)

        assertEquals(2, chapters.size)
        assertEquals("第一章 开始", chapters[0].title)
        assertEquals("第二章 继续", chapters[1].title)
    }

    @Test
    fun `splits chapters with cjk numerals`() {
        val text = "第十二回 归途\n正文甲\n第十三回 再会\n正文乙"

        val titles = ChapterSplitter.split(text).map { it.title }

        assertEquals(listOf("第十二回 归途", "第十三回 再会"), titles)
    }

    @Test
    fun `accepts full-width digits and separator punctuation`() {
        val text = "第１章：起点\n正文\n第２章、终点\n正文"

        val titles = ChapterSplitter.split(text).map { it.title }

        assertEquals(2, titles.size)
        assertTrue(titles[0].startsWith("第１章"))
        assertTrue(titles[1].startsWith("第２章"))
    }

    @Test
    fun `accepts whitespace between the number and the unit`() {
        val text = "第 12 章 中间有空格\n正文\n第 13 章 继续\n正文"

        val chapters = ChapterSplitter.split(text)

        assertEquals(2, chapters.size)
        assertTrue(chapters[0].title.contains("12"))
    }

    @Test
    fun `recognises standalone section titles`() {
        val text = "楔子\n序章正文\n第一章 正题\n正文\n尾声\n结局"

        val titles = ChapterSplitter.split(text).map { it.title }

        assertTrue("应识别楔子", titles.contains("楔子"))
        assertTrue("应识别尾声", titles.contains("尾声"))
    }

    // ── 回归测试：标题前不要求空行 ──────────────────────────────────────────
    //
    // 曾经的实现要求标题前必须是空行，但实际导出的中文 TXT 里标题通常紧跟在
    // 正文段落后面，没有空行——结果整本书被识别成「全文」一章。
    // 现在由 isHeading() 的规则本身负责过滤，不再要求空行。

    @Test
    fun `regression - heading immediately after a paragraph is still detected`() {
        // 注意这里没有任何空行，每个标题都直接跟在前一段正文后面。
        //
        // 需要至少两个真标题，因为 MIN_CHAPTERS = 2：只有一个标题时切分
        // 没有意义，会回退成「全文」（那条行为另有测试覆盖）。
        val text = "这是第一章的正文。\n第二章 紧跟着来\n这是第二章的正文。\n第三章 继续\n正文"

        val chapters = ChapterSplitter.split(text)

        assertEquals("标题前无空行时仍应切分", 3, chapters.size)
        assertEquals("第二章 紧跟着来", chapters[1].title)
        assertEquals("第三章 继续", chapters[2].title)
    }

    @Test
    fun `regression - a paragraph that merely starts with a heading word is not split`() {
        // 正文里出现「第二章」字样但整行以句号结尾，不应被当成标题。
        // 这是 isHeading() 里「标题不似句子」规则的直接体现。
        val text = "第一章 开端\n这里提到第二章 紧跟着来这个说法，但它只是正文。\n第二章 真正的标题\n正文\n第三章 结尾\n正文"

        val titles = ChapterSplitter.split(text).map { it.title }

        assertEquals(listOf("第一章 开端", "第二章 真正的标题", "第三章 结尾"), titles)
    }

    // ── 回归测试：独立标题必须独占一行 ──────────────────────────────────────
    //
    // 曾经的 STANDALONE 正则结尾是 `.*$`，于是任何以「引子」「序」开头的正文行
    // 都会被误判成章节标题。现在要求标题独占一行。

    @Test
    fun `regression - body line starting with a standalone keyword is not a heading`() {
        val text = """
            第一章 开端
            引子内容其实是正文的一部分，不应该被当成标题。
            第二章 继续
            正文
        """.trimIndent()

        val titles = ChapterSplitter.split(text).map { it.title }

        assertFalse(
            "以「引子」开头的正文行不能被当成标题：$titles",
            titles.any { it.startsWith("引子内容") }
        )
        assertEquals(listOf("第一章 开端", "第二章 继续"), titles)
    }

    @Test
    fun `standalone keyword followed by a separator and nothing else is a heading`() {
        // STANDALONE 正则的结尾是 `[:：.、]?\s*$`，即分隔符后面必须直接结束。
        // 所以「序章：」是标题，而「序章：故事之前」不是——后者被当作正文，
        // 这是有意为之，用于避免任何以「序」开头的正文行被误判。
        val text = "序章：\n正文甲\n第一章 正式开始\n正文乙"

        val titles = ChapterSplitter.split(text).map { it.title }

        assertEquals(listOf("序章：", "第一章 正式开始"), titles)
    }

    @Test
    fun `standalone keyword with trailing text is treated as body text`() {
        val text = "序章：故事之前\n正文甲\n第一章 正式开始\n正文乙"

        val titles = ChapterSplitter.split(text).map { it.title }

        assertFalse(
            "分隔符后还有文字时不应算标题：$titles",
            titles.any { it.contains("故事之前") }
        )
    }

    // ── 回退行为 ────────────────────────────────────────────────────────────

    @Test
    fun `falls back to a single chapter when no headings are found`() {
        val text = "这里没有任何章节标题。\n只有普通的正文段落。"

        val chapters = ChapterSplitter.split(text)

        assertEquals(1, chapters.size)
        assertEquals("全文", chapters[0].title)
        assertEquals(0, chapters[0].start)
        assertEquals(text.length, chapters[0].end)
    }

    @Test
    fun `a single heading is not enough and falls back to whole text`() {
        // MIN_CHAPTERS = 2：只有一个标题时，切分没有意义
        val text = "第一章 唯一的标题\n一大段正文"

        val chapters = ChapterSplitter.split(text)

        assertEquals(1, chapters.size)
        assertEquals("全文", chapters[0].title)
    }

    @Test
    fun `handles empty input without crashing`() {
        val chapters = ChapterSplitter.split("")

        assertEquals(1, chapters.size)
        assertEquals("全文", chapters[0].title)
        assertEquals(0, chapters[0].end)
    }

    // ── 完整性：切分不能丢字 ────────────────────────────────────────────────

    @Test
    fun `chapters are contiguous and cover the whole text`() {
        val text = "前言部分\n第一章 甲\n正文甲\n第二章 乙\n正文乙\n第三章 丙\n正文丙"

        val chapters = ChapterSplitter.split(text)

        assertTrue("章节数应大于 1", chapters.size > 1)
        assertEquals("首章必须从 0 开始", 0, chapters.first().start)
        assertEquals("末章必须到文本结尾", text.length, chapters.last().end)

        chapters.zipWithNext { a, b ->
            assertEquals("章节之间不能有缝隙", a.end, b.start)
        }
    }

    @Test
    fun `content before the first heading becomes a preface chapter`() {
        val text = "这是封面和作者的话。\n第一章 开始\n正文\n第二章 继续\n正文"

        val chapters = ChapterSplitter.split(text)

        assertEquals("前言", chapters.first().title)
        assertEquals(0, chapters.first().start)
        assertEquals("前言内容不能被丢弃", "这是封面和作者的话。", 
            text.substring(chapters.first().start, chapters.first().end).trim())
    }

    @Test
    fun `leading blank lines before the first heading produce no empty preface`() {
        val text = "\n\n\n第一章 开始\n正文\n第二章 继续\n正文"

        val chapters = ChapterSplitter.split(text)

        assertFalse(
            "全是空白的开头不应产生前言章节",
            chapters.any { it.title == "前言" }
        )
    }

    // ── 误判防护 ────────────────────────────────────────────────────────────

    @Test
    fun `lines ending in sentence punctuation are not headings`() {
        val text = "第一章 甲\n正文\n第二章 这是一个以句号结尾的普通句子。\n正文"

        val titles = ChapterSplitter.split(text).map { it.title }

        assertFalse(
            "以句号结尾的行不应被当成标题：$titles",
            titles.any { it.endsWith("。") }
        )
    }

    @Test
    fun `very long lines are not treated as headings`() {
        val longLine = "第一章 " + "很长的正文".repeat(20)
        assertTrue("构造的测试行应超过 40 字", longLine.length > 40)

        val text = "$longLine\n正文\n第二章 正常标题\n正文"

        val chapters = ChapterSplitter.split(text)

        assertFalse(
            "超过长度上限的行不应被当成标题",
            chapters.any { it.title.length > 40 }
        )
    }
}
