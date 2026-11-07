# FitLens — Day 1 Hard Gate Verification Runbook

This runbook guides engineers through verifying all 5 Day 1 Hard Gates on a physical Android device and in headless CI before proceeding with extended feature development.

---

## 🎯 The 5 Day 1 Hard Gates

| Gate # | Component | Objective | Target Outcome |
| :---: | :--- | :--- | :--- |
| **Gate 1** | **Presage SmartSpectra SDK** | Ingest video frames via `useCustomInput()` and extract valid rPPG pulse reading | Valid BPM (> 45) with confidence > 0.65 |
| **Gate 2** | **MediaPipe PoseLandmarker** | Stream camera frames concurrently and extract 33 3D skeletal landmarks | Landmark stream active (all 33 vertices) |
| **Gate 3** | **ADK Kotlin Agent** | Execute minimal `AdkLlmAgent` with `@Tool` functions via `AdkInMemoryRunner` | Plan generated via structured tools |
| **Gate 4** | **Firebase App Check** | Attest app authenticity with Play Integrity / Debug provider without client API keys | Calls pass with App Check; fail when disabled |
| **Gate 5** | **Secrets Injection** | Resolve `BuildConfig.PRESAGE_API_KEY` at compile time with zero secrets in Git history | `secrets.properties` untracked in Git |

---

## 🛠️ Step-by-Step Verification Procedure

### Gate 1 & Gate 2: Presage + MediaPipe Dual Stream

1. **Option A: Automated Headless Smoke Test (On-Device or CI)**:
   - Launch FitLens on your device or emulator.
   - On the main dashboard, tap **"Run Day 1 Hard Gate Automated Verification"**.
   - The [`HeadlessVideoFeeder`](file:///home/veldrine/FitLens/app/src/main/kotlin/com/fitlens/app/testing/HeadlessVideoFeeder.kt) pumps frames through [`DualStreamAnalyzer`](file:///home/veldrine/FitLens/app/src/main/kotlin/com/fitlens/app/camera/DualStreamAnalyzer.kt).
   - Expected Output:
     ```
     ✅ Day 1 Hard Gate VERIFIED: Presage rPPG active (136 BPM, confidence: 0.92) and MediaPipe emitted 33 landmarks.
     ```

2. **Option B: Live Camera Surface Verification**:
   - From Dashboard, tap **"Squats"** or **"Push-ups"**.
   - Tap the **Camera icon** in the top bar to grant camera permission.
   - Stand back so full body is visible in the viewfinder.
   - Verify:
     - The skeleton wireframe renders over the camera preview in green (`#00E676`).
     - Perform a squat: verify the rep counter increments and joint angle updates.
     - Intentionally cave knees inward: verify the wireframe highlights warning red (`#FF5252`) and the alert *"Knees caving inward (valgus)"* triggers.
     - Verify the live heart rate card displays your contact-free pulse reading.

---

### Gate 3: ADK Kotlin Agent Smoke Test

1. On the FitLens Dashboard, scroll to the **ADK Coach AI Generator** card.
2. Select:
   - Target Focus: `Hypertrophy`
   - Equipment: `Dumbbells`
3. Tap **"Generate Plan via ADK Agent"**.
4. Verify:
   - Agent executes via [`AdkInMemoryRunner`](file:///home/veldrine/FitLens/app/src/main/kotlin/com/fitlens/app/ai/adk/AdkAgentArchitecture.kt).
   - Tool `generateWorkoutPlan` returns exercises: *"Barbell Squat, Romanian Deadlift, Overhead Press"*.
   - Target rest interval is populated (e.g. 75s).
   - Zero `GEMINI_API_KEY` was passed or used in the call.

---

### Gate 4: Firebase App Check Attestation Test

1. **Firebase Console Configuration**:
   - Register your app's debug and release SHA-256 fingerprints under **Firebase Console ➔ Project Settings ➔ App Check**.
   - In debug builds, [`FitLensApp`](file:///home/veldrine/FitLens/app/src/main/kotlin/com/fitlens/app/FitLensApp.kt) initializes `DebugAppCheckProviderFactory`.
   - In release builds, `PlayIntegrityAppCheckProviderFactory` attests the app binary with Google Play.
2. **Negative Enforcement Test**:
   - In Firebase Console, temporarily set App Check enforcement to **Enforced**.
   - Run the app without registered debug token: verify cloud calls are rejected with `UNAUTHENTICATED (401/403)`.
   - Register the debug token logged in Logcat (`FirebaseAppCheck`): verify cloud calls immediately succeed.
   - This proves identity attestation protects your quota without a client-side secret.

---

### Gate 5: Secrets Injection & Git Audit

1. **Verify Secret Resolution**:
   - Confirm `secrets.properties` exists in project root:
     ```properties
     PRESAGE_API_KEY=your_actual_key_here
     ```
   - Build the app:
     ```bash
     ./gradlew assembleDebug
     ```
   - Confirm `BuildConfig.PRESAGE_API_KEY` contains the real key and not `PLACEHOLDER_RUN_SETUP`.
2. **Verify Untracked in Git**:
   - Run:
     ```bash
     git status --ignored
     git ls-files secrets.properties
     ```
   - Confirm `secrets.properties` output from `ls-files` is completely empty.

---

## 🔒 Health Data Privacy Verification (Local SQLite)

1. Open Android Studio **App Inspection ➔ Database Inspector**.
2. Select `fitlens_health_sqlite.db`.
3. Complete a 10-rep squat workout and finish the session.
4. Verify:
   - Table `workout_sessions` contains the session row.
   - Table `local_vitals_telemetry` contains time-series rows for each vitals reading during the set and rest interval.
   - Table `local_biomechanical_faults` logs any detected deviations.
5. Inspect Logcat / Network Profiler:
   - Confirm **zero network requests** containing heart rate, HRV, breathing, or joint angle payloads.
   - Health data is 100% on-device.
