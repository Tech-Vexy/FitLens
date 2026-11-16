package com.fitlens.app.workout

import com.fitlens.app.ai.FitnessCoachAgent
import com.fitlens.app.data.model.WorkoutSessionEntity
import com.fitlens.app.pose.DeviationSeverity
import com.fitlens.app.pose.FormDeviation
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
    val routineExercises: List<String> = emptyList(),
    val currentExerciseIndex: Int = 0,
    val isIsometricHold: Boolean = false,
    val currentSet: Int = 1,
    val totalSets: Int = 3,
    val targetReps: Int = 10,
    val completedReps: Int = 0,
    val elapsedSeconds: Int = 0,
    val phaseCountdownSeconds: Int = 0,
    val restSecondsRemaining: Int = 0,
    val isRestExtended: Boolean = false,
    val heartRateBpm: Double = 0.0,
    val peakHeartRateBpm: Double = 0.0,
    val heartRateRecoveryDropBpm: Double = 0.0,
    val heartRateZone: HeartRateZone = HeartRateZone.RESTING,
    val breathingRateBpm: Double = 0.0,
    val hrvRmssdMs: Double = 0.0,
    val facialExpression: String? = null,
    val signalConfidence: Float = 0f,
    val validationHint: String = "",
    val smartSpectraInsight: String? = null,
    val showBreathingPacer: Boolean = false,
    val breathingInhaleSeconds: Int = 4,
    val breathingExhaleSeconds: Int = 6,
    val breathingDirective: String = "",
    val expressionCoachCue: String? = null,
    val autonomicReadinessLabel: String = "Calibrating HRV",
    val formAccuracyPercentage: Float = 100f,
    val currentJointAngle: Double = 180.0,
    val activeDeviations: List<FormDeviation> = emptyList(),
    val activeCoachAlert: String? = null,
    val totalDeviations: Int = 0
)

class WorkoutManager(
    private val coachAgent: FitnessCoachAgent = FitnessCoachAgent(),
    private val poseLandmarkManager: PoseLandmarkManager = PoseLandmarkManager()
) {
    companion object {
        private const val DEFAULT_WARMUP_SECONDS = 20
        private const val DEFAULT_COOLDOWN_SECONDS = 25
    }

    private val _state = MutableStateFlow(WorkoutState())
    val state: StateFlow<WorkoutState> = _state.asStateFlow()

    private var sessionScope: CoroutineScope? = null
    private var timerJob: Job? = null
    private var restTimerJob: Job? = null
    private var phaseCountdownJob: Job? = null

    private val hrSamples = mutableListOf<Double>()
    private var consecutiveDeviations: Int = 0
    private var extendedRestTotalSeconds: Int = 0
    private var completedRepsAcrossFinishedSets: Int = 0
    private var configuredBaseRestSeconds: Int = 45
    private var configuredIncludeCooldown: Boolean = false
    private var userRestingHeartRateBpm: Double = 65.0
    private var hasCriticalHoldFault: Boolean = false
    private val completedExerciseNames = linkedSetOf<String>()

    @Synchronized
    fun updateRestingHeartRate(restingHrBpm: Double) {
        if (restingHrBpm in 35.0..120.0) {
            userRestingHeartRateBpm = restingHrBpm
        }
    }

    @Synchronized
    fun startWorkout(
        exercise: String = "Bodyweight Squats",
        targetReps: Int = 10,
        totalSets: Int = 3,
        baseRestSeconds: Int = 45,
        includeWarmup: Boolean = false,
        includeCooldown: Boolean = false
    ) {
        startRoutine(
            exercises = listOf(exercise),
            targetReps = targetReps,
            setsPerExercise = totalSets,
            baseRestSeconds = baseRestSeconds,
            includeWarmup = includeWarmup,
            includeCooldown = includeCooldown
        )
    }

    @Synchronized
    fun startRoutine(
        exercises: List<String>,
        targetReps: Int = 10,
        setsPerExercise: Int = 3,
        baseRestSeconds: Int = 45,
        includeWarmup: Boolean = false,
        includeCooldown: Boolean = false
    ) {
        val cleanedExercises = exercises.map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { listOf("Bodyweight Squats") }
        val firstExercise = cleanedExercises.first()
        val isometric = PoseLandmarkManager.isIsometricExercise(firstExercise)

        sessionScope?.cancel()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        sessionScope = scope

        poseLandmarkManager.setExercise(firstExercise)
        synchronized(hrSamples) { hrSamples.clear() }
        consecutiveDeviations = 0
        extendedRestTotalSeconds = 0
        completedRepsAcrossFinishedSets = 0
        configuredBaseRestSeconds = baseRestSeconds.coerceIn(15, 180)
        configuredIncludeCooldown = includeCooldown
        hasCriticalHoldFault = false
        completedExerciseNames.clear()
        completedExerciseNames.add(firstExercise)

        val initialPhase = if (includeWarmup) WorkoutPhase.WARMUP else WorkoutPhase.ACTIVE_SET
        val initialCountdown = if (includeWarmup) DEFAULT_WARMUP_SECONDS else 0
        val initialCue = when {
            includeWarmup -> "Dynamic Warmup: shoulder circles, hip hinges, and deep nasal breaths."
            isometric -> "Brace core into a straight shoulder-hip-ankle Plank line (${targetReps}s hold)."
            else -> "Get in position for $firstExercise. Keep chest up."
        }

        _state.value = WorkoutState(
            phase = initialPhase,
            exerciseName = firstExercise,
            routineExercises = cleanedExercises,
            currentExerciseIndex = 0,
            isIsometricHold = isometric,
            currentSet = 1,
            totalSets = setsPerExercise.coerceAtLeast(1),
            targetReps = targetReps.coerceAtLeast(1),
            completedReps = 0,
            elapsedSeconds = 0,
            phaseCountdownSeconds = initialCountdown,
            restSecondsRemaining = 0,
            formAccuracyPercentage = 100f,
            activeCoachAlert = initialCue
        )

        startElapsedTimer()
        if (includeWarmup) {
            startWarmupCountdown(DEFAULT_WARMUP_SECONDS)
        }
    }

    private fun startWarmupCountdown(seconds: Int) {
        phaseCountdownJob?.cancel()
        phaseCountdownJob = sessionScope?.launch {
            var remaining = seconds
            while (remaining > 0 && isActive && _state.value.phase == WorkoutPhase.WARMUP) {
                delay(1000L)
                remaining--
                synchronized(this@WorkoutManager) {
                    if (_state.value.phase == WorkoutPhase.WARMUP) {
                        _state.value = _state.value.copy(phaseCountdownSeconds = remaining)
                    }
                }
            }
            if (isActive) {
                skipWarmup()
            }
        }
    }

    @Synchronized
    fun skipWarmup() {
        if (_state.value.phase != WorkoutPhase.WARMUP) return
        phaseCountdownJob?.cancel()
        val exercise = _state.value.exerciseName
        val isometric = _state.value.isIsometricHold
        _state.value = _state.value.copy(
            phase = WorkoutPhase.ACTIVE_SET,
            phaseCountdownSeconds = 0,
            activeCoachAlert = if (isometric) {
                "Warmup complete! Hold steady Plank alignment for ${_state.value.targetReps}s."
            } else {
                "Warmup complete! Begin Set 1 of $exercise."
            }
        )
    }

    @Synchronized
    fun advanceIsometricHold(secondsDelta: Int = 5) {
        val current = _state.value
        if (current.phase != WorkoutPhase.ACTIVE_SET || !current.isIsometricHold) return
        val updated = current.completedReps + secondsDelta.coerceAtLeast(1)
        _state.value = current.copy(completedReps = updated)
        if (updated >= current.targetReps) {
            completeSet()
        }
    }

    @Synchronized
    fun incrementRep() {
        val current = _state.value
        if (current.phase != WorkoutPhase.ACTIVE_SET) return
        if (current.isIsometricHold) {
            advanceIsometricHold(5)
            return
        }
        val updated = current.completedReps + 1
        _state.value = current.copy(
            completedReps = updated,
            activeCoachAlert = "Rep $updated/${current.targetReps} logged — keep steady breathing and form!"
        )
        if (updated >= current.targetReps) {
            completeSet()
        }
    }

    @Synchronized
    fun onPoseFrame(frame: PoseAnalysisFrame) {
        if (_state.value.phase != WorkoutPhase.ACTIVE_SET) return

        val isIsometric = _state.value.isIsometricHold
        val newReps = if (isIsometric) _state.value.completedReps else maxOf(_state.value.completedReps, frame.currentRepCount)
        val criticalDeviations = frame.activeDeviations.filter {
            it.severity == DeviationSeverity.CRITICAL
        }

        if (criticalDeviations.isNotEmpty()) {
            consecutiveDeviations++
            hasCriticalHoldFault = true
            val firstAlert = criticalDeviations.first().message
            _state.value = _state.value.copy(
                activeCoachAlert = firstAlert,
                activeDeviations = frame.activeDeviations,
                formAccuracyPercentage = frame.formScore,
                currentJointAngle = frame.currentAngle,
                totalDeviations = poseLandmarkManager.getTotalDeviations()
            )

            if (consecutiveDeviations == 3) {
                sessionScope?.launch {
                    val fatigueResult = coachAgent.checkBiomechanicalFatigue(
                        consecutiveDeviations = consecutiveDeviations,
                        currentSetReps = newReps,
                        targetReps = _state.value.targetReps
                    )
                    if (fatigueResult.shouldHaltSet) {
                        synchronized(this@WorkoutManager) {
                            _state.value = _state.value.copy(
                                activeCoachAlert = "⚠️ " + fatigueResult.correctiveAction
                            )
                            completeSet()
                        }
                    }
                }
            }
        } else {
            consecutiveDeviations = 0
            hasCriticalHoldFault = false
            val nextAlert = when {
                _state.value.activeCoachAlert?.startsWith("⚠️") == true -> null
                frame.activeDeviations.isNotEmpty() -> frame.activeDeviations.first().message
                _state.value.expressionCoachCue != null -> _state.value.expressionCoachCue
                else -> _state.value.activeCoachAlert
            }
            _state.value = _state.value.copy(
                activeCoachAlert = nextAlert,
                activeDeviations = frame.activeDeviations,
                formAccuracyPercentage = frame.formScore,
                currentJointAngle = frame.currentAngle,
                totalDeviations = poseLandmarkManager.getTotalDeviations()
            )
        }

        if (!isIsometric && newReps != _state.value.completedReps) {
            _state.value = _state.value.copy(completedReps = newReps)
            if (newReps >= _state.value.targetReps) {
                completeSet()
            }
        }
    }

    @Synchronized
    fun onVitalsReading(vitals: VitalsReading) {
        if (vitals.heartRateBpm > 30.0) {
            synchronized(hrSamples) {
                hrSamples.add(vitals.heartRateBpm)
                // Bound memory for very long sessions (~10 min at 1 Hz)
                if (hrSamples.size > 600) {
                    hrSamples.subList(0, hrSamples.size - 600).clear()
                }
            }
        }
        val current = _state.value
        val newPeakHr = maxOf(current.peakHeartRateBpm, vitals.heartRateBpm)
        val hrDrop = if (newPeakHr > 0.0 && vitals.heartRateBpm > 0.0) {
            maxOf(0.0, newPeakHr - vitals.heartRateBpm)
        } else {
            current.heartRateRecoveryDropBpm
        }

        val bio = coachAgent.evaluateBiofeedback(
            heartRateBpm = vitals.heartRateBpm,
            breathingRateBpm = vitals.breathingRateBpm,
            hrvRmssdMs = vitals.heartRateVariabilityMs,
            facialExpression = vitals.facialExpression
        )

        val inRecoveryPhase = current.phase == WorkoutPhase.REST_INTERVAL ||
            current.phase == WorkoutPhase.COOLDOWN

        _state.value = current.copy(
            heartRateBpm = vitals.heartRateBpm,
            peakHeartRateBpm = newPeakHr,
            heartRateRecoveryDropBpm = hrDrop,
            heartRateZone = vitals.heartRateZone,
            breathingRateBpm = vitals.breathingRateBpm,
            hrvRmssdMs = vitals.heartRateVariabilityMs,
            facialExpression = vitals.facialExpression,
            signalConfidence = vitals.signalConfidence,
            validationHint = vitals.validationHint,
            smartSpectraInsight = vitals.smartSpectraLlmInsight,
            showBreathingPacer = inRecoveryPhase || bio.shouldTriggerBreathingPacer,
            breathingInhaleSeconds = bio.inhaleSeconds,
            breathingExhaleSeconds = bio.exhaleSeconds,
            breathingDirective = bio.breathingDirective,
            expressionCoachCue = bio.expressionCue,
            autonomicReadinessLabel = bio.autonomicReadinessLabel
        )
    }

    @Synchronized
    fun completeSet() {
        val current = _state.value
        if (current.phase != WorkoutPhase.ACTIVE_SET) return

        completedRepsAcrossFinishedSets += current.completedReps
        _state.value = current.copy(completedReps = 0, activeDeviations = emptyList())

        val hasMoreSetsInCurrentExercise = current.currentSet < current.totalSets
        val hasMoreExercisesInRoutine = current.currentExerciseIndex < current.routineExercises.lastIndex

        when {
            hasMoreSetsInCurrentExercise -> {
                startRestInterval(advanceToNextExercise = false)
            }
            hasMoreExercisesInRoutine -> {
                startRestInterval(advanceToNextExercise = true)
            }
            configuredIncludeCooldown -> {
                startCooldownPhase()
            }
            else -> {
                finishWorkout()
            }
        }
    }

    private fun startCooldownPhase() {
        restTimerJob?.cancel()
        phaseCountdownJob?.cancel()
        _state.value = _state.value.copy(
            phase = WorkoutPhase.COOLDOWN,
            phaseCountdownSeconds = DEFAULT_COOLDOWN_SECONDS,
            showBreathingPacer = true,
            activeCoachAlert = "Cooldown Recovery: follow the 4s inhale / 6s exhale ring to lower heart rate."
        )

        phaseCountdownJob = sessionScope?.launch {
            var remaining = DEFAULT_COOLDOWN_SECONDS
            while (remaining > 0 && isActive && _state.value.phase == WorkoutPhase.COOLDOWN) {
                delay(1000L)
                remaining--
                synchronized(this@WorkoutManager) {
                    if (_state.value.phase == WorkoutPhase.COOLDOWN) {
                        _state.value = _state.value.copy(phaseCountdownSeconds = remaining)
                    }
                }
            }
            if (isActive && _state.value.phase == WorkoutPhase.COOLDOWN) {
                finishWorkout()
            }
        }
    }

    private fun startRestInterval(advanceToNextExercise: Boolean) {
        val capturedHr = _state.value.heartRateBpm
        val capturedRhr = userRestingHeartRateBpm
        val baseRest = configuredBaseRestSeconds

        val upcomingLabel = if (advanceToNextExercise) {
            val nextIdx = _state.value.currentExerciseIndex + 1
            val nextEx = _state.value.routineExercises.getOrNull(nextIdx) ?: _state.value.exerciseName
            "Up next: $nextEx."
        } else {
            "Rest ${baseRest}s and recover."
        }

        _state.value = _state.value.copy(
            phase = WorkoutPhase.REST_INTERVAL,
            restSecondsRemaining = baseRest,
            isRestExtended = false,
            showBreathingPacer = true,
            activeCoachAlert = upcomingLabel
        )

        sessionScope?.launch {
            val restResult = coachAgent.calculateAdaptiveRest(
                baseRestSeconds = baseRest,
                currentHeartRateBpm = capturedHr,
                restingHeartRateBpm = capturedRhr
            )

            synchronized(this@WorkoutManager) {
                if (_state.value.phase != WorkoutPhase.REST_INTERVAL) return@launch

                val finalRestSeconds = restResult.adjustedRestSeconds
                if (restResult.isExtended) {
                    extendedRestTotalSeconds += (restResult.adjustedRestSeconds - baseRest)
                }

                _state.value = _state.value.copy(
                    restSecondsRemaining = finalRestSeconds,
                    isRestExtended = restResult.isExtended,
                    activeCoachAlert = restResult.reason
                )

                restTimerJob?.cancel()
                restTimerJob = launch {
                    var remaining = finalRestSeconds
                    while (remaining > 0 && isActive &&
                        _state.value.phase == WorkoutPhase.REST_INTERVAL
                    ) {
                        delay(1000L)
                        remaining--
                        synchronized(this@WorkoutManager) {
                            if (_state.value.phase == WorkoutPhase.REST_INTERVAL) {
                                _state.value = _state.value.copy(restSecondsRemaining = remaining)
                            }
                        }
                    }
                    if (isActive && _state.value.phase == WorkoutPhase.REST_INTERVAL) {
                        advanceAfterRest(advanceToNextExercise)
                    }
                }
            }
        }
    }

    @Synchronized
    fun skipRest() {
        if (_state.value.phase != WorkoutPhase.REST_INTERVAL) return
        restTimerJob?.cancel()
        val current = _state.value
        val advanceExercise = current.currentSet >= current.totalSets &&
            current.currentExerciseIndex < current.routineExercises.lastIndex
        advanceAfterRest(advanceExercise)
    }

    @Synchronized
    private fun advanceAfterRest(advanceToNextExercise: Boolean) {
        val current = _state.value
        if (advanceToNextExercise && current.currentExerciseIndex < current.routineExercises.lastIndex) {
            val nextIdx = current.currentExerciseIndex + 1
            val nextExercise = current.routineExercises[nextIdx]
            val nextIsometric = PoseLandmarkManager.isIsometricExercise(nextExercise)
            val nextTarget = if (nextIsometric && current.targetReps < 20) 30 else current.targetReps
            completedExerciseNames.add(nextExercise)
            poseLandmarkManager.setExercise(nextExercise)

            _state.value = current.copy(
                phase = WorkoutPhase.ACTIVE_SET,
                exerciseName = nextExercise,
                currentExerciseIndex = nextIdx,
                isIsometricHold = nextIsometric,
                currentSet = 1,
                targetReps = nextTarget,
                completedReps = 0,
                restSecondsRemaining = 0,
                isRestExtended = false,
                showBreathingPacer = false,
                activeCoachAlert = "Starting Exercise ${nextIdx + 1}/${current.routineExercises.size}: $nextExercise."
            )
        } else {
            poseLandmarkManager.resetRepCounter()
            val nextSetNum = current.currentSet + 1
            _state.value = current.copy(
                phase = WorkoutPhase.ACTIVE_SET,
                currentSet = nextSetNum,
                completedReps = 0,
                restSecondsRemaining = 0,
                isRestExtended = false,
                showBreathingPacer = false,
                activeCoachAlert = "Set $nextSetNum started. Focus on smooth eccentric control."
            )
        }
    }

    @Synchronized
    fun finishWorkout(): WorkoutSessionEntity {
        timerJob?.cancel()
        restTimerJob?.cancel()
        phaseCountdownJob?.cancel()

        val (avgHr, maxHr) = synchronized(hrSamples) {
            if (hrSamples.isNotEmpty()) {
                Pair(hrSamples.average(), hrSamples.maxOrNull() ?: 160.0)
            } else {
                Pair(135.0, 160.0)
            }
        }

        val current = _state.value
        val inProgressReps =
            if (current.phase == WorkoutPhase.ACTIVE_SET) current.completedReps else 0
        val totalDone = completedRepsAcrossFinishedSets + inProgressReps
        val totalExercisesCount = maxOf(1, current.routineExercises.size)
        val targetTotal = current.targetReps * current.totalSets * totalExercisesCount

        val exerciseSummaryLabel = if (completedExerciseNames.size > 1) {
            completedExerciseNames.joinToString(" + ")
        } else {
            current.exerciseName
        }

        val evaluation = runBlocking {
            coachAgent.evaluateSession(
                durationSeconds = current.elapsedSeconds,
                avgHrBpm = avgHr,
                formDeviations = current.totalDeviations
            )
        }

        val recoveryNote = if (current.heartRateRecoveryDropBpm >= 8.0) {
            " HR recovery drop: -${current.heartRateRecoveryDropBpm.toInt()} BPM."
        } else {
            ""
        }

        val sessionEntity = WorkoutSessionEntity(
            exerciseName = exerciseSummaryLabel,
            targetReps = targetTotal,
            completedReps = totalDone,
            durationSeconds = current.elapsedSeconds,
            averageHeartRate = avgHr,
            maxHeartRate = maxHr,
            formAccuracyPercentage = current.formAccuracyPercentage,
            totalFormDeviations = current.totalDeviations,
            extendedRestSeconds = extendedRestTotalSeconds,
            coachFeedback = "${evaluation.summary} ${evaluation.recommendation}$recoveryNote (Wellness coaching only — non-medical.)"
        )

        _state.value = current.copy(
            phase = WorkoutPhase.COMPLETED,
            phaseCountdownSeconds = 0,
            restSecondsRemaining = 0,
            showBreathingPacer = false,
            activeCoachAlert = "Workout complete! Excellent effort."
        )

        return sessionEntity
    }

    private fun startElapsedTimer() {
        timerJob?.cancel()
        timerJob = sessionScope?.launch {
            while (isActive) {
                delay(1000L)
                synchronized(this@WorkoutManager) {
                    val cur = _state.value
                    if (cur.phase == WorkoutPhase.IDLE || cur.phase == WorkoutPhase.COMPLETED) {
                        return@synchronized
                    }
                    var updated = cur.copy(elapsedSeconds = cur.elapsedSeconds + 1)

                    // Time-based isometric hold progression (e.g. Plank) when form is clean
                    if (cur.phase == WorkoutPhase.ACTIVE_SET && cur.isIsometricHold && !hasCriticalHoldFault) {
                        val nextHoldSec = cur.completedReps + 1
                        updated = updated.copy(completedReps = nextHoldSec)
                        _state.value = updated
                        if (nextHoldSec >= cur.targetReps) {
                            completeSet()
                        }
                    } else {
                        _state.value = updated
                    }
                }
            }
        }
    }

    fun cleanup() {
        sessionScope?.cancel()
    }
}
