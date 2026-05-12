# MeterX Project Overview

## Summary

MeterX is an Android internet speed meter that monitors device-wide network traffic in near real time and presents the results in a Compose-based dashboard plus a persistent foreground-service notification. The project is intentionally split into small modules so the UI, network sampling, and service lifecycle each have a clear responsibility.

## Core Goal

The app’s main goal is to show live upload and download speed with minimal user interaction. Once the foreground service is running, MeterX continuously samples system traffic counters, converts the raw byte deltas into readable speed values, and exposes that data to both the UI and the notification layer.

## Architecture

MeterX follows a simple modular architecture with a one-way dependency flow:

`app` -> `core-service` -> `core-network`

### Module responsibilities

#### `app`

The app module is the presentation and entry layer.

- Hosts the launcher activity.
- Requests notification permission on Android 13+.
- Observes service state and speed state.
- Renders the dashboard UI with Compose.
- Starts or stops the foreground service from the UI switch.

#### `core-network`

The core-network module owns the network sampling logic.

- Reads total receive and transmit byte counters from `android.net.TrafficStats`.
- Converts raw byte differences into download and upload speeds.
- Formats the speeds for display.
- Emits the latest values as a `Flow`.

#### `core-service`

The core-service module owns background execution.

- Runs the foreground service.
- Keeps the app alive while monitoring is active.
- Creates and updates the persistent notification.
- Shares the latest speed state through `StateFlow`.

## Features

### Real-time speed monitoring

MeterX samples the device’s total network counters every second. Each sample is compared with the previous one to estimate current download and upload speed.

### Foreground service support

The service runs in the foreground so Android treats the monitoring session as an active task. This is what keeps the speed meter visible and stable while the app is in the background.

### Compose dashboard

The UI shows the current download and upload values in a clean dashboard. The user can see whether the service is running and toggle it directly from the screen.

### Ongoing notification

MeterX updates an ongoing notification with the latest speed values. The notification also uses a generated icon so the status bar representation stays compact and readable.

### Notification permission handling

On Android 13 and newer, the app requests `POST_NOTIFICATIONS` so the foreground service can display its notification correctly.

## Overall Behavior

The app follows this runtime sequence:

1. The user opens MeterX.
2. `MainActivity` launches and requests notification permission if needed.
3. The dashboard renders the current speed state.
4. The user enables the background service switch.
5. Android starts `SpeedMeterService` as a foreground service.
6. The service starts collecting speed updates from `TrafficStatsProvider`.
7. Every second, the provider compares current counters with the previous counters.
8. The service publishes the new values to shared flows.
9. The dashboard and notification update with the latest download and upload speeds.
10. When the service is stopped, the running state resets and the UI reflects the change.

## UI Design

The current UI is intentionally simple and functional.

- A title identifies the app as Internet Meter.
- Two speed cards show download and upload values separately.
- A background service switch starts or stops live monitoring.
- The theme uses a dark Material 3 color scheme for a modern look.

### UI behavior in real time

The dashboard is bound to live state, so it refreshes as the shared flow values change. This creates a near-real-time view of network activity without requiring manual refresh.

## Data Flow

The data path is straightforward:

`TrafficStats` -> `TrafficStatsProvider` -> `NetworkSpeed` -> `SpeedMeterService` -> UI and notification

### What happens in the flow

- Raw byte counters are read from the operating system.
- Byte deltas are converted into per-second speeds.
- Raw values and formatted values are bundled in `NetworkSpeed`.
- The service collects the flow and mirrors the latest value into `StateFlow`.
- The Compose UI observes those flows and re-renders the dashboard.

## Project Structure

- `app` - Android entry point, Compose screen, and service control wiring.
- `core-network` - TrafficStats sampling and formatting helpers.
- `core-service` - Foreground service, notification channel, and shared runtime state.

This structure keeps the codebase small but still separates responsibilities in a way that can scale to more features later.

## Current Limitations

- MeterX measures device-wide traffic, not per-app traffic.
- Speed values depend on `TrafficStats` and may vary by device or Android version.
- The service refreshes once per second, so the display is near-real-time rather than instantaneous.
- The current service and UI both rely on shared static state, which is simple but not ideal for long-term testability.

## Future Directions

- Add settings for refresh rate and speed units.
- Separate sampling, formatting, and presentation more cleanly.
- Add history tracking for recent network usage.
- Improve testability by introducing an abstraction for the traffic data source.
- Add richer notification layouts or widget support.

## Technology Stack

- Kotlin
- Jetpack Compose
- Kotlin Coroutines and Flow
- Android Foreground Service
- `android.net.TrafficStats`

## Build Context

The project is configured for:

- `compileSdk` 34
- `targetSdk` 34
- `minSdk` 26
- AndroidX and Material 3

## Notes for Contributors

- Keep UI concerns in `app`.
- Keep byte sampling and formatting in `core-network`.
- Keep foreground service behavior and notification code in `core-service`.
- Prefer small, testable changes that preserve the current module boundaries.