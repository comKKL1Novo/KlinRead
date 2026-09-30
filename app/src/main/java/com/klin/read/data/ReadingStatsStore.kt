package com.klin.read.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

private val Context.statsStore: DataStore<Preferences> by preferencesDataStore(name = "reading_stats")

/** Reading time totals shown on the shelf. */
data class ReadingStats(
    val todayMinutes: Int = 0,
    val totalMinutes: Int = 0,
    /** Consecutive days with at least one minute read. */
    val streakDays: Int = 0
) {
    /** Short encouragement for the current total. */
    val encouragement: String
        get() = when {
            totalMinutes == 0 -> "还没开始，今天翻一页吧"
            todayMinutes == 0 -> "今天还没读，续上吧"
            todayMinutes < 5 -> "刚翻开，慢慢来"
            todayMinutes < 15 -> "已经读了一会儿了"
            todayMinutes < 30 -> "半小时内，状态不错"
            todayMinutes < 60 -> "今天读得挺多"
            else -> "今天很专注"
        }
}

/**
 * Records how long the reader has been open.
 *
 * Time is accumulated as whole minutes against the current day, so a session that
 * spans midnight still lands on the day it started. Nothing is uploaded.
 */
class ReadingStatsStore(private val context: Context) {

    private object Keys {
        val TOTAL_MINUTES = intPreferencesKey("total_minutes")
        val TODAY_MINUTES = intPreferencesKey("today_minutes")
        val TODAY_DAY = longPreferencesKey("today_day")
        val STREAK = intPreferencesKey("streak_days")
        val LAST_READ_DAY = longPreferencesKey("last_read_day")
    }

    val stats: Flow<ReadingStats> = context.statsStore.data.map { prefs ->
        val today = dayIndex()
        val storedDay = prefs[Keys.TODAY_DAY] ?: today
        // A stale "today" counter from a previous day reads as zero.
        val todayMinutes = if (storedDay == today) prefs[Keys.TODAY_MINUTES] ?: 0 else 0
        ReadingStats(
            todayMinutes = todayMinutes,
            totalMinutes = prefs[Keys.TOTAL_MINUTES] ?: 0,
            streakDays = prefs[Keys.STREAK] ?: 0
        )
    }

    /** Adds a minute of reading and updates the streak. */
    suspend fun addMinute() {
        context.statsStore.edit { prefs ->
            val today = dayIndex()
            val storedDay = prefs[Keys.TODAY_DAY] ?: today
            val todayMinutes = if (storedDay == today) prefs[Keys.TODAY_MINUTES] ?: 0 else 0

            prefs[Keys.TOTAL_MINUTES] = (prefs[Keys.TOTAL_MINUTES] ?: 0) + 1
            prefs[Keys.TODAY_MINUTES] = todayMinutes + 1
            prefs[Keys.TODAY_DAY] = today

            // Extend the streak only on the first minute of a new day.
            val lastRead = prefs[Keys.LAST_READ_DAY]
            if (lastRead != today) {
                val streak = if (lastRead == today - 1) (prefs[Keys.STREAK] ?: 0) + 1 else 1
                prefs[Keys.STREAK] = streak
                prefs[Keys.LAST_READ_DAY] = today
            }
        }
    }

    /** Days since the epoch, used to compare calendar days cheaply. */
    private fun dayIndex(): Long {
        val cal = Calendar.getInstance()
        return cal.timeInMillis / 86_400_000L
    }
}
