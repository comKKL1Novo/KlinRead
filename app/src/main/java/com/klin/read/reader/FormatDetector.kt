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

        /*
         * Trim a half-read character off the end before decoding.
         *
         * The 4096-byte window is a fixed byte count, so it lands mid-character
         * roughly two times in three for Chinese text (a UTF-8 CJK character is
         * three bytes). `TextDecoder.decode` then fails on the truncated tail, the
         * UTF-8 check below fails with it, and a perfectly ordinary novel is
         * rejected as an unrecognised format -- which is what happened to a plain
         * 24 KB TXT file.
         *
         * Backing off to the last complete character costs at most two bytes of
         * sniffing and makes the check describe the text rather than the cut.
         */
        val aligned = bytes.trimToCharacterBoundary()

        val text = runCatching { TextDecoder.decode(aligned) }.getOrNull().orEmpty()
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

        // Character-aligned bytes, not the raw window: the truncation that
        // `aligned` removes is exactly what made `isValidUtf8` fail here and
        // reject a valid novel. Checking the raw array would undo the fix above.
        return if (TextDecoder.isValidUtf8(aligned) || looksLikeGbk(aligned)) {
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

    /**
     * Drops a trailing partial UTF-8 sequence, if there is one.
     *
     * Two shapes have to be handled, and only the first was covered originally --
     * which is why a real 24 KB Chinese TXT still failed after the first attempt:
     *
     *   1. **A truncated continuation run.** The tail is one or more `10xxxxxx`
     *      bytes whose lead byte is present but whose character is short of the
     *      bytes the lead declares.
     *   2. **A bare lead byte.** The window ends exactly on the first byte of a
     *      multi-byte character, with none of its continuation bytes present. The
     *      device file that exposed this ends at byte 4096 with `0xE7`, the lead of
     *      a three-byte character.
     *
     * Both make `isValidUtf8` return false -- deliberately, since it validates a
     * whole buffer -- and that false is what rejected the book.
     *
     * Returns the array unchanged when the tail is already a complete character, so
     * ASCII and aligned text pay nothing for this.
     */
    private fun ByteArray.trimToCharacterBoundary(): ByteArray {
        if (isEmpty()) return this

        // How many trailing 10xxxxxx bytes there are, capped at the 3 that can
        // follow any lead byte.
        var continuation = 0
        while (continuation < 3 && continuation < size) {
            val b = this[size - 1 - continuation].toInt() and 0xFF
            if (b and 0xC0 != 0x80) break
            continuation++
        }

        // Case 2: nothing but a lead byte at the very end.
        if (continuation == 0) {
            val lead = this[size - 1].toInt() and 0xFF
            return if (neededBytes(lead) > 1) copyOf(size - 1) else this
        }

        // Case 1: the byte before the continuation run should be the lead.
        val leadIndex = size - 1 - continuation
        if (leadIndex < 0) return this
        val lead = this[leadIndex].toInt() and 0xFF

        val needed = neededBytes(lead)
        if (needed == 0) return this // not a valid lead; leave the data alone

        // continuation counts bytes AFTER the lead, so the character has
        // 1 + continuation bytes present in total.
        return if (1 + continuation < needed) copyOf(leadIndex) else this
    }

    /**
     * Total byte length of the character [lead] introduces, or 0 if it cannot
     * introduce one.
     */
    private fun neededBytes(lead: Int): Int = when {
        lead and 0x80 == 0x00 -> 1
        lead and 0xE0 == 0xC0 -> 2
        lead and 0xF0 == 0xE0 -> 3
        lead and 0xF8 == 0xF0 -> 4
        else -> 0
    }
}
