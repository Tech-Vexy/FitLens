package com.fitlens.app.workout

import android.util.Log
import com.fitlens.app.ai.FitnessCoachAgent
import com.fitlens.app.ai.WorkoutPlanResult
import com.fitlens.app.data.model.WorkoutSessionEntity
import com.fitlens.app.pose.PoseAnalysisFrame
import com.fitlens.app.pose.PoseLandmarkManager
import com.fitlens.app.vitals.HeartRateZone
import com.fitlens.app.vitals.VitalsReading
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WorkoutPhase {
    IDLE,
    WARMUP,
    ACTIVE_SET,
    REST_INTERVAL,
    COOLDOWN,
    COMPLETED
}

data class WorkoutState(
    val phase: WorkoutPhase = WorkoutPhase.IDLE,
    val exerciseName: String = "Squats",
    val currentSet: Int = 1,
    val totalSets: Int = 3,
    val targetReps: Int = 10,
    val completedReps: Int = 0,
    val elapsedSeconds: Int = 0,
    val restSecondsRemaining: Int = 0,
    val isRestExtended: Boolean = false,
    val heartRateBpm: Double = 0.0,
    val heartRateZone: HeartRateZone = HeartRateZone.RESTING,
    val formAccuracyPercentage: Float = 100f,
    val currentJointAngle: Double = 180.0,
    val activeCoachAlert: String? = null,
    val totalDeviations: Int = 0
)

class WorkoutManager(
    private val coachAgent: FitnessCoachAgent = FitnessCoachAgent(),
    private val poseLandmarkManager: PoseLandmarkManager = PoseLandmarkManager()
) {
    companion object {
        private const val TAG = "WorkoutManager"
    }

    private val _state = MutableStateFlow(WorkoutState())
    val state: StateFlow<WorkoutState> = _state.asStateFlow()

    private var sessionScope: CoroutineScope? = null
    private var timerJob: Job? = null
    private var restTimerJob: Job? = null

    private var hrSamples = mutableListOf<Double>()
    private var consecutiveDeviations: Int = 0
    private var extendedRestTotalSeconds: Int = 0
    // Accumulates reps actually completed across all finished sets to avoid
    // double-counting when finishWorkout() is called mid-set.
    private var completedRepsAcrossFinishedSets: Int = 0

    fun startWorkout(
        exercise: String = "Bodyweight Squats",
        targetReps: Int = 10,
        totalSets: Int = 3
    ) {
        sessionScope?.cancel()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        sessionScope = scope

        poseLandmarkManager.setExercise(exercise)
        hrSamples.clear()
        consecutiveDeviations = 0
        extendedRestTotalSeconds = 0
        completedRepsAcrossFinishedSets = 0

        _state.value = WorkoutState(
            phase = WorkoutPhase.ACTIVE_SET,
            exerciseName = exercise,
            currentSet = 1,
            totalSets = totalSets,
            targetReps = targetReps,
            completedReps = 0,
            elapsedSeconds = 0,
            restSecondsRemaining = 0,
            formAccuracyPercentage = 100f,
            activeCoachAlert = "Get in position. Keep chest up."
        )

        startElapsedTimer()
    }

    fun onPoseFrame(frame: PoseAnalysisFrame) {
        if (_state.value.phase != WorkoutPhase.ACTIVE_SET) return

        val newReps = frame.currentRepCount
        val criticalDeviations = frame.activeDeviations.filter {
            it.severity == com.fitlens.app.pose.DeviationSeverity.CRITICAL
        }

        if (criticalDeviations.isNotEmpty()) {
            consecutiveDeviations++
            val firstAlert = criticalDeviations.first().message
            _state.value = _state.value.copy(
                activeCoachAlert = firstAlert,
                formAccuracyPercentage = frame.formScore,
                currentJointAngle = frame.currentAngle,
                totalDeviations = poseLandmarkManager.getTotalDeviations()
            )

            // Check if ADK agent advises stopping set due to biomechanical fatigue
            sessionScope?.launch {
                val fatigueResult = coachAgent.checkBiomechanicalFatigue(
                    consecutiveDeviations = consecutiveDeviations,
                    currentSetReps = newReps,
                    targetReps = _state.value.targetReps
                )
                if (fatigueResult.shouldHaltSet) {
                    _state.value = _state.value.copy(
                        activeCoachAlert = "⚠️ " + fatigueResult.correctiveAction
                    )
                    completeSet()
                }
            }
        } else {
            consecutiveDeviations = 0
            _state.value = _state.value.copy(
                activeCoachAlert = if (_state.value.activeCoachAlert?.startsWith("⚠️") == true) null else _state.value.activeCoachAlert,
                formAccuracyPercentage = frame.formScore,
                currentJointAngle = frame.currentAngle
            )
        }

        if (newReps != _state.value.completedReps) {
            _state.value = _state.value.copy(completedReps = newReps)
            if (newReps >= _state.value.targetReps) {
                completeSet()
            }
        }
    }

    fun onVitalsReading(vitals: VitalsReading) {
        if (vitals.heartRateBpm > 30.0) {
            hrSamples.add(vitals.heartRateBpm)
        }
        _state.value = _state.value.copy(
            heartRateBpm = vitals.heartRateBpm,
            heartRateZone = vitals.heartRateZone
        )
    }

    fun completeSet() {
        // Record the reps actually completed in this set before transitioning.
        completedRepsAcrossFinishedSets += _state.value.completedReps
        if (_state.value.currentSet >= _state.value.totalSets) {
            finishWorkout()
        } else {
            startRestInterval()
        }
    }

    private fun startRestInterval() {
        // Capture HR synchronously before launching coroutine to avoid race condition
        // where _state may be updated by onVitalsReading() before the coroutine body runs.
        val capturedHr = _state.value.heartRateBpm
        val baseRest = 45

        sessionScope?.launch {
            // ADK zero-key agent calculates vitals-guided adaptive rest
            val restResult = coachAgent.calculateAdaptiveRest(
                baseRestSeconds = baseRest,
                currentHeartRateBpm = capturedHr
            )

            val finalRestSeconds = restResult.adjustedRestSeconds
            if (restResult.isExtended) {
                extendedRestTotalSeconds += (restResult.adjustedRestSeconds - baseRest)
            }

            _state.value = _state.value.copy(
                phase = WorkoutPhase.REST_INTERVAL,
                restSecondsRemaining = finalRestSeconds,
                isRestExtended = restResult.isExtended,
                activeCoachAlert = restResult.reason
            )

            restTimerJob?.cancel()
            restTimerJob = launch {
                var remaining = finalRestSeconds
                while (remaining > 0 && isActive) {
                    delay(1000L)
                    remaining--
                    _state.value = _state.value.copy(restSecondsRemaining = remaining)
                }
                // Transition to next set
                if (isActive) {
                    nextSet()
                }
            }
        }
    }

    fun skipRest() {
        restTimerJob?.cancel()
        nextSet()
    }

    private fun nextSet() {
        poseLandmarkManager.resetRepCounter()
        _state.value = _state.value.copy(
            phase = WorkoutPhase.ACTIVE_SET,
            currentSet = _state.value.currentSet + 1,
            completedReps = 0,
            restSecondsRemaining = 0,
            isRestExtended = false,
            activeCoachAlert = "Set ${_state.value.currentSet + 1} started. Focus on smooth eccentric control."
        )
    }

    fun finishWorkout(): WorkoutSessionEntity {
        timerJob?.cancel()
        restTimerJob?.cancel()

        val avgHr = if (hrSamples.isNotEmpty()) hrSamples.average() else 135.0
        val maxHr = if (hrSamples.isNotEmpty()) hrSamples.maxOrNull() ?: 160.0 else 160.0

        val sessionEntity = WorkoutSessionEntity(
            exerciseName = _state.value.exerciseName,
            targetReps = _state.value.targetReps * _state.value.totalSets,
            // Use the running total accumulated in completeSet() to avoid double-counting.
            // If finishWorkout() is called directly (e.g. user taps Finish mid-set), also
            // add the reps completed so far in the current in-progress set.
            completedReps = completedRepsAcrossFinishedSets + _state.value.completedReps,
            durationSeconds = _state.value.elapsedSeconds,
            averageHeartRate = avgHr,
            maxHeartRate = maxHr,
            formAccuracyPercentage = _state.value.formAccuracyPercentage,
            totalFormDeviations = _state.value.totalDeviations,
            extendedRestSeconds = extendedRestTotalSeconds,
            coachFeedback = "Completed ${_state.value.totalSets} sets of ${_state.value.exerciseName}. Form accuracy: ${_state.value.formAccuracyPercentage.toInt()}%."
        )

        _state.value = _state.value.copy(
            phase = WorkoutPhase.COMPLETED,
            activeCoachAlert = "Workout complete! Excellent exertion."
        )

        return sessionEntity
    }

    private fun startElapsedTimer() {
        timerJob?.cancel()
        timerJob = sessionScope?.launch {
            while (isActive) {
                delay(1000L)
                _state.value = _state.value.copy(
                    elapsedSeconds = _state.value.elapsedSeconds + 1
                )
            }
        }
    }

    fun cleanup() {
        sessionScope?.cancel()
    }
}
