package com.meterx.core.network

import android.content.Context
import android.net.TrafficStats
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Manages daily network usage tracking.
 * Handles reboot persistence and daily resets.
 */
class TrafficUsageManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("traffic_usage_prefs", Context.MODE_PRIVATE)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Data class to hold usage results
     */
    data class DailyUsage(val wifiBytes: Long, val mobileBytes: Long)

    /**
     * Returns the calculated daily usage for WiFi and Mobile.
     */
    fun getDailyUsage(): DailyUsage {
        rotateDayIfNeeded()

        // Bug6 fix: snapshot both counters in one call each so wifi = total−mobile
        // is computed from a consistent pair of readings.
        val snapMobile = getSafeMobileBytes()
        val snapWifi   = (getSafeTotalBytes() - snapMobile).coerceAtLeast(0L)

        val bootBaselineWifi   = prefs.getLong(PREF_KEY_BOOT_BASELINE_WIFI,   snapWifi)
        val bootBaselineMobile = prefs.getLong(PREF_KEY_BOOT_BASELINE_MOBILE, snapMobile)

        // Detect reboot: counters dropped below baseline → device was rebooted.
        if (snapWifi < bootBaselineWifi || snapMobile < bootBaselineMobile) {
            onBootDetected(snapWifi, snapMobile)
            return getDailyUsage()
        }

        val sessionWifi   = snapWifi   - bootBaselineWifi
        val sessionMobile = snapMobile - bootBaselineMobile

        val persistedWifi   = prefs.getLong(PREF_KEY_DAILY_WIFI,   0L)
        val persistedMobile = prefs.getLong(PREF_KEY_DAILY_MOBILE, 0L)

        return DailyUsage(
            wifiBytes   = persistedWifi   + sessionWifi,
            mobileBytes = persistedMobile + sessionMobile
        )
    }

    /**
     * Atomically persists the session delta AND returns the current daily totals
     * using a single TrafficStats snapshot.
     *
     * Bug1+Bug3 fix: eliminates the implicit ordering dependency between
     * [saveUsage] and [getDailyUsage] and removes the double [rotateDayIfNeeded]
     * call that occurred when both were called back-to-back in the polling loop.
     */
    fun saveAndGetDailyUsage(): DailyUsage {
        rotateDayIfNeeded()

        val snapMobile = getSafeMobileBytes()
        val snapWifi   = (getSafeTotalBytes() - snapMobile).coerceAtLeast(0L)

        val bootBaselineWifi   = prefs.getLong(PREF_KEY_BOOT_BASELINE_WIFI,   snapWifi)
        val bootBaselineMobile = prefs.getLong(PREF_KEY_BOOT_BASELINE_MOBILE, snapMobile)

        val sessionWifi   = (snapWifi   - bootBaselineWifi).coerceAtLeast(0L)
        val sessionMobile = (snapMobile - bootBaselineMobile).coerceAtLeast(0L)

        val persistedWifi   = prefs.getLong(PREF_KEY_DAILY_WIFI,   0L)
        val persistedMobile = prefs.getLong(PREF_KEY_DAILY_MOBILE, 0L)

        val totalWifi   = persistedWifi   + sessionWifi
        val totalMobile = persistedMobile + sessionMobile

        prefs.edit()
            .putLong(PREF_KEY_DAILY_WIFI,           totalWifi)
            .putLong(PREF_KEY_DAILY_MOBILE,         totalMobile)
            .putLong(PREF_KEY_BOOT_BASELINE_WIFI,   snapWifi)
            .putLong(PREF_KEY_BOOT_BASELINE_MOBILE, snapMobile)
            .apply()

        return DailyUsage(wifiBytes = totalWifi, mobileBytes = totalMobile)
    }

    /**
     * Persists the current session delta and advances the boot baselines.
     *
     * Bug3 fix: [rotateDayIfNeeded] is intentionally NOT called here.
     * Day rotation is the caller's responsibility. In the polling loop,
     * callers should use [saveAndGetDailyUsage] instead. This method is
     * retained for direct calls (e.g. [BootReceiver]) where rotation is
     * not needed.
     *
     * Bug6 fix: bytes are snapshotted once so wifi = total−mobile is a
     * consistent pair.
     */
    fun saveUsage() {
        val snapMobile = getSafeMobileBytes()
        val snapWifi   = (getSafeTotalBytes() - snapMobile).coerceAtLeast(0L)

        val bootBaselineWifi   = prefs.getLong(PREF_KEY_BOOT_BASELINE_WIFI,   snapWifi)
        val bootBaselineMobile = prefs.getLong(PREF_KEY_BOOT_BASELINE_MOBILE, snapMobile)

        // Clamp negative deltas — they indicate a reboot counter reset.
        val sessionWifi   = (snapWifi   - bootBaselineWifi).coerceAtLeast(0L)
        val sessionMobile = (snapMobile - bootBaselineMobile).coerceAtLeast(0L)

        val persistedWifi   = prefs.getLong(PREF_KEY_DAILY_WIFI,   0L)
        val persistedMobile = prefs.getLong(PREF_KEY_DAILY_MOBILE, 0L)

        prefs.edit()
            .putLong(PREF_KEY_DAILY_WIFI,           persistedWifi   + sessionWifi)
            .putLong(PREF_KEY_DAILY_MOBILE,         persistedMobile + sessionMobile)
            .putLong(PREF_KEY_BOOT_BASELINE_WIFI,   snapWifi)
            .putLong(PREF_KEY_BOOT_BASELINE_MOBILE, snapMobile)
            .apply()
    }

    /**
     * Rolls usage counters when date changes.
     *
     * Important: we only commit the already-persisted day counters.
     * We intentionally do NOT call [saveUsage] during rollover because when
     * the process was not active at midnight there is no reliable way to split
     * the unsaved delta across two dates. Saving first would wrongly inflate
     * yesterday with today's early traffic.
     */
    private fun rotateDayIfNeeded() {
        val today = getTodayKey()
        val storedDay = prefs.getString(PREF_KEY_LAST_DAY, "")
        if (today == storedDay) return

        if (!storedDay.isNullOrEmpty()) {
            val finalWifi = prefs.getLong(PREF_KEY_DAILY_WIFI, 0L)
            val finalMobile = prefs.getLong(PREF_KEY_DAILY_MOBILE, 0L)
            UsageHistoryRepository(context).saveDay(storedDay, finalWifi, finalMobile)
        }

        resetDailyUsage(today)
    }

    private fun onBootDetected(currentWifi: Long, currentMobile: Long) {
        prefs.edit()
            .putLong(PREF_KEY_BOOT_BASELINE_WIFI, currentWifi)
            .putLong(PREF_KEY_BOOT_BASELINE_MOBILE, currentMobile)
            .apply()
    }

    fun onBootOrStart() {
        val currentMobile = getSafeMobileBytes()
        val currentTotal = getSafeTotalBytes()
        val currentWifi = (currentTotal - currentMobile).coerceAtLeast(0L)

        val bootBaselineWifi = prefs.getLong(PREF_KEY_BOOT_BASELINE_WIFI, -1L)
        if (bootBaselineWifi == -1L || currentWifi < bootBaselineWifi) {
            onBootDetected(currentWifi, currentMobile)
        }
    }

    // Bug6 fix: snapshot once so both baseline keys are derived from the same
    // TrafficStats reading and can never diverge.
    private fun resetDailyUsage(newDay: String) {
        val snapMobile = getSafeMobileBytes()
        val snapWifi   = (getSafeTotalBytes() - snapMobile).coerceAtLeast(0L)
        prefs.edit()
            .putString(PREF_KEY_LAST_DAY,            newDay)
            .putLong(PREF_KEY_DAILY_WIFI,            0L)
            .putLong(PREF_KEY_DAILY_MOBILE,          0L)
            .putLong(PREF_KEY_BOOT_BASELINE_WIFI,    snapWifi)
            .putLong(PREF_KEY_BOOT_BASELINE_MOBILE,  snapMobile)
            .apply()
    }

    private fun getTodayKey(): String = dateFormat.format(Calendar.getInstance().time)

    private fun getSafeMobileBytes(): Long {
        val rx = TrafficStats.getMobileRxBytes()
        val tx = TrafficStats.getMobileTxBytes()
        return (if (rx == TrafficStats.UNSUPPORTED.toLong()) 0L else rx) +
               (if (tx == TrafficStats.UNSUPPORTED.toLong()) 0L else tx)
    }

    private fun getSafeTotalBytes(): Long {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        return (if (rx == TrafficStats.UNSUPPORTED.toLong()) 0L else rx) +
               (if (tx == TrafficStats.UNSUPPORTED.toLong()) 0L else tx)
    }

    companion object {
        private const val PREF_KEY_LAST_DAY = "last_day"
        private const val PREF_KEY_DAILY_WIFI = "daily_wifi"
        private const val PREF_KEY_DAILY_MOBILE = "daily_mobile"
        private const val PREF_KEY_BOOT_BASELINE_WIFI = "boot_baseline_wifi"
        private const val PREF_KEY_BOOT_BASELINE_MOBILE = "boot_baseline_mobile"
    }
}
