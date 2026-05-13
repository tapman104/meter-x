package com.meterx.core.network

import android.content.Context
import android.net.TrafficStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class NetworkSpeed(
    val downloadSpeedBytes: Long,
    val uploadSpeedBytes: Long,
    val formattedDownload: String,
    val formattedUpload: String,
    val dailyWifiBytes: Long = 0L,
    val dailyMobileBytes: Long = 0L,
    val formattedDailyWifi: String = "0 B",
    val formattedDailyMobile: String = "0 B"
)

/**
 * Implementation of [SpeedProvider] using Android's [TrafficStats] API.
 * Samples system-wide network traffic counters at adaptive intervals.
 */
class TrafficStatsProvider(context: Context) : SpeedProvider {

    private val usageManager = TrafficUsageManager(context)

    override fun getSpeedFlow(): Flow<NetworkSpeed> = flow {
        var previousRx = TrafficStats.getTotalRxBytes()
        var previousTx = TrafficStats.getTotalTxBytes()
        var lastTimestamp = System.currentTimeMillis()
        var currentInterval = 1000L

        if (previousRx == TrafficStats.UNSUPPORTED.toLong()) previousRx = 0L
        if (previousTx == TrafficStats.UNSUPPORTED.toLong()) previousTx = 0L

        var iterations = 0

        while (true) {
            delay(currentInterval)
            iterations++

            val currentRx = TrafficStats.getTotalRxBytes().let { if (it == TrafficStats.UNSUPPORTED.toLong()) 0L else it }
            val currentTx = TrafficStats.getTotalTxBytes().let { if (it == TrafficStats.UNSUPPORTED.toLong()) 0L else it }
            val currentTimestamp = System.currentTimeMillis()

            val timeDiffMs = (currentTimestamp - lastTimestamp).coerceAtLeast(1)
            
            val rxDiff = currentRx - previousRx
            val txDiff = currentTx - previousTx

            // Calculate bytes per second based on actual time elapsed for better accuracy
            val downloadSpeed = if (rxDiff >= 0) (rxDiff * 1000 / timeDiffMs) else 0L
            val uploadSpeed = if (txDiff >= 0) (txDiff * 1000 / timeDiffMs) else 0L

            // Periodically persist usage to avoid data loss on reboot (approx every 15s for better accuracy as requested)
            if (iterations % 15 == 0) {
                usageManager.saveUsage()
            }
            
            val dailyUsage = usageManager.getDailyUsage()

            val networkSpeed = NetworkSpeed(
                downloadSpeedBytes = downloadSpeed,
                uploadSpeedBytes = uploadSpeed,
                formattedDownload = formatSpeed(downloadSpeed),
                formattedUpload = formatSpeed(uploadSpeed),
                dailyWifiBytes = dailyUsage.wifiBytes,
                dailyMobileBytes = dailyUsage.mobileBytes,
                formattedDailyWifi = formatBytes(dailyUsage.wifiBytes),
                formattedDailyMobile = formatBytes(dailyUsage.mobileBytes)
            )

            emit(networkSpeed)

            // Adaptive interval: 1s during active traffic, 2s during idle periods
            val newInterval = if (downloadSpeed > 0 || uploadSpeed > 0) 1000L else 2000L
            
            // Always reset counters to ensure accurate next measurement
            previousRx = currentRx
            previousTx = currentTx
            lastTimestamp = currentTimestamp
            currentInterval = newInterval
        }
    }
}
