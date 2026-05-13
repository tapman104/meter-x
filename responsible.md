# MeterX File Responsibility Index

This document indexes the primary files in the MeterX project and details their specific responsibilities within the application architecture.

## Project Root

*   **`build.gradle.kts`**
    Root Gradle build configuration. Defines project-wide plugins, build scripts, and common repositories used across all modules.
*   **`settings.gradle.kts`**
    Gradle settings file. Defines the project modules (`:app`, `:core-network`, `:core-service`) and configures dependency resolution management.

---

## `app` Module (Main Application)

This module handles the main user interface and serves as the entry point for the application. It depends on the core modules to function.

### UI & Logic
*   **`app/src/main/java/com/meterx/app/MainActivity.kt`**
    The main entry point of the Android application. Responsible for setting up the Jetpack Compose content, requesting necessary user permissions (e.g., Notification permissions for Android 13+), and starting the background service.
*   **`app/src/main/java/com/meterx/app/ui/DashboardScreen.kt`**
    Jetpack Compose UI component defining the main dashboard of the application. Responsible for rendering the current internet speed and providing interactive user controls.

### Resources
*   **`app/src/main/res/values/strings.xml`**
    Contains localized string resources used throughout the UI of the `app` module.
*   **`app/src/main/res/values/themes.xml`**
    Defines the application's base theme, colors, and overall visual styling.
*   **`app/src/main/res/xml/backup_rules.xml`** & **`app/src/main/res/xml/data_extraction_rules.xml`**
    Configuration files specifying rules for Android Auto Backup and detailing which data should be included or excluded during extraction.

### Configuration
*   **`app/src/main/AndroidManifest.xml`**
    The application-level manifest. Declares the `MainActivity`, application themes, metadata, and app-level permissions.
*   **`app/build.gradle.kts`**
    Gradle build configuration for the `app` module. Defines Android application settings, dependencies on other modules (`:core-network`, `:core-service`), and external libraries like Jetpack Compose and Hilt.

---

## `core-network` Module

This library module isolates all network-related logic, specifically the responsibilities of measuring and formatting internet speed.

### Logic
*   **`core-network/src/main/java/com/meterx/core/network/TrafficStatsProvider.kt`**
    Responsible for fetching network traffic statistics (cumulative download and upload bytes) using the Android `TrafficStats` API. It acts as the engine to calculate current network speeds over time.
*   **`core-network/src/main/java/com/meterx/core/network/SpeedFormatter.kt`**
    Utility class responsible for converting raw byte values into human-readable strings (e.g., converting bytes/sec to KB/s, MB/s, or GB/s) ensuring correct units and formatting spacing.

### Configuration
*   **`core-network/src/main/AndroidManifest.xml`**
    Manifest for the `core-network` library module. Declares necessary network-related permissions (if any) and library definitions.
*   **`core-network/build.gradle.kts`**
    Gradle build configuration for the `core-network` module.

---

## `core-service` Module

This library module manages the background processes, primarily the foreground service that keeps the speed meter continuously running.

### Logic
*   **`core-service/src/main/java/com/meterx/core/service/SpeedMeterService.kt`**
    The foreground service responsible for running continuously in the background. It utilizes the components from the `core-network` module to poll network speeds and updates a persistent notification to display the live speed to the user, even when the app is closed.

### Resources
*   **`core-service/src/main/res/drawable/ic_speed_placeholder.xml`**
    Vector drawable icon used for the foreground service's persistent notification, ensuring the notification has a proper visual indicator in the status bar.

### Configuration
*   **`core-service/src/main/AndroidManifest.xml`**
    Manifest for the `core-service` library module. Declares the `SpeedMeterService` component and necessary foreground service permissions (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`).
*   **`core-service/build.gradle.kts`**
    Gradle build configuration for the `core-service` module. Defines dependencies required to run Android services and manage notifications.
