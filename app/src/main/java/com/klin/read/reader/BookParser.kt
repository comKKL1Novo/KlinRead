package com.klin.read.reader

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads a book from a Storage Access Framework URI.
 *
 * Format is decided by sniffing the file's bytes, with the file name used only as
 * a hint. See [FormatDetector] for why the extension alone is not reliable.
 */
object BookParser {

    /** Extensions the import picker offers. */
    val SUPPORTED_EXTENSIONS = setOf(
        "TXT", "EPUB", "FB2", "HTML", "HTM", "XHTML", "MOBI", "AZW", "AZW3", "PRC", "UMD"
    )

    /** Human-readable list, used in user-facing messages. */
    const val SUPPORTED_LABEL = "TXT、EPUB、FB2、HTML、MOBI/AZW、UMD"

    class UnsupportedFormatException(val format: String) :
        Exception("无法识别的文件格式")

    /**
     * Reads and splits the book at [uri] on the IO dispatcher.
     *
     * [displayName] is the label the document picker reported, used as a title
     * fallback and as a weak format hint.
     */
    suspend fun parse(
        context: Context,
        uri: Uri,
        displayName: String
    ): ParsedBook = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val extension = displayName.substringAfterLast('.', "").uppercase()

        // Sniff on a separate stream so the format decision never depends on
        // being able to rewind the resolver's stream.
        val format = resolver.openInputStream(uri)?.use { sniffStream ->
            FormatDetector.detect(sniffStream, extension)
        } ?: throw java.io.IOException("无法打开文件")

        val baseTitle = displayName.substringBeforeLast('.').trim()
            .takeIf { it.isNotEmpty() && !it.startsWith("content") }
            ?: "未命名"

        when (format) {
            BookFormat.EPUB -> resolver.openInputStream(uri)?.use { stream ->
                EpubParser.parse(stream, baseTitle)
            } ?: throw java.io.IOException("无法打开文件")

            BookFormat.TXT -> textBook(baseTitle, TextDecoder.decode(readAll(resolver, uri)))

            BookFormat.FB2 -> textBook(baseTitle, ExtraFormats.parseFb2(readAll(resolver, uri)))

            BookFormat.HTML -> textBook(baseTitle, ExtraFormats.parseHtml(readAll(resolver, uri)))

            BookFormat.MOBI -> textBook(baseTitle, ExtraFormats.parseMobi(readAll(resolver, uri)))

            BookFormat.UMD -> textBook(baseTitle, ExtraFormats.parseUmd(readAll(resolver, uri)))

            null -> throw UnsupportedFormatException(extension.ifEmpty { "未知" })
        }
    }

    private fun readAll(
        resolver: android.content.ContentResolver,
        uri: Uri
    ): ByteArray = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw java.io.IOException("无法打开文件")

    /** Wraps extracted prose into a book, splitting chapters the usual way. */
    private fun textBook(baseTitle: String, text: String): ParsedBook {
        if (text.isBlank()) throw java.io.IOException("这个文件是空的")
        return ParsedBook(
            title = titleFrom(baseTitle, text),
            text = text,
            chapters = ChapterSplitter.split(text)
        )
    }

    /**
     * Preferred title is the file name, since exports often carry a meaningful
     * one. A heading on the first non-blank line wins when the name is generic.
     */
    private fun titleFrom(baseTitle: String, text: String): String {
        val generic = baseTitle.isBlank() || baseTitle == "未命名" ||
            baseTitle.all { it.isDigit() || it == '-' || it == '_' }
        if (!generic) return baseTitle

        return text.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(40)
            ?: baseTitle
    }
}
