package com.meterx.core.network

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ─── Data model ──────────────────────────────────────────────────────────────

data class DailyUsageEntry(
    val date: String,          // "yyyy-MM-dd"
    val wifiBytes: Long,
    val mobileBytes: Long
) {
    val totalBytes: Long get() = wifiBytes + mobileBytes
}

// ─── Repository ───────────────────────────────────────────────────────────────

/**
 * Stores and retrieves per-day usage history in SharedPreferences.
 *
 * Keys: "<date>_wifi", "<date>_mobile"
 * Keeping it in a separate prefs file isolates it from live tracking state.
 */
class UsageHistoryRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat(DATE_PATTERN, Locale.US)

    // ── Write ─────────────────────────────────────────────────────────────────

    /** Persist the final totals for a completed day. */
    fun saveDay(date: String, wifiBytes: Long, mobileBytes: Long) {
        prefs.edit()
            .putLong("${date}_wifi", wifiBytes)
            .putLong("${date}_mobile", mobileBytes)
            .apply()
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns a list of [DailyUsageEntry] for the last [days] calendar days,
     * starting from yesterday (today's data is live, not yet committed).
     * Most-recent day first.
     */
    fun getHistory(days: Int = 30): List<DailyUsageEntry> {
        val entries = mutableListOf<DailyUsageEntry>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1) // start from yesterday

        repeat(days) {
            val dateKey = dateFormat.format(cal.time)
            entries.add(
                DailyUsageEntry(
                    date = dateKey,
                    wifiBytes = prefs.getLong("${dateKey}_wifi", 0L),
                    mobileBytes = prefs.getLong("${dateKey}_mobile", 0L)
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return entries
    }

    /** Aggregated usage for the last [days] calendar days (from yesterday). */
    fun getAggregated(days: Int): DailyUsageEntry {
        val entries = getHistory(days)
        return DailyUsageEntry(
            date = "Last $days days",
            wifiBytes = entries.sumOf { it.wifiBytes },
            mobileBytes = entries.sumOf { it.mobileBytes }
        )
    }

    /** Aggregated usage for the current calendar month (days elapsed so far). */
    fun getThisMonth(): DailyUsageEntry {
        val cal = Calendar.getInstance()
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH) - 1 // exclude today
        val aggregated = if (dayOfMonth > 0) getAggregated(dayOfMonth) else
            DailyUsageEntry("This Month", 0L, 0L)
        return aggregated.copy(date = "This Month")
    }

    // ── Debug helpers ─────────────────────────────────────────────────────────

    /** Clear all stored history. Useful for testing. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /** Returns all raw key→value pairs for inspection during debugging. */
    fun dumpAll(): Map<String, *> = prefs.all

    companion object {
        const val PREFS_NAME = "usage_history_prefs"
        const val DATE_PATTERN = "yyyy-MM-dd"
    }
}
