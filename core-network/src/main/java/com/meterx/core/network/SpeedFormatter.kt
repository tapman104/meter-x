package com.meterx.core.network

import java.util.Locale

fun formatSpeed(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%d GB/s", bytes / (1024L * 1024L * 1024L))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%d MB/s", bytes / (1024L * 1024L))
        bytes >= 1024L -> String.format(Locale.US, "%d KB/s", bytes / 1024L)
        else -> "0 KB/s"
    }
}
