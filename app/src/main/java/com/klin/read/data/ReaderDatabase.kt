package com.klin.read.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ReaderDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var instance: ReaderDatabase? = null

        fun get(context: Context): ReaderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReaderDatabase::class.java,
                    "reader.db"
                )
                    // v2 added cover caching, the finished flag, and categories.
                    // The schema change is additive but the shelf is cheap to
                    // rebuild by re-importing, so the database is recreated
                    // rather than carrying a migration for a pre-1.0 layout.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
