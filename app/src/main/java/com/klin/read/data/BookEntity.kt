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

    val addedAt: Long = System.currentTimeMillis()
)
