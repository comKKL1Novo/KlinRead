package com.klin.read.reader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Encoding detection tests.
 *
 * Chinese plain-text novels are very often GBK/GB18030 rather than UTF-8, and
 * decoding a GBK file as UTF-8 produces a wall of replacement characters. These
 * tests pin down which bytes must be treated as which encoding.
 *
 * The expected byte sequences below were produced with a real GB18030/UTF-8
 * encoder rather than written from memory.
 */
class TextDecoderTest {

    // 验证过的字节序列：
    //   "中文" UTF-8   = E4 B8 AD E6 96 87
    //   "中文" GB18030 = D6 D0 CE C4
    private val utf8Chinese = byteArrayOf(
        0xE4.toByte(), 0xB8.toByte(), 0xAD.toByte(),
        0xE6.toByte(), 0x96.toByte(), 0x87.toByte()
    )
    private val gbkChinese = byteArrayOf(
        0xD6.toByte(), 0xD0.toByte(), 0xCE.toByte(), 0xC4.toByte()
    )

    // ── BOM 优先 ────────────────────────────────────────────────────────────

    @Test
    fun `strips the utf-8 bom`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + utf8Chinese

        val decoded = TextDecoder.decode(bytes)

        assertEquals("中文", decoded)
        assertFalse("BOM 不应出现在结果里", decoded.startsWith("\uFEFF"))
    }

    @Test
    fun `decodes utf-16le with bom`() {
        // FF FE 是 UTF-16LE BOM，接着是 "中文" 的 UTF-16LE 编码
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xFE.toByte(),
            0x2D.toByte(), 0x4E.toByte(),  // 中
            0x87.toByte(), 0x65.toByte()   // 文
        )

        assertEquals("中文", TextDecoder.decode(bytes))
    }

    @Test
    fun `decodes utf-16be with bom`() {
        val bytes = byteArrayOf(
            0xFE.toByte(), 0xFF.toByte(),
            0x4E.toByte(), 0x2D.toByte(),  // 中
            0x65.toByte(), 0x87.toByte()   // 文
        )

        assertEquals("中文", TextDecoder.decode(bytes))
    }

    // ── 无 BOM 时的判断 ────────────────────────────────────────────────────

    @Test
    fun `decodes valid utf-8 without a bom`() {
        assertEquals("中文", TextDecoder.decode(utf8Chinese))
    }

    @Test
    fun `decodes gb18030 without a bom`() {
        // 这是关键用例：GBK 字节必须落到 GB18030 分支，
        // 而不是被当作 UTF-8 解出一堆乱码
        val decoded = TextDecoder.decode(gbkChinese)

        assertEquals("中文", decoded)
    }

    @Test
    fun `gbk bytes would be mangled if mistaken for utf-8`() {
        // 反向确认：这段 GBK 字节确实不是合法 UTF-8，
        // 所以 isValidUtf8 的判断是必要的，而不是多余的
        assertFalse(
            "GBK 字节不应通过 UTF-8 校验，否则会走错分支",
            TextDecoder.isValidUtf8(gbkChinese)
        )
    }

    @Test
    fun `ascii is valid utf-8`() {
        val ascii = "Hello, world!".toByteArray(Charsets.US_ASCII)

        assertTrue(TextDecoder.isValidUtf8(ascii))
        assertEquals("Hello, world!", TextDecoder.decode(ascii))
    }

    @Test
    fun `empty input decodes to empty string`() {
        assertEquals("", TextDecoder.decode(ByteArray(0)))
    }

    // ── UTF-8 严格校验 ──────────────────────────────────────────────────────

    @Test
    fun `rejects an incomplete multi-byte sequence at the end`() {
        // E4 B8 是「中」的前两个字节，缺少收尾字节
        assertFalse(TextDecoder.isValidUtf8(byteArrayOf(0xE4.toByte(), 0xB8.toByte())))
    }

    @Test
    fun `rejects a lone continuation byte`() {
        assertFalse(TextDecoder.isValidUtf8(byteArrayOf(0x80.toByte())))
    }

    @Test
    fun `rejects an overlong three-byte encoding`() {
        // E0 80 80 是 overlong 形式的 NUL，必须拒绝
        assertFalse(
            TextDecoder.isValidUtf8(
                byteArrayOf(0xE0.toByte(), 0x80.toByte(), 0x80.toByte())
            )
        )
    }

    @Test
    fun `rejects a surrogate code point encoded as utf-8`() {
        // ED A0 80 是 UTF-8 编码的 U+D800，属于非法代理区
        assertFalse(
            TextDecoder.isValidUtf8(
                byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte())
            )
        )
    }

    @Test
    fun `rejects the invalid byte FF`() {
        assertFalse(TextDecoder.isValidUtf8(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun `accepts a four-byte sequence for an emoji`() {
        // U+1F600 的 UTF-8 是 F0 9F 98 80
        val bytes = byteArrayOf(
            0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()
        )

        assertTrue(TextDecoder.isValidUtf8(bytes))
        assertEquals("\uD83D\uDE00", TextDecoder.decode(bytes))
    }

    // ── 流式入口 ────────────────────────────────────────────────────────────

    @Test
    fun `decodes from an input stream`() {
        val stream = ByteArrayInputStream(gbkChinese)

        assertEquals("中文", TextDecoder.decode(stream))
    }

    // ── 真实场景：多行正文 ──────────────────────────────────────────────────

    @Test
    fun `decodes a gbk novel excerpt correctly`() {
        val original = "第一章 开始\n这是正文的第一段。\n第二章 继续\n这是第二段。"
        val gbkBytes = original.toByteArray(charset("GB18030"))

        val decoded = TextDecoder.decode(gbkBytes)

        assertEquals("GBK 编码的中文正文应完整还原", original, decoded)
    }

    @Test
    fun `decodes a utf8 novel excerpt correctly`() {
        val original = "第一章 开始\n这是正文的第一段。"
        val bytes = original.toByteArray(Charsets.UTF_8)
        assertArrayEquals(bytes, original.toByteArray(Charsets.UTF_8))

        assertEquals(original, TextDecoder.decode(bytes))
    }
}
