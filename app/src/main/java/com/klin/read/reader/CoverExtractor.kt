package com.klin.read.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Extracts a book's cover image and caches it as a small JPEG.
 *
 * Covers are pulled out once, at import time, and written to the app's cache
 * directory. The shelf then loads a plain file instead of re-parsing a container
 * on every scroll.
 *
 * Coverage is deliberately partial and honest about it:
 *
 *   - **EPUB** — EPUB 3 `properties="cover-image"`, then the EPUB 2
 *     `<meta name="cover" content="id">` route. Both are common, so trying both
 *     matters.
 *   - **MOBI / AZW / AZW3 / PRC** — only the "first image record" heuristic. PalmDB
 *     and EXTH parsing is enough to find the offset; a MOBI without an embedded
 *     image yields nothing.
 *   - **FB2** — the `<coverpage><image xlink:href="...">` element, with the binary
 *     held in a `<binary>` block.
 *   - **TXT / HTML / UMD** — no cover exists in the format at all, so these always
 *     fall back to a generated placeholder. This is the common case for a
 *     Chinese-language shelf, which is mostly TXT.
 *
 * Every path returns null rather than throwing: a missing cover must never stop
 * a book from importing.
 */
object CoverExtractor {

    private const val TAG = "CoverExtractor"

    /** Longest edge of the cached image. Only ever drawn as a small thumbnail. */
    private const val MAX_EDGE = 400

    private const val JPEG_QUALITY = 85

    /**
     * Extracts and caches a cover for [uri].
     *
     * @return the absolute path of the written file, or null when the format has
     *   no cover or extraction failed for any reason.
     */
    fun extract(
        context: Context,
        uri: String,
        format: String,
        bookId: Long,
        openStream: (String) -> InputStream?
    ): String? {
        // A cover is a nicety: every failure path returns null so the shelf falls
        // back to a generated placeholder, and importing a book never fails
        // because of artwork. Warnings are logged so a silent fallback can still
        // be told apart from "this format has no cover".
        val bytes = try {
            openStream(uri)?.use { stream ->
                when (format.uppercase()) {
                    "EPUB" -> fromEpub(stream)
                    "MOBI", "AZW", "AZW3", "PRC" -> fromMobi(stream)
                    "FB2" -> fromFb2(stream)
                    else -> null
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "cover extraction failed for $uri ($format)", e)
            null
        } ?: return null

        return try {
            writeCache(context, bytes, bookId)
        } catch (e: Throwable) {
            Log.w(TAG, "cover cache write failed for $uri", e)
            null
        }
    }

    // ---- Format-specific extraction -----------------------------------------

    private fun fromEpub(stream: InputStream): ByteArray? {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(stream).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val name = entry.name.replace('\\', '/').trimStart('/')
                // Only image bytes and the two small metadata documents are kept;
                // holding a whole book's HTML in memory here would be wasteful.
                val interesting = name.endsWith(".opf", true) ||
                    name.equals("META-INF/container.xml", true) ||
                    isImage(name)
                if (interesting) {
                    entries[name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        if (entries.isEmpty()) return null

        val opfPath = findOpfPath(entries) ?: return null
        val opf = entries[opfPath]?.toString(Charsets.UTF_8) ?: return null
        val baseDir = opfPath.substringBeforeLast('/', "")

        // EPUB 3: the manifest item explicitly flagged as the cover image.
        val byProperty = ITEM.findAll(opf).firstOrNull { match ->
            PROPERTIES_COVER.containsMatchIn(match.value)
        }?.let { ATTR_HREF.find(it.value)?.groupValues?.get(1) }

        // EPUB 2: <meta name="cover" content="itemId"/> pointing at a manifest id.
        // META_COVER has a single capture group (the content attribute), so this
        // reads group 1 — reading group 2 threw IndexOutOfBoundsException.
        val byMetaId = META_COVER.find(opf)?.groupValues?.get(1)
            ?.let { coverId ->
                ITEM.findAll(opf).firstOrNull { match ->
                    ATTR_ID.find(match.value)?.groupValues?.get(1)?.trim() == coverId.trim()
                }?.let { ATTR_HREF.find(it.value)?.groupValues?.get(1) }
            }

        // Last resort: any manifest image whose name suggests a cover.
        val byName = ITEM.findAll(opf).firstOrNull { match ->
            val href = ATTR_HREF.find(match.value)?.groupValues?.get(1) ?: ""
            isImage(href) && href.contains("cover", ignoreCase = true)
        }?.let { ATTR_HREF.find(it.value)?.groupValues?.get(1) }

        val href = byProperty ?: byMetaId ?: byName ?: return null
        val resolved = resolve(baseDir, unescape(href).trim())
        return entries[resolved]
            ?: entries.entries.firstOrNull { it.key.endsWith(resolved.substringAfterLast('/')) }?.value
    }

    /**
     * Finds the first embedded image in a PalmDB/MOBI container.
     *
     * Locates the first image record by its magic bytes rather than fully parsing
     * EXTH metadata; that is enough for the majority of files and does not risk
     * mis-reading a record table we do not otherwise need.
     */
    private fun fromMobi(stream: InputStream): ByteArray? {
        val data = stream.readBytes()
        if (data.size < 100) return null

        for (i in 0 until data.size - 8) {
            val kind = when {
                data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte() -> "jpg"
                data[i] == 0x89.toByte() && data[i + 1] == 0x50.toByte() &&
                    data[i + 2] == 0x4E.toByte() && data[i + 3] == 0x47.toByte() -> "png"
                data[i] == 0x47.toByte() && data[i + 1] == 0x49.toByte() &&
                    data[i + 2] == 0x46.toByte() -> "gif"
                else -> null
            } ?: continue

            val end = when (kind) {
                "jpg" -> indexOf(data, i + 2, JPEG_END)?.plus(JPEG_END.size) ?: continue
                "png" -> indexOf(data, i, PNG_END)?.plus(PNG_END.size) ?: continue
                else -> continue
            }
            if (end > i + 512) return data.copyOfRange(i, minOf(end, data.size))
        }
        return null
    }

    private fun fromFb2(stream: InputStream): ByteArray? {
        val xml = stream.readBytes().toString(Charsets.UTF_8)

        val coverHref = COVER_IMAGE.find(xml)?.groupValues?.get(1)?.trim()?.removePrefix("#")
            ?: return null

        val binary = BINARY.findAll(xml).firstOrNull { match ->
            val id = ATTR_ID.find(match.value)?.groupValues?.get(1)
            id == coverHref
        }?.value ?: return null

        val payload = binary.substringAfter('>', "").substringBeforeLast('<')
            .filterNot { it.isWhitespace() }
        if (payload.isEmpty()) return null

        return runCatching { android.util.Base64.decode(payload, android.util.Base64.DEFAULT) }
            .getOrNull()
    }

    // ---- Cache ---------------------------------------------------------------

    /**
     * Decodes and re-encodes the cover into the app cache.
     *
     * The bytes are re-encoded rather than copied so an enormous or exotic source
     * image becomes a predictable, small thumbnail. Images that decode to
     * something unusably small are rejected so the placeholder is used instead.
     */
    private fun writeCache(context: Context, bytes: ByteArray, bookId: Long): String? {
        // Decode bounds first: a full-size decode of a large cover is wasteful.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

        // Reject near-empty images: some EPUBs ship a 1x1 or spacer "cover".
        if (bitmap.width < 40 || bitmap.height < 40) {
            bitmap.recycle()
            return null
        }

        val dir = File(context.cacheDir, "covers").apply { mkdirs() }
        val file = File(dir, "cover_$bookId.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        bitmap.recycle()
        return file.absolutePath
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= MAX_EDGE) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    // ---- Helpers -------------------------------------------------------------

    private fun isImage(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".gif") || lower.endsWith(".webp")
    }

    private fun findOpfPath(entries: Map<String, ByteArray>): String? {
        entries["META-INF/container.xml"]
            ?.toString(Charsets.UTF_8)
            ?.let { container ->
                ROOTFILE.find(container)?.groupValues?.get(1)?.let { found ->
                    val normalised = found.trimStart('/')
                    if (entries.containsKey(normalised)) return normalised
                }
            }
        return entries.keys.firstOrNull { it.endsWith(".opf") }
    }

    /** Resolves a manifest href against the OPF's directory, handling `../`. */
    private fun resolve(baseDir: String, href: String): String {
        val decoded = runCatching {
            java.net.URLDecoder.decode(href, "UTF-8")
        }.getOrDefault(href)

        if (baseDir.isEmpty()) return decoded
        val parts = (baseDir.split('/') + decoded.split('/')).toMutableList()
        val out = ArrayDeque<String>()
        for (part in parts) {
            when (part) {
                "", "." -> Unit
                ".." -> if (out.isNotEmpty()) out.removeLast()
                else -> out.addLast(part)
            }
        }
        return out.joinToString("/")
    }

    private fun unescape(s: String): String = s
        .replace("&amp;", "&").replace("&#38;", "&")

    /** Index of [needle] in [data] at or after [from], or null. */
    private fun indexOf(data: ByteArray, from: Int, needle: ByteArray): Int? {
        if (needle.isEmpty()) return null
        outer@ for (i in from..data.size - needle.size) {
            for (j in needle.indices) {
                if (data[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return null
    }

    private val JPEG_END = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    private val PNG_END = byteArrayOf(
        0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
    )

    // ---- Patterns ------------------------------------------------------------

    private val ROOTFILE = Regex("""full-path\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ITEM = Regex("""<item\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val ATTR_ID = Regex("""\bid\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ATTR_HREF = Regex("""\bhref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val PROPERTIES_COVER = Regex("""properties\s*=\s*["'][^"']*cover-image""", RegexOption.IGNORE_CASE)
    private val META_COVER = Regex(
        """<meta\b[^>]*name\s*=\s*["']cover["'][^>]*content\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val COVER_IMAGE = Regex(
        """<coverpage\b.*?<image\b[^>]*(?:xlink:)?href\s*=\s*["']([^"']+)["']""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val BINARY = Regex("""<binary\b[^>]*>.*?</binary>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
}
