# DeskGuard: Wi-Fi Radar & Intruder Defense Suite

[![Android](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose_Material3-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM_%2B_LifecycleService-00C853)](#architecture)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**DeskGuard** turns any standard Android device into a covert **Wi-Fi RF disturbance sensor and automated physical anti-tamper perimeter**. 

By passively tracking micro-fluctuations in ambient Wi-Fi beacon signals (**Fresnel zone multipath disturbance**) combined with an **accelerometer stationary gate**, DeskGuard detects when an intruder enters your desk space—even when your phone is locked with its screen off. Upon alarm verification, it autonomously captures a **front-camera photo of the intruder** and stores evidence in secure sandboxed storage.

---

## 🎬 Live Demo & Visuals

### 📹 App Walkthrough Video
<p align="center">
  <video src="docs/deskguard.mp4" controls width="100%" style="max-width: 680px;" poster="docs/screenshots/deskguard_hud.png">
    <a href="docs/deskguard.mp4">▶️ Watch / Download Demo Video (docs/deskguard.mp4)</a>
  </video>
</p>
<p align="center">
  <sub>▶️ If video playback is not embedded in your markdown viewer, you can view it directly at <a href="docs/deskguard.mp4"><b><code>docs/deskguard.mp4</code></b></a>.</sub>
</p>

---

## ⚡ Why DeskGuard?

Leaving your smartphone unattended on an office desk, conference room table, or hotel nightstand exposes your device and workspace to unauthorized access:
- **Optical tripwires & motion camera apps** drain battery rapidly, overheat the phone, and alert intruders by keeping camera sensors and screens visibly active.
- **Physical lock locks** don't notify you or take evidence if someone snoops around your workspace.

**DeskGuard solves this passively and covertly:**
1. **Zero Extra Radio Emission**: Purely passive RSSI listener—no packets transmitted, zero added electromagnetic radiation.
2. **Minimal Battery Drain**: Uses standard OS Wi-Fi beacon sweeps and hardware sensor listeners.
3. **Headless Intruder Identification**: Fires a stealthy front-camera capture through Android `Camera2` without activating any preview UI or turning on the screen.

---

## ✨ Key Features

### 📡 Passive Wi-Fi Multipath Radar
- Samples Wi-Fi Received Signal Strength Indicator (RSSI) over sliding observation windows.
- Calculates dynamic variance ($\sigma$ standard deviation) and peak rate-of-change ($\Delta\text{dBm}$).
- Detects the presence of human bodies moving between the device and surrounding Wi-Fi access points.

### 📷 Covert Front-Camera Photo Capture
- Employs a headless `Camera2` pipeline with an off-screen `ImageReader` surface (`ImageFormat.JPEG`).
- Silently triggers when an RF motion spike or physical tamper is confirmed.
- Saves high-resolution photos into private app sandboxed storage (`/data/data/com.prasoft.deskguard/files/intruder_captures/`).
- Includes an in-app evidence viewer dialog with direct system file-sharing via `FileProvider`.

### 🧭 Stationary Accelerometer Gate
- Integrated 3-axis accelerometer listener with a calibrated movement deadband filter.
- **Stationary Gate**: Ensures RF radar only arms when the phone is set down flat on a stable surface.
- **Tamper Alert**: Instantly triggers a high-priority alarm if the phone is lifted, tilted, or grabbed.

### 🔌 Carrier Loss & Router Tamper Detection
- Continually monitors connection to the active BSSID/SSID.
- Flags deliberate access point disconnections, jamming attempts, or office power outages as security events.

### 🔋 Jetpack Lifecycle Foreground Sentinel
- Backed by an Android `LifecycleService` (`WifiRadarService`) paired with `ProcessLifecycleOwner`.
- Maintains an ongoing high-priority notification with quick **DISARM** action.
- Holds a targeted `PARTIAL_WAKE_LOCK` to keep the radar vigilant through Android Doze mode.

### 🎨 Tactical OLED Cyberpunk HUD
- Pure Obsidian/OLED black background optimized for battery conservation and stealth.
- Custom Canvas radar sweep with rotating phosphor beam and dynamic proximity blips.
- Live 60-frame historical RSSI oscilloscope with real-time variance gauges and threshold sliders.

---

## 🏗️ System Architecture

DeskGuard adheres to modern Android Jetpack architecture guidelines with strict unidirectional data flow (UDF):

```
┌────────────────────────────────────────────────────────┐
│               Jetpack Compose UI Layer                 │
│   (RadarVisualizer, HUD Oscilloscope, IntruderDialog)  │
└──────────────────────────▲─────────────────────────────┘
                           │ StateFlow<WifiRadarUiState>
┌──────────────────────────┴─────────────────────────────┐
│                   WifiRadarViewModel                   │
│         - ProcessLifecycleOwner Observer               │
│         - Service Binding & State Management           │
└──────────────────────────▲─────────────────────────────┘
                           │
┌──────────────────────────┴─────────────────────────────┐
│                    WifiRadarService                    │
│             (Android LifecycleService)                 │
│         - Ongoing Foreground Notification              │
│         - Partial WakeLock & Alarm Sound Engine        │
└──────────────▲───────────────────────────▲─────────────┘
               │                           │
┌──────────────┴───────────┐ ┌─────────────┴────────────┐
│    WifiRadarEngine       │ │  IntruderPhotoCapturer   │
│ - WifiManager RSSI Poller│ │ - Headless Camera2 API   │
│ - SensorManager G-Sensor │ │ - ImageReader Surface    │
│ - Disturbance Evaluator  │ │ - Sandboxed File Output  │
└──────────────────────────┘ └──────────────────────────┘
```

### Module Breakdown:
- **`radar/WifiRadarEngine.kt`**: Core physics engine that ingests raw RSSI and accelerometer samples, computes rolling statistics, and evaluates alert thresholds.
- **`radar/IntruderPhotoCapturer.kt`**: Headless front-camera snapshot manager leveraging `CameraManager` and `CameraDevice.StateCallback`.
- **`radar/WifiRadarService.kt`**: Background daemon ensuring uninterrupted operation across lock states.
- **`radar/WifiRadarViewModel.kt`**: Business logic coordinating engine states, settings adjustments, and UI event streams.
- **`ui/components/RadarVisualizer.kt`**: Hardware-accelerated Jetpack Compose Canvas radar sweep and phosphor decay visuals.

---

## 🔐 Permissions & Privacy

DeskGuard operates with full respect for user privacy and security:

| Permission | Purpose |
| :--- | :--- |
| `CAMERA` | Captures front-facing intruder snapshot during verified alarm events. |
| `ACCESS_FINE_LOCATION` | Required by Android OS to query Wi-Fi RSSI / connection details. |
| `NEARBY_WIFI_DEVICES` | Discovers local Wi-Fi RF beacon environments on Android 13+. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps monitoring service alive when screen is locked. |
| `POST_NOTIFICATIONS` | Displays continuous status and critical security disturbance alerts. |

> **Privacy Guarantee**: All sensor calculations and intruder images are processed **100% on-device**. No images or telemetry are ever transmitted to external servers.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer
- JDK 17 or JDK 21
- Android device running Android 8.0 (API 26) through Android 16 (API 36)

### Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/vennamprasad/DeskGuard.git
   cd DeskGuard
   ```

2. Open the project in Android Studio, or build the debug APK via Gradle:
   ```bash
   ./gradlew assembleDebug
   ```

3. Install directly to a connected test device:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. Launch DeskGuard:
   ```bash
   adb shell am start -n com.prasoft.deskguard/.MainActivity
   ```

---

## 📖 Operational Guide

1. **Launch App & Grant Permissions**: Grant Camera, Location, and Notification permissions when prompted.
2. **Place on Desk**: Lay your smartphone flat and motionless on your desk or workspace.
3. **Calibrate Baseline**: Tap **"CALIBRATE BASELINE"** to measure the quiet ambient RF environment.
4. **Arm Sentinel**: Tap **"ARM SENTINEL"**. Lock your phone if desired.
5. **Step Away**: Any unauthorized human presence or physical pick-up will trigger the audio alarm, record the timestamp in the log, and capture a photo of the intruder!

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
