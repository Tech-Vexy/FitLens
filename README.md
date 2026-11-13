# FitLens — AI Fitness & Biomechanics Coach

FitLens is a real-time AI fitness coach combining computer vision pose tracking, contact-free vitals sensing, and personalized agentic coaching.

---

## 🏗️ Architecture: ADK Kotlin + Firebase AI Logic

FitLens features a **zero-key AI architecture** backed by Google's Agent Development Kit (ADK) Kotlin and Firebase App Check.

### 1. Presentation Layer (Jetpack Compose & Material 3)
- **`MainActivity` + Navigation Compose `NavHost`**: Single-activity app with four routes — `vitals` (dashboard), `workout`, `summary`, `analytics`.
  - **Vitals dashboard (`FitLensScreen`)**: Live CameraX preview with SmartSpectra rPPG metrics (pulse, breathing rate, HRV RMSSD, expression, arterial-pressure and breathing waveforms) plus Start/Stop measurement controls and entry points to the workout flow and analytics.
  - **`WorkoutDestination`**: Pre-workout setup (exercise picker, rep/set steppers) → live session → auto-persist + summary navigation. Starts/stops the SmartSpectra pipeline per visit.
  - `WorkoutSessionScreen`: Real-time HUD displaying camera/pose feedback, joint angles, live rep counter, Presage rPPG heart rate, heart rate zones, dynamic rest countdown, form deviation warnings, and TTS audio coaching.
  - `WorkoutSummaryScreen`: Post-workout biomechanical assessment, recovery intervention summaries, and progressive overload recommendations.
  - `WorkoutAnalyticsScreen`: Session history, aggregate stats, progression insight, and on-device JSON export.
- **Unidirectional State Flow**: `WorkoutViewModel` is activity-scoped (survives navigation) and exposes `StateFlow`s (`workoutState`, `vitalsFlow`, `currentLandmarks`, `workoutHistory`, `latestInsight`, `lastSavedSession`) backed by `WorkoutManager` and Room.

### 2. Application Layer
- **ADK Fitness Coach (`FitnessCoachAgent`)**:
  - Defined using ADK Kotlin `AdkLlmAgent` running via `AdkInMemoryRunner`.
  - **Authentication & Model Backends**:
    - **Firebase AI Logic (`gemini-3.8-flash`)**: Authenticated via **Firebase App Check** (Play Integrity in release, Debug provider in debug). No client API key in the APK.
    - **On-Device Edge adapter (`gemini-nano-v2`)**: `EdgeGeminiNanoAdapter` backed by `LocalEdgeEngine` — a deterministic, network-free tool router that discovers the agent's `@Tool` methods via reflection and dispatches matching intents to `FitnessTools` locally (sub-10ms latency, zero model download). There is no ML Kit / LiteRT LLM inference; edge "reasoning" is rule-based domain routing.
    - There is **no Google ADC backend** in the codebase; ADC/JVM execution described in earlier revisions was never implemented.
  - `@Tool` & `@Param` annotated domain functions in `FitnessTools`:
    - `generateWorkoutPlan`: Goal, experience, equipment, and duration-tailored routines.
    - `analyzeSession`: Comprehensive biomechanical and physiological evaluation.
    - `calculateAdaptiveRest`: Vitals-guided recovery tool that dynamically extends rest intervals (+15s / +30s) when heart rate remains elevated.
    - `evaluateBiomechanicalFatigue`: Safety tool monitoring consecutive form deviations to prevent joint injury.
    - `generateMesocyclePeriodization`: Multi-week periodization plan generator.
    - Note: `FitnessTools` functions are invoked **directly** by `FitnessCoachAgent` for safety/rest paths (e.g. `calculateAdaptiveRest`, `checkBiomechanicalFatigue`). For `runner.execute()` prompts, the cloud path calls Firebase AI Logic while the edge path routes through `LocalEdgeEngine`'s deterministic @Tool dispatcher — neither performs LLM-driven tool calling.
- **Workout Manager (`WorkoutManager`)**:
  - State machine: `IDLE` ➔ `ACTIVE_SET` ➔ `REST_INTERVAL` ➔ `COMPLETED`. (`WARMUP` and `COOLDOWN` exist in the `WorkoutPhase` enum but are never entered.)
  - Coordinates set transitions, adaptive rest intervals, and session entity creation. All state-mutating entry points are `@Synchronized` because they are invoked from the CameraX analyzer thread, the main thread, and session coroutines.
- **Audio Coach Engine (`FitLensAudioCoach`)**:
  - Hands-free voice coaching via Android TTS: vocalizes reps, form corrections, and phase transitions with throttling. Driven by `WorkoutViewModel` during live workout sessions.
- **Progress Analyzer (`ProgressAnalyzer`)**:
  - Tracks volume, form stability, and recovery readiness to recommend progressive overload (e.g. +2 reps or reduced rest intervals).

### 3. Sensing & Intelligence Layer
- **CameraX & DualStreamAnalyzer**:
  - `DualStreamAnalyzer` feeds each CameraX `ImageProxy` to both pipelines on the analyzer thread: MediaPipe pose **first** (because `PresageFrameAnalyzer` closes the proxy when it returns, per the SmartSpectra SDK contract), then SmartSpectra vitals. No camera device lock contention.
  - Pose results are delivered via an `onPoseFrame` callback to `WorkoutViewModel`, which feeds `WorkoutManager`, the `SkeletonOverlay` landmark state, and the local fault buffer. `enablePoseAnalysis = false` skips MediaPipe entirely on vitals-only screens (used by `MainActivity`).
  - `CameraPreviewView` binds/unbinds CameraX use cases to the Compose lifecycle and releases the camera on dispose.
  - `SkeletonOverlay` (used by `WorkoutSessionScreen`) draws bones and joints from the landmark map.
- **Presage Android SDK (`PresageFrameAnalyzer`)**:
  - Ingests CameraX `ImageProxy` frames via `useCustomInput()` (headless/custom-input mode); rejected submissions and pre-start frames are logged and skipped.
  - `CustomInput` handle is cached once per SDK session and reused across all frames; `resetCustomInput()` is called on stop/pause so the next session gets a fresh handle.
  - Computes pulse, HRV, breathing rate, and categorizes into 5 personalized heart-rate zones via the Karvonen HRR model (`UserProfile`).
  - SDK errors and validation status are observed and logged via `WorkoutViewModel`.
- **MediaPipe Pose Landmarker (`PoseLandmarkManager`)**:
  - Lazy-initialized `PoseLandmarker` from `pose_landmarker.task` asset on first frame.
  - Converts `ImageProxy` (YUV_420_888) to a grayscale `Bitmap` using the Y plane (honoring `rowStride`/`pixelStride` without disturbing the shared buffer), then maps the best-scoring detected pose to normalized `LandmarkPoint`s.
  - Real-time 33 3D skeletal landmarks across a multi-exercise library:
    - **Squats**: Knee flexion angle, knee valgus collapse detection, and torso forward lean.
    - **Push-ups**: Elbow flexion angle and spinal alignment.
    - **Lunges**: Front knee drive angle and vertical torso alignment.
    - **Plank**: Core straightness line (shoulder-hip-ankle) with hip sagging/piking flags.
  - Repetition detection state machine (`START` ➔ `ECCENTRIC` ➔ `INFLECTION` ➔ `CONCENTRIC` ➔ `COMPLETED`).
- **Firebase AI Logic**: Authenticated and gated via Firebase App Check (Play Integrity on production, Debug provider during development).

### 4. Data Layer (100% Local SQLite Health Data Vault)
- **Local SQLite Privacy Guarantee**: Workout sessions, physiological vitals time-series (heart rate, HRV, respiratory rate, HR zone, signal confidence — sampled during `ACTIVE_SET`/`REST_INTERVAL`), and biomechanical fault events are stored **strictly on-device in local SQLite (`fitlens_health_sqlite.db`)**. The live arterial-pressure waveform is rendered on screen but is **not** persisted to SQLite.
- **No Health Data Cloud Sync**: Biometric vitals and physiological data are explicitly prohibited from cloud transmission, maintaining complete user privacy and HIPAA/GDPR health compliance.
- **`WorkoutDatabase` & `WorkoutDao` (Room on SQLite)**:
  - Table `workout_sessions`: Session summaries, volume, form accuracy %, adaptive rest extensions, and coach feedback.
  - Table `local_vitals_telemetry`: High-frequency Presage rPPG time-series (BPM, HRV ms, breathing rate, HR zones, signal confidence).
  - Table `local_biomechanical_faults`: MediaPipe joint angle logs and biomechanical error timestamps.
- **`WorkoutRepository`**: Reactive SQLite repository exposing local queries and telemetry flows.

---

## 🔐 Security & Credential Management

### Zero AI Key Management (Firebase AI Logic + App Check)
- **No `GEMINI_API_KEY` or `GOOGLE_API_KEY` strings** in source code, build configs, or compiled APK binaries.
- Firebase App Check attests app authenticity via Google Play Integrity (production) and Debug Provider (development/emulators).
- Google Cloud backend enforces quotas and identity strictly via Firebase project attestation.

### Presage SDK Key Management (Secrets Gradle Plugin)
- Bundled at build time via `com.google.android.libraries.mapsplatform.secrets-gradle-plugin`.
- Real key stored in git-ignored `secrets.properties`:
  ```properties
  PRESAGE_API_KEY=your_actual_presage_key_here
  ```
- Public fallback stored in committed `local.defaults.properties`:
  ```properties
  PRESAGE_API_KEY=PLACEHOLDER_RUN_SETUP
  ```
- Accessible in Kotlin code via `com.fitlens.app.BuildConfig.PRESAGE_API_KEY`.

### Firebase Project Configuration

Firebase AI Logic and App Check use the official Firebase Android SDK. Download
the app configuration from **Firebase Console → Project settings → Your apps**
and save it as `app/google-services.json`; this file is git-ignored. Configure
the same file contents as the `GOOGLE_SERVICES_JSON` repository secret for CI/CD.
Debug builds use the App Check debug provider, while release builds use Play
Integrity. Register the release SHA-256 fingerprint in Firebase App Check before
publishing a release.

---

## 🔬 SmartSpectra Android SDK Integration Reference

Based on official Presage SmartSpectra documentation:

| Document | Topic | FitLens Integration |
|---|---|---|
| [API Reference](https://smartspectra.presagetech.com/docs/android/api-reference/) | `SmartSpectraSdk` lifecycle & config | `PresageManager.initializeSdk()` configures `SmartSpectraSdk.shared.config` (API key, metrics, image output) from `MainActivity.onCreate()`; `sdk.start()`/`sdk.stop()` are driven by the Start/Stop button and `onPause()` |
| [Headless Mode](https://smartspectra.presagetech.com/docs/android/headless-mode/) | `useCustomInput()` & `sendFrame()` | `CustomInput` handle cached once per session; `ImageProxy` frames submitted via `CustomInput.sendFrame()` without camera lock contention |
| [Metrics](https://smartspectra.presagetech.com/docs/android/metrics/) | `cardioMetrics` & `breathingMetrics` | Configures pulse rate (BPM), HRV (ms), arterial pressure trace waveform, and respiratory rate; all arterial pressure samples collected for waveform display |
| [LLM Insights](https://smartspectra.presagetech.com/docs/android/llm-insights/) | `sdk.insight` LiveData | **Not currently consumed** — no code observes `sdk.insight`; only `metrics`, `error`, and `validationStatus` LiveData are wired in `WorkoutViewModel` |
| [Option 2: OAuth](https://smartspectra.presagetech.com/docs/android/option-2-oauth/) | Production Auth Configuration | `presage_oauth_config.xml` exists as a resource but is **not referenced** by any code or manifest entry — the active auth path is the build-time `PRESAGE_API_KEY` |
| [Headless Testing in CI](https://smartspectra.presagetech.com/docs/android/headless-testing-in-ci/) | `@OptIn(SmartSpectraTestingApi::class)` | Automated headless verification using pre-recorded video frames |
| [Migration Guide](https://smartspectra.presagetech.com/docs/android/migration-guide/) | v3.4.0+ Architectural Standards | Implements current module naming and frame submission contracts |

### Key Integration Improvements

- **CustomInput handle caching**: The `CustomInput` handle from `useCustomInput()` is now cached once per SDK session and reused across all frames, rather than being obtained on every frame.
- **DualStreamAnalyzer completeness**: `DualStreamAnalyzer` now correctly dispatches frames to both SmartSpectra (vitals) and MediaPipe (pose) — previously it only forwarded to SmartSpectra.
- **SDK error & validation observation**: `WorkoutViewModel` now observes `sdk.error` and `sdk.validationStatus` LiveData for proper error handling and user guidance.
- **Clean session lifecycle**: `resetCustomInput()` is called after `sdk.stop()` to ensure the next session gets a fresh `CustomInput` handle.
- **MediaPipe frame analysis**: `PoseLandmarkManager.analyzeFrame()` converts `ImageProxy` to `Bitmap` and runs the MediaPipe `PoseLandmarker` for real-time pose detection.

---

## 🧪 Day 1 Hard Gate Verification Checklist

See detailed execution runbook: [**`docs/DAY_1_HARD_GATE_RUNBOOK.md`**](file:///home/veldrine/FitLens/docs/DAY_1_HARD_GATE_RUNBOOK.md)  
See submission pitch & demo script: [**`docs/COMPETITION_SUBMISSION_GUIDE.md`**](file:///home/veldrine/FitLens/docs/COMPETITION_SUBMISSION_GUIDE.md)

| Gate | Component | Test Criteria | Status Target |
|---|---|---|---|
| 1 | **Presage SDK** | `useCustomInput()` extracts valid pulse/HR data from live CameraX or recorded video stream | Valid HR reading |
| 2 | **MediaPipe** | `PoseLandmarker` emits 33 3D landmarks concurrently on CameraX stream | Form landmark feed active |
| 3 | **ADK Agent** | Minimal `LlmAgent` executes `@Tool` method via `InMemoryRunner` without client API keys | Plan generated |
| 4 | **Firebase App Check** | Attestation succeeds with valid provider and fails when App Check is disabled | Identity attested |
| 5 | **Secrets Plugin** | `BuildConfig.PRESAGE_API_KEY` resolves properly; `secrets.properties` absent from git | No secrets in git |

---

## 📁 Project Structure

```
FitLens/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/fitlens/app/
│   │   │   │   ├── ai/
│   │   │   │   │   ├── adk/
│   │   │   │   │   │   ├── AdkAgentArchitecture.kt# LlmAgent, InMemoryRunner, Firebase AI Logic & edge adapter
│   │   │   │   │   │   ├── AdkAnnotations.kt      # @Tool and @Param annotations
│   │   │   │   │   │   └── LocalEdgeEngine.kt     # Deterministic on-device @Tool discovery & dispatch
│   │   │   │   │   ├── firebase/
│   │   │   │   │   │   └── FirebaseAiService.kt    # Firebase AI Logic generative model wrapper
│   │   │   │   │   ├── FitnessCoachAgent.kt       # ADK LlmAgent orchestration
│   │   │   │   │   └── FitnessTools.kt            # @Tool functions (Plan, Analysis, Adaptive Rest, Fatigue, Mesocycle)
│   │   │   │   ├── audio/
│   │   │   │   │   └── FitLensAudioCoach.kt       # Android TTS hands-free vocal coaching
│   │   │   │   ├── camera/
│   │   │   │   │   ├── CameraPreviewView.kt       # Lifecycle-aware CameraX surface
│   │   │   │   │   ├── DualStreamAnalyzer.kt      # Concurrent MediaPipe & Presage frame dispatcher
│   │   │   │   │   └── SkeletonOverlay.kt         # Jetpack Compose 33-point skeletal overlay
│   │   │   │   ├── data/
│   │   │   │   │   ├── dao/WorkoutDao.kt          # Local SQLite DAO (Sessions, Vitals, Faults)
│   │   │   │   │   ├── model/WorkoutEntities.kt   # Local SQLite Tables (Zero cloud health sync)
│   │   │   │   │   ├── privacy/LocalHealthDataPrivacyEnforcer.kt # Local-only storage audit
│   │   │   │   │   ├── profile/UserProfile.kt     # DataStore & Karvonen Heart Rate Reserve zones
│   │   │   │   │   ├── LocalDataExportManager.kt  # On-device JSON export (GDPR data portability)
│   │   │   │   │   ├── WorkoutDatabase.kt         # Local SQLite DB (fitlens_health_sqlite.db)
│   │   │   │   │   └── WorkoutRepository.kt       # On-device health repository
│   │   │   │   ├── testing/
│   │   │   │   │   └── HeadlessVideoFeeder.kt     # Automated Day 1 Hard Gate video feed runner
│   │   │   │   ├── ui/
│   │   │   │   │   ├── WorkoutAnalyticsScreen.kt  # Biomechanical stability & vitals trends
│   │   │   │   │   ├── WorkoutDestination.kt      # Workout flow: setup -> session -> summary wiring
│   │   │   │   │   ├── WorkoutSessionScreen.kt    # Live workout HUD & feedback
│   │   │   │   │   ├── WorkoutSummaryScreen.kt    # Post-session insights & metrics
│   │   │   │   │   └── WorkoutViewModel.kt        # MVI/MVVM Session coordinator
│   │   │   │   ├── pose/
│   │   │   │   │   └── PoseLandmarkManager.kt     # Angles, rep state machine & valgus detection
│   │   │   │   ├── vitals/
│   │   │   │   │   ├── PresageFrameAnalyzer.kt    # CameraX ImageAnalysis + rPPG HR zones
│   │   │   │   │   ├── PresageManager.kt          # BuildConfig Presage key resolution
│   │   │   │   │   └── PulseWaveformView.kt       # rPPG arterial-pressure waveform composable
│   │   │   │   ├── workout/
│   │   │   │   │   ├── ProgressAnalyzer.kt        # Progressive overload calculator
│   │   │   │   │   └── WorkoutManager.kt          # Active workout state machine
│   │   │   │   ├── FitLensApp.kt                  # App Check (Play Integrity / Debug) init
│   │   │   │   └── MainActivity.kt                # NavHost: vitals dashboard, workout, summary, analytics
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   │       └── kotlin/com/fitlens/app/
│   │           └── FitnessCoachTest.kt            # Comprehensive unit test suite (21 tests)
│   ├── build.gradle.kts                           # App dependencies, secrets config, Room, ADK, DataStore
│   └── proguard-rules.pro                         # Proguard/R8 rules for ADK, Room, MediaPipe & SmartSpectra
├── docs/
│   ├── DAY_1_HARD_GATE_RUNBOOK.md                 # Step-by-step gate execution guide
│   └── COMPETITION_SUBMISSION_GUIDE.md            # Hackathon pitch & judge demo script
├── scripts/
│   └── verify_fitlens.sh                          # Automated 5-stage architectural verification script
├── .github/workflows/android-ci.yml               # PR/main verification, tests, lint & APK build
├── .github/workflows/android-cd.yml               # Version-tagged APK/AAB GitHub Releases
├── build.gradle.kts                               # Root plugins (Secrets, KSP, Google Services)
├── local.defaults.properties                      # Committed placeholder secrets
├── secrets.properties                             # Git-ignored developer secrets
└── .gitignore
```

## 🚀 CI/CD

Pull requests and pushes to `main` or `master` run the architectural checks, unit
tests, Android lint, and release APK build. To publish a release, create and push
a semantic version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The CD workflow builds two signed release APKs only: `armeabi-v7a` (ARM32) and
`arm64-v8a` (ARM64). No universal, x86, or x86_64 APK is produced. Configure
these repository secrets for production builds:

| Secret | Purpose |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Signing key password |
| `PRESAGE_API_KEY` | Presage SDK credential |
| `GOOGLE_SERVICES_JSON` | Firebase app configuration |
