package com.klin.read.ui.shelf

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.klin.read.data.BookEntity
import com.klin.read.data.ReaderDatabase
import com.klin.read.data.ReaderPreferences
import com.klin.read.data.ReadingStats
import com.klin.read.data.ReadingStatsStore
import com.klin.read.importer.BookImporter
import com.klin.read.reader.BookParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-off messages surfaced to the user, then cleared. */
sealed interface ShelfMessage {
    data class ImportFailed(val reason: String) : ShelfMessage
}

class ShelfViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = ReaderDatabase.get(app).bookDao()
    private val importer = BookImporter(app, dao)
    private val statsStore = ReadingStatsStore(app)
    private val prefs = ReaderPreferences(app)

    val books: StateFlow<List<BookEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<ReadingStats> = statsStore.stats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingStats())

    private val _message = MutableStateFlow<ShelfMessage?>(null)
    val message: StateFlow<ShelfMessage?> = _message.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    /**
     * How far through [book] the reader is, 0f..1f.
     *
     * Read synchronously from the stored chapter index rather than exposed as a
     * flow: the shelf only needs a rough bar, and a flow per book would mean one
     * subscription per row.
     */
    fun progressFor(book: BookEntity): Float {
        if (book.charCount <= 0) return 0f
        val chapter = cachedChapter(book.id)
        if (chapter <= 0) return 0f
        // Chapters are a proxy for position; a precise offset needs the parsed
        // text, which the shelf deliberately does not load.
        return (chapter.toFloat() / maxOf(chapter + 1, chapterCountHint(book))).coerceIn(0f, 1f)
    }

    /** Last known chapter per book, filled by [refreshProgress]. */
    private val chapterCache = mutableMapOf<Long, Int>()

    private fun cachedChapter(bookId: Long): Int = chapterCache[bookId] ?: 0

    private fun chapterCountHint(book: BookEntity): Int =
        // Roughly one chapter per 8000 characters, which is typical for a novel.
        (book.charCount / 8000).coerceAtLeast(1)

    init {
        // Populate the chapter cache once so the progress bars have a value.
        viewModelScope.launch {
            books.collect { list ->
                list.forEach { book ->
                    val saved = prefs.position(book.id).first()
                    chapterCache[book.id] = saved.chapterIndex
                }
            }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            try {
                importer.import(uri)
            } catch (e: BookParser.UnsupportedFormatException) {
                _message.value = ShelfMessage.ImportFailed(
                    "读不出这个文件，支持 ${BookParser.SUPPORTED_LABEL}"
                )
            } catch (e: Exception) {
                _message.value = ShelfMessage.ImportFailed(e.message ?: "无法读取该文件")
            } finally {
                _importing.value = false
            }
        }
    }

    /**
     * Takes a persistable read grant on [uri].
     *
     * The picker grants access for the current process only; without persisting
     * it, every shelf entry would fail to open after a restart.
     */
    fun persistReadPermission(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun remove(book: BookEntity) {
        viewModelScope.launch { dao.delete(book) }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
