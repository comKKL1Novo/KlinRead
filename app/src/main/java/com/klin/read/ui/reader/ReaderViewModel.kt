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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

            val position = prefs.position(bookId).first()

            val parsed = try {
                BookParser.parse(getApplication(), Uri.parse(entity.uri), entity.title)
            } catch (e: Exception) {
                _state.value = ReaderUiState.Failed(e.message ?: "无法读取该文件")
                return@launch
            }

            val startChapter = position.chapterIndex
                .coerceIn(0, (parsed.chapters.size - 1).coerceAtLeast(0))
            currentChapter = startChapter

            /*
             * Restore the paragraph too, not just the chapter.
             *
             * Only `chapterIndex` used to be read back, so reopening a book always
             * landed at the top of the chapter you left -- the exact character offset
             * that had just been saved was discarded. Combined with the shelf's
             * progress bar reading that same offset, the two disagreed: the bar said
             * you were 60% in while the reader opened at the chapter start.
             */
            val startParagraph = paragraphIndexAt(
                parsed.text,
                parsed.chapters[startChapter],
                position.charOffset
            )

            _state.value = ReaderUiState.Ready(
                book = parsed,
                chapterIndex = startChapter,
                paragraphIndex = startParagraph,
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
     * because this fires on every scroll frame. Reaching the end of the last
     * chapter is the one exception, and that write is guarded so it happens once.
     */
    fun onParagraphVisible(index: Int) {
        val current = _state.value as? ReaderUiState.Ready ?: return
        if (current.paragraphIndex != index) {
            _state.value = current.copy(paragraphIndex = index)
        }
        markFinishedIfAtEnd(current, index)
    }

    /**
     * Marks the book finished once the reader reaches the end of the last chapter.
     *
     * A chapter is a character range, so "the end" is measured in paragraphs of
     * that chapter's body: the book counts as finished only when the final
     * chapter is open and its last paragraph has been reached. That means merely
     * opening a book — which lands on paragraph 0 — never marks it finished.
     */
    private fun markFinishedIfAtEnd(current: ReaderUiState.Ready, paragraphIndex: Int) {
        if (current.chapterIndex != current.book.chapters.size - 1) return

        val paragraphs = splitParagraphs(
            current.book.text.substring(current.chapter.start, current.chapter.end)
        )
        val lastIndex = paragraphs.lastIndex
        if (lastIndex < 0 || paragraphIndex < lastIndex) return

        // Guarded in SQL as well: markFinished only affects rows still unfinished,
        // so the original finished_at survives repeated calls while scrolling.
        viewModelScope.launch {
            dao.markFinished(bookId)
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

    /**
     * Saves how far into the book the reader actually is.
     *
     * The offset is the START OF THE PARAGRAPH being looked at, not the start of the
     * chapter. Saving `chapter.start` -- which is what this used to do -- meant the
     * shelf only ever learned which chapter you were in, so progress read 0% for the
     * whole of chapter one and then jumped in chapter-sized steps. On a
     * single-chapter book (most TXT files, and any EPUB whose spine is one document)
     * it was 0% forever, which is what "EPUB 格式的书不能正常识别读书状态" describes.
     *
     * Paragraph index is what the reader actually tracks while scrolling, so
     * converting it back to a character offset is exact -- provided the same
     * splitting function is used, which is why [splitParagraphs] is shared rather
     * than reimplemented here.
     */
    fun persistPosition() {
        val current = _state.value as? ReaderUiState.Ready ?: return
        if (bookId < 0) return

        val chapter = current.chapterIndex
        val offset = paragraphOffset(current.book.text, current.chapter, current.paragraphIndex)

        /*
         * Written on an application-scoped coroutine, NOT viewModelScope.
         *
         * The main caller is `onDispose` when the reader screen is left, and by then
         * the ViewModel is being cleared -- `viewModelScope` is cancelled at that
         * moment, so a `launch` there could be torn down before the DataStore write
         * committed. Verified on device: the write logged and the shelf never saw
         * it, so a book stayed in 未读 after being read.
         *
         * The work is a few hundred bytes to a local file, so it does not need to be
         * tied to a UI lifetime. NonCancellable keeps the write atomic even if the
         * scope is cancelled mid-flight.
         */
        appScope.launch {
            withContext(NonCancellable) {
                prefs.savePosition(bookId, offset, chapter)
            }
        }
    }

    private companion object {
        /**
         * Scope for writes that must outlive the screen.
         *
         * Tied to the process rather than to a ViewModel, which is the point: the
         * position is saved precisely as the reader screen goes away.
         */
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
