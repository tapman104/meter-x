package com.meterx.core.network

fun formatSpeed(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.1f GB/s", bytes / (1024f * 1024f * 1024f))
        bytes >= 1024 * 1024 -> String.format("%.1f MB/s", bytes / (1024f * 1024f))
        bytes >= 1024 -> String.format("%.1f KB/s", bytes / 1024f)
        else -> "$bytes B/s"
    }
}
