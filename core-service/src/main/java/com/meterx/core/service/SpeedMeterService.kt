package com.meterx.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.meterx.core.network.NetworkSpeed
import com.meterx.core.network.SpeedProvider
import com.meterx.core.network.TrafficStatsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class SpeedMeterService : Service() {

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    // Initialized in onCreate() after attachBaseContext() so the Context is valid
    // when TrafficUsageManager calls getSharedPreferences().
    private lateinit var speedProvider: SpeedProvider
    private var trackingJob: Job? = null

    // Bug4 fix: reuse a single Bitmap across notification updates instead of
    // allocating a new off-heap object every 1–2 s and leaking the old one.
    private var notificationBitmap: Bitmap? = null

    private var isScreenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    updateTrackingState()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    updateTrackingState()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Context is now fully initialized — safe to create SpeedProvider.
        speedProvider = TrafficStatsProvider(this)
        ServiceStateManager.setRunning(true)
        createNotificationChannel()
        
        // Check initial screen state
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenOn = powerManager.isInteractive

        // Register screen receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)

        // Initial notification
        val initialSpeed = ServiceStateManager.speedFlow.value
        val notification = createNotification(initialSpeed)
        updateForeground(notification)

        // Start tracking immediately (screen is on when service starts)
        updateTrackingState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Ensure service is in foreground if started again
        val currentSpeed = ServiceStateManager.speedFlow.value
        val notification = createNotification(currentSpeed)
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

    private fun updateTrackingState() {
        if (isScreenOn) {
            startTracking()
        } else {
            stopTracking()
        }
    }

    private fun startTracking() {
        if (trackingJob?.isActive == true) return
        
        trackingJob = serviceScope.launch {
            speedProvider.getSpeedFlow().collect { speed ->
                ServiceStateManager.updateSpeed(speed)
                val notification = createNotification(speed)
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    private fun createNotification(speed: NetworkSpeed): Notification {
        val speedLine = "↓ ${speed.formattedDownload}  ↑ ${speed.formattedUpload}"
        val usageLine = "WiFi: ${speed.formattedDailyWifi} | Mobile: ${speed.formattedDailyMobile}"
        
        val bitmap = createSpeedBitmap(speed)
        val icon = IconCompat.createWithBitmap(bitmap)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(speedLine)
            .setContentText(usageLine)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSmallIcon(icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createSpeedBitmap(speed: NetworkSpeed): Bitmap {
        val density = resources.displayMetrics.density
        val size = 32 // Increased from 24dp to 32dp for larger, more readable status bar icon
        val sizePx = (size * density).toInt()
        val existing = notificationBitmap
        val bitmap = if (existing != null && !existing.isRecycled && existing.width == sizePx) {
            existing.eraseColor(Color.TRANSPARENT)
            existing
        } else {
            existing?.recycle()
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { notificationBitmap = it }
        }
        val canvas = Canvas(bitmap)
        
        // Choose dominant speed for the icon using raw bytes for better accuracy.
        val dominantBytes = maxOf(speed.downloadSpeedBytes, speed.uploadSpeedBytes)
        var (value, unit) = formatForIcon(dominantBytes)
        
        // Refine value for icon: remove .0 or truncate decimals if value is large (2+ digits)
        if (value.contains(".")) {
            val parts = value.split(".")
            val beforeDot = parts[0]
            val afterDot = parts.getOrNull(1) ?: ""
            if (beforeDot.length >= 2 || afterDot == "0") {
                value = beforeDot
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }

        val centerX = sizePx / 2f

        // 1. Draw Value (Top Part) - Increased size for 32dp canvas
        var vSize = 19f * density
        paint.textSize = vSize
        val vBounds = Rect()
        paint.getTextBounds(value, 0, value.length, vBounds)
        
        // Fit width if necessary
        while (vBounds.width() > sizePx * 0.95f && vSize > 12f * density) {
            vSize -= 0.5f * density
            paint.textSize = vSize
            paint.getTextBounds(value, 0, value.length, vBounds)
        }
        
        // Draw the speed value on top
        canvas.drawText(value, centerX, 18f * density, paint)

        // 2. Draw Unit (Bottom Part) - 'KB/s', 'MB/s', 'GB/s' - Increased size for 32dp canvas
        var uSize = 10f * density
        paint.textSize = uSize
        val uBounds = Rect()
        paint.getTextBounds(unit, 0, unit.length, uBounds)
        
        // Fit width if necessary (important for 'KB/s' which is wide)
        while (uBounds.width() > sizePx * 0.98f && uSize > 7f * density) {
            uSize -= 0.5f * density
            paint.textSize = uSize
            paint.getTextBounds(unit, 0, unit.length, uBounds)
        }
        
        // Draw the unit directly below, tightly spaced
        canvas.drawText(unit, centerX, 30f * density, paint)

        return bitmap
    }

    private fun formatForIcon(bytesPerSec: Long): Pair<String, String> {
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0

        return when {
            bytesPerSec <= 0L -> "0" to "KB/s"
            bytesPerSec >= gb.toLong() -> String.format(Locale.US, "%.1f", bytesPerSec / gb) to "GB/s"
            bytesPerSec >= mb.toLong() -> String.format(Locale.US, "%.1f", bytesPerSec / mb) to "MB/s"
            else -> String.format(Locale.US, "%.1f", bytesPerSec / kb) to "KB/s"
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
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ServiceStateManager.setRunning(false)
        
        unregisterReceiver(screenReceiver)
        serviceScope.cancel()
        notificationBitmap?.recycle()
        notificationBitmap = null
    }

    companion object {
        const val CHANNEL_ID = "speed_meter_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "ACTION_STOP_SERVICE"
    }
}
