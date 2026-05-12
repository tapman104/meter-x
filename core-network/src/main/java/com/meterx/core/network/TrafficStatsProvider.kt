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

        if (previousRx == TrafficStats.UNSUPPORTED.toLong()) previousRx = 0L
        if (previousTx == TrafficStats.UNSUPPORTED.toLong()) previousTx = 0L

        while (true) {
            delay(1000)

            var currentRx = TrafficStats.getTotalRxBytes()
            var currentTx = TrafficStats.getTotalTxBytes()

            if (currentRx == TrafficStats.UNSUPPORTED.toLong()) currentRx = 0L
            if (currentTx == TrafficStats.UNSUPPORTED.toLong()) currentTx = 0L

            val rxDiff = currentRx - previousRx
            val txDiff = currentTx - previousTx

            // Guard against negative values which could happen if device resets stats
            val downloadSpeed = if (rxDiff >= 0) rxDiff else 0L
            val uploadSpeed = if (txDiff >= 0) txDiff else 0L

            val networkSpeed = NetworkSpeed(
                downloadSpeedBytes = downloadSpeed,
                uploadSpeedBytes = uploadSpeed,
                formattedDownload = formatSpeed(downloadSpeed),
                formattedUpload = formatSpeed(uploadSpeed)
            )

            emit(networkSpeed)

            previousRx = currentRx
            previousTx = currentTx
        }
    }
}
