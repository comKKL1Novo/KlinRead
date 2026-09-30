package com.klin.read.ui.music

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.klin.read.data.MusicState
import com.klin.read.data.MusicStore
import com.klin.read.data.Track
import com.klin.read.music.MusicPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private val store = MusicStore(app)

    val state = store.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MusicState())

    val isPlaying: StateFlow<Boolean> = MusicPlayer.isPlaying

    /** The URI actually loaded in the player, or null when nothing is loaded. */
    val playingUri: StateFlow<String?> = MusicPlayer.playingUri

    /** Free-text filter applied to the imported list. */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * The imported list after applying the current query.
     *
     * Derived here, inside the flow, rather than by a plain function the screen
     * calls during composition. The earlier version read `_query.value` directly
     * from a non-observable `filtered()` call, so the result never recomposed and
     * typing in the search box changed nothing on screen.
     */
    val visibleTracks: StateFlow<List<Track>> = combine(state, _query) { s, q ->
        filterTracks(s.tracks, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setQuery(value: String) {
        _query.value = value
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            // Persist the read grant. Without this the URI is only valid for the
            // current process, so playback fails once the app is backgrounded or
            // restarted -- which made the music feature appear to do nothing.
            runCatching {
                getApplication<Application>().contentResolver
                    .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val name = withContext(Dispatchers.IO) { queryName(uri) }
            store.addTrack(Track(uri = uri.toString(), title = name))
            _message.value = "已导入「$name」"
        }
    }

    fun remove(track: Track) {
        viewModelScope.launch {
            store.removeTrack(track.uri)
            _message.value = "已移除「${track.title}」"
        }
    }

    fun play(track: Track) {
        viewModelScope.launch {
            store.setCurrent(track.uri)
            MusicPlayer.play(getApplication(), track.uri)
        }
    }

    fun toggleCurrent(track: Track) {
        viewModelScope.launch {
            store.setCurrent(track.uri)
            MusicPlayer.toggle(getApplication(), track.uri)
        }
    }

    fun stop() = MusicPlayer.stop()

    fun setAutoPlayAsync(enabled: Boolean) {
        viewModelScope.launch { store.setAutoPlayInReader(enabled) }
    }

    fun setVolume(volume: Float) {
        MusicPlayer.setVolume(volume)
        viewModelScope.launch { store.setVolume(volume) }
    }

    fun setLoop(enabled: Boolean) {
        MusicPlayer.setLoop(enabled)
        viewModelScope.launch { store.setLoop(enabled) }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** Display name for a picked document, falling back to the last path segment. */
    private fun queryName(uri: Uri): String {
        var name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?: "未命名音轨"
        getApplication<Application>().contentResolver
            .query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && !cursor.isNull(index)) {
                        cursor.getString(index)?.let { name = it }
                    }
                }
            }
        return name.substringBeforeLast('.').ifBlank { name }
    }
}

/**
 * Tracks whose title contains [query], case-insensitively.
 *
 * A blank query returns everything, and surrounding whitespace is ignored so a
 * stray space does not hide every track. Extracted as a top-level function so the
 * matching rules can be unit-tested without a ViewModel or an Android runtime.
 *
 * Search runs only over what the user imported: there is no catalogue and no
 * network access anywhere in this app.
 */
internal fun filterTracks(tracks: List<Track>, query: String): List<Track> {
    val needle = query.trim()
    if (needle.isEmpty()) return tracks
    return tracks.filter { it.title.contains(needle, ignoreCase = true) }
}
