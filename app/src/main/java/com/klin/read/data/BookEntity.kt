package com.klin.read.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One imported book on the shelf.
 *
 * The app never stores book text itself: it keeps the SAF URI the user granted
 * and reads content on demand. That keeps the database tiny and means we hold no
 * copy of the user's files.
 *
 * Cover art is the one exception. It is extracted once at import time and cached
 * as a small file, because re-parsing an EPUB cover on every shelf scroll would
 * be far too slow. [coverPath] points at that cache file.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Display name, derived from the file name or the document's own title. */
    val title: String,

    /** Persisted Storage Access Framework URI. */
    @ColumnInfo(name = "uri")
    val uri: String,

    /** Upper-case file extension used to choose a parser, e.g. "TXT". */
    val format: String,

    /** Size in bytes at import time, shown on the shelf. */
    val sizeBytes: Long,

    /** Total characters, used to render a rough progress percentage. */
    val charCount: Int,

    /** Last time this book was opened, or import time. Drives shelf ordering. */
    @ColumnInfo(name = "last_opened_at")
    val lastOpenedAt: Long,

    val addedAt: Long = System.currentTimeMillis(),

    /**
     * Absolute path to the cached cover image, or null when the format carries no
     * cover (TXT, HTML, UMD) or extraction failed. The shelf draws a generated
     * placeholder in that case.
     */
    @ColumnInfo(name = "cover_path")
    val coverPath: String? = null,

    /**
     * Set once the reader reaches the end of the last chapter.
     *
     * Drives the "已读完" badge on the cover and membership of the 读完 category.
     * It is never cleared automatically — a finished book stays finished until the
     * user changes the category by hand.
     */
    @ColumnInfo(name = "is_finished")
    val isFinished: Boolean = false,

    /** When the book was marked finished, for stable ordering inside 读完. */
    @ColumnInfo(name = "finished_at")
    val finishedAt: Long? = null,

    /**
     * User-assigned shelf category, or null for 未分类.
     *
     * Stored as the display name so a renamed category is a single update.
     */
    @ColumnInfo(name = "category")
    val category: String? = null
)
