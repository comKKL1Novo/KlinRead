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
    val darkGlass: Boolean = false,
    /** When false, panels are drawn more opaque. */
    val translucent: Boolean = true,
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
class ReaderPreferences(private val context: Context) {

    private object Keys {
        val FONT_SIZE = floatPreferencesKey("font_size_sp")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val THEME = stringPreferencesKey("theme")
        val DARK_GLASS = booleanPreferencesKey("dark_glass")
        val TRANSLUCENT = booleanPreferencesKey("translucent")
        val PAGE_TURN = stringPreferencesKey("page_turn")
        val MARGIN = floatPreferencesKey("margin_dp")
        val BRIGHTNESS = floatPreferencesKey("brightness")

        fun position(bookId: Long) = intPreferencesKey("position_$bookId")
        fun chapter(bookId: Long) = intPreferencesKey("chapter_$bookId")
    }

    val settings: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        ReaderSettings(
            fontSizeSp = prefs[Keys.FONT_SIZE] ?: 18f,
            lineHeightMultiplier = prefs[Keys.LINE_HEIGHT] ?: 1.6f,
            theme = prefs[Keys.THEME]?.let { name ->
                ReaderTheme.entries.firstOrNull { it.name == name }
            } ?: ReaderTheme.LIGHT,
            darkGlass = prefs[Keys.DARK_GLASS] ?: false,
            translucent = prefs[Keys.TRANSLUCENT] ?: true,
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

    suspend fun setDarkGlass(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_GLASS] = enabled }
    }

    suspend fun setTranslucent(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRANSLUCENT] = enabled }
    }

    /** Character offset within the book, plus the chapter it fell in. */
    data class Position(val charOffset: Int, val chapterIndex: Int)

    fun position(bookId: Long): Flow<Position> = context.dataStore.data.map { prefs ->
        Position(
            charOffset = prefs[Keys.position(bookId)] ?: 0,
            chapterIndex = prefs[Keys.chapter(bookId)] ?: 0
        )
    }

    suspend fun savePosition(bookId: Long, charOffset: Int, chapterIndex: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.position(bookId)] = charOffset.coerceAtLeast(0)
            prefs[Keys.chapter(bookId)] = chapterIndex.coerceAtLeast(0)
        }
    }

    companion object {
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
