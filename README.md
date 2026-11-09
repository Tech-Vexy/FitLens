# FitLens — AI Fitness & Biomechanics Coach

FitLens is a real-time AI fitness coach combining computer vision pose tracking, contact-free vitals sensing, and personalized agentic coaching.

---

## 🏗️ Architecture: ADK Kotlin + Firebase AI Logic

FitLens features a **zero-key AI architecture** backed by Google's Agent Development Kit (ADK) Kotlin and Firebase App Check.

### 1. Presentation Layer (Jetpack Compose & Material 3)
- **Interactive Multi-Screen Navigation**:
  - `FitLensDashboard`: Status of zero AI key & Presage credential injection, ADK workout plan generation, and recent session logs.
  - `WorkoutSessionScreen`: Real-time HUD displaying camera/pose feedback, joint angles, live rep counter, Presage rPPG heart rate, heart rate zones, dynamic rest countdown, and form deviation warnings.
  - `WorkoutSummaryScreen`: Post-workout biomechanical assessment, recovery intervention summaries, and progressive overload recommendations.
- **Unidirectional State Flow**: Driven by `WorkoutViewModel` with Kotlin Coroutines `StateFlow`.

### 2. Application Layer
- **ADK Fitness Coach (`FitnessCoachAgent`)**:
  - Defined using ADK Kotlin `AdkLlmAgent` running via `AdkInMemoryRunner`.
  - **Dual Enterprise / Mobile Authentication Architecture**:
    - **Firebase AI Logic (`gemini-3.8-flash`)**: Android on-device execution authenticated via **Firebase App Check** (Play Integrity). Completely eliminates client API keys in APK.
    - **Google ADC (Application Default Credentials)**: JVM / Cloud Workstation / CI / Backend proxy execution authenticated via `gcloud auth application-default login` or `GOOGLE_APPLICATION_CREDENTIALS`.
    - **On-Device Edge Sub-Agent (`gemini-nano-v2`)**: Local ML Kit inference for sub-10ms latency-critical safety checks.
  - Zero-reflection `@Tool` & `@Param` annotated functions in `FitnessTools`:
    - `generateWorkoutPlan`: Goal, experience, equipment, and duration-tailored routines.
    - `analyzeSession`: Comprehensive biomechanical and physiological evaluation.
    - `calculateAdaptiveRest`: Vitals-guided recovery tool that dynamically extends rest intervals (+15s / +30s) when heart rate remains elevated.
    - `evaluateBiomechanicalFatigue`: Safety tool monitoring consecutive form deviations to prevent joint injury.
- **Workout Manager (`WorkoutManager`)**:
  - State machine: `IDLE` ➔ `WARMUP` ➔ `ACTIVE_SET` ➔ `REST_INTERVAL` ➔ `COOLDOWN` ➔ `COMPLETED`.
  - Coordinates set transitions, audio/visual cues, and adaptive rest intervals.
- **Audio Coach Engine (`FitLensAudioCoach`)**:
  - Hands-free voice coaching via Android TTS: vocalizes reps, form corrections, rest countdowns, and vitals alerts with dynamic throttling.
- **Progress Analyzer (`ProgressAnalyzer`)**:
  - Tracks volume, form stability, and recovery readiness to recommend progressive overload (e.g. +2 reps or reduced rest intervals).

### 3. Sensing & Intelligence Layer
- **CameraX & DualStreamAnalyzer**:
  - High-performance non-blocking dual dispatcher feeding CameraX `ImageProxy` frames simultaneously to MediaPipe and SmartSpectra.
  - Lifecycle-aware `CameraPreviewView` with live Jetpack Compose `SkeletonOverlay` displaying 33 skeletal joints and connecting bones.
- **Presage Android SDK (`PresageFrameAnalyzer`)**:
  - Ingests CameraX `ImageProxy` frames via `useCustomInput()`.
  - Computes pulse, HRV, breathing rate, and categorizes into 5 physiological zones (Resting, Warmup, Aerobic, Anaerobic, Max).
- **MediaPipe Pose Landmarker (`PoseLandmarkManager`)**:
  - Real-time 33 3D skeletal landmarks across a multi-exercise library:
    - **Squats**: Knee flexion angle, knee valgus collapse detection, and torso forward lean.
    - **Push-ups**: Elbow flexion angle and spinal alignment.
    - **Lunges**: Front knee drive angle and vertical torso alignment.
    - **Plank**: Core straightness line (shoulder-hip-ankle) with hip sagging/piking flags.
  - Repetition detection state machine (`START` ➔ `ECCENTRIC` ➔ `INFLECTION` ➔ `CONCENTRIC` ➔ `COMPLETED`).
- **Firebase AI Logic**: Authenticated and gated via Firebase App Check (Play Integrity on production, Debug provider during development).

### 4. Data Layer (100% Local SQLite Health Data Vault)
- **Local SQLite Privacy Guarantee**: All physiological vitals (heart rate, HRV, respiratory rate, arterial pressure traces), skeletal joint angles, and workout telemetry are stored **strictly on-device in local SQLite (`fitlens_health_sqlite.db`)**.
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
| [API Reference](https://smartspectra.presagetech.com/docs/android/api-reference/) | `SmartSpectraSdk.shared` & Lifecycle | Initialized via `SmartSpectraSdk.initialize(context, config)` |
| [Headless Mode](https://smartspectra.presagetech.com/docs/android/headless-mode/) | `useCustomInput()` & `sendFrame()` | Ingests CameraX `ImageProxy` frames directly via `CustomInput.sendFrame(imageProxy)` without camera lock contention |
| [Metrics](https://smartspectra.presagetech.com/docs/android/metrics/) | `cardioMetrics` & `breathingMetrics` | Configures pulse rate (BPM), HRV (ms), arterial pressure trace waveform, and respiratory rate |
| [LLM Insights](https://smartspectra.presagetech.com/docs/android/llm-insights/) | `sdk.insight` LiveData | Streams natural-language physiological summaries into ADK agent context for multi-agent reasoning |
| [Option 2: OAuth](https://smartspectra.presagetech.com/docs/android/option-2-oauth/) | Production Auth Configuration | Configured via `app/src/main/res/xml/presage_oauth_config.xml` eliminating static keys in production |
| [Headless Testing in CI](https://smartspectra.presagetech.com/docs/android/headless-testing-in-ci/) | `@OptIn(SmartSpectraTestingApi::class)` | Automated headless verification using pre-recorded video frames |
| [Migration Guide](https://smartspectra.presagetech.com/docs/android/migration-guide/) | v3.4.0+ Architectural Standards | Implements current module naming and frame submission contracts |

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
│   │   │   │   │   │   ├── AdkAgentArchitecture.kt# LlmAgent, InMemoryRunner, Google ADC & Firebase AI Logic
│   │   │   │   │   │   └── AdkAnnotations.kt      # @Tool and @Param annotations
│   │   │   │   │   ├── FitnessCoachAgent.kt       # ADK LlmAgent orchestration
│   │   │   │   │   └── FitnessTools.kt            # @Tool functions (Plan, Analysis, Adaptive Rest, Fatigue)
│   │   │   │   ├── audio/
│   │   │   │   │   └── FitLensAudioCoach.kt       # Android TTS hands-free vocal coaching
│   │   │   │   ├── camera/
│   │   │   │   │   ├── CameraPreviewView.kt       # Lifecycle-aware CameraX surface
│   │   │   │   │   ├── DualStreamAnalyzer.kt      # Concurrent MediaPipe & Presage frame dispatcher
│   │   │   │   │   └── SkeletonOverlay.kt         # Jetpack Compose 33-point skeletal overlay
│   │   │   │   ├── data/
│   │   │   │   │   ├── dao/WorkoutDao.kt          # Local SQLite DAO (Sessions, Vitals, Faults)
│   │   │   │   │   ├── model/WorkoutEntities.kt   # Local SQLite Tables (Zero cloud health sync)
│   │   │   │   │   ├── sync/                      # Local Health Data Privacy Enforcer
│   │   │   │   │   ├── LocalDataExportManager.kt  # On-device JSON export (GDPR data portability)
│   │   │   │   │   ├── WorkoutDatabase.kt         # Local SQLite DB (fitlens_health_sqlite.db)
│   │   │   │   │   └── WorkoutRepository.kt       # On-device health repository
│   │   │   │   ├── pose/
│   │   │   │   │   └── PoseLandmarkManager.kt     # Angles, rep state machine & valgus detection
│   │   │   │   ├── profile/
│   │   │   │   │   └── UserProfile.kt             # DataStore & Karvonen Heart Rate Reserve zones
│   │   │   │   ├── testing/
│   │   │   │   │   └── HeadlessVideoFeeder.kt     # Automated Day 1 Hard Gate video feed runner
│   │   │   │   ├── ui/
│   │   │   │   │   ├── WorkoutAnalyticsScreen.kt  # Biomechanical stability & vitals trends
│   │   │   │   │   ├── WorkoutSessionScreen.kt    # Live workout HUD & feedback
│   │   │   │   │   ├── WorkoutSummaryScreen.kt    # Post-session insights & metrics
│   │   │   │   │   └── WorkoutViewModel.kt        # MVI/MVVM Session coordinator
│   │   │   │   ├── vitals/
│   │   │   │   │   ├── PresageFrameAnalyzer.kt    # CameraX ImageAnalysis + rPPG HR zones
│   │   │   │   │   └── PresageManager.kt          # BuildConfig Presage key resolution
│   │   │   │   ├── workout/
│   │   │   │   │   ├── ProgressAnalyzer.kt        # Progressive overload calculator
│   │   │   │   │   └── WorkoutManager.kt          # Active workout state machine
│   │   │   │   ├── FitLensApp.kt                  # App Check (Play Integrity / Debug) init
│   │   │   │   └── MainActivity.kt                # Multi-screen Jetpack Compose host
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

The CD workflow builds the release APK and AAB, stores them as workflow artifacts,
and attaches them to a generated GitHub Release. Configure the `PRESAGE_API_KEY`
and `GOOGLE_SERVICES_JSON` repository secrets for production builds; CI uses
local fallbacks only when those secrets are not present.
