<p align="center">
  <img src="docs/fitlens_logo.png" alt="FitLens Logo" width="140" />
</p>

# FitLens — Contactless Human-Sensing AI Fitness & Wellness Coach

FitLens is a real-time Android fitness and wellness coach built with the **Presage SmartSpectra Android SDK** and on-device/optional cloud AI coaching. Using a single camera stream without wearables or chest straps, FitLens responds live to **pulse rate**, **breathing rate**, **HRV (RMSSD)**, **facial expressions**, and respiratory waveforms—adapting rest intervals, recovery-breathing guidance, readiness cues, and expression-aware coaching prompts.

> **Wellness & Fitness Disclaimer**: FitLens is strictly a non-medical fitness, exercise-guidance, and wellness biofeedback application. It is **not a medical device** and does not diagnose, treat, cure, or prevent any disease or support medical decision-making.

---

## 🌟 Real-Time Response to Live Presage SmartSpectra Signals

FitLens owns a single CameraX pipeline through [`DualStreamAnalyzer`](app/src/main/kotlin/com/fitlens/app/camera/DualStreamAnalyzer.kt) and feeds frames headlessly via `SmartSpectraSdk.shared.useCustomInput()`.

| Live SmartSpectra Signal | SDK Metric Group | Real-Time FitLens Experience Response |
|---|---|---|
| **Pulse Rate & Relative Arterial Pressure Trace** | `SmartSpectraConfig.cardioMetrics` | Classifies live exertion across **5 personalized Karvonen Heart Rate Reserve (HRR)** zones ([`UserProfile`](app/src/main/kotlin/com/fitlens/app/data/profile/UserProfile.kt)), dynamically extends inter-set recovery (`+15s` / `+30s`) via [`calculateAdaptiveRest`](app/src/main/kotlin/com/fitlens/app/ai/FitnessTools.kt) when HR remains elevated, renders a live pulse waveform, and measures **HR Recovery Drop** during cooldown. |
| **Breathing Rate & Chest / Abdomen Traces** | `SmartSpectraConfig.breathingMetrics` | Displays live `brpm` and chest/abdomen waveform traces, feeds respiratory rate into holistic coaching, and pairs rest/cooldown with an animated recovery-breathing pacer. Elevated breathing or heart rate can trigger a coached recovery prompt. |
| **Heart Rate Variability (HRV RMSSD)** | `SmartSpectraConfig.cardioMetrics` | Surfaces a live **Autonomic Readiness & Exertion Load** badge (`High Readiness`, `Balanced Load`, `High Exertion Load`) in the HUD and longitudinal readiness scoring. |
| **Facial Expressions** | `MetricType.EXPRESSIONS` | Detects facial grimace/tension (`Angry`, `Fear`, `Disgust`, `Sad`, `Contempt`) and other expression states (`Happy`, `Neutral`, `Surprise`) via [`evaluateHolisticBiofeedback`](app/src/main/kotlin/com/fitlens/app/ai/FitnessTools.kt), coaching athletes to unclench their jaw, drop their shoulders, and exhale through sticking points. |
| **Validation Status & SmartSpectra Insights** | `sdk.validationStatus`, `sdk.insight`, & `sdk.requestInsight(...)` | Displays live framing/lighting hints directly in the dashboard and streams SmartSpectra wellness insights into the Coach experience; insights can also be requested periodically while pulse is available or from the mood check-in flow. |

---

## 🔐 Consent, Privacy & Responsible Data Handling

- **Explicit Pre-Capture Camera Consent**: Camera preview and SmartSpectra frame processing remain **disabled** until the user reviews and accepts [`SensingConsentDialog`](app/src/main/kotlin/com/fitlens/app/ui/ConsentAndPrivacySheet.kt) and Android grants camera permission. Consent can be revoked anytime in **Profile & Privacy**, which stops sensing and clears current session readings.
- **Volatile In-Memory Video Processing**: For SmartSpectra sensing, camera frames (`YUV_420_888`) are processed in volatile RAM on-device and immediately closed. FitLens does **not** write raw video or images to disk; optional Live Voice uses the separately disclosed compressed-frame path below.
- **Optional Live Voice Media**: If the user explicitly enables **Live Voice**, FitLens may send microphone audio and compressed, low-resolution JPEG camera frames to Firebase AI Live for voice/visual coaching. This path is optional, user-initiated, and stops when Live Voice ends.
- **Local SQLite Health Vault (`fitlens_health_sqlite.db`)**:
  - Completed workout summaries (`workout_sessions`) are stored locally via Room/SQLite with no cloud sync of those stored health records.
  - **User Retention & One-Tap Deletion**: Data remains on-device under user control. Users can permanently wipe all stored health records in one tap (**Clear Local Data** in [`ProfileAndPrivacySheet`](app/src/main/kotlin/com/fitlens/app/ui/ConsentAndPrivacySheet.kt)). A code-level local JSON export utility is also included in [`LocalDataExportManager`](app/src/main/kotlin/com/fitlens/app/data/LocalDataExportManager.kt).
- **Presage SmartSpectra Network Use & Telemetry Opt-Out**:
  - Physiological metric computation runs **on-device** inside the SmartSpectra SDK. SDK network access is used for API key authentication, subscription gating, and SmartSpectra LLM Insights.
  - SmartSpectra Insights send buffered numeric vitals (pulse, HRV, breathing) plus prompt/request metadata to Presage’s Analytics Gateway; raw video and facial imagery are not sent for insights. FitLens observes `sdk.insight`, periodically requests insights while pulse is available, and can request an insight from the mood check-in flow.
  - SmartSpectra's aggregate per-session diagnostic telemetry is disclosed in the consent/profile UI and can be toggled off (`sdk.config.enableTelemetry = false`) in [`ConsentAndPrivacySheet`](app/src/main/kotlin/com/fitlens/app/ui/ConsentAndPrivacySheet.kt).
- **Zero Client AI Keys & Optional Cloud/Offline AI**:
  - Presage vitals are **not** sent to Firebase workout-plan generation.
  - Optional workout routine and 4-week mesocycle generation uses **Firebase AI Logic + App Check** (zero `GEMINI_API_KEY` in source or APK, sending only non-biometric preferences: goal, experience, equipment, duration), or can be locked to **100% Offline On-Device Edge AI** ([`LocalEdgeEngine`](app/src/main/kotlin/com/fitlens/app/ai/adk/LocalEdgeEngine.kt)).
  - Optional Coach Live Voice/text may send disclosed wellness context—such as heart rate, HRV, breathing rate, expression label, name, validation hint, and SmartSpectra insight—to Firebase when cloud AI is enabled. Offline Edge AI keeps coach text/routine responses local and disables the cloud Live Voice path.

---

## 🏗️ Architecture Overview

1. **Presentation Layer (Jetpack Compose & Material 3)**:
   - [`MainActivity`](app/src/main/kotlin/com/fitlens/app/MainActivity.kt): Single-activity host coordinating explicit sensing consent, runtime permissions, and the unified CameraX + SmartSpectra pipeline with optional Gemini Live features via [`WorkoutViewModel`](app/src/main/kotlin/com/fitlens/app/ui/WorkoutViewModel.kt).
  - [`PersonalTrainerScreen`](app/src/main/kotlin/com/fitlens/app/ui/PersonalTrainerScreen.kt): Unified 3-tab interface (**Train**, **Vitals**, **Coach**) with persistent PiP / Full / Hidden CameraX preview, real-time SmartSpectra biometrics & PPG/respiratory waveforms, workout program customizer, optional Gemini Live voice/text coaching, and compact bottom vitals strip.
  - [`WorkoutVisualPlayer`](app/src/main/kotlin/com/fitlens/app/ui/WorkoutVisualPlayer.kt): Interactive active-workout stage with rep/hold progress, TTS voice coaching ([`FitLensAudioCoach`](app/src/main/kotlin/com/fitlens/app/audio/FitLensAudioCoach.kt)), and the **SmartSpectra Coherent Recovery Breathing Pacer**.
  - [`ConsentAndPrivacySheet`](app/src/main/kotlin/com/fitlens/app/ui/ConsentAndPrivacySheet.kt): Explicit pre-capture consent dialog, personalized Karvonen HRR profile settings, SmartSpectra telemetry & Offline Edge AI toggles, pipeline smoke test runner, and one-tap local data deletion.
2. **Sensing Intelligence**:
  - [`PresageFrameAnalyzer`](app/src/main/kotlin/com/fitlens/app/vitals/PresageFrameAnalyzer.kt) & [`PresageManager`](app/src/main/kotlin/com/fitlens/app/vitals/PresageManager.kt): Headless `CustomInput` frame ingestion, Karvonen HRR classification, and runtime telemetry configuration.
3. **Workout & ADK Coaching Engine**:
  - [`WorkoutManager`](app/src/main/kotlin/com/fitlens/app/workout/WorkoutManager.kt): State machine (`IDLE` ➔ `WARMUP` ➔ `ACTIVE_SET` ⇄ `REST_INTERVAL` ➔ `COOLDOWN` ➔ `COMPLETED`) supporting multi-exercise routines, manual rep logging, timed isometric Plank holds, and vitals-guided adaptive rest.
   - [`FitnessCoachAgent`](app/src/main/kotlin/com/fitlens/app/ai/FitnessCoachAgent.kt), [`FitnessTools`](app/src/main/kotlin/com/fitlens/app/ai/FitnessTools.kt), & [`LocalEdgeEngine`](app/src/main/kotlin/com/fitlens/app/ai/adk/LocalEdgeEngine.kt): Hybrid Cloud + deterministic sub-10ms On-Device Edge `@Tool` dispatcher.

### Key Architectural Decisions

- **One camera pipeline, two consumers**: `DualStreamAnalyzer` submits every CameraX frame to SmartSpectra first, then optionally reuses the same frame for the separate Gemini Live path. FitLens does not run a second camera/capture stack.
- **Deterministic physiological adaptation stays local**: `WorkoutManager` and `FitnessTools` turn live pulse/HRV/breathing/expression signals into adaptive rest, readiness, recovery-breathing, and session-summary decisions without waiting on a cloud model.
- **Headless SmartSpectra input**: `SmartSpectraSdk.shared.useCustomInput()` lets FitLens feed `ImageProxy` frames directly while CameraX continues to own preview lifecycle and permissions.
- **Honest unavailable metrics**: unmeasured heart-rate/form values are stored as unavailable sentinels instead of fabricated defaults, and simulated vitals are excluded from persisted workout HR.
- **Privacy boundaries are explicit**: SmartSpectra computes vitals on-device; SmartSpectra Insights sends only buffered numeric vitals/prompt metadata; optional Gemini Live sends disclosed audio/JPEG context; local health records stay in Room/SQLite until deleted.

### Current Challenges & Honest Limitations

- **No camera-based exercise recognition**: reps are manual `+1 Rep` taps and Plank is a timed hold. FitLens adapts recovery/coaching from physiology, not pose tracking.
- **SmartSpectra still needs network for auth/subscription gating**: metric computation is on-device, but the sensing path is not a fully offline wearable replacement.
- **Signal quality depends on lighting/framing/device**: confidence and validation hints are surfaced so users can improve capture, but readings can be intermittent.
- **Optional cloud AI is not required for core coaching**: Firebase/Google AI features need `google-services.json`; without it, `LocalEdgeEngine` still handles plans/routines locally, but Live Voice is unavailable.
- **Local verification depends on a healthy Gradle/AAPT2 environment**: CI is the authoritative check for compile, lint, unit tests, and signed release APK assembly.

---

## 🛠️ Setup & Testing Instructions (For Judges & Developers)

### Prerequisites
- **Android Studio Ladybug (2024.2+)** or **JDK 17** with **Android SDK Platform 36**
- An Android phone running **Android 9.0+ (API 28+, ARM64/ARM32)** for live camera rPPG + face/chest sensing *(or an emulator using the built-in Headless Smoke Test & Live Simulation Controls — no login credentials required!)*

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
- **Zero-setup fallback**: If `app/google-services.json` is omitted or Firebase AI Logic is unavailable, `FitnessCoachAgent` automatically routes workout plans and 4-week mesocycles locally via [`LocalEdgeEngine`](app/src/main/kotlin/com/fitlens/app/ai/adk/LocalEdgeEngine.kt). SmartSpectra itself still requires network access for API-key/subscription gating.

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
- [**Verification Runbook (`docs/DAY_1_HARD_GATE_RUNBOOK.md`)**](docs/DAY_1_HARD_GATE_RUNBOOK.md)
