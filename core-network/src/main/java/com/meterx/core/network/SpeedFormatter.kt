package com.meterx.core.network

import java.util.Locale

fun formatSpeed(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB/s", bytes.toDouble() / (1024L * 1024L * 1024L))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB/s", bytes.toDouble() / (1024L * 1024L))
        bytes >= 1024L -> String.format(Locale.US, "%d KB/s", bytes / 1024L)
        else -> "0 KB/s"
    }
}

fun formatBytes(bytes: Long): String {
    val kb = 1024L
    val mb = kb * 1024L
    val gb = mb * 1024L
    
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / mb)
        bytes >= kb -> String.format(Locale.US, "%d KB", bytes / kb)
        else -> "$bytes B"
    }
}
