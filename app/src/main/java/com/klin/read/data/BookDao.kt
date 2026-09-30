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
}
