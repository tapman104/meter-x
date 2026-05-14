package com.meterx.core.network

import android.content.Context
import android.net.TrafficStats
import android.os.SystemClock
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

    init {
        // Bug2 fix: ensure boot baselines are set before the first getDailyUsage()
        // call, even when BootReceiver hasn't fired (e.g. sideloaded/debugged builds).
        usageManager.onBootOrStart()
    }

    override fun getSpeedFlow(): Flow<NetworkSpeed> = flow {
        var previousRx = TrafficStats.getTotalRxBytes()
        var previousTx = TrafficStats.getTotalTxBytes()
        var lastTimestamp = SystemClock.elapsedRealtime()
        // Bug5 fix: fixed 1 s interval — eliminates the one-cycle lag where the
        // adaptive interval change was applied one iteration late.

        if (previousRx == TrafficStats.UNSUPPORTED.toLong()) previousRx = 0L
        if (previousTx == TrafficStats.UNSUPPORTED.toLong()) previousTx = 0L

        var iterations = 0

        while (true) {
            delay(1000L)
            iterations++

            val currentRx = TrafficStats.getTotalRxBytes().let { if (it == TrafficStats.UNSUPPORTED.toLong()) 0L else it }
            val currentTx = TrafficStats.getTotalTxBytes().let { if (it == TrafficStats.UNSUPPORTED.toLong()) 0L else it }
            val currentTimestamp = SystemClock.elapsedRealtime()

            val timeDiffMs = (currentTimestamp - lastTimestamp).coerceAtLeast(1)
            
            val rxDiff = currentRx - previousRx
            val txDiff = currentTx - previousTx

            // Calculate bytes per second based on actual time elapsed for better accuracy
            val downloadSpeed = if (rxDiff >= 0) (rxDiff * 1000 / timeDiffMs) else 0L
            val uploadSpeed = if (txDiff >= 0) (txDiff * 1000 / timeDiffMs) else 0L

            // Bug1+Bug3 fix: use the atomic method every 15 s so save and read
            // share one TrafficStats snapshot and rotateDayIfNeeded fires only once.
            val dailyUsage = if (iterations % 15 == 0) {
                usageManager.saveAndGetDailyUsage()
            } else {
                usageManager.getDailyUsage()
            }

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

            previousRx = currentRx
            previousTx = currentTx
            lastTimestamp = currentTimestamp
        }
    }
}
