*This is a submission for the [Presage SmartSpectra SDK Challenge](https://dev.to/challenges/presage-2026-11-04)*

## What I Built

**FitLens** is a contactless, real-time Android fitness and biomechanics coach that turns an ordinary phone camera into a physiological and movement-aware training partner—no smartwatch, chest strap, or cloud health account required.

Standard workout apps operate on blind, static timers: they count down a fixed 45-second rest whether your heart is still pounding at 172 BPM or calm at 98 BPM, and they keep counting reps even when fatigue causes your knees to cave inward. **FitLens closes the loop between external biomechanics and internal human physiology** by running **Presage SmartSpectra** and **MediaPipe 3D Pose Landmarker** concurrently on a single shared CameraX stream:

- **Vitals-Guided Workouts**: Counts dynamic reps (**Squats**, **Push-Ups**, **Lunges**) and timed isometric holds (**Plank**) with real-time joint-angle wireframes and hands-free TTS voice coaching.
- **Physiology-Driven Recovery**: Automatically extends rest intervals when pulse recovery lags, launches an **Animated Coherent Breathing Pacer** synced to your live respiratory rate, tracks **HRV (RMSSD)** readiness, and even coaches you to unclench your jaw when SmartSpectra detects **facial strain** mid-set.
- **AI Periodization with Zero Client Keys & 100% Local Vault**: Generates tailored multi-exercise routines and 4-week mesocycles via an ADK Kotlin coach (with a 100% offline On-Device Edge mode), while keeping all biometric telemetry strictly on-device in local SQLite.

## Demo

> 🎬 **[Insert Demo Video Link / YouTube or Loom Embed Here]**
>
> *Demo walkthrough highlights:*
> 1. **Consent-First Activation**: Reviewing the in-app Camera & Biometric Sensing Consent gate (camera stays completely off prior to consent).
> 2. **Live Vitals Dashboard**: Contactless SmartSpectra extraction of **Pulse BPM** (mapped to personalized Karvonen HR zones), **Breathing Rate (brpm)**, **HRV RMSSD (ms)**, **Facial Expression**, real-time **Arterial Pressure & Chest/Abdomen Waveforms**, framing validation hints, and on-demand **SmartSpectra Insights**.
> 3. **ADK AI Routine & Live Dual-Stream Workout HUD**: Concurrent 33-point skeletal tracking + SmartSpectra rPPG without camera hardware contention, biomechanical valgus/sag intervention, **+30s Adaptive Vitals Rest Extension**, and the **4s-Inhale / 6s-Exhale Coherent Breathing Pacer** driven by live breathing rate.
> 4. **Local Health Vault**: SQLite biomechanical telemetry breakdown, local JSON data export, and one-tap **Delete All Local Data**.

## Code

- **Public GitHub Repository**: [https://github.com/Tech-Vexy/FitLens](https://github.com/Tech-Vexy/FitLens)
- Full setup, API key configuration (`secrets.properties`), architecture verification (`./scripts/verify_fitlens.sh`), and emulator/device testing instructions are included in [`README.md`](https://github.com/Tech-Vexy/FitLens/blob/main/README.md) (no login or test credentials required).

## How It Responds to Human Signals

### Dual-Stream Headless Integration Without Camera Lock Contention
Running two computer-vision SDKs on Android usually crashes with `CameraDevice` contention because both try to open the camera. FitLens solves this via [`DualStreamAnalyzer.kt`](https://github.com/Tech-Vexy/FitLens/blob/main/app/src/main/kotlin/com/fitlens/app/camera/DualStreamAnalyzer.kt) and [`PresageFrameAnalyzer.kt`](https://github.com/Tech-Vexy/FitLens/blob/main/app/src/main/kotlin/com/fitlens/app/vitals/PresageFrameAnalyzer.kt):
1. `PresageManager` configures `SmartSpectraSdk.shared.config` with `SmartSpectraConfig.cardioMetrics`, `SmartSpectraConfig.breathingMetrics`, and `MetricType.EXPRESSIONS` (`imageOutputEnabled = false` since CameraX renders `PreviewView` directly).
2. On each CameraX `YUV_420_888` `ImageProxy`, MediaPipe Pose (`RunningMode.VIDEO` with upright sensor-rotation/mirroring normalization and EMA smoothing) reads duplicated read-only plane buffers first, and then `PresageFrameAnalyzer` submits the untouched `ImageProxy` to a session-cached `SmartSpectraSdk.shared.useCustomInput()` handle (`input.sendFrame(imageProxy)`), closing the proxy in `finally` per the SDK contract.
3. `WorkoutViewModel` observes `sdk.metrics`, `sdk.validationStatus`, `sdk.insight`, and `sdk.error` LiveData streams reactively.

### Real-Time Multi-Signal Biofeedback Loops
FitLens responds simultaneously to **four live Presage signals** plus validation & insight streams:
1. **Pulse Rate & Arterial Pressure Trace (`cardio`)**:
   - Classifies effort into 5 personalized **Karvonen Heart Rate Reserve** zones (`Resting` → `Max Threshold`) and renders the relative arterial pulse waveform live in the HUD.
   - **Adaptive Rest Extension**: When a set ends, `calculateAdaptiveRest` compares live BPM against the user's resting HR—automatically extending recovery by `+15s` or `+30s` (with TTS voice announcement) when cardiovascular recovery is still elevated, and tracking **HR Recovery Drop** during post-workout cooldown.
2. **Breathing Rate & Chest/Abdomen Traces (`breathing`)**:
   - Whenever post-set respiratory cadence climbs (`≥ 21 brpm`), `WorkoutSessionScreen` activates an **Animated Coherent Recovery Breathing Pacer** (`4s Inhale / 6s Exhale` pulsing ring) to guide slower nasal exhalations alongside live `brpm` feedback.
3. **HRV RMSSD (`cardio`)**:
   - Computes real-time autonomic load (`High Readiness`, `Balanced Load`, `High Exertion Load`) displayed in the workout HUD and factored into post-session progressive overload readiness.
4. **Facial Expressions (`face` / `MetricType.EXPRESSIONS`)**:
   - `evaluateHolisticBiofeedback` pairs live facial expression detections with exertion load: when tension/grimace expressions (`Angry`, `Fear`, `Disgust`, `Sad`) appear under load, the coach prompts the athlete to *"unclench your jaw, relax your brow, and exhale smoothly"*—and celebrates relaxed composure when `Happy` is detected!
5. **Validation Hints & LLM Insights**:
   - Surfaces `sdk.validationStatus` hints (lighting, face centering, stability) directly on-screen and supports one-tap wellness summaries via `sdk.requestInsight(...)`.

## Prize Categories

Entering **both** prize categories (in addition to **Best Overall Human-Sensing Experience**):

### 1. Best Thoughtful Real-World Use
- **The Real-World Need**: Solo home and gym exercisers lack objective feedback on *internal recovery readiness* and *joint-safe form*. Fixed countdown timers either rush athletes into their next set before cardiovascular recovery (leading to form breakdown and knee/lumbar strain) or waste time. Meanwhile, many people avoid camera-based fitness apps because they fear video frames or intimate biometric vitals being uploaded to cloud servers.
- **Who It's For & Usability Design**: Built for everyday athletes training with a propped-up phone—featuring hands-free Android TTS voice cues (rep counts, timed Plank milestones, urgent form warnings, adaptive rest announcements), front/rear camera switching, live SmartSpectra framing guidance, personalized Karvonen HRR sliders, and full offline operation.

### 2. Most Creative or Delightful Use
- **What Makes It Original & Playful**: Pairing **facial expression biofeedback** ("jaw/brow strain release coaching" vs. "smiling through the burn!") with a **live respiratory-driven Coherent Breathing Pacer ring** and **3D glowing skeletal wireframe** creates an unexpectedly empathetic, reactive mirror—your coach notices when you're holding your breath or gritting your teeth mid-squat and responds in the moment.

## Consent and Data Handling

FitLens is architected from the ground up around explicit consent, data minimization, and non-medical wellness boundaries:

- **Explicit Camera & Sensing Consent**: Neither `CameraPreviewView` nor `SmartSpectraSdk.shared.start()` activates on launch. Users are first presented with an explicit **Camera & Biometric Sensing Consent** dialog (`SensingConsentDialog` in [`ConsentAndPrivacySheet.kt`](https://github.com/Tech-Vexy/FitLens/blob/main/app/src/main/kotlin/com/fitlens/app/ui/ConsentAndPrivacySheet.kt)) explaining what signals are measured and how data is handled, paired with Android's runtime `CAMERA` permission. Users can revoke consent at any time in **Profile, Zones & Privacy**, immediately unbinding the camera. There is zero background or covert monitoring.
- **Video Frames (Zero Storage / Zero Upload)**: Camera frames exist strictly in volatile device RAM during real-time on-device SmartSpectra and MediaPipe inference and are closed immediately after each frame. Raw video or images are never saved to disk or transmitted externally.
- **Local Storage, Retention, Export & Deletion**:
  - Completed workout summaries, sampled vitals logs (BPM, HRV, breathing rate, HR zone, confidence), and biomechanical fault episodes are stored **100% locally on-device** in an Android Room SQLite database (`fitlens_health_sqlite.db`).
  - **Sharing**: Zero health or physiological data is shared with any cloud backend, analytics SDK, or third party.
  - **Retention & Deletion**: Records remain on-device solely until deleted by the user or uninstalling the app. Users can export all records to a local JSON file (`LocalDataExportManager`) or permanently erase every session, vitals log, and fault record in one tap via **Delete All Local Vault Data** (available in both **Profile & Privacy** and **Local Health Vault**).
- **Presage SmartSpectra SDK Gating & Telemetry**:
  - All vital-sign computation executes locally on-device inside the SmartSpectra SDK. Network access is used by the SDK to authenticate the developer API key (`BuildConfig.PRESAGE_API_KEY`, injected via Secrets Gradle Plugin from git-ignored `secrets.properties`) and validate subscription gating.
  - By default, SmartSpectra can send a small, aggregate per-session diagnostic summary for SDK release stability (containing **no raw video frames, no vital sign values, and no user or device identifiers**). FitLens discloses this upfront in the Consent dialog and exposes a live opt-out toggle wired directly to `SmartSpectraSdk.shared.config.enableTelemetry`.
  - When the user explicitly taps the optional **Insight** chip (`sdk.requestInsight(...)`), SmartSpectra's insight service generates a brief wellness summary of current session metrics.
- **Optional AI Service Disclosure**:
  - Physiological vitals and biomechanical telemetry are **never** sent to cloud LLMs; adaptive rest, fatigue halting, holistic biofeedback, and post-session evaluation always run 100% locally on-device.
  - Optional pre-workout routine & 4-week mesocycle generation can call **Firebase AI Logic** (authenticated via Firebase App Check with zero embedded API keys), receiving *only* non-biometric workout preferences (`goal`, `experience`, `equipment`, `durationMinutes`), or can be switched to **100% Offline On-Device Edge Mode** (`LocalEdgeEngine`) right from the Consent dialog or Profile settings.
- **Non-Medical Wellness Boundary**: FitLens includes prominent in-app and documentation disclaimers stating that all metrics (including relative arterial pressure waveform shape, pulse, breathing rate, HRV, and form cues) are for general fitness and wellness coaching only—never for diagnosis, treatment, or medical decision-making.

## Agent Session

<!-- Optional: Upload curated session transcript at https://dev.to/agent_sessions/new (verify zero API keys/secrets in transcript, click "Make Public", and paste the embed tag below). -->
