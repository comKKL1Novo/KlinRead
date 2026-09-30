package com.klin.read.reader

import java.io.InputStream

/**
 * Identifies a book's format from its bytes rather than its file name.
 *
 * File names cannot be trusted: the Storage Access Framework returns a
 * DISPLAY_NAME that some providers hand back without an extension, which made
 * extension-based routing reject readable books.
 *
 * Ordering matters: container signatures (ZIP, PalmDB, UMD) are checked before
 * the "does this look like text" heuristic, because a container can contain bytes
 * that would otherwise read as text.
 */
enum class BookFormat {
    EPUB,
    TXT,
    FB2,
    HTML,
    MOBI,
    UMD
}

object FormatDetector {

    /** Local file header, and the two other ZIP record signatures. */
    private val ZIP_SIGNATURES = listOf(
        byteArrayOf(0x50, 0x4B, 0x03, 0x04),
        byteArrayOf(0x50, 0x4B, 0x05, 0x06),
        byteArrayOf(0x50, 0x4B, 0x07, 0x08)
    )

    /** Enough bytes to see a signature and a little text. */
    private const val SNIFF_SIZE = 4096

    /**
     * Reads the leading bytes of [stream] and classifies it.
     *
     * [hint] is the extension from the file name, used as a tie-breaker and to
     * separate formats sharing a container (MOBI and AZW are both PalmDB).
     */
    fun detect(stream: InputStream, hint: String? = null): BookFormat? {
        val head = ByteArray(SNIFF_SIZE)
        var read = 0
        while (read < SNIFF_SIZE) {
            val n = stream.read(head, read, SNIFF_SIZE - read)
            if (n <= 0) break
            read += n
        }
        if (read == 0) return null

        val bytes = if (read == SNIFF_SIZE) head else head.copyOf(read)
        val hintUpper = hint?.uppercase()

        if (ZIP_SIGNATURES.any { sig -> bytes.startsWith(sig) }) return BookFormat.EPUB

        // MOBI/AZW carry a PalmDB header whose type field sits at offset 60.
        val palmType = if (bytes.size >= 68) String(bytes, 60, 8, Charsets.US_ASCII) else ""
        if (palmType.startsWith("BOOKMOBI") || palmType.startsWith("TEXtREAd")) {
            return BookFormat.MOBI
        }
        if (hintUpper in setOf("MOBI", "AZW", "AZW3", "PRC")) return BookFormat.MOBI

        // UMD begins with a UTF-16LE BOM followed by "UMD".
        if (bytes.size >= 8 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() &&
            bytes[2] == 0x55.toByte() && bytes[3] == 0x00.toByte()
        ) {
            return BookFormat.UMD
        }

        // A NUL byte in the first block normally means binary, so this is checked
        // only after the binary-container signatures above.
        if (bytes.any { it == 0.toByte() }) return null

        val text = runCatching { TextDecoder.decode(bytes) }.getOrNull().orEmpty()
        val trimmed = text.trimStart()

        if (trimmed.contains("<FictionBook", ignoreCase = true)) return BookFormat.FB2
        if (trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true)
        ) {
            return BookFormat.HTML
        }

        if (hintUpper == "FB2") return BookFormat.FB2
        if (hintUpper in setOf("HTML", "HTM", "XHTML")) return BookFormat.HTML
        if (hintUpper == "UMD") return BookFormat.UMD
        if (hintUpper == "EPUB") return BookFormat.EPUB
        if (hintUpper == "TXT") return BookFormat.TXT

        // A leading XML declaration with no FictionBook marker is treated as text
        // rather than guessed at, since many plain novels open with one.
        if (trimmed.startsWith("<?xml")) {
            return if (trimmed.contains("<body", ignoreCase = true)) {
                BookFormat.FB2
            } else {
                BookFormat.TXT
            }
        }

        return if (TextDecoder.isValidUtf8(bytes) || looksLikeGbk(bytes)) {
            BookFormat.TXT
        } else {
            null
        }
    }

    /**
     * Heuristic for GB18030 text.
     *
     * GBK lead bytes are 0x81-0xFE followed by 0x40-0xFE. Rather than fully
     * validating, this checks the stream contains the kind of multi-byte pairs GBK
     * text is made of and no obvious binary runs.
     */
    private fun looksLikeGbk(bytes: ByteArray): Boolean {
        var i = 0
        var multiBytePairs = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b < 0x80 -> i++
                b in 0x81..0xFE && i + 1 < bytes.size -> {
                    val next = bytes[i + 1].toInt() and 0xFF
                    if (next !in 0x40..0xFE) return false
                    multiBytePairs++
                    i += 2
                }
                else -> return false
            }
        }
        return multiBytePairs > 0
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }
}
