# FitLens — Presage SmartSpectra SDK Challenge Submission & Demo Guide

FitLens is tailored to compete for **Best Overall Human-Sensing Experience**, **Best Thoughtful Real-World Use**, and **Most Creative or Delightful Use** in the **Presage SmartSpectra SDK Challenge (`#presagechallenge`)**.

- Ready-to-publish DEV post (using the official challenge template): [**`docs/DEV_SUBMISSION_POST.md`**](docs/DEV_SUBMISSION_POST.md)

---

## 🎥 2–3 Minute Judge Demo Video Script

Record on a physical Android phone in good front-facing light so judges clearly see both your live SmartSpectra physiological signals and the aligned 3D MediaPipe skeleton!

| Timestamp | Action on Screen | What Judges See & Hear |
| :---: | :--- | :--- |
| **0:00 – 0:20** | **Open FitLens** (Cold Launch) | Camera is **off** behind the **Consent-First Human Sensing** backdrop. Tap **"Review Consent & Privacy"** to show the explicit consent modal disclosing ephemeral RAM frames, 100% local SQLite storage, SmartSpectra aggregate telemetry opt-out (`enableTelemetry`), offline Edge AI toggle, and the non-medical wellness disclaimer. Tap **"Agree & Enable Sensing"**. |
| **0:20 – 0:55** | **Live Vitals Dashboard** (`Start Measurement`) | Tap **"Start Measurement"** and face the camera. Within seconds, judges see live **Pulse BPM + Karvonen HR Zone**, **Breathing Rate (`brpm`)**, **HRV RMSSD (`ms`)**, **Facial Expression**, and three real-time waveforms (**Arterial Pressure**, **Chest**, **Abdomen**), plus live `validationStatus` framing hints. Tap **"Insight"** in the top bar to show a live SmartSpectra wellness summary. |
| **0:55 – 1:15** | **Profile, Zones & Smoke Test** | Tap the **Profile/Privacy icon** (top-right). Adjust the Age / Resting HR slider to show personalized **Karvonen 5-Zone BPM thresholds** updating live, show the **Telemetry** & **100% On-Device Edge AI** switches, and tap **"Run Day 1 Hard Gate Smoke Test"** for instant green pipeline verification. |
| **1:15 – 1:40** | **ADK AI Coach Routine Generator** | Tap **"Start Workout"** → switch to **"ADK AI Coach Plan"**. Select `Mobility & Core` + `Bodyweight` and tap **"Generate Plan & 4-Week Mesocycle"**. Show the generated multi-exercise circuit and 4-week RPE periodization phases, or pick **Quick Exercise** (`Bodyweight Squats` or timed `Plank`) and start! |
| **1:40 – 2:20** | **Live Dual-Stream Workout HUD** | Perform 2–3 real squats (or tap `Simulate Rep` / `Simulate Valgus` / `Sim High HR` if demonstrating fault overrides):<br>• **3D Neon Skeleton** tracks upright & mirrored in `RunningMode.VIDEO` without camera contention.<br>• **4-Signal Biofeedback Card** updates live Pulse, Breath `brpm`, HRV `ms`, Expression, and pulse waveform.<br>• **Biomechanical Safety**: Cave knees inward—skeleton joints glow red and TTS audio warns *"Knees caving inward (valgus)!"*.<br>• **Vitals-Guided Rest + Breathing Pacer**: Complete the set at elevated HR—rest auto-extends (`+30s`) and the **Animated 4s-In / 6s-Out Coherent Breathing Pacer Ring** pulses to guide respiratory recovery! |
| **2:20 – 2:45** | **Summary & Local SQLite Vault** | Finish into Cooldown/Summary, then open **Local Vault & Stats** from the dashboard. Show the SQLite-computed **Biomechanical Telemetry Breakdown**, tap the **Download icon** to export local JSON, and point out the **Trash icon** for one-tap permanent local data erasure. |

---

## ✅ Pre-Submission Checklist (`#presagechallenge` Rules)

- [x] **Functional SmartSpectra SDK integration** (`com.presagetech:smartspectra:3.4.0` via `useCustomInput()`) responding in real time to **Pulse**, **Breathing Rate**, **HRV RMSSD**, and **Facial Expressions**, plus `validationStatus` and `sdk.insight`.
- [x] **Consent & Data Handling complete** both in-app ([`ConsentAndPrivacySheet.kt`](app/src/main/kotlin/com/fitlens/app/ui/ConsentAndPrivacySheet.kt)) and in the DEV post ([`docs/DEV_SUBMISSION_POST.md`](docs/DEV_SUBMISSION_POST.md)).
- [x] **Strict non-medical wellness framing**—zero diagnosis, treatment, or clinical decision-making claims; zero covert or pre-consent monitoring.
- [x] **No secrets in Git or transcript**—`secrets.properties` is git-ignored; zero `GEMINI_API_KEY` strings in source; verify any uploaded Agent Session transcript contains no keys and is set to **Public** on DEV.
- [x] **Include tag `#presagechallenge`** when publishing [`docs/DEV_SUBMISSION_POST.md`](docs/DEV_SUBMISSION_POST.md) before **November 22, 2026 at 11:59 PM PST**.
