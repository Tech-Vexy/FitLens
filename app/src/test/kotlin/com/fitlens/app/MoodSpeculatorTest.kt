package com.fitlens.app

import com.fitlens.app.ai.live.BiometricTrendTracker
import com.fitlens.app.ai.live.MoodSpeculator
import com.fitlens.app.vitals.VitalsReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodSpeculatorTest {

    @Test
    fun speculateDetectsSurprisedAndAlertState() {
        val vitals = VitalsReading(
            heartRateBpm = 88.0,
            breathingRateBpm = 16.0,
            heartRateVariabilityMs = 34.0,
            facialExpression = "Surprise",
            signalConfidence = 0.85f
        )

        val result = MoodSpeculator.speculate(vitals, restingHeartRate = 65.0)
        assertEquals("Alert & Surprised", result.label)
        assertTrue(result.summary.contains("88 bpm"))
    }

    @Test
    fun speculateDetectsCalmAndGroundedOnSlowBreathing() {
        val vitals = VitalsReading(
            heartRateBpm = 68.0,
            breathingRateBpm = 6.5,
            heartRateVariabilityMs = 52.0,
            facialExpression = "Neutral",
            signalConfidence = 0.90f
        )

        val result = MoodSpeculator.speculate(vitals, restingHeartRate = 65.0)
        assertEquals("Calm & Grounded", result.label)
    }

    @Test
    fun buildBiometricContextIncludesKeySignals() {
        val vitals = VitalsReading(
            heartRateBpm = 86.0,
            breathingRateBpm = 7.0,
            heartRateVariabilityMs = 41.2,
            facialExpression = "Happy",
            signalConfidence = 0.72f
        )

        val ctx = MoodSpeculator.buildBiometricContext(vitals, userName = "Alex", restingHeartRate = 65.0)
        assertTrue(ctx.contains("Alex"))
        assertTrue(ctx.contains("86 bpm"))
        assertTrue(ctx.contains("7 breaths/min"))
        assertTrue(ctx.contains("41.2 ms"))
        assertTrue(ctx.contains("Happy"))
    }

    @Test
    fun cleanAssistantTextStripsRawMarkdownArtifacts() {
        val raw = """
            ## Vitals Check-In
            | Metric | Value | Status |
            |--------|-------|--------|
            | Heart Rate | ~86 bpm | Normal |
            ---
            **Breathing looks great!**
            ### Gentle Suggestions
            - Take a slow breath
        """.trimIndent()

        val cleaned = MoodSpeculator.cleanAssistantText(raw)
        assertFalse(cleaned.contains("##"))
        assertFalse(cleaned.contains("**"))
        assertFalse(cleaned.contains("---"))
        assertFalse(cleaned.contains("|---"))
        assertTrue(cleaned.contains("Breathing looks great!"))
        assertTrue(cleaned.contains("• Take a slow breath"))
    }

    @Test
    fun speculateDetectsSmilingWithElevatedPulseIncongruence() {
        val vitals = VitalsReading(
            heartRateBpm = 92.0,
            breathingRateBpm = 16.0,
            heartRateVariabilityMs = 28.0,
            facialExpression = "Happy",
            signalConfidence = 0.85f
        )

        val result = MoodSpeculator.speculate(vitals, restingHeartRate = 65.0)
        assertEquals("Upbeat or Keyed Up", result.label)
        assertNotNull(result.incongruenceHint)
    }

    @Test
    fun trendTrackerDebouncesExpressionShiftAcrossConsecutiveFrames() {
        val tracker = BiometricTrendTracker(expressionConfirmationCount = 2)
        tracker.record(
            VitalsReading(
                heartRateBpm = 72.0,
                facialExpression = "Neutral",
                signalConfidence = 0.8f,
                timestampMs = 100_000L
            )
        )
        val firstFlicker = tracker.record(
            VitalsReading(
                heartRateBpm = 88.0,
                facialExpression = "Surprise",
                signalConfidence = 0.8f,
                timestampMs = 150_000L
            )
        )
        assertNull(firstFlicker)

        val confirmedShift = tracker.record(
            VitalsReading(
                heartRateBpm = 88.0,
                facialExpression = "Surprise",
                signalConfidence = 0.8f,
                timestampMs = 150_200L
            )
        )
        assertNotNull(confirmedShift)
        assertTrue(confirmedShift!!.description.contains("Neutral to Surprise"))
    }

    @Test
    fun trendTrackerComputesRisingAndSettlingPulseTrends() {
        val tracker = BiometricTrendTracker(minSampleIntervalMs = 1000L)
        listOf(64.0, 65.0, 66.0, 68.0).forEachIndexed { idx, hr ->
            tracker.record(
                VitalsReading(
                    heartRateBpm = hr,
                    signalConfidence = 0.8f,
                    timestampMs = 10_000L + idx * 1500L
                )
            )
        }
        val rising = tracker.computeTrendNote(VitalsReading(heartRateBpm = 78.0, signalConfidence = 0.8f))
        assertNotNull(rising)
        assertTrue(rising!!.startsWith("Pulse rising"))

        val settling = tracker.computeTrendNote(VitalsReading(heartRateBpm = 54.0, signalConfidence = 0.8f))
        assertNotNull(settling)
        assertTrue(settling!!.startsWith("Pulse settling"))
    }

    @Test
    fun trendTrackerDetectsHeartRateSpike() {
        val tracker = BiometricTrendTracker(minSampleIntervalMs = 1000L)
        listOf(66.0, 67.0, 66.0).forEachIndexed { idx, hr ->
            tracker.record(
                VitalsReading(
                    heartRateBpm = hr,
                    facialExpression = "Neutral",
                    signalConfidence = 0.8f,
                    timestampMs = 100_000L + idx * 1500L
                )
            )
        }
        val spike = tracker.record(
            VitalsReading(
                heartRateBpm = 86.0,
                facialExpression = "Neutral",
                signalConfidence = 0.8f,
                timestampMs = 150_000L
            )
        )
        assertNotNull(spike)
        assertTrue(spike!!.description.contains("Heart rate jumped"))
    }

    @Test
    fun speculateHandlesWaitingAndActivatedStates() {
        val waiting = MoodSpeculator.speculate(VitalsReading())
        assertEquals("Waiting for signals", waiting.label)

        val activated = MoodSpeculator.speculate(
            VitalsReading(
                heartRateBpm = 90.0,
                breathingRateBpm = 16.0,
                heartRateVariabilityMs = 26.0,
                facialExpression = "Neutral",
                signalConfidence = 0.8f
            ),
            restingHeartRate = 65.0
        )
        assertEquals("Activated or Stressed", activated.label)
        assertNotNull(activated.incongruenceHint)
    }
}
