*This is a submission for the [Presage SmartSpectra SDK Challenge](https://dev.to/challenges/presage-2026-11-04)*

## What I Built

**FitLens** is a consent-first Android fitness companion that turns a phone camera into a contactless wellness coach—no smartwatch, chest strap, or wearable required.

Instead of using a fixed workout timer that ignores how your body is responding, FitLens runs **Presage SmartSpectra** on the live camera stream to measure **pulse rate**, **HRV (RMSSD)**, **breathing rate**, **relative arterial-pressure waveform**, **chest/abdomen traces**, **facial expressions**, and SDK **validation/insight** signals. Those live signals drive a workout HUD with personalized Karvonen heart-rate zones, adaptive rest intervals, recovery-breathing guidance, readiness labels, and optional AI coaching.

FitLens supports quick exercises and multi-exercise routines, timed isometric holds such as Plank, manual rep logging, Android TTS cues, front/rear camera switching, and local workout history. SmartSpectra metric computation stays on-device; SmartSpectra Insights, optional Firebase AI features, and local data handling are disclosed below.

## Demo

> 🎬 **Demo video:** [Paste your public demo video link or embed here]
>
> Suggested 2–3 minute walkthrough:
> 1. **Consent-first launch** — show that the camera remains off until the user reviews the sensing/privacy consent and grants Android camera permission.
> 2. **Live SmartSpectra vitals** — face the camera and show pulse BPM, breathing rate, HRV RMSSD, expression, PPG/respiratory waveforms, confidence, and validation hints updating in real time.
> 3. **Adaptive workout** — start a workout, show live vitals feeding the coaching banner, extended rest when heart rate remains elevated, the recovery breathing pacer, and readiness/HRV cues.
> 4. **Privacy close-out** — show local recent sessions and the Profile & Privacy controls for consent, SDK telemetry, offline edge mode, and deleting local health data.

## Code

- **Public repository:** [https://github.com/Tech-Vexy/FitLens](https://github.com/Tech-Vexy/FitLens)
- **Setup:** see [`README.md`](https://github.com/Tech-Vexy/FitLens/blob/main/README.md). The project builds without a Presage key for smoke tests, but live vitals require a developer key in a git-ignored `secrets.properties` file:

  ```properties
  PRESAGE_API_KEY=your_presage_smartspectra_api_key
  ```

- **Verification:** `./scripts/verify_fitlens.sh` runs the local architecture/unit checks, `./gradlew testDebugUnitTest` runs the JVM test suite, and `./gradlew installDebug` builds the Android app.
- **CI:** GitHub Actions (`android-ci`) checks that secrets are not committed, verifies the architecture invariants, compiles Kotlin, runs lint/unit tests, builds signed release APKs, and verifies critical classes survive R8 minification.
- **Honest metric handling:** FitLens stores unavailable heart-rate/form values as explicit unavailable sentinels rather than fabricating defaults, and simulated vitals are excluded from persisted workout HR.

## How It Responds to Human Signals

### SmartSpectra integration

FitLens uses one CameraX pipeline for both preview and analysis:

1. [`PresageManager`](https://github.com/Tech-Vexy/FitLens/blob/main/app/src/main/kotlin/com/fitlens/app/vitals/PresageManager.kt) configures `SmartSpectraSdk.shared` with the API key, `imageOutputEnabled = false`, the user’s telemetry preference, and `SmartSpectraConfig.cardioMetrics + SmartSpectraConfig.breathingMetrics + listOf(MetricType.EXPRESSIONS)`.
2. [`PresageFrameAnalyzer`](https://github.com/Tech-Vexy/FitLens/blob/main/app/src/main/kotlin/com/fitlens/app/vitals/PresageFrameAnalyzer.kt) obtains a session-scoped `SmartSpectraSdk.shared.useCustomInput()` handle and submits CameraX `ImageProxy` frames through `input.sendFrame(...)`.
3. [`DualStreamAnalyzer`](https://github.com/Tech-Vexy/FitLens/blob/main/app/src/main/kotlin/com/fitlens/app/camera/DualStreamAnalyzer.kt) submits each frame to SmartSpectra first, then optionally reuses the same frame for the separate Gemini Live path without preventing CameraX from closing the proxy.
4. [`WorkoutViewModel`](https://github.com/Tech-Vexy/FitLens/blob/main/app/src/main/kotlin/com/fitlens/app/ui/WorkoutViewModel.kt) observes `sdk.metricsFlow`, `sdk.validationStatus`, `sdk.insight`, and `sdk.error`, then maps the latest samples into the app’s `VitalsReading` state.

### Live signal responses

- **Pulse rate & relative arterial-pressure trace (`cardio`)** — the latest `cardio.pulseRateList` sample drives BPM, confidence, and personalized Karvonen zones. `arterialPressureTraceList` feeds the live pulse waveform. When a set ends, `calculateAdaptiveRest` compares current pulse with the user’s resting HR and can extend rest by **+15s** or **+30s**; cooldown also tracks HR recovery drop.
- **Breathing rate & chest/abdomen traces (`breathing`)** — `breathing.rateList` drives live BRPM, while `upperTraceList` and `lowerTraceList` feed respiratory waveforms. Rest and cooldown show a guided **4s inhale / 6s exhale** recovery pacer alongside the countdown; breathing rate also feeds the holistic coaching prompt.
- **HRV RMSSD (`cardio`)** — `cardio.hrvList` samples are converted into readiness labels such as `High Readiness`, `Balanced Load`, and `High Exertion Load`, shown in the vitals/workout UI and included in local session context.
- **Facial expressions (`MetricType.EXPRESSIONS`)** — the highest-confidence `face.expressionList` result is mapped to SDK labels such as Happy, Neutral, Sad, Angry, Surprise, Fear, Disgust, and Contempt. `evaluateHolisticBiofeedback` turns detected strain-related expressions into cues like relaxing the jaw/brow and exhaling, while positive expressions are treated as composure/effort signals.
- **Validation, errors, and insights** — `sdk.validationStatus` hints are surfaced in the vitals UI so users can improve lighting/framing. `sdk.insight` responses appear in the Coach experience, and `sdk.error` is observed and logged for SDK failures.

The deterministic workout/coach path runs locally through [`FitnessTools`](https://github.com/Tech-Vexy/FitLens/blob/main/app/src/main/kotlin/com/fitlens/app/ai/FitnessTools.kt), so adaptive rest, readiness, breathing cues, and session summaries do not depend on a cloud model. Optional Gemini/Firebase features are described under Consent and Data Handling.

### Architecture decisions

- **One camera pipeline, two consumers:** `DualStreamAnalyzer` submits every CameraX frame to SmartSpectra first, then optionally reuses the same frame for the separate Gemini Live path. FitLens does not run a second capture stack.
- **Headless SmartSpectra input:** `SmartSpectraSdk.shared.useCustomInput()` lets FitLens feed `ImageProxy` frames directly while CameraX owns preview lifecycle and permissions.
- **Local physiological adaptation:** `WorkoutManager` and `FitnessTools` convert live pulse/HRV/breathing/expression signals into adaptive rest, readiness, recovery-breathing, and summary decisions without cloud latency.
- **Optional cloud AI is separated from sensing:** Firebase/Google AI is used only for disclosed coach/plan/live-voice features; the core SmartSpectra → workout adaptation loop remains local.
- **Privacy boundaries are explicit:** SmartSpectra computes vitals on-device; Insights sends only buffered numeric vitals/prompt metadata; optional Live Voice sends disclosed audio/JPEG context; health records stay in local Room/SQLite until deleted.

### Challenges and honest limitations

- **No camera-based exercise recognition:** reps are manual `+1 Rep` taps and Plank is a timed hold. The SmartSpectra contribution is physiological adaptation, not pose tracking.
- **SmartSpectra still needs network for auth/subscription gating:** metric computation is on-device, but the sensing path is not a fully offline wearable replacement.
- **Signal quality depends on lighting/framing/device:** confidence and validation hints are surfaced so users can improve capture, but readings can be intermittent.
- **Optional cloud AI requires Firebase configuration:** `google-services.json` enables cloud plan/live-voice paths; without it, `LocalEdgeEngine` still handles plans/routines locally, but Live Voice is unavailable.
- **Unavailable metrics are represented honestly:** missing HR/form data is shown as unavailable rather than replaced with fabricated averages or 100% form accuracy.

## Prize Categories

FitLens is entering **both** prize categories and remains eligible for **Best Overall Human-Sensing Experience** because it integrates SmartSpectra into a functional app with live camera signals.

### Best Thoughtful Real-World Use

Solo exercisers often train with a propped-up phone but have no objective feedback about recovery between sets. FitLens addresses that gap by turning the same camera into a privacy-conscious wellness coach: it can wait for cardiovascular recovery before the next set, guide breathing during rest, and keep health records on-device rather than requiring a wearable ecosystem or cloud health account.

The experience is designed for real home/gym use: explicit consent before camera sensing, front/rear camera switching, live framing hints, personalized Karvonen zones, Android TTS cues, timed holds, local session history, and one-tap deletion of local health records.

### Most Creative or Delightful Use

FitLens treats the phone camera like an empathetic training partner rather than a dashboard. It combines pulse recovery, breathing cadence, HRV readiness, and facial-expression cues into one live biofeedback loop—so the coach can respond when a user looks tense, is holding their breath, or needs a longer recovery window. The animated recovery-breathing ring and expression-aware coaching prompts make the SmartSpectra signals feel tangible and human.

## Consent and Data Handling

- **Explicit camera consent:** FitLens does not start camera capture or SmartSpectra frame processing until the user accepts the in-app sensing consent and Android grants camera permission. Consent can be revoked in **Profile & Privacy**, which stops sensing and clears current session readings. There is no background monitoring.
- **Camera frames:** CameraX `YUV_420_888` frames are processed in volatile memory by SmartSpectra on-device and are not written to disk as raw video by FitLens.
- **Optional Gemini Live media:** If the user explicitly enables **Live Voice**, FitLens may send microphone audio and compressed, low-resolution JPEG camera frames to Firebase AI Live so the coach can respond to voice and visual context. This is optional, user-initiated, and stops when Live Voice ends.
- **Optional AI coaching context:** Workout plan generation can use Firebase AI Logic with only non-biometric preferences such as goal, experience, equipment, and duration. Coach text/chat features may include current wellness context—such as heart rate, HRV, breathing rate, expression label, name, validation hint, and SmartSpectra insight—when cloud AI is enabled. Users can enable **Offline Edge AI Mode** to keep coach responses local.
- **SmartSpectra Insights:** SmartSpectra LLM Insights send buffered numeric vitals such as pulse, HRV, and breathing rate, plus prompt text and request metadata, to Presage’s Analytics Gateway. FitLens observes the SDK insight stream, periodically calls `requestInsight` while pulse is available, and can request an insight from the mood check-in flow; raw video and facial imagery are not sent for this feature.
- **SDK telemetry:** SmartSpectra’s aggregate diagnostic telemetry preference is exposed in the consent/profile UI through `enableTelemetry`. FitLens describes it as anonymous stability telemetry without video, vitals, or user/device identifiers and lets users disable it.
- **Local storage, retention, and deletion:** Completed workout summaries—duration, reps, average/max heart rate, extended rest, and coach feedback—are stored locally in the app’s Room/SQLite database (`fitlens_health_sqlite.db`). Health records remain on-device until the user deletes them or uninstalls the app. **Clear Local Data** in Profile & Privacy deletes the local health database records; profile preferences are stored separately in DataStore and can be edited.
- **Wellness boundary:** FitLens is for general fitness and wellness coaching only. It does not diagnose, treat, cure, or prevent disease and does not support medical decision-making.

## Agent Session

<!-- Optional: upload a curated transcript at https://dev.to/agent_sessions/new, verify it contains no keys or sensitive data, make it public, and paste the embed here. -->

<!-- Suggested cover image: docs/fitlens_logo.png — upload it in DEV's cover image field. -->
