package com.klin.read.reader

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Minimal EPUB reader.
 *
 * Hand-rolled rather than pulled from a toolkit: an EPUB is a ZIP holding an OPF
 * package document, so reading it needs only the ZIP, XML and HTML facilities
 * already on the platform. That keeps dependencies at zero and the behaviour
 * verifiable.
 *
 * Handles EPUB 2 and 3, spine order, nested nav/ncx tables of contents, and the
 * usual HTML entity noise. Does not handle DRM, fixed-layout books,
 * right-to-left progression, or embedded media.
 */
object EpubParser {

    class InvalidEpubException(message: String) : Exception(message)

    /**
     * Parses [stream] as an EPUB.
     *
     * [fallbackTitle] is used when the package document declares no title.
     */
    fun parse(stream: InputStream, fallbackTitle: String): ParsedBook {
        val entries = readZipEntries(stream)

        val opfPath = findOpfPath(entries)
            ?: throw InvalidEpubException("不是有效的 EPUB：找不到 content.opf")

        val opfBase = opfPath.substringBeforeLast('/', "")
        val opf = entries[opfPath]?.toString(Charsets.UTF_8)
            ?: throw InvalidEpubException("不是有效的 EPUB：无法读取 $opfPath")

        val title = extractTitle(opf) ?: fallbackTitle

        // Spine order is the authoritative reading order; the manifest alone is
        // unordered and the nav order can disagree with the spine.
        val manifest = parseManifest(opf)
        val spineIds = parseSpineIds(opf)

        val spinePaths = spineIds.mapNotNull { id ->
            manifest[id]?.let { resolvePath(opfBase, it) }
        }.ifEmpty {
            // Some malformed files omit the spine; fall back to all HTML docs in
            // manifest order rather than refusing to open the book.
            manifest.values
                .filter { it.endsWith(".xhtml") || it.endsWith(".html") || it.endsWith(".htm") }
                .map { resolvePath(opfBase, it) }
        }

        if (spinePaths.isEmpty()) throw InvalidEpubException("这本书里没有可读的正文")

        // Nav labels give real chapter titles; the spine alone would leave file
        // names like "text00007".
        val labels = parseNavLabels(entries, opfBase, manifest)
            .ifEmpty { parseNcxLabels(entries, opfBase, manifest) }

        val text = StringBuilder()
        val chapters = ArrayList<Chapter>()

        spinePaths.forEachIndexed { index, path ->
            val raw = entries[path] ?: return@forEachIndexed
            val html = raw.toString(Charsets.UTF_8)
            val body = extractBodyText(html)
            if (body.isBlank()) return@forEachIndexed

            val label = labels[path]
                ?: labels[path.substringBefore('#')]
                ?: headingFrom(html)
                ?: "第 ${index + 1} 节"

            val start = text.length
            text.append(body.trim())
            text.append("\n\n")
            chapters += Chapter(title = label, start = start, end = text.length)
        }

        if (text.isBlank()) throw InvalidEpubException("这本书里没有可读的正文")

        if (chapters.isNotEmpty()) {
            val last = chapters.last()
            chapters[chapters.size - 1] = last.copy(end = text.length)
        }

        return ParsedBook(title = title, text = text.toString(), chapters = chapters)
    }

    // ---- ZIP ----

    private fun readZipEntries(stream: InputStream): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(stream).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.replace('\\', '/').trimStart('/')
                out[name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        if (out.isEmpty()) throw InvalidEpubException("不是有效的 EPUB：压缩包是空的")
        return out
    }

    /**
     * Locates the package document.
     *
     * The correct route is META-INF/container.xml, which exists so readers do not
     * have to guess the OPF name. A scan is kept as a fallback for the many files
     * shipping a broken container.
     */
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

    // ---- OPF ----

    private fun extractTitle(opf: String): String? =
        DC_TITLE.find(opf)?.groupValues?.get(1)
            ?.let { unescape(it).trim() }
            ?.takeIf { it.isNotEmpty() }

    private fun parseManifest(opf: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ITEM.findAll(opf).forEach { match ->
            val attrs = match.value
            val id = ATTR_ID.find(attrs)?.groupValues?.get(1) ?: return@forEach
            val href = ATTR_HREF.find(attrs)?.groupValues?.get(1) ?: return@forEach
            out[id] = unescape(href).trim()
        }
        return out
    }

    private fun parseSpineIds(opf: String): List<String> {
        val spineBlock = SPINE.findAll(opf).map { it.value }.joinToString(" ")
        return ITEMREF.findAll(spineBlock).mapNotNull { match ->
            ATTR_IDREF.find(match.value)?.groupValues?.get(1)
        }.toList()
    }

    // ---- Navigation ----

    private fun parseNavLabels(
        entries: Map<String, ByteArray>,
        opfBase: String,
        manifest: Map<String, String>
    ): Map<String, String> {
        val navEntry = entries.entries
            .firstOrNull { (path, bytes) ->
                (path.endsWith(".xhtml") || path.endsWith(".html") || path.endsWith(".htm")) &&
                    bytes.toString(Charsets.UTF_8).contains("epub:type=\"nav\"")
            }
            ?: manifest.values.firstOrNull {
                it.lowercase().endsWith(".xhtml") || it.lowercase().endsWith(".html")
            }?.let { href -> entries.entries.firstOrNull { it.key == resolvePath(opfBase, href) } }
            ?: return emptyMap()

        val html = navEntry.value.toString(Charsets.UTF_8)
        val base = navEntry.key.substringBeforeLast('/', "")

        val out = LinkedHashMap<String, String>()
        ANCHOR.findAll(html).forEach { match ->
            val href = ATTR_HREF.find(match.value)?.groupValues?.get(1) ?: return@forEach
            val label = unescape(TAG_STRIP.replace(match.groupValues[1], "")).trim()
            if (label.isEmpty()) return@forEach
            out.putIfAbsent(resolvePath(base, unescape(href)).substringBefore('#'), label)
        }
        return out
    }

    private fun parseNcxLabels(
        entries: Map<String, ByteArray>,
        opfBase: String,
        manifest: Map<String, String>
    ): Map<String, String> {
        val ncxHref = manifest.values.firstOrNull { it.lowercase().endsWith(".ncx") }
            ?: entries.keys.firstOrNull { it.lowercase().endsWith(".ncx") }
            ?: return emptyMap()

        val path = if (entries.containsKey(ncxHref)) ncxHref else resolvePath(opfBase, ncxHref)
        val ncx = entries[path]?.toString(Charsets.UTF_8) ?: return emptyMap()
        val base = path.substringBeforeLast('/', "")

        val out = LinkedHashMap<String, String>()
        NAVPOINT.findAll(ncx).forEach { point ->
            val block = point.value
            val src = ATTR_SRC.find(block)?.groupValues?.get(1) ?: return@forEach
            val label = unescape(
                TAG_STRIP.replace(TEXT_TAG.find(block)?.groupValues?.get(1) ?: "", "")
            ).trim()
            if (label.isEmpty()) return@forEach
            out.putIfAbsent(resolvePath(base, unescape(src)).substringBefore('#'), label)
        }
        return out
    }

    private fun headingFrom(html: String): String? =
        HEADING.find(html)?.groupValues?.get(1)
            ?.let { unescape(TAG_STRIP.replace(it, "")).trim() }
            ?.takeIf { it.isNotEmpty() && it.length <= 60 }

    // ---- HTML -> text ----

    /**
     * Extracts readable text from a content document.
     *
     * Block tags become newlines so paragraphs stay separated. Entities are
     * decoded afterwards, because decoding first could turn an escaped tag into
     * something the stripper then removes.
     */
    private fun extractBodyText(html: String): String {
        val body = BODY.find(html)?.groupValues?.get(1) ?: html
        val withoutHead = SCRIPT_STYLE.replace(body, " ")
        val withBreaks = BLOCK_BOUNDARY.replace(withoutHead, "\n")
        val stripped = TAG_STRIP.replace(withBreaks, "")

        return unescape(stripped)
            .lineSequence()
            .map { it.replace('\u00A0', ' ').trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    // ---- helpers ----

    private fun resolvePath(baseDir: String, relative: String): String {
        if (relative.startsWith("/")) return relative.trimStart('/')
        val parts = (if (baseDir.isEmpty()) listOf() else baseDir.split('/')) + relative.split('/')
        val stack = ArrayList<String>()
        parts.forEach { part ->
            when (part) {
                "", "." -> {}
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                else -> stack += part
            }
        }
        return stack.joinToString("/")
    }

    private fun unescape(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
        .replace("&nbsp;", " ").replace("&mdash;", "—").replace("&ndash;", "–")
        .replace("&hellip;", "…").replace("&ldquo;", "“").replace("&rdquo;", "”")
        .replace("&lsquo;", "‘").replace("&rsquo;", "’").replace("&amp;", "&")

    private val ROOTFILE = Regex("""full-path\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val DC_TITLE = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ITEM = Regex("""<item\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val ITEMREF = Regex("""<itemref\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val SPINE = Regex("""<spine\b[^>]*>.*?</spine>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ATTR_ID = Regex("""\bid\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ATTR_IDREF = Regex("""\bidref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ATTR_HREF = Regex("""\bhref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ATTR_SRC = Regex("""\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ANCHOR = Regex("""<a\b[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val NAVPOINT = Regex("""<navPoint\b.*?</navPoint>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TEXT_TAG = Regex("""<text[^>]*>(.*?)</text>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val HEADING = Regex("""<h[1-6][^>]*>(.*?)</h[1-6]>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val BODY = Regex("""<body\b[^>]*>(.*?)</body>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val SCRIPT_STYLE = Regex("""<(script|style)\b.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val BLOCK_BOUNDARY = Regex(
        """</?(p|div|br|h[1-6]|li|tr|blockquote|section|article)\b[^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val TAG_STRIP = Regex("""<[^>]+>""")
}
