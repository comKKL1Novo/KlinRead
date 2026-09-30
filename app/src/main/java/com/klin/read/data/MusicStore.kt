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

private val Context.musicStore: DataStore<Preferences> by preferencesDataStore(name = "music")

/**
 * A track the user imported.
 *
 * Only the URI is stored; the audio itself stays wherever the user picked it.
 */
data class Track(
    val uri: String,
    val title: String,
    val durationMs: Long = 0L,
    val addedAt: Long = System.currentTimeMillis()
)

data class MusicState(
    val tracks: List<Track> = emptyList(),
    val currentUri: String? = null,
    /** Playback follows the reader automatically when this is on. */
    val autoPlayInReader: Boolean = false,
    val volume: Float = 0.6f,
    val loop: Boolean = true,
    /** Where the current track was last left off, so it resumes. */
    val positionMs: Int = 0
)

/**
 * Persists the imported playlist and playback preferences.
 *
 * The list is stored as a single delimited string rather than a database table:
 * playlists are small, order matters, and this keeps the whole thing in one
 * atomic write.
 */
class MusicStore(private val context: Context) {

    private object Keys {
        val TRACKS = stringPreferencesKey("tracks")
        val CURRENT = stringPreferencesKey("current_uri")
        val AUTO_IN_READER = booleanPreferencesKey("auto_in_reader")
        val VOLUME = floatPreferencesKey("volume")
        val LOOP = booleanPreferencesKey("loop")
        val POSITION = intPreferencesKey("position_ms")
    }

    val state: Flow<MusicState> = context.musicStore.data.map { prefs ->
        MusicState(
            tracks = decodeTracks(prefs[Keys.TRACKS].orEmpty()),
            currentUri = prefs[Keys.CURRENT]?.takeIf { it.isNotBlank() },
            autoPlayInReader = prefs[Keys.AUTO_IN_READER] ?: false,
            volume = prefs[Keys.VOLUME] ?: 0.6f,
            loop = prefs[Keys.LOOP] ?: true,
            positionMs = prefs[Keys.POSITION] ?: 0
        )
    }

    /** Stores the resume point for the current track. */
    suspend fun setPosition(ms: Int) {
        context.musicStore.edit { it[Keys.POSITION] = ms.coerceAtLeast(0) }
    }

    suspend fun addTrack(track: Track) {
        context.musicStore.edit { prefs ->
            val existing = decodeTracks(prefs[Keys.TRACKS].orEmpty())
            // Re-importing the same file should not duplicate it.
            if (existing.any { it.uri == track.uri }) return@edit
            prefs[Keys.TRACKS] = encodeTracks(existing + track)
            // Deliberately does NOT select the new track.
            //
            // This used to set CURRENT to the first imported track, which made
            // that row render as "已选中" the moment it was imported — before the
            // user ever tapped play. "Selected" has to mean "chosen by the user",
            // so nothing is current until something is actually played.
        }
    }

    suspend fun removeTrack(uri: String) {
        context.musicStore.edit { prefs ->
            val remaining = decodeTracks(prefs[Keys.TRACKS].orEmpty()).filterNot { it.uri == uri }
            prefs[Keys.TRACKS] = encodeTracks(remaining)
            if (prefs[Keys.CURRENT] == uri) {
                // Clearing to "" rather than picking a neighbour: removing the
                // selected track should leave nothing selected, not silently
                // select a different one.
                prefs[Keys.CURRENT] = ""
            }
        }
    }

    suspend fun setCurrent(uri: String) {
        context.musicStore.edit { it[Keys.CURRENT] = uri }
    }

    suspend fun setAutoPlayInReader(enabled: Boolean) {
        context.musicStore.edit { it[Keys.AUTO_IN_READER] = enabled }
    }

    suspend fun setVolume(volume: Float) {
        context.musicStore.edit { it[Keys.VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setLoop(loop: Boolean) {
        context.musicStore.edit { it[Keys.LOOP] = loop }
    }

    /**
     * Tracks are encoded as `uri\u001Ftitle\u001Fduration` per line.
     *
     * A unit separator is used for the fields and a newline between records, so a
     * title containing an ordinary separator cannot corrupt the list.
     */
    private fun encodeTracks(tracks: List<Track>): String = tracks.joinToString("\n") {
        "${it.uri}\u001F${it.title}\u001F${it.durationMs}"
    }

    private fun decodeTracks(raw: String): List<Track> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\u001F')
            if (parts.size < 2 || parts[0].isBlank()) return@mapNotNull null
            Track(
                uri = parts[0],
                title = parts[1],
                durationMs = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            )
        }.toList()
    }
}
