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

class TrafficStatsProvider {

    fun getSpeedFlow(): Flow<NetworkSpeed> = flow {
        var previousRx = TrafficStats.getTotalRxBytes()
        var previousTx = TrafficStats.getTotalTxBytes()
        var lastTimestamp = System.currentTimeMillis()

        if (previousRx == TrafficStats.UNSUPPORTED.toLong()) previousRx = 0L
        if (previousTx == TrafficStats.UNSUPPORTED.toLong()) previousTx = 0L

        while (true) {
            delay(1000)

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

            previousRx = currentRx
            previousTx = currentTx
            lastTimestamp = currentTimestamp
        }
    }
}
