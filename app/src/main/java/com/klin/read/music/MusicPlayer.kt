package com.klin.read.music

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Playback wrapper around [MediaPlayer].
 *
 * A process-wide singleton rather than a bound service: the app has no background
 * playback requirement, so a service would add a notification, a lifecycle and a
 * foreground-service permission for no benefit. Playback stops with the process,
 * which is what background music inside a reader should do.
 *
 * Failures are surfaced on [error] instead of being swallowed. The earlier
 * version caught everything and did nothing, so a failed start looked identical
 * to a button that did not work.
 */
object MusicPlayer {

    private var player: MediaPlayer? = null
    private var currentUri: String? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playingUri = MutableStateFlow<String?>(null)
    val playingUri: StateFlow<String?> = _playingUri.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loopEnabled = true
    private var volumeLevel = 0.6f

    /** Fade animation, so playback does not start or stop abruptly. */
    private var fadeJob: kotlinx.coroutines.Job? = null
    private val fadeScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()
    )

    /**
     * Ramps the player volume between [from] and [to].
     *
     * Runs on the main dispatcher because MediaPlayer must be touched from the
     * thread that created it.
     */
    private fun fade(from: Float, to: Float, durationMs: Long = 700) {
        fadeJob?.cancel()
        fadeJob = fadeScope.launch {
            val steps = 24
            val delayPerStep = (durationMs / steps).coerceAtLeast(8)
            for (i in 0..steps) {
                val v = from + (to - from) * (i.toFloat() / steps)
                runCatching { player?.setVolume(v, v) }
                kotlinx.coroutines.delay(delayPerStep)
            }
            runCatching { player?.setVolume(to, to) }
        }
    }

    /** Starts [uri], replacing whatever is playing, resuming at [startMs]. */
    fun play(context: Context, uri: String, startMs: Int = 0) {
        if (currentUri == uri && player?.isPlaying == true) return

        releaseInternal()
        _error.value = null

        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(context.applicationContext, Uri.parse(uri))
            mp.isLooping = loopEnabled
            mp.setVolume(0f, 0f)
            mp.setOnPreparedListener {
                // Resume where the track was left off, unless the stored position
                // is past the end of this file.
                if (startMs > 0 && startMs < it.duration) {
                    it.seekTo(startMs)
                }
                it.start()
                _isPlaying.value = true
                _playingUri.value = uri
                // Ease in rather than starting at full volume.
                fade(0f, volumeLevel)
            }
            mp.setOnCompletionListener {
                if (loopEnabled) {
                    it.start()
                } else {
                    _isPlaying.value = false
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                // MediaPlayer error codes are opaque; report something actionable.
                _isPlaying.value = false
                _error.value = "无法播放这个音轨（错误 $what/$extra）"
                true
            }
            mp.prepareAsync()
            player = mp
            currentUri = uri
        } catch (e: Exception) {
            // setDataSource throws for a URI the app no longer has access to,
            // which is the usual cause when a persisted grant is missing.
            runCatching { mp.release() }
            player = null
            currentUri = null
            _isPlaying.value = false
            _error.value = "无法打开这个音轨：${e.message ?: e::class.simpleName}"
        }
    }

    /**
     * Fades out, then pauses.
     *
     * The pause is deferred until the ramp finishes, so stopping does not cut the
     * sound off mid-note. [onPaused] runs after the audio has actually stopped,
     * which callers use to persist the resume position.
     */
    fun pause(onPaused: (() -> Unit)? = null) {
        val mp = player
        if (mp == null || !mp.isPlaying) {
            _isPlaying.value = false
            onPaused?.invoke()
            return
        }

        _isPlaying.value = false
        fadeJob?.cancel()
        fadeJob = fadeScope.launch {
            val steps = 16
            val delayPerStep = 30L
            for (i in 0..steps) {
                val v = volumeLevel * (1f - i.toFloat() / steps)
                runCatching { player?.setVolume(v, v) }
                kotlinx.coroutines.delay(delayPerStep)
            }
            runCatching { player?.takeIf { it.isPlaying }?.pause() }
            // Restore the level so the next start begins from the right place.
            runCatching { player?.setVolume(volumeLevel, volumeLevel) }
            onPaused?.invoke()
        }
    }

    fun resume() {
        runCatching {
            player?.let {
                if (!it.isPlaying) {
                    it.setVolume(0f, 0f)
                    it.start()
                    _isPlaying.value = true
                    fade(0f, volumeLevel)
                }
            }
        }
    }

    fun toggle(context: Context, uri: String) {
        if (currentUri == uri && player != null) {
            if (_isPlaying.value) pause() else resume()
        } else {
            play(context, uri)
        }
    }

    /** Fades out and releases. */
    fun stop() {
        val mp = player
        if (mp == null || !mp.isPlaying) {
            releaseInternal()
            _isPlaying.value = false
            _playingUri.value = null
            return
        }

        _isPlaying.value = false
        fadeJob?.cancel()
        fadeJob = fadeScope.launch {
            val steps = 16
            for (i in 0..steps) {
                val v = volumeLevel * (1f - i.toFloat() / steps)
                runCatching { player?.setVolume(v, v) }
                kotlinx.coroutines.delay(30L)
            }
            releaseInternal()
            _playingUri.value = null
        }
    }

    fun setLoop(enabled: Boolean) {
        loopEnabled = enabled
        runCatching { player?.isLooping = enabled }
    }

    fun setVolume(level: Float) {
        volumeLevel = level.coerceIn(0f, 1f)
        runCatching { player?.setVolume(volumeLevel, volumeLevel) }
    }

    /** Current offset in ms, or 0 when nothing is loaded. */
    fun positionMs(): Int = runCatching {
        player?.takeIf { currentUri != null }?.currentPosition ?: 0
    }.getOrDefault(0)

    /** Duration in ms, or 0 when unknown. */
    fun durationMs(): Int = runCatching {
        player?.takeIf { currentUri != null }?.duration ?: 0
    }.getOrDefault(0)

    fun consumeError() {
        _error.value = null
    }

    private fun releaseInternal() {
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
        currentUri = null
    }
}
