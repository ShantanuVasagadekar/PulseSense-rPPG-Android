# PulseSense — On-Device rPPG Heart Rate Vitals Monitor

**Edge-Native Remote Photoplethysmography (rPPG) on Android (Poco F5 Reference Target)**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Android SDK](https://img.shields.io/badge/Compile%20SDK-36-blue.svg)](https://developer.android.com)
[![CameraX](https://img.shields.io/badge/CameraX-1.4.1-green.svg)](https://developer.android.com/training/camerax)
[![ML Kit](https://img.shields.io/badge/ML%20Kit-Face%20Detection-orange.svg)](https://developers.google.com/ml-kit/vision/face-detection)
[![Offline](https://img.shields.io/badge/Privacy-100%25%20Offline-brightgreen.svg)](https://github.com)

---

## 🎯 Project Overview

PulseSense turns an off-the-shelf Android smartphone into a contact-free physiological vitals monitor. Using only the device's front-facing camera, PulseSense measures micro-variations in facial skin reflectance caused by pulsating blood volume during cardiac cycles (remote photoplethysmography).

- **100% On-Device & Offline**: No cloud APIs, zero network permissions.
- **Reference Target**: Optimized for Qualcomm Snapdragon 7+ Gen 2 (Poco F5) running continuous 30.0 FPS.
- **Pure Native Signal Processing**: Native Kotlin port of the **Plane-Orthogonal-to-Skin (POS)** algorithm (*Wang et al., IEEE TBME 2017*) with advanced sub-harmonic analysis.

---

## 🔬 169 BPM Investigation & Resolution

### Root Cause Analysis

When evaluating raw rPPG algorithms under camera lighting or facial motion, estimates frequently spike to approximately **168–169 BPM** due to three interacting factors:

1. **Pulse Wave Non-Sinusoidal Harmonics**:
   The human blood volume pulse wave (systolic surge + dicrotic notch) is non-sinusoidal. Its Fourier transform contains a strong fundamental frequency ($f_0 \approx 1.35\text{ Hz} = 81\text{ BPM}$) and a prominent second harmonic ($2f_0 \approx 2.70 - 2.82\text{ Hz} = 162 - 169\text{ BPM}$).
2. **JPEG Compression Noise**:
   Naive implementations convert CameraX YUV frames into JPEG and then decode to Bitmaps. JPEG 8x8 DCT quantization and 4:2:0 chroma subsampling destroy the subtle $\Delta \approx 0.5\%$ skin chrominance variation and inject high-frequency noise into the green/red channels.
3. **Bandpass & Peak Selection Distortion**:
   Unconstrained FFT peak search ($0.75 - 3.0\text{ Hz}$) naively selects the highest magnitude bin ($2.82\text{ Hz}$), mistaking the second harmonic for tachycardia ($169\text{ BPM}$).

### The Solutions Implemented in PulseSense

```
Raw Camera Frame (YUV_420_888 @ 30 FPS)
  │
  ├──> Hardware Monotonic Timestamp (nanosecond clock from camera sensor)
  │
  ├──> Direct Zero-Allocation YUV->RGB Extraction
  │    (Direct ITU-R BT.601 math from ByteBuffers, ZERO JPEG artifacts)
  │
  ├──> Multi-ROI Skin Aggregation (Forehead + Left Cheek + Right Cheek)
  │
  ├──> POS Projection with 1.6s Chrominance Normalization Window
  │
  ├──> Sparse-Regularized Tikhonov Detrending (λ = 100)
  │
  ├──> Zero-Phase Butterworth Bandpass Filter (0.75 - 2.50 Hz = 45 - 150 BPM)
  │
  ├──> Multi-Peak Harmonic Resolution
  │    (Detects if peak at f_max has a sub-harmonic fundamental at f_max / 2)
  │
  ├──> 3-Point Parabolic Spectral Interpolation (Fractional-Hz precision)
  │
  └──> Weighted EMA Smoothing with Median Outlier Gating -> True BPM
```

---

## 📱 Poco F5 Camera & System Diagnostics

### ADB Real-Time Diagnostic Stream

To inspect the real-time camera pipeline and DSP diagnostics on your connected Poco F5:

```bash
adb logcat -s RPPG_DIAG
```

**Example Log Output**:
```text
D/RPPG_DIAG: [CameraDiag] Dimensions=640x480, Rot=270, HW_FPS=29.9, AcceptedFrames=480, FaceTracked=true, Motion=0.008
D/RPPG_DIAG: [rPPG] FPS=29.9, Samples=480, Peak=2.70Hz (162 BPM), Corrected=true, Fund=1.35Hz -> Final HR=81.2 BPM, Conf=0.84, SNR=6.4dB
```

---

## 🧪 Unit Test Suite (16 / 16 Passed)

```bash
cd android-app
.\gradlew.bat testDebugUnitTest
```

### Verified Test Cases:
- `FFT of pure sine at 1.2 Hz gives ~72 BPM` ✅
- `FFT of pure sine at 1.25 Hz gives ~75 BPM` ✅
- `FFT of pure sine at 1.3 Hz gives ~78 BPM` ✅
- `FFT of pure sine at 1.5 Hz gives ~90 BPM` ✅
- `FFT of pure sine at 2.0 Hz gives ~120 BPM` ✅
- `harmonic doubling within passband with strong 2nd harmonic at 2.0 Hz resolves to fundamental 60 BPM` ✅
- `169 BPM high frequency noise at 2.82 Hz is rejected in favor of true fundamental 81 BPM` ✅
- `engine returns null with insufficient samples` ✅
- `engine handles empty sample list` ✅
- `high SNR synthetic signal gives GOOD or FAIR quality` ✅
- `pure noise BPM is within physiological bounds if returned` ✅
- `reset clears BPM state` ✅
- `BPM output is always within physiological range when valid` ✅
- `RgbSignalBuffer respects max capacity` ✅
- `RgbSignalBuffer lastN returns correct count` ✅
- `RgbSignalBuffer computes reasonable FPS` ✅

---

## 📲 APK Installation

The compiled debug APK is located at:
```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

To install on your connected android:
```bash
adb install -r "c:\Users\Shantanu Vasagadekar\Downloads\rPPG-Toolbox-main\rPPG-Toolbox-main\android-app\app\build\outputs\apk\debug\app-debug.apk"
adb shell am start -n com.rppg.vitals/.MainActivity
```
