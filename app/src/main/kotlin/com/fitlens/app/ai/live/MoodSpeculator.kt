package com.fitlens.app.ai.live

import com.fitlens.app.vitals.VitalsReading
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Real-time emotional & physiological state speculation derived from SmartSpectra
 * facial expressions, heart rate, HRV (RMSSD), breathing cadence, and recent trends.
 *
 * Non-medical: frames observations as empathetic, human-like speculations for the
 * Personal AI Trainer & Wellness Companion.
 */
data class MoodSpeculation(
    val label: String,
    val emoji: String,
    val confidenceNote: String,
    val summary: String,
    val trendNote: String? = null,
    val incongruenceHint: String? = null
)

data class BiometricShiftEvent(
    val description: String,
    val gentlePrompt: String
)

/**
 * Tracks a rolling window of vitals & facial expressions to detect meaningful
 * emotional/physiological shifts (e.g. confirmed expression transition, rising pulse, calming down).
 */
class BiometricTrendTracker(
    private val maxSamples: Int = 20,
    private val minSampleIntervalMs: Long = 1500L,
    private val expressionConfirmationCount: Int = 2
) {
    private val history = ArrayDeque<VitalsReading>(maxSamples)
    private var lastExpression: String? = null
    private var pendingExpression: String? = null
    private var pendingExpressionCount: Int = 0
    private var lastSampledAtMs: Long = 0L
    private var lastShiftNotifiedAtMs: Long = 0L

    @Synchronized
    fun record(reading: VitalsReading): BiometricShiftEvent? {
        if (reading.heartRateBpm <= 35.0 && reading.facialExpression.isNullOrBlank()) {
            return null
        }

        val now = reading.timestampMs
        val prevExpr = lastExpression
        val currExpr = reading.facialExpression?.takeIf { it.isNotBlank() }

        var confirmedExpressionShift = false
        if (currExpr != null) {
            if (lastExpression == null) {
                lastExpression = currExpr
            } else if (currExpr != lastExpression) {
                if (pendingExpression == currExpr) {
                    pendingExpressionCount++
                } else {
                    pendingExpression = currExpr
                    pendingExpressionCount = 1
                }
                if (pendingExpressionCount >= expressionConfirmationCount) {
                    lastExpression = currExpr
                    pendingExpression = null
                    pendingExpressionCount = 0
                    confirmedExpressionShift = true
                }
            } else {
                pendingExpression = null
                pendingExpressionCount = 0
            }
        }

        val recentHrs = history.map { it.heartRateBpm }.filter { it > 35.0 }
        val recentAvgHr = if (recentHrs.size >= 3) recentHrs.average() else null

        if (lastSampledAtMs == 0L || now - lastSampledAtMs >= minSampleIntervalMs) {
            if (history.size >= maxSamples) {
                history.removeFirst()
            }
            history.addLast(reading)
            lastSampledAtMs = now
        }

        if (now - lastShiftNotifiedAtMs < 45_000L) return null

        // 1. Detect notable confirmed facial expression shift
        if (confirmedExpressionShift && prevExpr != null && currExpr != null &&
            currExpr in setOf("Surprise", "Sad", "Angry", "Fear", "Happy")
        ) {
            lastShiftNotifiedAtMs = now
            val hrContext = if (reading.heartRateBpm > 35.0) {
                " (pulse ${reading.heartRateBpm.roundToInt()} bpm)"
            } else ""
            val gentlePrompt = when (currExpr) {
                "Surprise" -> "Did something just catch your attention? You suddenly looked surprised."
                "Sad" -> "You seemed to go a little quiet just now — is everything okay?"
                "Angry" -> "I noticed a flash of frustration there. Want to talk about what's going on?"
                "Fear" -> "You looked a bit startled or uneasy just then. I'm right here if you want to talk it through."
                "Happy" -> "There's a nice smile — what just brightened your mood?"
                else -> "I noticed your expression shift just now. How are you feeling?"
            }
            return BiometricShiftEvent(
                description = "Expression shifted from $prevExpr to $currExpr$hrContext",
                gentlePrompt = gentlePrompt
            )
        }

        // 2. Detect notable heart rate spike over recent rolling average
        if (recentAvgHr != null &&
            reading.heartRateBpm > 35.0 &&
            reading.signalConfidence >= 0.45f &&
            reading.heartRateBpm - recentAvgHr >= 14.0
        ) {
            lastShiftNotifiedAtMs = now
            return BiometricShiftEvent(
                description = "Heart rate jumped +${(reading.heartRateBpm - recentAvgHr).roundToInt()} bpm above recent average (${reading.heartRateBpm.roundToInt()} bpm)",
                gentlePrompt = "Your pulse just picked up noticeably — are you feeling a rush of excitement, stress, or just moving around?"
            )
        }

        return null
    }

    @Synchronized
    fun computeTrendNote(current: VitalsReading): String? {
        val hrs = history.map { it.heartRateBpm }.filter { it > 35.0 }
        if (hrs.size < 4 || current.heartRateBpm <= 35.0) return null
        val earlyAvg = hrs.take(hrs.size / 2).average()
        val delta = current.heartRateBpm - earlyAvg
        return when {
            delta >= 8.0 -> "Pulse rising (+${delta.roundToInt()} bpm)"
            delta <= -8.0 -> "Pulse settling (${delta.roundToInt()} bpm)"
            else -> "Steady rhythm"
        }
    }

    @Synchronized
    fun clear() {
        history.clear()
        lastExpression = null
        pendingExpression = null
        pendingExpressionCount = 0
        lastSampledAtMs = 0L
        lastShiftNotifiedAtMs = 0L
    }
}

object MoodSpeculator {

    fun speculate(
        vitals: VitalsReading,
        restingHeartRate: Double = 65.0,
        trendNote: String? = null
    ): MoodSpeculation {
        val hr = vitals.heartRateBpm
        val br = vitals.breathingRateBpm
        val hrv = vitals.heartRateVariabilityMs
        val expr = vitals.facialExpression?.trim().orEmpty()
        val hasPulse = hr > 35.0
        val hasBreathing = br > 3.0
        val hasHrv = hrv > 0.0

        if (!hasPulse && !hasBreathing && expr.isBlank()) {
            return MoodSpeculation(
                label = "Waiting for signals",
                emoji = "✨",
                confidenceNote = "Tap Start Sensing to read facial cues & vitals",
                summary = "Look toward the camera in steady light so I can read your expression and pulse."
            )
        }

        val hrDelta = if (hasPulse) hr - restingHeartRate else 0.0
        val lowPulseConfidence = hasPulse && vitals.signalConfidence < 0.45f
        val confidenceNote = buildString {
            append(
                when {
                    lowPulseConfidence -> "Preliminary pulse (${(vitals.signalConfidence * 100).roundToInt()}%)"
                    hasPulse -> "Live vitals (${(vitals.signalConfidence * 100).roundToInt()}%)"
                    else -> "Facial & breathing cues"
                }
            )
            if (!trendNote.isNullOrBlank()) {
                append(" • $trendNote")
            }
        }

        val incongruenceHint = when {
            expr.equals("Happy", ignoreCase = true) && hasPulse && hrDelta >= 18.0 && (!hasHrv || hrv < 35.0) ->
                "Smiling on the outside, though your pulse is elevated — could be excitement or masked stress."
            expr.equals("Neutral", ignoreCase = true) && hasPulse && hrDelta >= 20.0 ->
                "Your face looks composed, yet your heart rate is running high beneath the surface."
            else -> null
        }

        val (label, emoji, summary) = when {
            expr.equals("Happy", ignoreCase = true) -> {
                if (incongruenceHint != null) {
                    Triple(
                        "Upbeat or Keyed Up",
                        "😊",
                        "You're smiling, though your pulse is running warm at ${hr.roundToInt()} bpm — excited or pushing through something?"
                    )
                } else {
                    Triple(
                        "Upbeat & Warm",
                        "😊",
                        "You're showing a warm expression" + if (hasPulse) " with a ${hr.roundToInt()} bpm pulse." else "."
                    )
                }
            }
            expr.equals("Surprise", ignoreCase = true) -> {
                if (hrDelta >= 15.0) {
                    Triple(
                        "Alert & Surprised",
                        "😮",
                        "Your surprised expression paired with an elevated ${hr.roundToInt()} bpm pulse suggests something caught you off guard."
                    )
                } else {
                    Triple(
                        "Curious & Engaged",
                        "😮",
                        "You look surprised or intrigued, while your vitals stay relatively steady."
                    )
                }
            }
            expr.equals("Angry", ignoreCase = true) || expr.equals("Contempt", ignoreCase = true) -> Triple(
                "Tense or Frustrated",
                "😤",
                "Your facial tension" + (if (hasPulse) " and ${hr.roundToInt()} bpm heart rate" else "") + " hint you might be feeling frustrated or keyed up."
            )
            expr.equals("Fear", ignoreCase = true) -> Triple(
                "Anxious or On Edge",
                "😟",
                "You appear a bit unsettled or tense" + if (hasBreathing) " with breathing around ${br.roundToInt()} brpm." else "."
            )
            expr.equals("Sad", ignoreCase = true) -> Triple(
                "Low or Reflective",
                "😔",
                "Your expression looks a bit subdued or reflective right now."
            )
            expr.equals("Disgust", ignoreCase = true) -> Triple(
                "Uncomfortable",
                "😒",
                "Something seems to be bothering you or feeling off."
            )
            hasPulse && hrDelta >= 18.0 && (!hasHrv || hrv < 35.0) -> Triple(
                "Activated or Stressed",
                "⚡",
                "Even with a ${expr.ifBlank { "neutral" }.lowercase()} face, your heart rate (${hr.roundToInt()} bpm) is elevated above your ${restingHeartRate.roundToInt()} bpm baseline."
            )
            hasBreathing && br <= 10.0 && (!hasPulse || hrDelta < 12.0) -> Triple(
                "Calm & Grounded",
                "😌",
                "Your slow ${br.roundToInt()} brpm breathing suggests a relaxed, grounded state."
            )
            hasHrv && hrv >= 45.0 -> Triple(
                "Balanced & Steady",
                "🌿",
                "Your ${hrv.roundToInt()} ms HRV and steady rhythm point to good composure and focus."
            )
            else -> Triple(
                if (expr.isNotBlank()) "Present • $expr" else "Present & Steady",
                "🙂",
                "Your signals look fairly steady" + if (hasPulse) " around ${hr.roundToInt()} bpm." else "."
            )
        }

        return MoodSpeculation(
            label = label,
            emoji = emoji,
            confidenceNote = confidenceNote,
            summary = summary,
            trendNote = trendNote,
            incongruenceHint = incongruenceHint
        )
    }

    fun buildBiometricContext(
        vitals: VitalsReading,
        userName: String = "User",
        restingHeartRate: Double = 65.0,
        trendNote: String? = null
    ): String {
        val speculation = speculate(vitals, restingHeartRate, trendNote)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeOfDay = when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..21 -> "evening"
            else -> "late night"
        }

        val parts = buildList {
            add("User name: $userName")
            add("Time of day: $timeOfDay")
            if (vitals.heartRateBpm > 35.0) {
                val confPct = (vitals.signalConfidence * 100).roundToInt()
                add("Heart rate: ${vitals.heartRateBpm.roundToInt()} bpm (resting baseline ${restingHeartRate.roundToInt()} bpm, confidence $confPct%)")
            } else {
                add("Heart rate: still stabilizing")
            }
            if (vitals.breathingRateBpm > 3.0) {
                add("Breathing rate: ${vitals.breathingRateBpm.roundToInt()} breaths/min")
            }
            if (vitals.heartRateVariabilityMs > 0.0) {
                add("HRV RMSSD: ${"%.1f".format(vitals.heartRateVariabilityMs)} ms")
            }
            vitals.facialExpression?.takeIf { it.isNotBlank() }?.let {
                add("Facial expression: $it")
            }
            if (!trendNote.isNullOrBlank()) {
                add("Recent trend: $trendNote")
            }
            speculation.incongruenceHint?.let {
                add("Subtle cue: $it")
            }
            if (vitals.validationHint.isNotBlank()) {
                add("Framing note: ${vitals.validationHint}")
            }
            vitals.smartSpectraLlmInsight?.takeIf { it.isNotBlank() }?.let {
                add("SmartSpectra LLM Insight: ${cleanAssistantText(it)}")
            }
            add("Overall impression: ${speculation.label}")
        }
        return "[LIVE BIOMETRIC & FACIAL CONTEXT] " + parts.joinToString(" | ")
    }

    /**
     * Strips raw Markdown formatting (headers, bold markers, pipe tables, rules)
     * so chat bubbles never render broken Markdown syntax.
     */
    fun cleanAssistantText(raw: String): String {
        if (raw.isBlank()) return ""
        val lines = raw.lines()
            .map { it.trimEnd() }
            .filterNot { line ->
                val t = line.trim()
                t == "---" || t == "***" || Regex("^\\|[-:\\s|]+\\|$").matches(t)
            }
            .map { line ->
                var cleaned = line.trim()
                if (cleaned.startsWith("|") && cleaned.endsWith("|")) {
                    cleaned = cleaned.trim('|')
                        .split("|")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                }
                cleaned = cleaned.replace(Regex("^#{1,6}\\s*"), "")
                cleaned = cleaned.replace("**", "").replace("__", "")
                cleaned = cleaned.replace(Regex("^[-*]\\s+"), "• ")
                cleaned
            }
        return lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
