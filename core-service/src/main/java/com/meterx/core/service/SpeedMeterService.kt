package com.meterx.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.meterx.core.network.NetworkSpeed
import com.meterx.core.network.TrafficStatsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SpeedMeterService : Service() {

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val trafficStatsProvider = TrafficStatsProvider()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        _runningFlow.value = true
        createNotificationChannel()
        
        // Initial speed state
        _speedFlow.value = ZERO_SPEED
        val notification = createNotification(ZERO_SPEED, usePlaceholder = true)
        updateForeground(notification)
        
        startTracking()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Ensure service is in foreground if started again
        val initialSpeed = _speedFlow.value
        val notification = createNotification(initialSpeed, usePlaceholder = initialSpeed == ZERO_SPEED)
        updateForeground(notification)
        
        return START_STICKY
    }

    private fun updateForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startTracking() {
        serviceScope.launch {
            trafficStatsProvider.getSpeedFlow().collect { speed ->
                _speedFlow.value = speed
                val notification = createNotification(speed)
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun createNotification(speed: NetworkSpeed, usePlaceholder: Boolean = false): Notification {
        val content = "↓ ${speed.formattedDownload}  ↑ ${speed.formattedUpload}"
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Network Speed")
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (!usePlaceholder) {
            val bitmap = createSpeedBitmap(speed)
            val icon = IconCompat.createWithBitmap(bitmap)
            builder.setSmallIcon(icon)
        } else {
            builder.setSmallIcon(R.drawable.ic_speed_placeholder)
        }

        return builder.build()
    }

    private fun createSpeedBitmap(speed: NetworkSpeed): Bitmap {
        val density = resources.displayMetrics.density
        val size = 24 // 24dp square
        val sizePx = (size * density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Choose dominant speed for the icon
        val dominantSpeed = if (speed.downloadSpeedBytes >= speed.uploadSpeedBytes) {
            speed.formattedDownload
        } else {
            speed.formattedUpload
        }

        var (value, unit) = formatForIcon(dominantSpeed)
        
        // Refine value for icon: remove .0 or round if 2+ digits
        if (value.contains(".")) {
            if (value.substringBefore(".").length >= 2 || value.endsWith(".0")) {
                value = value.substringBefore(".")
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            // Use condensed bold to match the tall, tight look in the screenshot
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }

        val centerX = sizePx / 2f
        val bounds = Rect()

        // 1. Draw Value (Top Part)
        var vSize = 14f * density
        paint.textSize = vSize
        paint.getTextBounds(value, 0, value.length, bounds)
        
        // Scale down if it doesn't fit width
        while (bounds.width() > sizePx * 0.95f && vSize > 8f * density) {
            vSize -= 0.5f * density
            paint.textSize = vSize
            paint.getTextBounds(value, 0, value.length, bounds)
        }
        
        // Position value baseline
        canvas.drawText(value, centerX, 13.5f * density, paint)

        // 2. Draw Unit (Bottom Part)
        var uSize = 7.5f * density
        paint.textSize = uSize
        paint.getTextBounds(unit, 0, unit.length, bounds)
        
        // Scale down if unit doesn't fit
        while (bounds.width() > sizePx * 0.95f && uSize > 5f * density) {
            uSize -= 0.5f * density
            paint.textSize = uSize
            paint.getTextBounds(unit, 0, unit.length, bounds)
        }
        
        // Position unit baseline at the bottom
        canvas.drawText(unit, centerX, 21.5f * density, paint)

        return bitmap
    }

    private fun formatForIcon(formatted: String): Pair<String, String> {
        val parts = formatted.split(" ")
        val value = parts.getOrNull(0) ?: "0"
        val unit = parts.getOrNull(1) ?: "B/s"
        return Pair(value, unit)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Network Speed Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time internet speed in the status bar"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        _runningFlow.value = false
        _speedFlow.value = ZERO_SPEED
        serviceScope.cancel()
    }

    companion object {
        private val ZERO_SPEED = NetworkSpeed(0L, 0L, "0 B/s", "0 B/s")
        const val CHANNEL_ID = "speed_meter_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "ACTION_STOP_SERVICE"
        @Volatile
        var isRunning: Boolean = false
            private set

        private val _speedFlow = MutableStateFlow(ZERO_SPEED)
        val speedFlow: StateFlow<NetworkSpeed> = _speedFlow.asStateFlow()

        private val _runningFlow = MutableStateFlow(false)
        val runningFlow: StateFlow<Boolean> = _runningFlow.asStateFlow()
    }
}
