# MeterX

MeterX is an Android internet speed meter built with Kotlin, Jetpack Compose, and a small modular architecture. The app samples device-wide network traffic with `TrafficStats`, turns those byte deltas into live download and upload speeds, and surfaces the result in both the main UI and a persistent foreground-service notification. It also tracks daily WiFi and mobile data usage with persistence across reboots.

## What it does

- Shows live download and upload speed on a Compose dashboard.
- Runs a foreground service to keep monitoring active in the background, started automatically on app launch.
- Updates an ongoing notification with the current speed and today's data usage breakdown (WiFi and mobile separately).
- Tracks daily network usage with automatic midnight resets and reboot-safe persistence via `SharedPreferences`.
- Resumes correct usage baseline after a device reboot via a `BootReceiver`.
- Pauses traffic sampling while the screen is off to save battery.
- Requests the notification permission on Android 13+ so the service can show its status bar notification.
- Separates the app into three Gradle modules for clearer responsibilities.

## Project structure

- `app` - UI layer and activity entry point.
- `core-network` - Network sampling, speed formatting, and daily usage tracking.
- `core-service` - Foreground service, boot receiver, notification handling, and shared runtime state.

This layout keeps the network sampling logic separate from the service lifecycle and UI code, which makes the project easier to extend later.

## Architecture overview

### `app`

The app module contains the launcher activity and Compose UI.

- `MainActivity` requests notification permission on Android 13+, starts the foreground service automatically on launch, and observes `ServiceStateManager.speedFlow`.
- `DashboardScreen` shows the current download and upload values. There is no manual service toggle — the service runs for the lifetime of the app process.

### `core-network`

This module owns the network speed model, sampling logic, and daily usage tracking.

- `NetworkSpeed` stores raw byte values, formatted speed strings, and daily usage totals for WiFi and mobile.
- `TrafficStatsProvider` reads total receive and transmit counters from `android.net.TrafficStats`, calculates bytes/s from actual elapsed time, and integrates `TrafficUsageManager` for daily totals. Takes a `Context` parameter for usage tracking.
- `TrafficUsageManager` persists daily WiFi and mobile byte counts in `SharedPreferences`, resets at midnight, and recalibrates baselines after a device reboot using counter comparison.
- `SpeedFormatter` provides `formatSpeed(bytes: Long)` for speed strings and `formatBytes(bytes: Long)` for human-readable byte totals.
- The provider uses adaptive polling: 1 s when traffic is active, 2 s when idle.

### `core-service`

This module owns the foreground service, boot receiver, and notification lifecycle.

- `SpeedMeterService` starts in the foreground, registers a `BroadcastReceiver` for screen on/off, pauses sampling when the screen is off, and updates both the notification and `ServiceStateManager` on each sample.
- The notification shows two lines: a speed line (`↓ download  ↑ upload`) and a usage line (`WiFi: X | Mobile: Y`).
- The notification icon is a dynamically rendered 32 dp bitmap showing the dominant speed value and unit.
- The service creates a low-importance notification channel on Android 8.0+.
- `BootReceiver` handles `BOOT_COMPLETED`, recalibrates `TrafficUsageManager` baselines, and optionally restarts the service if it was running before the reboot.

## Requirements

- Android Studio Hedgehog or newer is recommended.
- JDK 17 or the JDK bundled with a modern Android Studio installation.
- Android SDK 34 for compile and target.
- Minimum supported Android version: API 26.

## Permissions used

The app declares the following permissions in the manifests:

- `INTERNET` - declared in `app` manifest.
- `POST_NOTIFICATIONS` - required on Android 13+ for foreground-service notifications.
- `FOREGROUND_SERVICE` - required to run the service.
- `FOREGROUND_SERVICE_SPECIAL_USE` - required for the special-use foreground service type on Android 14+.
- `RECEIVE_BOOT_COMPLETED` - declared in `core-service` manifest; allows `BootReceiver` to recalibrate usage after reboot.
- `ACCESS_NETWORK_STATE` - declared in `core-service` manifest; used by `TrafficUsageManager` to distinguish WiFi vs. mobile traffic when saving usage.

## How the app works

1. `MainActivity` launches, requests notification permission when needed, and immediately starts the foreground service.
2. The Compose dashboard renders the latest values from `ServiceStateManager.speedFlow`.
3. The service creates its notification channel and begins collecting network samples at 1–2 s intervals.
4. Every interval, `TrafficStatsProvider` compares current and previous byte totals to calculate bytes/s, and updates daily WiFi and mobile usage totals.
5. The service updates its notification (speed + daily usage) and `ServiceStateManager` with the latest values.
6. While the screen is off, sampling is paused to conserve battery; it resumes when the screen turns on.
7. On device reboot, `BootReceiver` recalibrates the usage baselines so daily totals remain accurate.

## Build and run

### From Android Studio

1. Open the project root in Android Studio.
2. Let Gradle sync finish.
3. Run the `app` configuration on a physical device or emulator.

### From the command line

```bash
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

To install the debug build on a connected device:

```bash
./gradlew installDebug
```

## Usage

1. Launch MeterX.
2. Accept the notification permission prompt if it appears.
3. Monitoring starts automatically — no manual toggle needed.
4. Watch the download and upload values update in the dashboard.
5. Pull down the notification shade to see the speed and today's WiFi / mobile usage totals.

## Notes and limitations

- The app measures device-wide network totals, not per-app usage.
- Speed values depend on `TrafficStats` behavior, so results may vary by device and Android version.
- The service updates at an adaptive interval (1 s when traffic is active, 2 s when idle).
- The UI is intentionally near-real-time rather than instant.
- Daily usage is tracked from midnight to midnight and persists across reboots via `SharedPreferences`.
- WiFi and mobile usage attribution relies on `ConnectivityManager` knowing the active transport at save time; brief transitions may result in minor inaccuracies.
- The current implementation uses shared `StateFlow` objects on the service to expose the latest values to the UI.

## Troubleshooting

### The notification does not appear

- Make sure notification permission is granted on Android 13+.
- Confirm that the foreground service started (check Logcat for errors).
- Check whether the device or emulator is blocking background notifications.

### Speed stays at 0 KB/s

- Verify that the device is actually sending or receiving network traffic.
- Some emulators and idle devices may not produce useful `TrafficStats` changes.
- Try testing on a physical device with active Wi-Fi or mobile data traffic.

### Daily usage shows 0 B after a reboot

- The `BootReceiver` should recalibrate baselines automatically on `BOOT_COMPLETED`.
- If the device boots with battery optimization that delays broadcasts, the first sample may be off by a small margin.

### The service stops after leaving the app

- Foreground service behavior can still vary by OEM battery optimizations.
- Disable battery optimization for the app if the device aggressively restricts background work.

## Future improvements

- Add user preferences for units, refresh rate, and daily reset time.
- Add a manual service start/stop toggle to the dashboard.
- Add a historical view for recent speed changes (hourly or daily chart).
- Move `TrafficStatsProvider` construction behind a DI boundary so it can be swapped for a mock in tests.
- Add unit tests for speed conversion, byte formatting, usage persistence, and reboot calibration.
- Consider per-app usage tracking once `NetworkStatsManager` API requirements are met.

## Development stack

- Kotlin
- Jetpack Compose
- Kotlin Coroutines and Flow
- Android foreground services
- `android.net.TrafficStats`

## License

No license file is included in the repository yet. Add one if you plan to publish or share the project.