# AuraHealth — On-Device Contactless rPPG Heart Rate Monitor

**Edge-Native Remote Photoplethysmography (rPPG) on Android**  
*(Reference Target: Xiaomi Poco F5 / Qualcomm Snapdragon 7+ Gen 2)*

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Android SDK](https://img.shields.io/badge/Compile%20SDK-36-blue.svg)](https://developer.android.com)
[![CameraX](https://img.shields.io/badge/CameraX-1.4.1-green.svg)](https://developer.android.com/training/camerax)
[![ML Kit](https://img.shields.io/badge/ML%20Kit-Face%20Detection-orange.svg)](https://developers.google.com/ml-kit/vision/face-detection)
[![Offline](https://img.shields.io/badge/Privacy-100%25%20Offline-brightgreen.svg)](https://github.com)

---

## 🎯 Overview

**AuraHealth** turns an off-the-shelf Android smartphone into a contact-free physiological vitals monitor. By leveraging the device's front-facing camera, AuraHealth measures micro-variations in facial skin reflectance caused by blood volume changes during cardiac cycles (Remote Photoplethysmography — rPPG).

- **100% Edge-Native & Offline**: Runs entirely on the device. No cloud services, no backend servers, zero video data stored or transmitted.
- **Hardware Reference**: Tested and benchmarked on the **Poco F5** (Snapdragon 7+ Gen 2) at continuous 30.0 FPS.
- **Scientific Signal Processing**: Built with a native Kotlin implementation of the **Plane-Orthogonal-to-Skin (POS)** algorithm (*Wang et al., IEEE TBME 2017*) enhanced with multi-ROI spatial averaging and zero-phase Butterworth filtering.

---

## 🚀 Key Features

- **Real-Time Face & ROI Tracking**: Uses Google ML Kit Face Detection to isolate forehead and cheek regions for optimal pulse signal acquisition.
- **Zero-Copy Lossless YUV Extraction**: Direct pixel extraction from camera YUV_420_888 byte buffers without lossy compression or garbage collection overhead.
- **Robust POS Algorithm**: Projects temporal RGB color variations onto an orthogonal plane to separate blood volume pulse signals from motion and illumination artifacts.
- **Sub-Harmonic & Harmonic Resolution**: Advanced spectral peak analysis with parabolic interpolation to accurately extract fundamental heart rate frequencies.
- **Modern Jetpack Compose UI**: Sleek dark-mode aesthetic with real-time waveform visualization, BPM confidence metrics, and face framing guides.

---

## 🏗️ Signal Processing Pipeline

```
Front Camera Stream (YUV_420_888 @ 30 FPS)
  │
  ├──> Hardware Monotonic Sensor Timestamps (Zero Clock Drift)
  │
  ├──> Direct YUV -> RGB Skin Extraction (ITU-R BT.601)
  │
  ├──> Multi-ROI Spatial Skin Aggregation (Forehead + Cheeks)
  │
  ├──> Plane-Orthogonal-to-Skin (POS) Color Space Projection
  │
  ├──> Tikhonov Regularized Trend Removal
  │
  ├──> Zero-Phase Butterworth Bandpass Filter (0.75 - 2.50 Hz / 45 - 150 BPM)
  │
  ├──> Fast Fourier Transform (FFT) & Harmonic Peak Resolution
  │
  └──> Exponential Moving Average (EMA) & Outlier Gating -> Final BPM
```

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 2.0.21
- **UI Framework**: Jetpack Compose with Material 3 & Jetpack Navigation
- **Camera API**: CameraX (Preview + ImageAnalysis @ 640x480)
- **Computer Vision**: ML Kit Face Detection
- **Architecture**: Modern MVVM (Model-View-ViewModel) with Kotlin Coroutines & StateFlow
- **Target SDK**: Android SDK 36 (Min SDK: 26 / Android 8.0+)

---

## 🧪 Unit Test Suite

The project includes a comprehensive unit test suite covering POS signal projection, Butterworth filtering, harmonic resolution, and signal buffering:

```bash
# Run unit tests
./gradlew testDebugUnitTest
```

---

## 📦 Building and Running

### Prerequisites
- Android Studio Ladybug / Meerkat or later
- Android SDK 36 (Java 17)
- Device running Android 8.0+ (Reference device: Poco F5)

### Build with Gradle
```bash
# Assemble Debug APK
./gradlew assembleDebug
```

### APK Location & Installation
The compiled debug APK is generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

Install via ADB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.rppg.vitals/.MainActivity
```

---

## 🔒 Privacy & Security

AuraHealth processes all camera frames directly in volatile memory. No video feeds, images, or biometric telemetry are ever written to disk or transmitted over the network.

---

## 📄 Disclaimer

AuraHealth is intended for research, educational, and personal fitness monitoring purposes only. It is not a medical device and is not intended for clinical diagnosis, treatment, or prevention of any disease.
