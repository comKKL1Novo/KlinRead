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

    /** Deadline guard for a `prepareAsync` that never calls back. */
    private var prepareTimeoutJob: kotlinx.coroutines.Job? = null

    /** Set once a track is actually ready, so the prepare timeout can stand down. */
    private var prepared = false

    private const val PREPARE_TIMEOUT_MS = 8_000L

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

        /*
         * Check the source is still readable BEFORE handing it to MediaPlayer.
         *
         * MediaPlayer reports a missing file through a logcat warning and never
         * calls the error listener, so `setDataSource` "succeeds" and the prepared
         * callback never fires. The track then sits at "已暂停" forever with no
         * message -- indistinguishable from a broken button, which is exactly how
         * this was reported. An imported URI stays in the store after the user
         * deletes or moves the underlying file, so this is the normal case rather
         * than an edge case.
         */
        val readable = runCatching {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
        }.getOrDefault(false)

        if (!readable) {
            _isPlaying.value = false
            _playingUri.value = null
            _error.value = "找不到这个音轨，文件可能已被删除或移动"
            return
        }

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
                prepared = true
                prepareTimeoutJob?.cancel()
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

            /*
             * Timeout for a prepare that never calls back.
             *
             * MediaPlayer can accept a data source and then simply never fire
             * onPrepared or onError -- a truncated file and an unsupported codec
             * both do this. Without a deadline the row stayed at "已暂停" forever
             * with no message, which is the state this feature was reported in.
             *
             * 8s is generous: a local file prepares in well under a second, so
             * anything still pending is not going to succeed.
             */
            prepareTimeoutJob?.cancel()
            prepareTimeoutJob = fadeScope.launch {
                kotlinx.coroutines.delay(PREPARE_TIMEOUT_MS)
                if (player === mp && !prepared && _error.value == null) {
                    runCatching { mp.release() }
                    if (player === mp) {
                        player = null
                        currentUri = null
                    }
                    _isPlaying.value = false
                    _playingUri.value = null
                    _error.value = "这个音轨无法播放，可能已损坏或格式不支持"
                }
            }
        } catch (e: Exception) {
            // setDataSource throws for a URI the app no longer has access to,
            // which is the usual cause when a persisted grant is missing.
            runCatching { mp.release() }
            player = null
            currentUri = null
            _isPlaying.value = false
            _playingUri.value = null
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
        prepareTimeoutJob?.cancel()
        prepareTimeoutJob = null
        prepared = false
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
