# FitLens — Verification Runbook

This runbook verifies the current FitLens pipeline: consent-gated CameraX capture, Presage SmartSpectra custom-input vitals, workout coaching, local persistence, secrets handling, and optional Firebase AI boundaries.

Suggested DEV title for the challenge post:

**FitLens: The AI-Powered Fitness App You Can Use Without Wearables**

---

## ✅ Automated Checks

Run from the repository root:

```bash
./scripts/verify_fitlens.sh
./gradlew testDebugUnitTest
./gradlew installDebug
```

`verify_fitlens.sh` checks that:

- `secrets.properties` is not tracked by Git.
- No raw `GEMINI_API_KEY` string exists in app source.
- `local.defaults.properties` provides the build fallback.
- The local SQLite health database is configured.
- Unit tests pass.

---

## 📷 1. Consent & Camera Gate

1. Launch FitLens on a physical Android device.
2. Confirm the camera preview stays off and SmartSpectra does not process frames before consent.
3. Tap **Sense** or **Enable**, review **Camera & Vitals Consent**, and accept.
4. Grant Android camera permission when prompted.
5. Confirm sensing can be stopped from the bottom **Stop** control and that revoking **Camera Sensing Consent** in Profile & Settings stops the session.

Expected: no camera frames are processed before consent + permission, and no background monitoring continues after the app is paused or consent is revoked.

---

## 💓 2. Live SmartSpectra Vitals

1. Open the **Vitals** tab and keep the face centered in steady, diffuse light.
2. Confirm live values appear for:
   - Heart rate (`BPM`)
   - Breathing rate (`BRPM`)
   - HRV (`RMSSD`, `ms`)
   - Facial expression / readiness cue
   - Signal confidence
   - PPG and respiratory waveform traces
   - SmartSpectra `validationStatus` framing hints
3. Optionally open **Coach** to view SmartSpectra insight/mood output.

Notes:

- SmartSpectra metric computation runs on-device, but the SDK needs network access for API-key/subscription gating.
- SmartSpectra LLM Insights send buffered numeric vitals plus prompt/request metadata to Presage’s Analytics Gateway; raw video and facial imagery are not sent for insights.
- Live camera verification is required for the challenge demo; emulator simulation is only for development/testing.

---

## 🏋️ 3. Workout Coaching Flow

1. On the **Train** tab, open **Workout settings** and choose a goal, level, and duration.
2. Tap **Update Routine**, then either:
   - Tap a single exercise card for a short session, or
   - Tap **Start** for the full generated routine.
3. During an active set:
   - Use **+1 Rep** for rep-based exercises or let a timed hold such as **Plank** progress automatically.
   - Confirm TTS/audio cues and the coaching banner reflect live vitals where available.
4. At the end of a set, confirm the rest/cooldown stage shows:
   - Countdown
   - Recovery breathing pacer (**4s inhale / 6s exhale**)
   - Adaptive-rest messaging when heart rate remains elevated beyond the personalized resting-HR delta thresholds (**+15s / +30s**)
   - HRV/readiness context
5. Finish or stop the workout and confirm the **Workout Complete** summary shows duration, reps, average/peak HR, HR recovery, adaptive rest added, exertion score, and coach feedback.

---

## 🧪 4. Pipeline Smoke Test

1. Open the profile icon → **Profile & Settings**.
2. Tap **Run Pipeline Smoke Test**.
3. Confirm the result reports the SmartSpectra gate as passed.

This smoke test exercises the app-side vitals/simulation path for CI and emulator checks. It is not a substitute for live camera verification with a real Presage signal.

---

## 🔐 5. Privacy & Data Handling Checks

- Confirm raw camera frames are processed in memory and are not written to disk as video/images by FitLens.
- Confirm completed workout summaries persist locally in `fitlens_health_sqlite.db`.
- In **Profile & Settings**, confirm **Clear Local Data** removes stored local health records.
- Confirm **SDK Diagnostic Telemetry** can be disabled via the `enableTelemetry` preference.
- If testing optional cloud AI:
  - Routine generation sends only non-biometric preferences such as goal, experience, equipment, and duration.
  - **Offline Edge AI Mode** keeps coach text/routine responses local and disables the cloud Live Voice path.
  - Optional **Live Voice** may send microphone audio, compressed low-resolution JPEG frames, and disclosed wellness context to Firebase AI Live.
- Confirm the app includes the non-medical wellness disclaimer and does not claim diagnosis or treatment.

---

## 🔑 6. Secrets & Optional Firebase

- Keep `secrets.properties` git-ignored and never commit `PRESAGE_API_KEY`.
- If `secrets.properties` is absent, the build falls back to `local.defaults.properties` for smoke-test development.
- `app/google-services.json` is optional. Without it or when Firebase AI Logic is unavailable, `FitnessCoachAgent` falls back to the local edge path.
- SmartSpectra still requires network access for API-key/subscription gating even when Firebase AI is unavailable.
