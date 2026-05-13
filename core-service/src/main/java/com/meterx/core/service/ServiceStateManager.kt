package com.meterx.core.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.meterx.core.network.NetworkSpeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager for the SpeedMeterService state.
 * Provides a centralized way to manage service lifecycle and access state flows.
 *
 * Usage:
 * ```
 * val stateManager = ServiceStateManager.getInstance()
 * stateManager.speedFlow.collect { speed -> /* update UI */ }
 * ```
 */
object ServiceStateManager : ServiceStateProvider {
    private val ZERO_SPEED = NetworkSpeed(0L, 0L, "0 KB/s", "0 KB/s")

    private val _speedFlow = MutableStateFlow(ZERO_SPEED)
    override val speedFlow: StateFlow<NetworkSpeed> = _speedFlow.asStateFlow()

    private val _runningFlow = MutableStateFlow(false)
    override val runningFlow: StateFlow<Boolean> = _runningFlow.asStateFlow()

    @Volatile
    private var _isRunning = false

    /**
     * Observes speed updates from the service.
     * Called internally by SpeedMeterService.
     */
    internal fun updateSpeed(speed: NetworkSpeed) {
        _speedFlow.value = speed
    }

    /**
     * Updates the running state of the service.
     * Called internally by SpeedMeterService.
     */
    internal fun setRunning(running: Boolean) {
        _isRunning = running
        _runningFlow.value = running
    }

    override fun startService() {
        _isRunning = true
        _runningFlow.value = true
    }

    override fun stopService() {
        _isRunning = false
        _runningFlow.value = false
        _speedFlow.value = ZERO_SPEED
    }

    override fun isRunning(): Boolean = _isRunning

    /**
     * Singleton accessor.
     */
    fun getInstance(): ServiceStateProvider = this
}
