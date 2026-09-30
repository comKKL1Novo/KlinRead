package com.klin.read.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.klin.read.data.ReaderPreferences
import com.klin.read.data.ReaderTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ThemeChoice { LIGHT, DARK, SEPIA }

fun ThemeChoice.label(): String = when (this) {
    ThemeChoice.LIGHT -> "浅色"
    ThemeChoice.DARK -> "深色"
    ThemeChoice.SEPIA -> "护眼"
}

data class SettingsUiState(
    val theme: ThemeChoice = ThemeChoice.SEPIA,
    val darkTheme: Boolean = false,
    val brightness: Float = 0.75f
)

/**
 * A value being dragged right now.
 *
 * Persisted settings come back through DataStore asynchronously, so a control
 * bound only to stored state lags the finger and appears to snap back. This is
 * applied to the UI immediately and written on a short debounce.
 */
private data class LiveValues(val brightness: Float? = null)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = ReaderPreferences(app)

    private val live = MutableStateFlow(LiveValues())

    private var brightnessJob: Job? = null

    val state: StateFlow<SettingsUiState> = combine(
        prefs.settings,
        live
    ) { settings, overrides ->
        SettingsUiState(
            theme = settings.theme.toChoice(),
            darkTheme = settings.darkTheme,
            // An in-flight drag wins over the stored value.
            brightness = overrides.brightness ?: settings.brightness
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Applied immediately for preview; persisted once dragging pauses. */
    fun previewBrightness(value: Float) {
        live.value = LiveValues(brightness = value)
        brightnessJob?.cancel()
        brightnessJob = viewModelScope.launch {
            delay(120)
            prefs.setBrightness(value)
        }
    }

    /**
     * Committed on release with the value actually dragged to.
     *
     * Taking a parameter rather than re-reading the caller's value matters: the
     * stored value lags the drag, so committing that would revert the change.
     */
    fun commitBrightness(value: Float) {
        brightnessJob?.cancel()
        live.value = LiveValues(brightness = null)
        viewModelScope.launch { prefs.setBrightness(value) }
    }

    fun setTheme(choice: ThemeChoice) {
        viewModelScope.launch { prefs.setTheme(choice.toTheme()) }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { prefs.setDarkTheme(enabled) }
    }
}

private fun ReaderTheme.toChoice(): ThemeChoice = when (this) {
    ReaderTheme.LIGHT -> ThemeChoice.LIGHT
    ReaderTheme.DARK -> ThemeChoice.DARK
    ReaderTheme.SEPIA -> ThemeChoice.SEPIA
}

private fun ThemeChoice.toTheme(): ReaderTheme = when (this) {
    ThemeChoice.LIGHT -> ReaderTheme.LIGHT
    ThemeChoice.DARK -> ReaderTheme.DARK
    ThemeChoice.SEPIA -> ReaderTheme.SEPIA
}
