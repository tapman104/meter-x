package com.meterx.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.meterx.app.ui.DashboardScreen
import com.meterx.core.network.NetworkSpeed
import com.meterx.core.service.SpeedMeterService

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // If permission is denied, the foreground service might not show notifications or start
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val networkSpeed by SpeedMeterService.speedFlow.collectAsState(
                initial = NetworkSpeed(0L, 0L, "0 B/s", "0 B/s")
            )
            val isServiceRunning by SpeedMeterService.runningFlow.collectAsState(
                initial = SpeedMeterService.isRunning
            )

            // Using dark theme for a modern look as requested
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(
                        networkSpeed = networkSpeed,
                        isServiceRunning = isServiceRunning,
                        onToggleService = { start ->
                            val serviceIntent = Intent(this, SpeedMeterService::class.java)
                            if (start) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    startForegroundService(serviceIntent)
                                } else {
                                    startService(serviceIntent)
                                }
                            } else {
                                serviceIntent.action = SpeedMeterService.ACTION_STOP
                                startService(serviceIntent)
                            }
                        }
                    )
                }
            }
        }
    }
}
