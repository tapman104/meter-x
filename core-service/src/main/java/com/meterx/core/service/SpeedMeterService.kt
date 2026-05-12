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
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.meterx.core.network.NetworkSpeed
import com.meterx.core.network.TrafficStatsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
        val initialSpeed = NetworkSpeed(0, 0, "0 KB/s", "0 KB/s")
        _speedFlow.value = initialSpeed
        val notification = createNotification(initialSpeed)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        startTracking()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    private fun startTracking() {
        serviceScope.launch {
            trafficStatsProvider.getSpeedFlow().collect { speed ->
                _speedFlow.value = speed
                notificationManager.notify(NOTIFICATION_ID, createNotification(speed))
            }
        }
    }

    private fun createNotification(speed: NetworkSpeed): Notification {
        val content = "↓ ${speed.formattedDownload}  ↑ ${speed.formattedUpload}"
        val icon = IconCompat.createWithBitmap(createSpeedBitmap(speed))

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Network Speed")
            .setContentText(content)
            .setSmallIcon(icon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createSpeedBitmap(speed: NetworkSpeed): Bitmap {
        // Choose dominant speed for the icon
        val dominantSpeed = if (speed.downloadSpeedBytes >= speed.uploadSpeedBytes) {
            speed.formattedDownload
        } else {
            speed.formattedUpload
        }

        val (value, unit) = formatForIcon(dominantSpeed)

        val bitmap = Bitmap.createBitmap(80, 50, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 26f
        canvas.drawText(value, 40f, 26f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText(unit, 40f, 46f, paint)

        return bitmap
    }

    private fun formatForIcon(formatted: String): Pair<String, String> {
        val parts = formatted.split(" ")
        return if (parts.size >= 2) {
            val value = parts[0]
            val cleanValue = if (value.endsWith(".0")) value.dropLast(2) else value
            Pair(cleanValue, parts[1])
        } else {
            Pair(formatted, "")
        }
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
