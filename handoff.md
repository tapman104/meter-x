# MeterX Handoff

## Project Snapshot

- Project: MeterX (Android internet speed meter)
- Stack: Kotlin, Jetpack Compose, Coroutines/Flow, Foreground Service
- Modules: `app`, `core-network`, `core-service`
- Current target: API 34
- Minimum SDK: API 26

## Current Status

The project is in a working prototype phase with modular structure in place and baseline documentation added.

Recently added repository docs/config:

- `README.md` (detailed project documentation)
- `project.md` (architecture, behavior, UI, data flow)
- `.gitignore` (Android/Gradle-safe ignore rules)
- `.coderabbit.yaml` (CodeRabbit review configuration)

## Architecture and Responsibilities

### app

- Hosts `MainActivity` and Compose UI (`DashboardScreen`)
- Requests notification permission on Android 13+
- Starts/stops the foreground service
- Observes service state via shared `StateFlow`

### core-network

- Provides `TrafficStatsProvider`
- Samples `TrafficStats` once per second
- Produces `NetworkSpeed` model with raw and formatted values

### core-service

- Runs `SpeedMeterService` as a foreground service
- Creates and updates persistent notification
- Publishes speed/running state used by UI

## Runtime Flow

1. User opens app.
2. UI requests notification permission where required.
3. User toggles service on.
4. Foreground service starts and begins speed sampling.
5. Service pushes updates to shared flow and notification.
6. UI and status-bar notification update near real time.

## What Is Working

- Compose dashboard with download/upload speed cards
- Service toggle from UI
- Foreground service lifecycle with notification channel
- Real-time speed calculation using total byte deltas
- Shared flow state from service to UI

## Known Risks and Gaps

- Data source uses device-wide totals (not per-app breakdown)
- `TrafficStats` behavior may vary by device/OEM
- Service currently owns concrete provider construction (limited DI/testability)
- UI and service are coupled through shared static state
- Limited automated test coverage

## Build and Run

PowerShell (Windows):

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

Android Studio:

1. Open project root.
2. Sync Gradle.
3. Run `app` on device/emulator.

## Recommended Next Actions

1. Add a provider abstraction interface for network stats and inject dependencies into service.
2. Split speed sampling and formatting into separate components.
3. Add unit tests for speed calculations and format conversions.
4. Add explicit unsupported-state handling (instead of silent 0 B/s fallback).
5. Consider introducing a repository/use-case layer to remove static shared service state.

## Validation Checklist For Next Owner

- Build succeeds with `assembleDebug`.
- App launches and requests notification permission (Android 13+).
- Toggling service updates both UI and notification.
- Speed values change when network activity is generated.
- Service stops cleanly and UI reflects stopped state.

## Key Files

- `README.md`
- `project.md`
- `app/src/main/java/com/meterx/app/MainActivity.kt`
- `app/src/main/java/com/meterx/app/ui/DashboardScreen.kt`
- `core-network/src/main/java/com/meterx/core/network/TrafficStatsProvider.kt`
- `core-service/src/main/java/com/meterx/core/service/SpeedMeterService.kt`

## Notes

This handoff focuses on current implementation reality and immediate continuation steps. The highest leverage technical improvement is decoupling service/runtime orchestration from concrete sampling implementation so testing and future feature growth become safer.