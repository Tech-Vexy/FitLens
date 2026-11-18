# FitLens — Presage SmartSpectra SDK Challenge Submission & Demo Guide

FitLens is tailored to compete for **Best Overall Human-Sensing Experience**, **Best Thoughtful Real-World Use**, and **Most Creative or Delightful Use** in the **Presage SmartSpectra SDK Challenge (`#presagechallenge`)**.

- Ready-to-publish DEV post (using the official challenge template): [**`docs/DEV_SUBMISSION_POST.md`**](docs/DEV_SUBMISSION_POST.md)

---

## 🎥 2–3 Minute Judge Demo Video Script

Record on a physical Android phone in good front-facing light so judges clearly see your live SmartSpectra physiological signals and the AI coach adapting in real time!

| Timestamp | Action on Screen | What Judges See & Hear |
| :---: | :--- | :--- |
| **0:00 – 0:20** | **Open FitLens** (Cold Launch) | Camera is **off** behind the **Consent-First Human Sensing** backdrop. Tap **"Review Consent & Privacy"** to show the explicit consent modal disclosing ephemeral RAM frames, 100% local SQLite storage, SmartSpectra aggregate telemetry opt-out (`enableTelemetry`), offline Edge AI toggle, and the non-medical wellness disclaimer. Tap **"Agree & Enable Sensing"**. |
| **0:20 – 0:55** | **Live Vitals Dashboard** (`Start Measurement`) | Tap **"Start Measurement"** and face the camera. Within seconds, judges see live **Pulse BPM + Karvonen HR Zone**, **Breathing Rate (`brpm`)**, **HRV RMSSD (`ms`)**, **Facial Expression**, and three real-time waveforms (**Arterial Pressure**, **Chest**, **Abdomen**), plus live `validationStatus` framing hints. Tap **"Insight"** in the top bar to show a live SmartSpectra wellness summary. |
| **0:55 – 1:15** | **Profile, Zones & Smoke Test** | Tap the **Profile/Privacy icon** (top-right). Adjust the Age / Resting HR slider to show personalized **Karvonen 5-Zone BPM thresholds** updating live, show the **Telemetry** & **100% On-Device Edge AI** switches, and tap **"Run Day 1 Hard Gate Smoke Test"** for instant green pipeline verification. |
| **1:15 – 1:40** | **AI Coach Workout Selection** | Tap **"Start Workout"** → switch to **"ADK AI Coach Plan"**. Select `Core & Abs` + `Bodyweight` and tap **"Generate Plan"**. Show the AI-generated multi-exercise circuit tailored to your goal, or pick a **Quick Exercise** (`Bodyweight Squats` or timed `Plank`) and start! |
| **1:40 – 2:20** | **Live Workout HUD with AI Coaching** | Perform 2–3 real squats (or tap `Sim High HR` to trigger AI adaptation):<br>• **4-Signal Biofeedback Card** updates live Pulse, Breath `brpm`, HRV `ms`, Expression, and pulse waveform — all from contactless Presage sensing.<br>• **AI Coach Adapts**: As heart rate rises, the AI coach observes the trend and suggests a recovery interval. TTS voice announces *"Your heart rate is elevated — taking a brief recovery break."*<br>• **Vitals-Guided Rest + Breathing Pacer**: Rest auto-extends (`+30s`) and the **Animated 4s-In / 6s-Out Coherent Breathing Pacer Ring** pulses to guide respiratory recovery! |
| **2:20 – 2:45** | **Workout Summary Screen** | Finish the workout into the **Summary Screen**. Judges see: session duration, exercises completed, average & peak heart rate, breathing rate trend, and AI-generated coaching recommendations (e.g., *"Good effort — your HR stayed in Zone 3–4. Consider adding 2 minutes of cooldown next time."*). Then briefly show the **Recent Sessions** list proving local SQLite persistence. |

---

## ✅ Pre-Submission Checklist (`#presagechallenge` Rules)

- [x] **Functional SmartSpectra SDK integration** (`com.presagetech:smartspectra:3.4.0` via `useCustomInput()`) responding in real time to **Pulse**, **Breathing Rate**, **HRV RMSSD**, and **Facial Expressions**, plus `validationStatus` and `sdk.insight`.
- [x] **Consent & Data Handling complete** both in-app ([`ConsentAndPrivacySheet.kt`](app/src/main/kotlin/com/fitlens/app/ui/ConsentAndPrivacySheet.kt)) and in the DEV post ([`docs/DEV_SUBMISSION_POST.md`](docs/DEV_SUBMISSION_POST.md)).
- [x] **Strict non-medical wellness framing**—zero diagnosis, treatment, or clinical decision-making claims; zero covert or pre-consent monitoring.
- [x] **No secrets in Git or transcript**—`secrets.properties` is git-ignored; zero `GEMINI_API_KEY` strings in source; verify any uploaded Agent Session transcript contains no keys and is set to **Public** on DEV.
- [x] **Include tag `#presagechallenge`** when publishing [`docs/DEV_SUBMISSION_POST.md`](docs/DEV_SUBMISSION_POST.md) before **November 22, 2026 at 11:59 PM PST**.
