package com.klin.read.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.klin.read.data.BookDao
import com.klin.read.data.BookEntity
import com.klin.read.reader.BookParser
import com.klin.read.reader.CoverExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a user-picked document URI into a shelf entry.
 *
 * Access to the file comes from the Storage Access Framework, and the permission
 * granted by the picker is persisted so the book still opens after a restart.
 * That is why the app declares no storage permission at all.
 */
class BookImporter(
    private val context: Context,
    private val bookDao: BookDao
) {

    /** Metadata a content provider can tell us before reading the file. */
    data class DocumentInfo(val displayName: String, val sizeBytes: Long)

    /**
     * Imports [uri], returning the new row id, or the existing one when the same
     * document was already on the shelf.
     *
     * Throws [BookParser.UnsupportedFormatException] for formats we cannot read,
     * and [java.io.IOException] when the file itself cannot be opened.
     */
    suspend fun import(uri: Uri): Long = withContext(Dispatchers.IO) {
        // Re-importing the same file should refresh it, not duplicate it.
        bookDao.findByUri(uri.toString())?.let { existing ->
            bookDao.touch(existing.id)
            return@withContext existing.id
        }

        val info = queryDocumentInfo(uri)
        val parsed = BookParser.parse(context, uri, info.displayName)

        val format = info.displayName.substringAfterLast('.', "")
            .uppercase()
            .takeIf { it.isNotEmpty() }
            ?: guessFormatLabel(uri)

        val book = BookEntity(
            title = parsed.title,
            uri = uri.toString(),
            // Stored for display only; the real format is re-detected on open, so
            // a wrong or missing extension here cannot break reading.
            format = format,
            sizeBytes = info.sizeBytes,
            charCount = parsed.charCount,
            lastOpenedAt = System.currentTimeMillis()
        )

        val id = bookDao.insert(book)

        // Covers are cached after the row exists, because the cache file is named
        // after the id. A failure here must not lose the book, so it is swallowed
        // and the shelf falls back to a generated placeholder.
        val cover = CoverExtractor.extract(
            context = context,
            uri = uri.toString(),
            format = format,
            bookId = id,
            openStream = { path ->
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(path))
                }.getOrNull()
            }
        )
        if (cover != null) bookDao.setCover(id, cover)

        return@withContext id
    }

    /** Best-effort label for the shelf when the provider gave no extension. */
    private fun guessFormatLabel(uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val head = ByteArray(4)
            if (stream.read(head) == 4) head else ByteArray(0)
        } ?: ByteArray(0)
        val isZip = bytes.size == 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        return if (isZip) "EPUB" else "TXT"
    }

    /**
     * Reads DISPLAY_NAME and SIZE from the provider.
     *
     * Both columns are optional for a generic provider, so every field falls back
     * to a sensible default instead of failing the import.
     */
    private fun queryDocumentInfo(uri: Uri): DocumentInfo {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "未命名"
        var size = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    cursor.getString(nameIndex)?.let { name = it }
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        return DocumentInfo(displayName = name, sizeBytes = size)
    }
}
