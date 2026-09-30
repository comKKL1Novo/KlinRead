package com.klin.read.ui.splash

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Supplies the line shown under the loading bar.
 *
 * Lines come from `assets/text.txt`, one per line, with blank lines and `#`
 * comments ignored. The file shipped with this project, so [FALLBACK] only
 * applies if it is missing or empty.
 */
object DailyQuote {

    private const val ASSET_NAME = "text.txt"

    private val FALLBACK = listOf(
        "读书不是为了雄辩和驳斥，而是为了思考和权衡。",
        "一本书就是一个世界，翻开就是远行。",
        "慢慢来，比较快。",
        "今天也读一点吧。"
    )

    /**
     * Returns a line for this launch.
     *
     * The asset is read on every call rather than cached. A process-wide cache
     * meant the same line appeared for as long as the app stayed in memory, which
     * is why the loading screen never seemed to change.
     *
     * Selection is random so consecutive launches normally differ. The previous
     * day-based index was deterministic, so restarting the app within the same day
     * always showed the same line -- which also read as "never changes".
     */
    suspend fun today(context: Context): String {
        val lines = lines(context)
        if (lines.isEmpty()) return FALLBACK.random()
        return lines.random()
    }

    suspend fun lines(context: Context): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.getOrNull()
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith("#") }
            ?.toList()
            .orEmpty()
            .ifEmpty { FALLBACK }
    }
}
