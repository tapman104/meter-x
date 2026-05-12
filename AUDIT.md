# Architecture Audit Report

Date: 2026-05-12
Project: MeterX
Scope: Read-only architectural analysis of app, core-network, and core-service modules.

## Executive Summary

Overall architecture is a good start for a small app, but separation and testability are only partial.

- Module direction is mostly correct: app -> core-service -> core-network.
- Core-network currently depends on Android framework APIs directly (TrafficStats), which limits pure unit testing.
- Core-service hardcodes construction of TrafficStatsProvider, reducing substitutability and testability.
- App module includes both UI and runtime wiring, but also directly polls network stats, duplicating responsibility already used by service.
- No circular module dependencies were found.

Overall rating: Medium maturity (functional, but not yet feature-ready for significant expansion).

---

## 1. Module Separation

### Assessment

### app module
- Contains UI (Compose screen) and wiring to start/stop service.
- Also instantiates TrafficStatsProvider directly and collects speed flow in activity.
- Result: app is not only UI/wiring; it also consumes low-level network provider directly.

Rating: Medium

### core-network module
- Single primary purpose: network speed sampling and formatting.
- No UI/service code leaks.
- However, it is Android-coupled due to direct use of android.net.TrafficStats.

Rating: Medium

### core-service module
- Focused on foreground service + notification rendering.
- Depends on core-network for speed data model/provider.
- No obvious UI leakage.

Rating: Good

### Checklist answers
- Is each module single-purpose? Partially.
- Does core-network leak Android service/UI concerns? No service/UI concerns, but yes to Android framework coupling.
- Does core-service depend on anything it should not? No major module violation; main issue is concrete provider construction.
- Does app only contain UI and wiring? Not fully; app directly uses TrafficStatsProvider.

---

## 2. Dependency Flow

## Module Graph
- app -> core-network
- app -> core-service
- core-service -> core-network
- core-network -> androidx.core, kotlinx-coroutines-core
- core-service -> androidx.core, kotlinx-coroutines-android

### Circular dependencies
No circular module dependencies detected.

### Injection and hardcoding findings
- TrafficStatsProvider is not injected in SpeedMeterService.
- SpeedMeterService creates provider internally using direct construction.
- There is no provider interface abstraction currently used.

Checklist answers:
- Is TrafficStatsProvider injectable? No, currently hardcoded.
- Is SpeedMeterService hardcoding provider instead of interface? Yes.

---

## 3. Testability

### TrafficStatsProvider testability
Current state: difficult for local JVM unit tests.

Why:
- Direct calls to static Android TrafficStats API.
- Infinite flow loop with delay(1000).
- No clock abstraction, no data source abstraction.

Can it be unit tested without real device?
- Practical answer: Not well in current form.
- It can be instrumented on Android, but not cleanly unit tested in pure JVM.

### Network data source abstraction
- No interface exists for network byte counters.
- Provider owns both collection and formatting.

### SpeedMeterService testability
Current state: difficult without Android runtime.

Why:
- Inherits Service and directly uses framework notification APIs.
- Creates concrete provider internally.
- Bitmap rendering, notification building, and flow collection are tightly coupled in one class.

Checklist answers:
- Can SpeedMeterService be tested without real Android service? Mostly no (except limited isolated method tests via Robolectric/instrumentation).

Overall testability rating: Hard to test.

---

## 4. Extensibility Ratings

### 1) Per-app data usage breakdown
Rating: Hard

Reasons:
- Current provider only reads total device rx/tx bytes.
- No per-UID tracking pipeline or repository model.
- Requires new data model, filtering, and likely permission/OS-constraint handling.

### 2) Historical speed graph (last 24h)
Rating: Medium-Hard

Reasons:
- No persistence layer currently.
- Need sampling storage (Room/DataStore/file) and retention policy.
- Current architecture has no domain/repository boundary for historical queries.

### 3) Download/Upload shown separately in status bar (two icons)
Rating: Hard

Reasons:
- Android status bar supports one small icon per notification.
- Would require either two notifications, custom compact/expanded views, or alternative representation.
- Current icon pipeline renders one dominant speed only.

### 4) Unit preference (auto KB/MB vs always MB)
Rating: Easy-Medium

Reasons:
- Formatting logic exists in one place, so conversion rules can be centralized.
- But no settings abstraction yet; would need user preference storage and propagation.

### 5) Widget support
Rating: Medium-Hard

Reasons:
- Requires AppWidgetProvider/Glance integration and periodic updates.
- Existing flow/service logic can be reused partially, but update orchestration and lifecycle are new.

---

## 5. Current Bugs and Risks

### Coroutine scope structure in SpeedMeterService
Finding: Uses CoroutineScope(Dispatchers.Default + Job()).

Risk:
- Job means one child failure can cancel siblings under same scope.
- With single collector now impact is limited, but future parallel children increase fragility.

Recommendation: Prefer SupervisorJob for service-level scope.
Severity: Medium

### Bitmap lifecycle and notification updates
Finding:
- New bitmap is created on each update and wrapped in IconCompat.
- No explicit recycle call.

Risk:
- Frequent allocation churn and GC pressure over time.
- Not an immediate leak by itself, but long-running service may experience avoidable memory overhead.

Recommendation:
- Reuse one bitmap/canvas/paint buffer where possible, or throttle icon updates.
Severity: Medium

### TrafficStats unsupported devices
Finding:
- UNSUPPORTED values are converted to 0 and flow continues.

Risk:
- Silent fallback may mask unsupported state and mislead user with persistent 0 B/s.

Recommendation:
- Surface explicit unsupported state in model and UI/notification.
Severity: Medium

### Memory leaks
Findings:
- Service scope is canceled in onDestroy (good).
- No obvious static context leak.
- Companion isRunning flag is process-local; potential state mismatch after process death/restart, but not a leak.

Leak risk rating: Low-Medium (more churn/state consistency risk than classic leak).

### Additional architectural risk
- Both MainActivity and SpeedMeterService independently instantiate TrafficStatsProvider and collect flow.
- This duplicates polling work and can create inconsistent readings/timing.
Severity: Medium

---

## 6. Recommended Refactors (Priority Order)

### P1. Introduce network stats abstraction and inject provider
- Add interface for byte counters (example: NetworkStatsDataSource with totalRx/totalTx).
- Let TrafficStats-backed implementation live in core-network.
- Inject abstraction into TrafficStatsProvider and SpeedMeterService.

Why first:
- Unblocks testability and decouples from Android static API.

### P2. Split formatting and sampling responsibilities
- Keep sampling in one class, formatting in a separate formatter component.
- Return raw rates in domain model and format at presentation boundaries.

Why:
- Improves unit testing and future unit-preference feature.

### P3. Introduce service-facing use case/repository boundary
- Service should depend on a SpeedStream use case interface rather than concrete provider.
- App and service can share one source of truth.

Why:
- Removes duplicated polling and simplifies orchestration.

### P4. Strengthen coroutine supervision
- Replace Job with SupervisorJob in service scope.
- Add exception handling policy for flow collector.

Why:
- Better resilience for long-running foreground service.

### P5. Isolate notification/icon rendering
- Extract notification builder and bitmap renderer into dedicated classes.
- Add update throttling and optional icon buffer reuse.

Why:
- Reduces service class complexity and allocation overhead.

### P6. Add minimal automated tests before feature expansion
- Unit tests for formatting rules and speed-delta logic.
- Tests for unsupported-state behavior.
- Service behavior tests via Robolectric or instrumentation where needed.

Why:
- Reduces regression risk as planned features are added.

---

## Evidence Reviewed

- settings.gradle.kts
- app/build.gradle.kts
- core-network/build.gradle.kts
- core-service/build.gradle.kts
- app/src/main/java/com/meterx/app/MainActivity.kt
- app/src/main/java/com/meterx/app/ui/DashboardScreen.kt
- app/src/main/AndroidManifest.xml
- core-network/src/main/java/com/meterx/core/network/TrafficStatsProvider.kt
- core-service/src/main/java/com/meterx/core/service/SpeedMeterService.kt
- core-service/src/main/AndroidManifest.xml
- core-network/src/main/AndroidManifest.xml
- plan.md
