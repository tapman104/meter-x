package com.meterx.core.service

import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for managing service state and speed updates.
 * Provides read-only access to the service's current state via StateFlows.
 */
interface ServiceStateProvider {
    /**
     * StateFlow emitting the current network speed measurement.
     * Subscribers are updated whenever a new speed measurement is available.
     */
    val speedFlow: StateFlow<com.meterx.core.network.NetworkSpeed>

    /**
     * StateFlow indicating whether the speed monitoring service is currently running.
     * Subscribers are updated when the service starts or stops.
     */
    val runningFlow: StateFlow<Boolean>

    /**
     * Starts the speed monitoring service.
     */
    fun startService()

    /**
     * Stops the speed monitoring service.
     */
    fun stopService()

    /**
     * Checks if the service is currently running.
     * @return true if the service is active, false otherwise
     */
    fun isRunning(): Boolean
}
