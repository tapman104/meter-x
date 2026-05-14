# MeterX — Architecture & Codebase Reference

## File Tree

```
internet mete x apk\
│   .gitignore
│   arch.md
│   AUDIT.md
│   build.gradle.kts
│   coderabbit.yaml
│   current-state.md
│   gradle.properties
│   local.properties
│   plan.md
│   project.md
│   README.md
│   responsible.md
│   settings.gradle.kts
│
├───app\
│   │   build.gradle.kts
│   └───src\
│       └───main\
│           │   AndroidManifest.xml
│           ├───java\
│           │   └───com\meterx\app\
│           │       │   MainActivity.kt
│           │       └───ui\
│           │               DashboardScreen.kt
│           └───res\
│               ├───values\
│               │       strings.xml
│               │       themes.xml
│               └───xml\
│                       backup_rules.xml
│                       data_extraction_rules.xml
│
├───core-network\
│   │   build.gradle.kts
│   └───src\
│       └───main\
│           │   AndroidManifest.xml
│           └───java\
│               └───com\meterx\core\network\
│                       package-info.kt
│                       SpeedFormatter.kt
│                       SpeedProvider.kt
│                       TrafficStatsProvider.kt
│                       TrafficUsageManager.kt
│
└───core-service\
    │   build.gradle.kts
    └───src\
        └───main\
            │   AndroidManifest.xml
            ├───java\
            │   └───com\meterx\core\service\
            │           package-info.kt
            │           BootReceiver.kt
            │           ServiceStateManager.kt
            │           ServiceStateProvider.kt
            │           SpeedMeterService.kt
            └───res\
                └───drawable\
                        ic_speed_placeholder.xml
```

---

## Project Overview

**MeterX** is an Android application (`applicationId = "com.meterx"`) that monitors real-time internet speed (download & upload) and daily network usage (WiFi and mobile separately) and displays both in a persistent foreground notification and on a Compose dashboard.

- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 34 (Android 14)
- **Language**: Kotlin
- **UI**: Jetpack Compose with Material3 (dark theme)
- **Build System**: Gradle with Kotlin DSL (`.gradle.kts`)

---

## Module Structure

```
MeterX/
├── app/                  ← Main application module
├── core-network/         ← Network speed measurement + daily usage tracking
└── core-service/         ← Foreground service, boot receiver & state management
```

### Module Dependency Graph

```
app
 ├── core-network
 └── core-service
       └── core-network
```

---

## Module: `core-network`

> **namespace**: `com.meterx.core.network`
> **Purpose**: Pure network measurement and usage tracking — no Android UI or service code.

### Files

| File | Description |
|------|-------------|
| `SpeedProvider.kt` | Interface contract for speed measurement implementations |
| `TrafficStatsProvider.kt` | Concrete `SpeedProvider` using `android.net.TrafficStats`; requires `Context` for usage tracking |
| `TrafficUsageManager.kt` | Daily WiFi/mobile usage persistence, midnight reset, and reboot baseline calibration |
| `NetworkSpeed.kt` (data class inside `TrafficStatsProvider.kt`) | Extended model with speed fields and daily usage fields |
| `SpeedFormatter.kt` | `formatSpeed(bytes: Long): String` and `formatBytes(bytes: Long): String` |
| `package-info.kt` | KDoc listing the module's public API |
| `AndroidManifest.xml` | Empty manifest (library requires no permissions of its own) |
| `build.gradle.kts` | Depends on `core-ktx:1.12.0` and `kotlinx-coroutines-core:1.7.3` |

### Key Classes & Interfaces

#### `SpeedProvider` (interface)
```kotlin
interface SpeedProvider {
    fun getSpeedFlow(): Flow<NetworkSpeed>
}
```
Single-method contract; implementations return a continuous `Flow` of speed measurements.

#### `NetworkSpeed` (data class)
```kotlin
data class NetworkSpeed(
    val downloadSpeedBytes: Long,
    val uploadSpeedBytes: Long,
    val formattedDownload: String,        // e.g. "12.3 MB/s"
    val formattedUpload: String,          // e.g. "3 KB/s"
    val dailyWifiBytes: Long = 0L,
    val dailyMobileBytes: Long = 0L,
    val formattedDailyWifi: String = "0 B",
    val formattedDailyMobile: String = "0 B"
)
```

#### `TrafficStatsProvider(context: Context) : SpeedProvider`
- Reads `TrafficStats.getTotalRxBytes()` and `getTotalTxBytes()` at adaptive intervals.
- **Adaptive polling**: 1 s when traffic is active, 2 s when idle.
- Calculates bytes/s from actual elapsed time (not fixed interval) for accuracy.
- Handles `TrafficStats.UNSUPPORTED` gracefully (falls back to `0L`).
- Handles counter wrap/reset (negative diffs clamped to `0L`).
- Calls `usageManager.saveUsage()` every 15 iterations (≈15–30 s) to persist daily usage.
- Includes `TrafficUsageManager.getDailyUsage()` in every emitted `NetworkSpeed`.

#### `TrafficUsageManager(context: Context)`
- Stores state in `SharedPreferences` (`traffic_usage_prefs`).
- Tracks separate WiFi and mobile byte accumulators keyed by current date (`yyyy-MM-dd`).
- **Daily reset**: on midnight crossing, resets accumulators and updates baselines.
- **Reboot detection**: compares live `TrafficStats` counters against stored baselines; on counter rollback, calls `onBootDetected()` to recalibrate.
- **Attribution**: `saveUsage()` checks `ConnectivityManager.getNetworkCapabilities()` to assign the session delta only to the active transport.
- `onBootOrStart()` — called by `BootReceiver` on `BOOT_COMPLETED` to set initial post-reboot baselines.

#### `formatSpeed(bytes: Long): String`
| Range | Output format |
|-------|--------------|
| ≥ 1 GB/s | `"N.N GB/s"` |
| ≥ 1 MB/s | `"N.N MB/s"` |
| ≥ 1 KB/s | `"N KB/s"` |
| < 1 KB/s | `"0 KB/s"` |

#### `formatBytes(bytes: Long): String`
| Range | Output format |
|-------|--------------|
| ≥ 1 GB | `"N.NN GB"` |
| ≥ 1 MB | `"N.N MB"` |
| ≥ 1 KB | `"N KB"` |
| < 1 KB | `"N B"` |

---

## Module: `core-service`

> **namespace**: `com.meterx.core.service`
> **Purpose**: Android foreground service lifecycle, boot handling, screen-state optimization, notification rendering, and shared state broadcasting.

### Files

| File | Description |
|------|-------------|
| `SpeedMeterService.kt` | `Service` subclass — runs in foreground, collects speed, updates notification |
| `BootReceiver.kt` | `BroadcastReceiver` for `BOOT_COMPLETED` — recalibrates usage baselines and optionally restarts service |
| `ServiceStateManager.kt` | Singleton `object` — bridges service state to UI via `StateFlow`s |
| `ServiceStateProvider.kt` | Interface defining the public contract for `ServiceStateManager` |
| `package-info.kt` | KDoc + `typealias ServiceState = ServiceStateProvider` |
| `AndroidManifest.xml` | Declares `SpeedMeterService` and `BootReceiver`; requests boot, network state, and foreground permissions |
| `build.gradle.kts` | Depends on `core-network`, `core-ktx`, `lifecycle-process`, `lifecycle-runtime-ktx`, `kotlinx-coroutines-android` |

### Key Classes & Interfaces

#### `ServiceStateProvider` (interface)
```kotlin
interface ServiceStateProvider {
    val speedFlow: StateFlow<NetworkSpeed>
    val runningFlow: StateFlow<Boolean>
    fun startService()
    fun stopService()
    fun isRunning(): Boolean
}
```
Provides reactive state and lifecycle control for consumers (UI layer).

#### `ServiceStateManager : ServiceStateProvider` (singleton object)
- Holds `MutableStateFlow`s for speed and running state.
- `updateSpeed(speed)` — called internally by `SpeedMeterService` on each measurement.
- `setRunning(running)` — called by `SpeedMeterService.onCreate()` / `onDestroy()`.
- `getInstance()` — returns `this` as `ServiceStateProvider`.
- Initial speed: `NetworkSpeed(0L, 0L, "0 KB/s", "0 KB/s")`.

#### `SpeedMeterService : Service`
- **Lifecycle**: started as a foreground service by `MainActivity.onCreate()`.
- **Screen-state optimization**: registers a `BroadcastReceiver` for `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF`; pauses tracking while screen is off to save battery. Checks `PowerManager.isInteractive` on startup for the initial screen state.
- **Tracking**: `startTracking()` launches a coroutine (`serviceScope`) collecting from `TrafficStatsProvider.getSpeedFlow()` and pushing updates to both `ServiceStateManager` and the notification. `stopTracking()` cancels the job.
- **Notification content**:
  - Title: `↓ {formattedDownload}  ↑ {formattedUpload}`
  - Text: `WiFi: {formattedDailyWifi} | Mobile: {formattedDailyMobile}`
- **Notification icon**: dynamically renders a 32 dp `Bitmap` with the dominant speed value and unit drawn in white using `sans-serif-condensed Bold` — displayed in the status bar.
- **Notification channel**: `speed_meter_channel`, `IMPORTANCE_LOW`, no sound/vibration, public lock-screen visibility.
- **Stop action**: `ACTION_STOP` intent stops the service cleanly.
- **Android 14+ compatibility**: uses `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` for `startForeground()`.

#### `BootReceiver : BroadcastReceiver`
- Listens for `android.intent.action.BOOT_COMPLETED`.
- Calls `TrafficUsageManager.onBootOrStart()` to recalibrate usage baselines after `TrafficStats` counters reset to zero.
- If `ServiceStateManager.isRunning()` returns `true`, restarts `SpeedMeterService` via `startForegroundService`.

---

## Module: `app`

> **namespace**: `com.meterx`
> **Purpose**: Entry point, UI, permissions.

### Files

| File | Description |
|------|-------------|
| `MainActivity.kt` | Single activity — requests permissions, auto-starts service, hosts Compose UI |
| `ui/DashboardScreen.kt` | Composable dashboard — shows download and upload speed cards |
| `AndroidManifest.xml` | App manifest with permissions and activity declaration |
| `res/values/strings.xml` | String resources (`app_name`) |
| `res/values/themes.xml` | `Theme.MeterX` style (Material Light, no action bar) |
| `res/xml/data_extraction_rules.xml` | Android backup data extraction rules (includes `sharedpref`) |
| `res/xml/backup_rules.xml` | Android Auto Backup rules (includes `sharedpref`) |
| `build.gradle.kts` | Application build config; depends on `core-network`, `core-service`, Compose BOM |

### Key Classes

#### `MainActivity : ComponentActivity`
- Requests `POST_NOTIFICATIONS` permission at runtime (Android 13+).
- Starts `SpeedMeterService` as a foreground service automatically in `onCreate` (`startForegroundService` on API ≥ 26).
- Sets Compose content: collects `ServiceStateManager.speedFlow` via `collectAsState()` and passes it to `DashboardScreen`.
- Uses `darkColorScheme()` Material3 theme.

#### `DashboardScreen` (Composable)
- Receives `NetworkSpeed` as a parameter.
- Shows the app title ("Internet Meter"), a download `SpeedDisplay` card, and an upload `SpeedDisplay` card.
- `SpeedDisplay` composable: a `Card` with direction icon, title, and formatted speed text.
- No service start/stop toggle (service is always active when the app is running).

---

## Build Configuration

### Root `build.gradle.kts`
```kotlin
plugins {
    "com.android.application"      version "8.2.0"  apply false
    "com.android.library"          version "8.2.0"  apply false
    "org.jetbrains.kotlin.android" version "1.9.20" apply false
}
```

### `settings.gradle.kts`
```kotlin
rootProject.name = "MeterX"
include(":app")
include(":core-network")
include(":core-service")
```

### `gradle.properties`
Standard Android Gradle properties (JVM args, AndroidX, etc.)

---

## Data Flow

```
TrafficStatsProvider          (core-network)
      │  Flow<NetworkSpeed>   (speed + daily usage)
      ▼
SpeedMeterService             (core-service)
      │  ServiceStateManager.updateSpeed()
      ▼
ServiceStateManager.speedFlow (StateFlow)
      │  collectAsState()
      ▼
DashboardScreen               (app UI)
      └── Notification        (status bar: speed line + usage line)
```

1. `TrafficStatsProvider` polls `TrafficStats` every 1–2 s, calculates bytes/s, accumulates daily usage via `TrafficUsageManager`, and emits `NetworkSpeed`.
2. `SpeedMeterService` collects the flow, pushes state to `ServiceStateManager`, and updates the persistent notification.
3. `MainActivity` observes `ServiceStateManager.speedFlow` and recomposes `DashboardScreen` on every new value.
4. On reboot, `BootReceiver` recalibrates `TrafficUsageManager` baselines before usage drift can occur.

---

## Permissions

| Permission | Where declared | Purpose |
|-----------|---------------|---------|
| `INTERNET` | `app/AndroidManifest.xml` | Network access |
| `POST_NOTIFICATIONS` | both manifests | Show foreground notification |
| `FOREGROUND_SERVICE` | both manifests | Run foreground service |
| `FOREGROUND_SERVICE_SPECIAL_USE` | both manifests | Required for API 34+ special-use FGS type |
| `RECEIVE_BOOT_COMPLETED` | `core-service/AndroidManifest.xml` | Allow `BootReceiver` to trigger on reboot |
| `ACCESS_NETWORK_STATE` | `core-service/AndroidManifest.xml` | Detect active network transport in `TrafficUsageManager` |

---

## Key Dependencies

| Library | Version | Module | Use |
|---------|---------|--------|-----|
| `androidx.core:core-ktx` | 1.12.0 | all | Kotlin extensions |
| `kotlinx-coroutines-core` | 1.7.3 | core-network | Flow, coroutines |
| `kotlinx-coroutines-android` | 1.7.3 | core-service | Android dispatcher |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.7.0 | app, core-service | Lifecycle-aware coroutines |
| `androidx.lifecycle:lifecycle-process` | 2.7.0 | core-service | Process lifecycle |
| `androidx.activity:activity-compose` | 1.8.2 | app | Compose activity integration |
| `androidx.compose:compose-bom` | 2023.10.01 | app | Compose version alignment |
| `androidx.compose.material3:material3` | (BOM) | app | Material3 UI components |
| `com.android.tools.build:gradle` | 8.2.0 | root | Android Gradle Plugin |
| `org.jetbrains.kotlin.android` | 1.9.20 | root | Kotlin Android plugin |
| Compose Kotlin Compiler Extension | 1.5.4 | app | Compose compiler |


---

## Project Overview

**MeterX** is an Android application (`applicationId = "com.meterx"`) that monitors real-time internet speed (download & upload) and displays it in a persistent foreground notification in the status bar.

- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 34 (Android 14)
- **Language**: Kotlin
- **UI**: Jetpack Compose with Material3 (dark theme)
- **Build System**: Gradle with Kotlin DSL (`.gradle.kts`)

---

## Module Structure

```
MeterX/
├── app/                  ← Main application module
├── core-network/         ← Network speed measurement logic
└── core-service/         ← Foreground service & state management
```

### Module Dependency Graph

```
app
 ├── core-network
 └── core-service
       └── core-network
```

---

## Module: `core-network`

> **namespace**: `com.meterx.core.network`
> **Purpose**: Pure network measurement — no Android UI or service code.

### Files

| File | Description |
|------|-------------|
| `SpeedProvider.kt` | Interface contract for speed measurement implementations |
| `TrafficStatsProvider.kt` | Concrete `SpeedProvider` using `android.net.TrafficStats` API |
| `NetworkSpeed.kt` (inside `TrafficStatsProvider.kt`) | `data class NetworkSpeed(downloadSpeedBytes, uploadSpeedBytes, formattedDownload, formattedUpload)` |
| `SpeedFormatter.kt` | Top-level `formatSpeed(bytes: Long): String` — converts bytes/s to `KB/s`, `MB/s`, `GB/s` |
| `package-info.kt` | KDoc listing the module's public API |
| `AndroidManifest.xml` | Empty manifest (library requires no permissions of its own) |
| `build.gradle.kts` | Depends on `core-ktx:1.12.0` and `kotlinx-coroutines-core:1.7.3` |

### Key Classes & Interfaces

#### `SpeedProvider` (interface)
```kotlin
interface SpeedProvider {
    fun getSpeedFlow(): Flow<NetworkSpeed>
}
```
- Single-method contract; implementations return a continuous `Flow` of speed measurements.

#### `TrafficStatsProvider : SpeedProvider`
- Reads `TrafficStats.getTotalRxBytes()` and `getTotalTxBytes()` at adaptive intervals.
- **Adaptive polling**: 1 s when traffic is active, 2 s when idle.
- Calculates bytes/s from actual elapsed time (not fixed interval) for accuracy.
- Handles `TrafficStats.UNSUPPORTED` gracefully (falls back to `0L`).
- Handles counter wrap/reset (negative diffs clamped to `0L`).

#### `NetworkSpeed` (data class)
```kotlin
data class NetworkSpeed(
    val downloadSpeedBytes: Long,
    val uploadSpeedBytes: Long,
    val formattedDownload: String,   // e.g. "12 MB/s"
    val formattedUpload: String      // e.g. "3 KB/s"
)
```

#### `formatSpeed(bytes: Long): String`
| Range | Output format |
|-------|--------------|
| ≥ 1 GB/s | `"N GB/s"` |
| ≥ 1 MB/s | `"N MB/s"` |
| ≥ 1 KB/s | `"N KB/s"` |
| < 1 KB/s | `"0 KB/s"` |

---

## Module: `core-service`

> **namespace**: `com.meterx.core.service`
> **Purpose**: Android foreground service lifecycle, screen-state optimization, notification rendering, and shared state broadcasting.

### Files

| File | Description |
|------|-------------|
| `SpeedMeterService.kt` | `Service` subclass — runs in foreground, collects speed, updates notification |
| `ServiceStateManager.kt` | Singleton `object` — bridges service state to UI via `StateFlow`s |
| `ServiceStateProvider.kt` | Interface defining the public contract for `ServiceStateManager` |
| `package-info.kt` | KDoc + `typealias ServiceState = ServiceStateProvider` |
| `AndroidManifest.xml` | Declares `SpeedMeterService` with `foregroundServiceType="specialUse"` |
| `build.gradle.kts` | Depends on `core-network`, `core-ktx`, `lifecycle-process`, `lifecycle-runtime-ktx`, `kotlinx-coroutines-android` |

### Key Classes & Interfaces

#### `ServiceStateProvider` (interface)
```kotlin
interface ServiceStateProvider {
    val speedFlow: StateFlow<NetworkSpeed>
    val runningFlow: StateFlow<Boolean>
    fun startService()
    fun stopService()
    fun isRunning(): Boolean
}
```
Provides read-only reactive state for consumers (UI layer).

#### `ServiceStateManager : ServiceStateProvider` (singleton object)
- Holds `MutableStateFlow`s for speed and running state.
- `updateSpeed(speed)` — called internally by `SpeedMeterService` on each measurement.
- `setRunning(running)` — called by `SpeedMeterService.onCreate()` / `onDestroy()`.
- `getInstance()` — returns `this` as `ServiceStateProvider` (for DI-friendly usage).
- Initial speed: `NetworkSpeed(0L, 0L, "0 KB/s", "0 KB/s")`.

#### `SpeedMeterService : Service`
- **Lifecycle**: started as a foreground service by `MainActivity`.
- **Screen-state optimization**: registers a `BroadcastReceiver` for `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF`; pauses tracking while screen is off to save battery.
- **Tracking**: launches a coroutine (`serviceScope`) that collects from `TrafficStatsProvider.getSpeedFlow()` and updates both `ServiceStateManager` and the notification.
- **Notification icon**: dynamically renders a `Bitmap` with the dominant speed value + unit (e.g., `12` / `MB/s`) drawn on a 32 dp canvas using `Paint` — displayed in the status bar.
- **Notification channel**: `speed_meter_channel`, `IMPORTANCE_LOW`, no sound/vibration.
- **Stop action**: `ACTION_STOP` intent stops the service cleanly.
- **Android 14+ compatibility**: uses `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` for `startForeground()`.

---

## Module: `app`

> **namespace**: `com.meterx`  
> **Purpose**: Entry point, UI, permissions.

### Files

| File | Description |
|------|-------------|
| `MainActivity.kt` | Single activity — requests permissions, starts service, hosts Compose UI |
| `ui/DashboardScreen.kt` | Composable dashboard — shows download and upload speed cards |
| `AndroidManifest.xml` | App manifest with permissions and activity declaration |
| `res/values/strings.xml` | String resources (`app_name`, etc.) |
| `res/values/themes.xml` | `Theme.MeterX` style definition |
| `res/xml/data_extraction_rules.xml` | Android backup data extraction rules |
| `res/xml/backup_rules.xml` | Android Auto Backup rules |
| `build.gradle.kts` | Application build config; depends on `core-network`, `core-service`, Compose BOM |

### Key Classes

#### `MainActivity : ComponentActivity`
- Requests `POST_NOTIFICATIONS` permission at runtime (Android 13+).
- Starts `SpeedMeterService` as a foreground service (`startForegroundService` on API ≥ 26).
- Sets Compose content: collects `ServiceStateManager.speedFlow` via `collectAsState()` and passes it to `DashboardScreen`.
- Uses `darkColorScheme()` Material3 theme.

#### `DashboardScreen` (Composable)
- Receives `NetworkSpeed` as a parameter.
- Shows the app title, a download `SpeedDisplay` card, and an upload `SpeedDisplay` card.
- `SpeedDisplay` composable: a `Card` with direction icon, title, and formatted speed text.

---

## Build Configuration

### Root `build.gradle.kts`
```kotlin
plugins {
    "com.android.application"  version "8.2.0"  apply false
    "com.android.library"      version "8.2.0"  apply false
    "org.jetbrains.kotlin.android" version "1.9.20" apply false
}
```

### `settings.gradle.kts`
```kotlin
rootProject.name = "MeterX"
include(":app")
include(":core-network")
include(":core-service")
```

### `gradle.properties`
Standard Android Gradle properties (JVM args, AndroidX, etc.)

---

## Data Flow

```
TrafficStatsProvider          (core-network)
      │  Flow<NetworkSpeed>
      ▼
SpeedMeterService             (core-service)
      │  ServiceStateManager.updateSpeed()
      ▼
ServiceStateManager.speedFlow (StateFlow)
      │  collectAsState()
      ▼
DashboardScreen               (app UI)
      +── Notification icon   (status bar)
```

1. `TrafficStatsProvider` polls `TrafficStats` every 1–2 s and emits `NetworkSpeed`.
2. `SpeedMeterService` collects the flow, pushes state to `ServiceStateManager`, and updates the persistent notification.
3. `MainActivity` observes `ServiceStateManager.speedFlow` and recomposes `DashboardScreen` on every new value.

---

## Permissions

| Permission | Where declared | Purpose |
|-----------|---------------|---------|
| `INTERNET` | `app/AndroidManifest.xml` | Network access |
| `POST_NOTIFICATIONS` | both manifests | Show foreground notification |
| `FOREGROUND_SERVICE` | both manifests | Run foreground service |
| `FOREGROUND_SERVICE_SPECIAL_USE` | both manifests | Required for API 34+ special-use FGS type |

---

## Key Dependencies

| Library | Version | Module | Use |
|---------|---------|--------|-----|
| `androidx.core:core-ktx` | 1.12.0 | all | Kotlin extensions |
| `kotlinx-coroutines-core` | 1.7.3 | core-network | Flow, coroutines |
| `kotlinx-coroutines-android` | 1.7.3 | core-service | Android dispatcher |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.7.0 | app, core-service | Lifecycle-aware coroutines |
| `androidx.lifecycle:lifecycle-process` | 2.7.0 | core-service | Process lifecycle |
| `androidx.activity:activity-compose` | 1.8.2 | app | Compose activity integration |
| `androidx.compose:compose-bom` | 2023.10.01 | app | Compose version alignment |
| `androidx.compose.material3:material3` | (BOM) | app | Material3 UI components |
| `com.android.tools.build:gradle` | 8.2.0 | root | Android Gradle Plugin |
| `org.jetbrains.kotlin.android` | 1.9.20 | root | Kotlin Android plugin |
| Compose Kotlin Compiler Extension | 1.5.4 | app | Compose compiler |
