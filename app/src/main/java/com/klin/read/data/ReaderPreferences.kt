package com.klin.read.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The single DataStore for reader preferences.
 *
 * Declared on `Context` by `preferencesDataStore`, which is a property delegate:
 * it caches one instance PER DELEGATE, and the receiver matters. An Activity and
 * its Application are different `Context` instances, so resolving this delegate on
 * each of them yields two independent DataStores over the same file -- writes
 * through one are not seen by readers of the other.
 *
 * That was a real, hard-to-see bug: the reader (created from the Activity) wrote a
 * position, and the shelf (created from the Application) never observed it, so a
 * book stayed in "未读" after being read. The reader and the shelf each had a
 * live subscription; they were just subscribed to different stores.
 *
 * Everything below therefore resolves through [applicationContext], so there is
 * exactly one store for the process.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_prefs")

/** Visual theme for the reading surface. */
enum class ReaderTheme { LIGHT, DARK, SEPIA }

/** Typography the user controls from the reading screen. */
data class ReaderSettings(
    val fontSizeSp: Float = 18f,
    val lineHeightMultiplier: Float = 1.7f,
    /**
     * Sepia by default: the reading surface should open in the warm paper-like
     * mode rather than stark white.
     */
    val theme: ReaderTheme = ReaderTheme.SEPIA,
    /** App-wide dark appearance. */
    val darkTheme: Boolean = false,
    /** How the reader advances between screens. */
    val pageTurn: PageTurnMode = PageTurnMode.SCROLL,
    /** Horizontal page margin in dp. */
    val marginDp: Float = 20f,
    /**
     * Window brightness, 0f..1f.
     *
     * Only this app's window is affected: Android does not let an app change the
     * system brightness without a special permission, and silently altering a
     * device-wide setting would be a poor trade anyway.
     */
    val brightness: Float = 1f
)

/**
 * Persists per-book reading position and the shared typography settings.
 *
 * Position is stored as a character offset into the parsed text, which keeps it
 * independent of font size, screen size, and line spacing — a saved page number
 * would break the moment the user changed any of those.
 */
class ReaderPreferences(context: Context) {

    /**
     * Always the application context.
     *
     * `context.dataStore` is a per-Context property delegate, so constructing this
     * class from an Activity and from an Application produced two independent
     * DataStores over one file: a write through one was invisible to readers of the
     * other. See the delegate's own comment for the bug that caused.
     */
    private val context: Context = context.applicationContext

    private object Keys {
        val FONT_SIZE = floatPreferencesKey("font_size_sp")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val THEME = stringPreferencesKey("theme")
        // Renamed from "dark_glass": the glass treatment is gone, so the key
        // should not still name it.
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val PAGE_TURN = stringPreferencesKey("page_turn")
        val MARGIN = floatPreferencesKey("margin_dp")
        val BRIGHTNESS = floatPreferencesKey("brightness")

        fun position(bookId: Long) = intPreferencesKey("$POSITION_PREFIX$bookId")
        fun chapter(bookId: Long) = intPreferencesKey("$CHAPTER_PREFIX$bookId")

        /**
         * Set the first time a book is opened.
         *
         * A separate flag rather than inferring "has been opened" from the saved
         * position, because the position cannot express it: chapter one starts at
         * offset 0 and is index 0, so a reader who opens a book and reads the whole
         * first chapter leaves both signals at zero. The shelf then kept showing it
         * as 未读, which was reported as "读了没反应".
         */
        fun opened(bookId: Long) = booleanPreferencesKey("$OPENED_PREFIX$bookId")
    }

    val settings: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        ReaderSettings(
            fontSizeSp = prefs[Keys.FONT_SIZE] ?: 18f,
            lineHeightMultiplier = prefs[Keys.LINE_HEIGHT] ?: 1.6f,
            theme = prefs[Keys.THEME]?.let { name ->
                ReaderTheme.entries.firstOrNull { it.name == name }
            } ?: ReaderTheme.LIGHT,
            darkTheme = prefs[Keys.DARK_THEME] ?: false,
            pageTurn = prefs[Keys.PAGE_TURN]?.let { name ->
                PageTurnMode.entries.firstOrNull { it.name == name }
            } ?: PageTurnMode.SCROLL,
            marginDp = prefs[Keys.MARGIN] ?: 20f,
            brightness = prefs[Keys.BRIGHTNESS] ?: 1f
        )
    }

    suspend fun setBrightness(value: Float) {
        context.dataStore.edit { it[Keys.BRIGHTNESS] = value.coerceIn(MIN_BRIGHTNESS, 1f) }
    }

    suspend fun setPageTurn(mode: PageTurnMode) {
        context.dataStore.edit { it[Keys.PAGE_TURN] = mode.name }
    }

    suspend fun setMargin(dp: Float) {
        context.dataStore.edit { it[Keys.MARGIN] = dp.coerceIn(MIN_MARGIN, MAX_MARGIN) }
    }

    suspend fun setFontSize(sp: Float) {
        context.dataStore.edit { it[Keys.FONT_SIZE] = sp.coerceIn(MIN_FONT, MAX_FONT) }
    }

    suspend fun setLineHeight(multiplier: Float) {
        context.dataStore.edit {
            it[Keys.LINE_HEIGHT] = multiplier.coerceIn(MIN_LINE_HEIGHT, MAX_LINE_HEIGHT)
        }
    }

    suspend fun setTheme(theme: ReaderTheme) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    /** Character offset within the book, plus the chapter it fell in. */
    data class Position(
        val charOffset: Int,
        val chapterIndex: Int,
        /** Whether the book has ever been opened. See [Keys.opened]. */
        val opened: Boolean = false
    )

    fun position(bookId: Long): Flow<Position> = context.dataStore.data.map { prefs ->
        Position(
            charOffset = prefs[Keys.position(bookId)] ?: 0,
            chapterIndex = prefs[Keys.chapter(bookId)] ?: 0,
            opened = prefs[Keys.opened(bookId)] ?: false
        )
    }

    /**
     * Every book's reading position, keyed by book id.
     *
     * One subscription to the whole store rather than a flow per book. The shelf
     * needs all of them at once to compute its chip counts, and a per-book flow
     * meant the shelf could miss an update written while it was not the composed
     * screen -- see ShelfViewModel's init block for the device evidence.
     *
     * Ids are recovered by scanning for the known key prefixes, since DataStore
     * preferences are flat.
     */
    fun observePositions(): Flow<Map<Long, Position>> = context.dataStore.data.map { prefs ->
        val out = mutableMapOf<Long, Position>()
        prefs.asMap().forEach { (key, value) ->
            val name = key.name
            val id = when {
                name.startsWith(POSITION_PREFIX) ->
                    name.removePrefix(POSITION_PREFIX).toLongOrNull()

                name.startsWith(CHAPTER_PREFIX) ->
                    name.removePrefix(CHAPTER_PREFIX).toLongOrNull()

                name.startsWith(OPENED_PREFIX) ->
                    name.removePrefix(OPENED_PREFIX).toLongOrNull()

                else -> null
            } ?: return@forEach

            val existing = out[id]
            out[id] = when {
                name.startsWith(POSITION_PREFIX) ->
                    (existing ?: Position(0, 0)).copy(charOffset = value as? Int ?: 0)

                name.startsWith(CHAPTER_PREFIX) ->
                    (existing ?: Position(0, 0)).copy(chapterIndex = value as? Int ?: 0)

                else ->
                    (existing ?: Position(0, 0)).copy(opened = value as? Boolean ?: false)
            }
        }
        out
    }

    suspend fun savePosition(bookId: Long, charOffset: Int, chapterIndex: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.position(bookId)] = charOffset.coerceAtLeast(0)
            prefs[Keys.chapter(bookId)] = chapterIndex.coerceAtLeast(0)
            // Opening a book is what marks it read; the position alone cannot, for
            // the reason given on Keys.opened.
            prefs[Keys.opened(bookId)] = true
        }
    }

    companion object {
        /**
         * Key prefixes for the per-book reading position.
         *
         * Named rather than inlined because [observePositions] parses ids back out
         * of these keys, so the writer and the reader have to agree on the format.
         */
        const val POSITION_PREFIX = "position_"
        const val CHAPTER_PREFIX = "chapter_"
        const val OPENED_PREFIX = "opened_"

        const val MIN_FONT = 12f
        const val MAX_FONT = 32f
        const val MIN_LINE_HEIGHT = 1.0f
        const val MAX_LINE_HEIGHT = 2.4f
        const val MIN_MARGIN = 8f
        const val MAX_MARGIN = 48f
        /** Never fully black: a screen at 0 is unusable. */
        const val MIN_BRIGHTNESS = 0.05f
    }
}
