package com.meterx.core.network

import kotlinx.coroutines.flow.Flow

/**
 * Contract for speed measurement providers.
 * Implementations are responsible for sampling network traffic and emitting speed updates.
 */
interface SpeedProvider {
    /**
     * Returns a continuous flow of network speed measurements.
     * The flow should emit at regular intervals (adaptive or fixed).
     * @return Flow<NetworkSpeed> emitting speed updates
     */
    fun getSpeedFlow(): Flow<NetworkSpeed>
}
