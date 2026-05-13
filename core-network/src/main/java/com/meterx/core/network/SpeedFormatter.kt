package com.meterx.core.network

import java.util.Locale

fun formatSpeed(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB/s", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB/s", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB/s", bytes / 1024.0)
        else -> "$bytes B/s"
    }
}
