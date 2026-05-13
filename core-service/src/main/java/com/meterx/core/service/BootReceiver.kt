package com.meterx.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meterx.core.network.TrafficUsageManager

/**
 * Handles device reboot to ensure traffic usage tracking remains accurate.
 * When the device reboots, Android's TrafficStats counters are reset to zero.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val usageManager = TrafficUsageManager(context)
            // Calibrate baselines after reboot
            usageManager.onBootOrStart()
            
            // Optionally restart the service if it was running before
            if (ServiceStateManager.isRunning()) {
                val serviceIntent = Intent(context, SpeedMeterService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
