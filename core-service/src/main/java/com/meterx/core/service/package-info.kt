/**
 * Core Service Module Public API
 *
 * This package exports the public contracts for the service module:
 *
 * - [ServiceStateProvider] - Interface for accessing service state
 * - [ServiceStateManager] - Singleton implementation of ServiceStateProvider
 * - [SpeedMeterService] - The actual Android Service
 *
 * Typical usage:
 * ```
 * // Access state without creating service directly
 * val stateProvider: ServiceStateProvider = ServiceStateManager
 * val speedFlow = stateProvider.speedFlow
 *
 * // Or use directly
 * ServiceStateManager.speedFlow.collect { speed ->
 *     println("Current speed: $speed")
 * }
 * ```
 */
package com.meterx.core.service

// Re-export public API
public typealias ServiceState = ServiceStateProvider
