package com.klin.read.reader

import java.io.InputStream
import java.nio.charset.Charset

/**
 * Decodes raw bytes into text, guessing the encoding.
 *
 * Chinese plain-text novels are very often GBK/GB18030 rather than UTF-8, and
 * decoding a GBK file as UTF-8 yields a wall of replacement characters. So rather
 * than assuming UTF-8, this detects.
 */
object TextDecoder {

    /** BOMs are decisive when present. */
    private val BOMS = listOf(
        byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) to "UTF-8",
        byteArrayOf(0xFF.toByte(), 0xFE.toByte()) to "UTF-16LE",
        byteArrayOf(0xFE.toByte(), 0xFF.toByte()) to "UTF-16BE"
    )

    fun decode(bytes: ByteArray): String {
        for ((bom, charsetName) in BOMS) {
            if (bytes.size >= bom.size && bom.indices.all { bytes[it] == bom[it] }) {
                return String(bytes, bom.size, bytes.size - bom.size, Charset.forName(charsetName))
            }
        }

        // No BOM. Validate UTF-8 strictly; if it holds up it is almost certainly
        // UTF-8, because random GBK bytes rarely form valid multi-byte sequences.
        if (isValidUtf8(bytes)) {
            return String(bytes, Charsets.UTF_8)
        }

        // Fall back to GB18030, a superset of GBK and GB2312.
        return try {
            String(bytes, Charset.forName("GB18030"))
        } catch (e: Exception) {
            String(bytes, Charsets.ISO_8859_1)
        }
    }

    fun decode(stream: InputStream): String = decode(stream.readBytes())

    /**
     * Strict UTF-8 validation, following the byte-sequence rules in RFC 3629.
     *
     * A manual scan is used because `CharsetDecoder` with REPORT would need the
     * same exception plumbing for the same result.
     */
    internal fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            val extra = when {
                b <= 0x7F -> 0
                b in 0xC2..0xDF -> 1
                b in 0xE0..0xEF -> 2
                b in 0xF0..0xF4 -> 3
                else -> return false
            }
            if (i + extra >= bytes.size) return false
            for (j in 1..extra) {
                val c = bytes[i + j].toInt() and 0xFF
                if (c !in 0x80..0xBF) return false
            }
            // Reject overlong encodings for the multi-byte forms.
            if (extra == 2) {
                val c1 = bytes[i + 1].toInt() and 0xFF
                if (b == 0xE0 && c1 < 0xA0) return false
                if (b == 0xED && c1 > 0x9F) return false
            }
            if (extra == 3) {
                val c1 = bytes[i + 1].toInt() and 0xFF
                if (b == 0xF0 && c1 < 0x90) return false
                if (b == 0xF4 && c1 > 0x8F) return false
            }
            i += extra + 1
        }
        return true
    }
}
