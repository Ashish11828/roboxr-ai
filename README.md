# RoboXR AI

An Android prototype for CNC machine-tending teach-in, jam-recovery planning, and digital-twin inspection, with XREAL external-display and Android XR integration.

Built with Kotlin and Jetpack Compose, RoboXR AI turns operator instructions and pick/place coordinates into waypoint plans. Plans can be generated locally or through Firebase AI Logic, inspected against deterministic workspace constraints, and exported as robot-program text.

## Features

- **Teach-in workspace:** choose a CNC task preset or enter an instruction and spatial anchors.
- **Two planning modes:** deterministic Offline Kinematics and a cloud Gemini planner through Firebase AI Logic.
- **Digital-twin inspection:** visualize waypoints and review clearance, reach, fixture-collision, and velocity checks.
- **Jam-recovery scenarios:** explore chuck misalignment, chip obstruction, and spindle-stall examples with operator guidance.
- **Program generation:** produce URScript, KUKA KRL, or ISO G-Code after the application's validation and approval flow.
- **Local audit log:** store operator sign-offs, generated program text, and SHA-256 digests using Room.
- **XR diagnostics:** inspect USB device detection, external displays, available orientation sensors, and Android XR capabilities.
- **Guided demo:** walk through planning, rejected paths, recovery, and audit records.

## Application workflow

1. Select a teach-in preset or jam-recovery scenario.
2. Choose Offline Kinematics or the Gemini AI planner.
3. Generate a plan from the instruction and anchor coordinates.
4. Inspect the path and validation results in the digital twin.
5. Approve a passing plan, generate program text, and review the audit record.

The implemented workspace checks use these fixed demonstration limits:

| Check | Configured limit |
| --- | --- |
| Minimum Z clearance | 35 mm |
| Maximum radial reach | 820 mm |
| Maximum waypoint speed | 250 mm/s |
| Fixture keep-out region | X: 310–395 mm; Y: 65–145 mm; Z: at or below 160 mm |

These checks model a simplified demonstration cell. They do not establish safety certification, validate a real machine's full geometry, or replace controller validation and a cell-specific risk assessment. Treat exported programs as prototype output requiring independent review before use on equipment.

## Technology

| Area | Implementation |
| --- | --- |
| Android UI | Kotlin, Jetpack Compose, Material 3 |
| State and background work | ViewModel, StateFlow, Kotlin coroutines |
| Cloud planning | Firebase AI Logic and structured JSON output |
| App attestation | Firebase App Check |
| Local storage | Room with KSP |
| Spatial UI | Jetpack XR Runtime, SceneCore, and XR Compose |
| External glasses display | Android USB and display APIs, Presentation |
| Tests | JUnit, Robolectric, Compose UI tests |

## Getting started

### Requirements

- Android Studio with support for the versions declared in `gradle/libs.versions.toml`.
- Android SDK matching the app's compile configuration: API 36, minor API level 1.
- An Android device or emulator meeting the declared minimum SDK of API 24. XR dependencies and features may impose additional device or runtime requirements.
- A Firebase project for cloud planning.

The source declares Android Gradle Plugin **9.1.1**, Kotlin **2.4.21**, and Gradle **9.3.1**. Check toolchain compatibility when importing this exported project; the Java 11 source compatibility setting does not specify the JDK required to run Gradle.

### Open the project

```bash
git clone https://github.com/Ashish11828/roboxr-ai.git
cd roboxr-ai
```

Open this directory in Android Studio and sync the project.

**Export limitation:** the repository contains `gradle/wrapper/gradle-wrapper.properties`, but does not include `gradlew`, `gradlew.bat`, or `gradle-wrapper.jar`. Restore a compatible Gradle wrapper before using wrapper commands. With a compatible Gradle installation available, generate it with:

```bash
gradle wrapper --gradle-version 9.3.1
```

### Configure local debug signing

The exported `app/build.gradle.kts` points the debug build at a root-level `debug.keystore`, which is not included. For a local Android Studio checkout, remove this assignment from the `debug` build type to use Android's default debug signing:

```kotlin
signingConfig = signingConfigs.getByName("debugConfig")
```

Alternatively, provide the expected local debug keystore. Keep signing keys out of Git. Preserve the export's signing setup separately if you continue building through AI Studio.

### Configure Firebase AI Logic

1. Register an Android app in your own Firebase project using application ID `com.aistudio.roboxr.alclan`, or update the application ID and Firebase registration together.
2. Replace `app/google-services.json` with your project's Android configuration.
3. Configure Firebase AI Logic and App Check for that project.
4. For debug builds, register the App Check debug token emitted by the SDK. Do not commit debug tokens.
5. Review `AppCheckInit.kt` and the `recaptcha_site_key` resource before configuring release attestation.
6. Review the model identifier in `RoboXRPlanner.kt`. The export currently requests `gemini-3.8-flash`; confirm availability in your Firebase project and update it if needed.

The current planner uses the Firebase client SDK and **does not require a client-side `GEMINI_API_KEY`**. The `.env.example` file documents this. Offline Kinematics generates plans locally without a cloud inference request; the application still contains Firebase initialization.

### Run and test

After restoring the wrapper and configuring signing, select the `app` run configuration in Android Studio and launch it on your device or emulator.

```bash
# macOS / Linux
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest

# Windows PowerShell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Instrumented tests require a connected Android device or emulator:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

These are setup and verification commands, not a claim that this exported project has passed a build or device test.

## Try the demo

Start with **Offline Kinematics** and the **Haas VF-2 Chuck Load** preset. Generate a plan, inspect its digital-twin results, and follow the approval flow.

Then select **Clamp Keep-Out Test**, **Z-Floor & Speed Stress**, or **820mm Reach Boundary** to inspect deliberate constraint violations. Explore the jam-recovery scenarios and audit log afterward.

## XREAL and Android XR

The hardware bridge detects selected XREAL USB identifiers and Android presentation displays. A compatible USB-C video-output device and glasses connection are needed for an external optical display. Android XR spatial UI depends on the device's available runtime capabilities.

Orientation telemetry uses Android's available rotation-vector sensor; this alone does not establish glasses-native tracking or six-degree-of-freedom world anchoring. Device detection and capability checks are implemented in source, but compatibility with every listed glasses model has not been verified here.

## Source layout

```text
app/src/main/java/com/example/
├── MainActivity.kt              # Application entry and Compose shell
├── ui/theme/                    # App theme and typography
└── roboxr/
    ├── ai/                      # Firebase App Check initialization
    ├── data/                    # Room audit database
    ├── model/                   # Plans, waypoints, presets, and scenarios
    ├── planner/                 # Cloud and offline planning
    ├── safety/                  # Validation and program generation
    ├── ui/                      # ViewModel, screens, and components
    └── xr/                      # Hardware bridge and XR presentation
```

Unit tests are under `app/src/test`; instrumented tests are under `app/src/androidTest`.

## Project status

This repository contains an Android project exported from AI Studio. Build configuration, cloud model availability, release signing, and physical-device behavior need validation in your environment. No open-source license file is currently included.
