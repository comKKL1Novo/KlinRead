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

        /*
         * The file is opened once, and the parse works from those bytes.
         *
         * This used to open the URI two or three times per import: once to sniff the
         * format, once inside `readAll`, and for some formats once more in the
         * parser. That works against a permissive provider and fails against a
         * strict one -- OPPO's document picker hands back a short-lived grant, so a
         * second `openInputStream` returns null and the reader reported
         * "无法打开文件" for a file it had just imported successfully.
         *
         * EPUB is not buffered: it is parsed straight from a stream, because a book
         * can be tens of megabytes and avoiding that allocation is the entire point
         * of the streaming parser. It gets exactly one open of its own.
         */
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw java.io.IOException("无法打开文件")

        val format = FormatDetector.detect(bytes.inputStream(), extension)

        val baseTitle = displayName.substringBeforeLast('.').trim()
            .takeIf { it.isNotEmpty() && !it.startsWith("content") }
            ?: "未命名"

        return@withContext when (format) {
            BookFormat.EPUB -> EpubParser.parse(bytes.inputStream(), baseTitle)

            BookFormat.TXT -> textBook(baseTitle, TextDecoder.decode(bytes))

            BookFormat.FB2 -> textBook(baseTitle, ExtraFormats.parseFb2(bytes))

            BookFormat.HTML -> textBook(baseTitle, ExtraFormats.parseHtml(bytes))

            BookFormat.MOBI -> textBook(baseTitle, ExtraFormats.parseMobi(bytes))

            BookFormat.UMD -> textBook(baseTitle, ExtraFormats.parseUmd(bytes))

            null -> throw UnsupportedFormatException(extension.ifEmpty { "未知" })
        }
    }

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
