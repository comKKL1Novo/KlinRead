package com.klin.read.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY last_opened_at DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findById(id: Long): BookEntity?

    /** Used to avoid adding the same file twice when the user re-imports it. */
    @Query("SELECT * FROM books WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): BookEntity?

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("UPDATE books SET last_opened_at = :timestamp WHERE id = :id")
    suspend fun touch(id: Long, timestamp: Long = System.currentTimeMillis())

    /** Caches the extracted cover path; null means "no cover available". */
    @Query("UPDATE books SET cover_path = :path WHERE id = :id")
    suspend fun setCover(id: Long, path: String?)

    /**
     * Marks a book finished.
     *
     * Guarded on `is_finished = 0` so the original timestamp survives repeated
     * calls — the reader reports completion on every page turn past the end, and
     * without this the 读完 ordering would jitter.
     */
    @Query(
        "UPDATE books SET is_finished = 1, finished_at = :timestamp " +
            "WHERE id = :id AND is_finished = 0"
    )
    suspend fun markFinished(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE books SET is_finished = 0, finished_at = NULL WHERE id = :id")
    suspend fun clearFinished(id: Long)

    @Query("UPDATE books SET category = :category WHERE id = :id")
    suspend fun setCategory(id: Long, category: String?)

    /** Distinct non-null categories, for building the filter row. */
    @Query(
        "SELECT DISTINCT category FROM books " +
            "WHERE category IS NOT NULL AND category != '' ORDER BY category"
    )
    fun observeCategories(): Flow<List<String>>
}
