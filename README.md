# FitLens — Contactless Human-Sensing AI Fitness & Biomechanics Coach

FitLens is a real-time Android fitness and biomechanics coach built with the **Presage SmartSpectra Android SDK**, **MediaPipe Pose Landmarker**, and **Google ADK Kotlin**. Using a single camera stream without wearables or chest straps, FitLens responds live to **pulse rate**, **breathing rate**, **HRV (RMSSD)**, **facial expressions**, and **33-point 3D joint angles**—adapting rest intervals, pacing recovery breathing, coaching facial tension release, and halting sets before form breakdown causes joint strain.

> **Wellness & Fitness Disclaimer**: FitLens is strictly a non-medical fitness, exercise form, and wellness biofeedback application. It is **not a medical device** and does not diagnose, treat, cure, or prevent any disease or support medical decision-making.

---

## 🌟 Real-Time Response to Live Presage SmartSpectra Signals

FitLens owns a single CameraX pipeline through [`DualStreamAnalyzer`](app/src/main/kotlin/com/fitlens/app/camera/DualStreamAnalyzer.kt)—eliminating `CameraDevice` hardware lock contention between MediaPipe and SmartSpectra—and feeds frames headlessly via `SmartSpectraSdk.shared.useCustomInput()`.

| Live SmartSpectra Signal | SDK Metric Group | Real-Time FitLens Experience Response |
|---|---|---|
| **Pulse Rate & Relative Arterial Pressure Trace** | `SmartSpectraConfig.cardioMetrics` | Classifies live exertion across **5 personalized Karvonen Heart Rate Reserve (HRR)** zones ([`UserProfile`](app/src/main/kotlin/com/fitlens/app/data/profile/UserProfile.kt)), dynamically extends inter-set recovery (`+15s` / `+30s`) via [`calculateAdaptiveRest`](app/src/main/kotlin/com/fitlens/app/ai/FitnessTools.kt) when HR remains elevated, renders a live pulse waveform, and measures **HR Recovery Drop** during cooldown. |
| **Breathing Rate & Chest / Abdomen Traces** | `SmartSpectraConfig.breathingMetrics` | Triggers an **Animated Coherent Recovery Breathing Pacer** (`4s Inhale / 6s Exhale` expanding ring) in [`WorkoutSessionScreen`](app/src/main/kotlin/com/fitlens/app/ui/WorkoutSessionScreen.kt) during rest intervals and cooldown whenever respiratory rate rises (`≥ 21 brpm`), guiding athletes back toward calm nasal breathing. |
| **Heart Rate Variability (HRV RMSSD)** | `SmartSpectraConfig.cardioMetrics` | Surfaces a live **Autonomic Readiness & Exertion Load** badge (`High Readiness`, `Balanced Load`, `High Exertion Load`) in the HUD and longitudinal readiness scoring. |
| **Facial Expressions** | `MetricType.EXPRESSIONS` | Detects facial grimace/tension (`Angry`, `Fear`, `Disgust`, `Sad`, `Strain`) vs. composed effort (`Happy`, `Neutral`) via [`evaluateHolisticBiofeedback`](app/src/main/kotlin/com/fitlens/app/ai/FitnessTools.kt), coaching athletes to unclench their jaw, drop their shoulders, and exhale through sticking points. |
| **Validation Status & On-Demand Insights** | `sdk.validationStatus` & `sdk.insight` | Displays live framing/lighting hints directly in the dashboard and workout HUD, and triggers natural-language wellness summaries via `sdk.requestInsight(...)`. |

---

## 🔐 Consent, Privacy & Responsible Data Handling

- **Explicit Pre-Capture Camera Consent**: Camera preview and SmartSpectra frame processing remain **completely disabled** until the user reviews and accepts [`SensingConsentDialog`](app/src/main/kotlin/com/fitlens/app/ui/ConsentAndPrivacySheet.kt). Consent can be revoked anytime in **Profile, Zones & Privacy**.
- **Volatile In-Memory Video Processing**: Camera frames (`YUV_420_888`) are processed strictly in volatile RAM on-device by MediaPipe and SmartSpectra and immediately closed. Video frames are **never** written to disk or transmitted over the network.
- **100% Local SQLite Health Vault (`fitlens_health_sqlite.db`)**:
  - Session summaries (`workout_sessions`), vitals telemetry (`local_vitals_telemetry`), and joint fault episodes (`local_biomechanical_faults`) are stored strictly locally via Room/SQLite—zero cloud health sync.
  - **User Retention, Export & One-Tap Deletion**: Data remains on-device under user control. Users can export their vault to local JSON ([`LocalDataExportManager`](app/src/main/kotlin/com/fitlens/app/data/LocalDataExportManager.kt)) or permanently wipe all stored records in one tap (**Delete All Local Vault Data** in [`ProfileAndPrivacySheet`](app/src/main/kotlin/com/fitlens/app/ui/ConsentAndPrivacySheet.kt) or [`WorkoutAnalyticsScreen`](app/src/main/kotlin/com/fitlens/app/ui/WorkoutAnalyticsScreen.kt)).
- **Presage SmartSpectra Network Gating & Telemetry Opt-Out**:
  - All physiological signal extraction runs **on-device** inside the SmartSpectra SDK. Network calls to Presage are used only for API key authentication and subscription gating.
  - SmartSpectra's optional aggregate per-session diagnostic telemetry (which contains **no raw video frames, no vital sign values, and no user or device identifiers**) is transparently disclosed and can be toggled off (`sdk.config.enableTelemetry = false`) in [`ConsentAndPrivacySheet`](app/src/main/kotlin/com/fitlens/app/ui/ConsentAndPrivacySheet.kt).
- **Zero Client AI Keys & 100% Offline Edge AI Option**:
  - Biometric vitals are **never** sent to cloud AI services.
  - Optional workout routine and 4-week mesocycle generation uses **Firebase AI Logic + App Check** (zero `GEMINI_API_KEY` in source or APK, sending only non-biometric preferences: goal, experience, equipment, duration), or can be locked to **100% Offline On-Device Edge AI** ([`LocalEdgeEngine`](app/src/main/kotlin/com/fitlens/app/ai/adk/LocalEdgeEngine.kt)).

---

## 🏗️ Architecture Overview

1. **Presentation Layer (Jetpack Compose & Material 3)**:
   - [`MainActivity`](app/src/main/kotlin/com/fitlens/app/MainActivity.kt): Consent-gated Vitals Dashboard displaying live CameraX preview, Pulse + Karvonen Zone, Breathing Rate, HRV RMSSD, Facial Expression, 3 real-time waveforms (Arterial Pressure, Chest, Abdomen), SmartSpectra Insights, and Profile/Privacy controls.
   - [`WorkoutDestination`](app/src/main/kotlin/com/fitlens/app/ui/WorkoutDestination.kt): Pre-workout configurator supporting both **Quick Exercise** (Bodyweight Squats, Push-Ups, Lunges, and **Time-Based Isometric Plank**, plus optional Warmup/Cooldown) and **ADK AI Coach Plan & 4-Week Mesocycle Generator**.
   - [`WorkoutSessionScreen`](app/src/main/kotlin/com/fitlens/app/ui/WorkoutSessionScreen.kt): Real-time HUD with front/rear camera switching, `FILL_CENTER`-aligned [`SkeletonOverlay`](app/src/main/kotlin/com/fitlens/app/camera/SkeletonOverlay.kt) with joint fault halos, 4-signal SmartSpectra biofeedback bar, animated **Coherent Breathing Pacer**, TTS voice coaching ([`FitLensAudioCoach`](app/src/main/kotlin/com/fitlens/app/audio/FitLensAudioCoach.kt)), and live simulation controls for emulator/judge testing.
   - [`WorkoutSummaryScreen`](app/src/main/kotlin/com/fitlens/app/ui/WorkoutSummaryScreen.kt) & [`WorkoutAnalyticsScreen`](app/src/main/kotlin/com/fitlens/app/ui/WorkoutAnalyticsScreen.kt): Post-workout biomechanics + vitals evaluation, readiness scoring, SQLite-computed per-joint fault breakdown, JSON export, and one-tap vault deletion.
2. **Sensing & Pose Intelligence**:
   - [`PoseLandmarkManager`](app/src/main/kotlin/com/fitlens/app/pose/PoseLandmarkManager.kt): MediaPipe `PoseLandmarker` running in **`RunningMode.VIDEO`** with sensor rotation (`0°/90°/180°/270°`) and front-camera mirroring normalization into downscaled full-color `ARGB_8888` bitmaps (reusing scratch buffers for zero per-frame allocations) and **EMA temporal landmark smoothing**.
   - [`PresageFrameAnalyzer`](app/src/main/kotlin/com/fitlens/app/vitals/PresageFrameAnalyzer.kt) & [`PresageManager`](app/src/main/kotlin/com/fitlens/app/vitals/PresageManager.kt): Headless `CustomInput` frame ingestion, Karvonen HRR classification, and runtime telemetry configuration.
3. **Workout & ADK Coaching Engine**:
   - [`WorkoutManager`](app/src/main/kotlin/com/fitlens/app/workout/WorkoutManager.kt): Thread-safe state machine (`IDLE` ➔ `WARMUP` ➔ `ACTIVE_SET` ⇄ `REST_INTERVAL` ➔ `COOLDOWN` ➔ `COMPLETED`) supporting multi-exercise routines, timed isometric Plank holds, 3-fault biomechanical safety set halting, and vitals-guided adaptive rest.
   - [`FitnessCoachAgent`](app/src/main/kotlin/com/fitlens/app/ai/FitnessCoachAgent.kt), [`FitnessTools`](app/src/main/kotlin/com/fitlens/app/ai/FitnessTools.kt), & [`LocalEdgeEngine`](app/src/main/kotlin/com/fitlens/app/ai/adk/LocalEdgeEngine.kt): Hybrid Cloud + deterministic sub-10ms On-Device Edge `@Tool` dispatcher.

---

## 🛠️ Setup & Testing Instructions (For Judges & Developers)

### Prerequisites
- **Android Studio Ladybug (2024.2+)** or **JDK 17** with **Android SDK Platform 36**
- An Android phone running **Android 9.0+ (API 28+, ARM64/ARM32)** for live camera rPPG + pose sensing *(or an emulator using the built-in Headless Smoke Test & Live Simulation Controls — no login credentials required!)*

### 1. Clone the Repository
```bash
git clone https://github.com/Tech-Vexy/FitLens.git
cd FitLens
```

### 2. Add Your Presage SmartSpectra API Key
1. Register for a free Presage developer key at [physiology.presagetech.com/auth/register](https://physiology.presagetech.com/auth/register).
2. Create a git-ignored `secrets.properties` file in the project root:
   ```properties
   PRESAGE_API_KEY=your_presage_smartspectra_api_key
   ```
   *(If `secrets.properties` is omitted, Gradle falls back to `local.defaults.properties` so the project still builds cleanly and all simulation / automated smoke-test features remain testable.)*

### 3. (Optional) Firebase AI Logic Configuration
- To enable cloud Gemini routine generation via Firebase App Check, place `app/google-services.json` from Firebase Console.
- **Zero-setup fallback**: If `app/google-services.json` is omitted or the device is offline, `FitnessCoachAgent` automatically routes all workout plans and 4-week mesocycles locally via [`LocalEdgeEngine`](app/src/main/kotlin/com/fitlens/app/ai/adk/LocalEdgeEngine.kt).

### 4. Run Automated Architecture & Unit Verification
```bash
./scripts/verify_fitlens.sh
```

### 5. Build & Install Debug APK
```bash
./gradlew installDebug
```

---

## 📄 Submission & Verification Docs
- [**Official DEV Challenge Submission Post Draft (`docs/DEV_SUBMISSION_POST.md`)**](docs/DEV_SUBMISSION_POST.md)
- [**Judge Demo Walkthrough & Pitch Guide (`docs/COMPETITION_SUBMISSION_GUIDE.md`)**](docs/COMPETITION_SUBMISSION_GUIDE.md)
- [**Day 1 Hard Gate Verification Runbook (`docs/DAY_1_HARD_GATE_RUNBOOK.md`)**](docs/DAY_1_HARD_GATE_RUNBOOK.md)
