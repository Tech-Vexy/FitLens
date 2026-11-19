# FitLens — Presage SmartSpectra SDK Challenge Submission & Demo Guide

Suggested DEV title:

**FitLens: The AI-Powered Fitness App You Can Use Without Wearables**

FitLens is tailored to compete for **Best Overall Human-Sensing Experience**, **Best Thoughtful Real-World Use**, and **Most Creative or Delightful Use** in the **Presage SmartSpectra SDK Challenge (`#presagechallenge`)**.

- Ready-to-publish DEV post (using the official challenge template): [**`docs/DEV_SUBMISSION_POST.md`**](docs/DEV_SUBMISSION_POST.md)

---

## 🎥 2–3 Minute Judge Demo Video Script

Record on a physical Android phone in good front-facing light so judges clearly see live SmartSpectra physiological signals and the workout coach responding in real time.

| Timestamp | Action on Screen | What Judges See & Hear |
| :---: | :--- | :--- |
| **0:00 – 0:20** | **Open FitLens** | The camera stays off behind the sensing consent flow. Tap **Sense** (bottom bar) or **Enable** on the permission card, review the consent dialog covering camera/vitals consent, SDK telemetry, Offline Edge AI, and the non-medical wellness boundary, then tap **Enable Sensing** and grant Android camera permission. |
| **0:20 – 0:55** | **Live Vitals Dashboard** | Open the **Vitals** tab and keep the face centered in good light. Show live **Heart Rate BPM**, **Breathing BRPM**, **HRV RMSSD**, readiness/mood cue, facial expression, signal confidence, PPG + respiratory waveform traces, and SmartSpectra `validationStatus` framing hints. Optionally open **Coach** to show a SmartSpectra insight/mood readout. |
| **0:55 – 1:15** | **Profile & Privacy** | Tap the profile icon in the top bar. Show personalized age/resting-HR settings that drive the Karvonen zone labels shown in Vitals, the camera-consent toggle, **SDK Diagnostic Telemetry** opt-out, **Offline Edge AI Mode**, **Run Pipeline Smoke Test**, and **Clear Local Data**. |
| **1:15 – 1:40** | **Start a Workout** | On the **Train** tab, use **Workout settings** to choose a goal, level, and duration, tap **Update Routine**, then either tap a single exercise card (for a short demo) or **Start** for the full routine. A timed **Plank** hold or manual **+1 Rep** logging both work well on camera. |
| **1:40 – 2:20** | **Live Workout HUD** | Show the active workout stage: exercise illustration, rep/hold progress, readiness label, live HR/BR/HRV strip, TTS cues, and the AI coaching banner reacting to SmartSpectra vitals. When the set ends, show the rest/cooldown recovery stage with countdown, adaptive-rest messaging, and the **4s inhale / 6s exhale** breathing pacer. If heart rate remains elevated beyond the personalized resting-HR delta thresholds, point out the **+15s/+30s** adaptive rest extension. |
| **2:20 – 2:45** | **Workout Summary & Privacy Close-Out** | Finish or stop the workout into the **Workout Complete** summary: duration, reps, average/peak heart rate, HR recovery, adaptive rest added, exertion score, and coach recommendations. Then show **Recent Sessions** in Vitals/Train and briefly revisit Profile & Privacy to reinforce local storage and one-tap deletion. |

> **Demo tip:** Use a real device and real camera for the SmartSpectra segments. The Vitals tab also has clearly labeled exertion presets for emulator/CI testing; do not present those presets as live SmartSpectra measurements.

---

## ✅ Pre-Submission Checklist (`#presagechallenge` Rules)

- [x] **Functional SmartSpectra SDK integration** (`com.presagetech:smartspectra:3.4.0` via `useCustomInput()`) responding in real time to **Pulse**, **Breathing Rate**, **HRV RMSSD**, and **Facial Expressions**, plus `validationStatus` and `sdk.insight`.
- [x] **Consent & Data Handling complete** both in-app ([`ConsentAndPrivacySheet.kt`](app/src/main/kotlin/com/fitlens/app/ui/ConsentAndPrivacySheet.kt)), in [`README.md`](README.md), and in the DEV post ([`docs/DEV_SUBMISSION_POST.md`](docs/DEV_SUBMISSION_POST.md)).
- [x] **Strict non-medical wellness framing**—zero diagnosis, treatment, or clinical decision-making claims; zero covert or pre-consent monitoring.
- [x] **Demo uses live camera signals** on a physical Android device for the SmartSpectra portion; simulation controls are clearly separated for testing.
- [x] **No secrets in Git or transcript**—`secrets.properties` is git-ignored; verify any uploaded Agent Session transcript contains no keys and is set to **Public** on DEV.
- [x] **Include tag `#presagechallenge`** when publishing [`docs/DEV_SUBMISSION_POST.md`](docs/DEV_SUBMISSION_POST.md) before **November 22, 2026 at 11:59 PM PST**.
