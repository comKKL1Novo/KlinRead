package com.klin.read.ui.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.klin.read.data.ReaderDatabase
import com.klin.read.data.ReaderPreferences
import com.klin.read.data.ReaderSettings
import com.klin.read.reader.BookParser
import com.klin.read.reader.Chapter
import com.klin.read.reader.ParsedBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the reading screen renders. */
sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Failed(val reason: String) : ReaderUiState
    data class Ready(
        val book: ParsedBook,
        val chapterIndex: Int,
        val settings: ReaderSettings,
        /** First visible paragraph, used to restore position across modes. */
        val paragraphIndex: Int = 0
    ) : ReaderUiState {
        val chapter: Chapter get() = book.chapters[chapterIndex]
        val body: String get() = book.text.substring(chapter.start, chapter.end)
    }
}

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = ReaderDatabase.get(app).bookDao()
    private val prefs = ReaderPreferences(app)
    private val statsStore = com.klin.read.data.ReadingStatsStore(app)

    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var bookId: Long = -1L

    /** The chapter the user is in, tracked so settings changes can be reapplied. */
    private var currentChapter: Int = 0

    /** Latest typography, mirrored so it can be read synchronously. */
    private var latestSettings: ReaderSettings = ReaderSettings()

    init {
        // Started once per ViewModel rather than per load(), so reloading a book
        // cannot stack duplicate collectors.
        viewModelScope.launch {
            prefs.settings.collectLatest { settings ->
                latestSettings = settings
                _state.update { current ->
                    if (current is ReaderUiState.Ready) current.copy(settings = settings) else current
                }
            }
        }
    }

    /**
     * Counts a minute of reading time.
     *
     * Driven by the screen while a book is open. Only whole minutes are recorded,
     * so the stored total does not grow from a brief accidental visit.
     */
    fun recordReadingMinute() {
        viewModelScope.launch { statsStore.addMinute() }
    }

    fun load(bookId: Long) {
        if (this.bookId == bookId) return
        this.bookId = bookId

        viewModelScope.launch {
            val entity = dao.findById(bookId)
            if (entity == null) {
                _state.value = ReaderUiState.Failed("这本书已不在书架上")
                return@launch
            }

            dao.touch(bookId)

            val saved = prefs.position(bookId).first().chapterIndex

            val parsed = try {
                BookParser.parse(getApplication(), Uri.parse(entity.uri), entity.title)
            } catch (e: Exception) {
                _state.value = ReaderUiState.Failed(e.message ?: "无法读取该文件")
                return@launch
            }

            val startChapter = saved.coerceIn(0, (parsed.chapters.size - 1).coerceAtLeast(0))
            currentChapter = startChapter

            _state.value = ReaderUiState.Ready(
                book = parsed,
                chapterIndex = startChapter,
                settings = latestSettings
            )
        }
    }

    fun goToChapter(index: Int) {
        val current = _state.value as? ReaderUiState.Ready ?: return
        val clamped = index.coerceIn(0, current.book.chapters.size - 1)
        currentChapter = clamped
        // A chapter change resets to its top; keeping the old paragraph index
        // would land the reader mid-chapter for no reason.
        _state.value = current.copy(chapterIndex = clamped, paragraphIndex = 0)
        persistPosition()
    }

    fun nextChapter() {
        val current = _state.value as? ReaderUiState.Ready ?: return
        if (current.chapterIndex < current.book.chapters.size - 1) {
            goToChapter(current.chapterIndex + 1)
        }
    }

    fun previousChapter() {
        val current = _state.value as? ReaderUiState.Ready ?: return
        if (current.chapterIndex > 0) goToChapter(current.chapterIndex - 1)
    }

    /**
     * Records how far into the chapter the reader has scrolled.
     *
     * Only the index is kept in view state; no database write happens here,
     * because this fires on every scroll frame.
     */
    fun onParagraphVisible(index: Int) {
        val current = _state.value as? ReaderUiState.Ready ?: return
        if (current.paragraphIndex != index) {
            _state.value = current.copy(paragraphIndex = index)
        }
    }

    fun setFontSize(sp: Float) {
        viewModelScope.launch { prefs.setFontSize(sp) }
    }

    fun setLineHeight(multiplier: Float) {
        viewModelScope.launch { prefs.setLineHeight(multiplier) }
    }

    fun setMargin(dp: Float) {
        viewModelScope.launch { prefs.setMargin(dp) }
    }

    fun setPageTurn(mode: com.klin.read.data.PageTurnMode) {
        viewModelScope.launch { prefs.setPageTurn(mode) }
    }

    fun setTheme(theme: com.klin.read.data.ReaderTheme) {
        viewModelScope.launch { prefs.setTheme(theme) }
    }

    /** Saves the current chapter's starting offset as the reading position. */
    fun persistPosition() {
        val current = _state.value as? ReaderUiState.Ready ?: return
        if (bookId < 0) return
        viewModelScope.launch {
            prefs.savePosition(bookId, current.chapter.start, current.chapterIndex)
        }
    }
}
