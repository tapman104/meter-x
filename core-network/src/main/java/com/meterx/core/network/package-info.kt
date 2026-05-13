/**
 * Core Network Module Public API
 *
 * This package exports the public contracts for the network module:
 *
 * - [SpeedProvider] - Interface for speed measurement implementations
 * - [TrafficStatsProvider] - Reference implementation using Android's TrafficStats API
 * - [NetworkSpeed] - Data class representing a speed measurement
 * - [formatSpeed] - Utility function for formatting speeds
 *
 * Typical usage:
 * ```
 * // Use the reference implementation
 * val provider: SpeedProvider = TrafficStatsProvider()
 * provider.getSpeedFlow().collect { speed ->
 *     println("Download: ${speed.formattedDownload}")
 *     println("Upload: ${speed.formattedUpload}")
 * }
 *
 * // Or implement your own provider
 * class MockSpeedProvider : SpeedProvider {
 *     override fun getSpeedFlow() = flowOf(
 *         NetworkSpeed(1024000, 512000, "1.0 MB/s", "0.5 MB/s")
 *     )
 * }
 * ```
 */
package com.meterx.core.network
