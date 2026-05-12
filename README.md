# MeterX

MeterX is an Android internet speed meter built with Kotlin, Jetpack Compose, and a small modular architecture. The app samples device-wide network traffic with `TrafficStats`, turns those byte deltas into live download and upload speeds, and surfaces the result in both the main UI and a persistent foreground-service notification.

## What it does

- Shows live download and upload speed on a Compose dashboard.
- Runs a foreground service to keep monitoring active in the background.
- Updates an ongoing notification with the current speed.
- Requests the notification permission on Android 13+ so the service can show its status bar notification.
- Separates the app into three Gradle modules for clearer responsibilities.

## Project structure

- `app` - UI layer and activity entry point.
- `core-network` - Network sampling and speed formatting.
- `core-service` - Foreground service, notification handling, and shared runtime state.

This layout keeps the network sampling logic separate from the service lifecycle and UI code, which makes the project easier to extend later.

## Architecture overview

### `app`

The app module contains the launcher activity and Compose UI.

- `MainActivity` requests notification permission on Android 13+.
- The activity observes `SpeedMeterService.speedFlow` and `SpeedMeterService.runningFlow`.
- `DashboardScreen` shows the current download and upload values and provides a switch to start or stop the service.

### `core-network`

This module owns the network speed model and sampling logic.

- `NetworkSpeed` stores raw byte values plus formatted text.
- `TrafficStatsProvider` reads total receive and transmit counters from `android.net.TrafficStats`.
- The provider samples once per second and emits a `Flow<NetworkSpeed>`.

### `core-service`

This module owns the foreground service and notification lifecycle.

- `SpeedMeterService` starts in the foreground, collects the speed flow, and updates a notification every second.
- The service creates a notification channel on Android 8.0+.
- A small bitmap icon is generated dynamically to display the dominant speed in the notification icon area.

## Requirements

- Android Studio Hedgehog or newer is recommended.
- JDK 17 or the JDK bundled with a modern Android Studio installation.
- Android SDK 34 for compile and target.
- Minimum supported Android version: API 26.

## Permissions used

The app declares the following permissions in the manifest:

- `INTERNET` - included by the project manifest.
- `POST_NOTIFICATIONS` - required on Android 13+ for foreground-service notifications.
- `FOREGROUND_SERVICE` - required to run the service.
- `FOREGROUND_SERVICE_SPECIAL_USE` - used by the service on Android 14+.

## How the app works

1. `MainActivity` launches and requests notification permission when needed.
2. The Compose dashboard renders the latest values from `SpeedMeterService`.
3. When the switch is enabled, the activity starts the foreground service.
4. The service creates its notification channel and begins collecting network samples.
5. Every second, the app compares current and previous byte totals to calculate speed.
6. The service updates its notification and shared state flows with the latest values.

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
3. Use the Background Service switch to start monitoring.
4. Watch the download and upload values update in the dashboard and notification.

## Notes and limitations

- The app measures device-wide network totals, not per-app usage.
- Speed values depend on `TrafficStats` behavior, so results may vary by device and Android version.
- The service updates once per second, so the UI is intentionally near-real-time rather than instant.
- The current implementation uses shared `StateFlow` objects on the service to expose the latest values to the UI.

## Troubleshooting

### The notification does not appear

- Make sure notification permission is granted on Android 13+.
- Confirm that the app has started the foreground service.
- Check whether the device or emulator is blocking background notifications.

### Speed stays at 0 B/s

- Verify that the device is actually sending or receiving network traffic.
- Some emulators and idle devices may not produce useful `TrafficStats` changes.
- Try testing on a physical device with active Wi-Fi or mobile data traffic.

### The service stops after leaving the app

- Foreground service behavior can still vary by OEM battery optimizations.
- Disable battery optimization for the app if the device aggressively restricts background work.

## Future improvements

- Add user preferences for units and refresh behavior.
- Separate the sampling and formatting responsibilities more cleanly.
- Add a historical view for recent speed changes.
- Add tests for speed conversion, formatting, and unsupported-device handling.

## Development stack

- Kotlin
- Jetpack Compose
- Kotlin Coroutines and Flow
- Android foreground services
- `android.net.TrafficStats`

## License

No license file is included in the repository yet. Add one if you plan to publish or share the project.