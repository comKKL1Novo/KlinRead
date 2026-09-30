package com.klin.read.reader

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * Readers for the plain-text-ish novel formats beyond TXT and EPUB.
 *
 * These are the containers a Chinese ebook is likely to arrive in. Each is an
 * "unwrap bytes into text" step; once text exists, chapter splitting is shared.
 */
object ExtraFormats {

    class UnreadableException(message: String) : Exception(message)

    // ---- FB2 ----

    /**
     * FictionBook 2 is XML. The body lives under `<body>`, with paragraphs in
     * `<p>` and section titles in `<title>`.
     */
    fun parseFb2(bytes: ByteArray): String {
        val xml = decodeXml(bytes)
        val body = Regex("""<body\b[^>]*>(.*?)</body>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(xml)?.groupValues?.get(1) ?: xml

        val withBreaks = Regex("""<title\b[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(body) { "\n\n" + it.groupValues[1] + "\n\n" }

        val stripped = Regex("""<[^>]+>""").replace(withBreaks, "")
        return decodeEntities(stripped)
            .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    // ---- HTML ----

    /** Web page or single-file HTML book. */
    fun parseHtml(bytes: ByteArray): String {
        val html = TextDecoder.decode(bytes)
        val body = Regex("""<body\b[^>]*>(.*?)</body>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1) ?: html

        val noScript = Regex("""<(script|style)\b.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(body, " ")
        val withBreaks = Regex(
            """</?(p|div|br|h[1-6]|li|tr|blockquote|section|article)\b[^>]*>""",
            RegexOption.IGNORE_CASE
        ).replace(noScript, "\n")
        val stripped = Regex("""<[^>]+>""").replace(withBreaks, "")

        return decodeEntities(stripped)
            .lineSequence().map { it.replace('\u00A0', ' ').trim() }.filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    // ---- MOBI / AZW3 ----

    /**
     * Extracts the text records of a MOBI/AZW.
     *
     * Only unencrypted books with PalmDOC (type 1 or 2) text are handled. Modern
     * Kindle files usually use HUFF/CDIC (17480) or carry DRM; both are rejected
     * with a clear message rather than producing mojibake.
     */
    fun parseMobi(bytes: ByteArray): String {
        if (bytes.size < 16) throw UnreadableException("文件太小，不是有效的 MOBI")

        val type = readAscii(bytes, 60, 8)
        if (!type.startsWith("BOOKMOBI") && !type.startsWith("TEXtREAd")) {
            throw UnreadableException("不是有效的 MOBI / AZW 文件")
        }

        val recordCount = readU16(bytes, 76)
        val recordOffsets = (0 until recordCount).map { readU32(bytes, 78 + it * 8) }

        val mobiHeaderOffset = recordOffsets.getOrNull(0) ?: throw UnreadableException("MOBI 头缺失")

        val compression = readU16(bytes, mobiHeaderOffset)
        val textLength = readU32(bytes, mobiHeaderOffset + 4)
        val textRecordCount = readU16(bytes, mobiHeaderOffset + 8)

        // Bit 1 of the encryption flags means DRM.
        val encryption = readU16(bytes, mobiHeaderOffset + 12)
        if (encryption != 0) throw UnreadableException("这本书有 DRM 保护，无法读取")

        if (compression == 17480) {
            throw UnreadableException("暂不支持 HUFF/CDIC 压缩的 MOBI，请先转换成 EPUB 或 TXT")
        }
        if (compression != 1 && compression != 2) {
            throw UnreadableException("不支持的 MOBI 压缩方式（$compression）")
        }

        val out = ByteArrayOutputStream()
        for (i in 1..textRecordCount) {
            val start = recordOffsets.getOrNull(i) ?: break
            val end = recordOffsets.getOrNull(i + 1) ?: bytes.size
            if (start >= end || end > bytes.size) continue
            val record = bytes.copyOfRange(start, end)
            when (compression) {
                1 -> out.write(record)
                else -> out.write(decompressPalmDoc(record))
            }
        }

        val raw = out.toByteArray()
        val slice = if (textLength in 1..raw.size) raw.copyOfRange(0, textLength) else raw

        // Text records are HTML-ish, so reuse the HTML extractor.
        return parseHtml(slice)
    }

    /**
     * PalmDOC (LZ77-style) decompression.
     *
     * Bytes 0x09-0x7F are literals, 0x01-0x08 are back-references, 0x00 is a
     * literal NUL, and 0x80-0xBF are two-byte distance/length pairs.
     */
    private fun decompressPalmDoc(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(input.size * 2)
        var i = 0
        while (i < input.size) {
            val b = input[i].toInt() and 0xFF
            when {
                b == 0 -> {
                    out.write(0)
                    i++
                }
                b in 1..8 -> {
                    for (k in 0 until b) {
                        if (i + 1 < input.size) out.write(input[i + 1].toInt() and 0xFF)
                    }
                    i += 2
                }
                b in 0x09..0x7F -> {
                    out.write(b)
                    i++
                }
                b in 0x80..0xBF -> {
                    if (i + 1 >= input.size) break
                    val pair = (b shl 8) or (input[i + 1].toInt() and 0xFF)
                    val distance = (pair shr 3) and 0x07FF
                    val length = (pair and 0x07) + 3
                    val data = out.toByteArray()
                    for (k in 0 until length) {
                        val idx = data.size - distance + k
                        if (idx in data.indices) out.write(data[idx].toInt() and 0xFF)
                    }
                    i += 2
                }
                else -> i++ // 0xC0-0xFF are unused in the spec
            }
        }
        return out.toByteArray()
    }

    // ---- UMD ----

    /**
     * Chinese UMD book.
     *
     * UMD stores chapters as zlib-compressed chunks after a fixed header. Chunks
     * marked with the text type are inflated and concatenated.
     */
    fun parseUmd(bytes: ByteArray): String {
        if (bytes.size < 32) throw UnreadableException("文件太小，不是有效的 UMD")

        val signature = readAscii(bytes, 0, 4)
        if (signature != "\uFEFFUMD") throw UnreadableException("不是有效的 UMD 文件")

        val out = StringBuilder()
        var offset = 8

        while (offset + 5 < bytes.size) {
            val marker = bytes[offset].toInt() and 0xFF
            val size = ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
            offset += 4
            if (size <= 0 || offset + size > bytes.size) break

            val chunk = bytes.copyOfRange(offset, offset + size)
            offset += size

            // 0x0A is the text marker; other markers carry metadata or images.
            if (marker == 0x0A) {
                val text = runCatching { inflate(chunk) }.getOrNull() ?: continue
                // Some builds prefix each chunk with a 5-byte length field.
                val cleaned = if (text.size > 5 && looksBinaryHeader(text)) {
                    text.copyOfRange(5, text.size)
                } else {
                    text
                }
                out.append(TextDecoder.decode(cleaned)).append('\n')
            }
        }

        val result = out.toString()
        if (result.isBlank()) throw UnreadableException("UMD 里没有可读的正文")
        return result
    }

    private fun looksBinaryHeader(bytes: ByteArray): Boolean =
        bytes.take(5).any { it == 0.toByte() }

    private fun inflate(input: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(input)
        val out = ByteArrayOutputStream(input.size * 3)
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            } else {
                out.write(buffer, 0, n)
            }
        }
        inflater.end()
        return out.toByteArray()
    }

    // ---- helpers ----

    private fun readAscii(bytes: ByteArray, offset: Int, length: Int): String {
        if (offset + length > bytes.size) return ""
        return String(bytes, offset, length, Charsets.US_ASCII)
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun readU32(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun decodeXml(bytes: ByteArray): String {
        val head = bytes.take(200).toByteArray().toString(Charsets.ISO_8859_1)
        val declared = Regex("""encoding\s*=\s*["']([^"']+)["']""").find(head)?.groupValues?.get(1)
        val charset = declared?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() }
        return if (charset != null) String(bytes, charset) else TextDecoder.decode(bytes)
    }

    private fun decodeEntities(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
        .replace("&nbsp;", " ").replace("&mdash;", "—").replace("&ndash;", "–")
        .replace("&hellip;", "…").replace("&ldquo;", "“").replace("&rdquo;", "”")
        .replace("&lsquo;", "‘").replace("&rsquo;", "’").replace("&amp;", "&")
}
