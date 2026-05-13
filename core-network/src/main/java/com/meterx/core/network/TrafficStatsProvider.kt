package com.meterx.core.network

import android.net.TrafficStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class NetworkSpeed(
    val downloadSpeedBytes: Long,
    val uploadSpeedBytes: Long,
    val formattedDownload: String,
    val formattedUpload: String
)

/**
 * Implementation of [SpeedProvider] using Android's [TrafficStats] API.
 * Samples system-wide network traffic counters at adaptive intervals.
 */
class TrafficStatsProvider : SpeedProvider {

    override fun getSpeedFlow(): Flow<NetworkSpeed> = flow {
        var previousRx = TrafficStats.getTotalRxBytes()
        var previousTx = TrafficStats.getTotalTxBytes()
        var lastTimestamp = System.currentTimeMillis()
        var currentInterval = 1000L

        if (previousRx == TrafficStats.UNSUPPORTED.toLong()) previousRx = 0L
        if (previousTx == TrafficStats.UNSUPPORTED.toLong()) previousTx = 0L

        while (true) {
            delay(currentInterval)

            val currentRx = TrafficStats.getTotalRxBytes().let { if (it == TrafficStats.UNSUPPORTED.toLong()) 0L else it }
            val currentTx = TrafficStats.getTotalTxBytes().let { if (it == TrafficStats.UNSUPPORTED.toLong()) 0L else it }
            val currentTimestamp = System.currentTimeMillis()

            val timeDiffMs = (currentTimestamp - lastTimestamp).coerceAtLeast(1)
            
            val rxDiff = currentRx - previousRx
            val txDiff = currentTx - previousTx

            // Calculate bytes per second based on actual time elapsed for better accuracy
            val downloadSpeed = if (rxDiff >= 0) (rxDiff * 1000 / timeDiffMs) else 0L
            val uploadSpeed = if (txDiff >= 0) (txDiff * 1000 / timeDiffMs) else 0L

            val networkSpeed = NetworkSpeed(
                downloadSpeedBytes = downloadSpeed,
                uploadSpeedBytes = uploadSpeed,
                formattedDownload = formatSpeed(downloadSpeed),
                formattedUpload = formatSpeed(uploadSpeed)
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
