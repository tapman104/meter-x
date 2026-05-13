package com.meterx.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Data class to hold usage results
     */
    data class DailyUsage(val wifiBytes: Long, val mobileBytes: Long)

    /**
     * Returns the calculated daily usage for WiFi and Mobile.
     */
    fun getDailyUsage(): DailyUsage {
        val today = getTodayKey()
        val storedDay = prefs.getString(PREF_KEY_LAST_DAY, "")

        if (today != storedDay) {
            saveUsage()
            resetDailyUsage(today)
        }

        val currentTotalMobile = getSafeMobileBytes()
        val currentTotalAll = getSafeTotalBytes()
        val currentTotalWifi = (currentTotalAll - currentTotalMobile).coerceAtLeast(0L)

        val bootBaselineWifi = prefs.getLong(PREF_KEY_BOOT_BASELINE_WIFI, currentTotalWifi)
        val bootBaselineMobile = prefs.getLong(PREF_KEY_BOOT_BASELINE_MOBILE, currentTotalMobile)

        // Detect reboot
        if (currentTotalWifi < bootBaselineWifi || currentTotalMobile < bootBaselineMobile) {
            onBootDetected(currentTotalWifi, currentTotalMobile)
            return getDailyUsage()
        }

        val sessionWifi = currentTotalWifi - bootBaselineWifi
        val sessionMobile = currentTotalMobile - bootBaselineMobile

        val persistedWifi = prefs.getLong(PREF_KEY_DAILY_WIFI, 0L)
        val persistedMobile = prefs.getLong(PREF_KEY_DAILY_MOBILE, 0L)

        return DailyUsage(
            wifiBytes = persistedWifi + sessionWifi,
            mobileBytes = persistedMobile + sessionMobile
        )
    }

    /**
     * Persists the current session usage and updates baselines.
     * This version uses active network detection to prevent misattribution.
     */
    fun saveUsage() {
        val currentTotalMobile = getSafeMobileBytes()
        val currentTotalAll = getSafeTotalBytes()
        val currentTotalWifi = (currentTotalAll - currentTotalMobile).coerceAtLeast(0L)

        val bootBaselineWifi = prefs.getLong(PREF_KEY_BOOT_BASELINE_WIFI, currentTotalWifi)
        val bootBaselineMobile = prefs.getLong(PREF_KEY_BOOT_BASELINE_MOBILE, currentTotalMobile)

        val sessionWifi = (currentTotalWifi - bootBaselineWifi).coerceAtLeast(0L)
        val sessionMobile = (currentTotalMobile - bootBaselineMobile).coerceAtLeast(0L)

        // Attribution check: only add session delta if the specific network is active
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        
        val isWifiActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellularActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        val persistedWifi = prefs.getLong(PREF_KEY_DAILY_WIFI, 0L)
        val persistedMobile = prefs.getLong(PREF_KEY_DAILY_MOBILE, 0L)

        val newDailyWifi = if (isWifiActive) persistedWifi + sessionWifi else persistedWifi
        val newDailyMobile = if (isCellularActive) persistedMobile + sessionMobile else persistedMobile

        prefs.edit()
            .putLong(PREF_KEY_DAILY_WIFI, newDailyWifi)
            .putLong(PREF_KEY_DAILY_MOBILE, newDailyMobile)
            .putLong(PREF_KEY_BOOT_BASELINE_WIFI, currentTotalWifi)
            .putLong(PREF_KEY_BOOT_BASELINE_MOBILE, currentTotalMobile)
            .apply()
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

    private fun resetDailyUsage(newDay: String) {
        prefs.edit()
            .putString(PREF_KEY_LAST_DAY, newDay)
            .putLong(PREF_KEY_DAILY_WIFI, 0L)
            .putLong(PREF_KEY_DAILY_MOBILE, 0L)
            .putLong(PREF_KEY_BOOT_BASELINE_WIFI, (getSafeTotalBytes() - getSafeMobileBytes()).coerceAtLeast(0L))
            .putLong(PREF_KEY_BOOT_BASELINE_MOBILE, getSafeMobileBytes())
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
