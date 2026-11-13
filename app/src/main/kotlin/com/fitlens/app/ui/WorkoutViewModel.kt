package com.fitlens.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.fitlens.app.ai.FitnessCoachAgent
import com.fitlens.app.audio.FitLensAudioCoach
import com.fitlens.app.camera.DualStreamAnalyzer
import com.fitlens.app.data.WorkoutDatabase
import com.fitlens.app.data.WorkoutRepository
import com.fitlens.app.data.model.WorkoutSessionEntity
import com.fitlens.app.data.profile.UserPreferencesRepository
import com.fitlens.app.pose.DeviationSeverity
import com.fitlens.app.pose.LandmarkPoint
import com.fitlens.app.pose.PoseAnalysisFrame
import com.fitlens.app.pose.PoseLandmarkManager
import com.fitlens.app.vitals.PresageFrameAnalyzer
import com.fitlens.app.workout.ProgressAnalyzer
import com.fitlens.app.workout.ProgressionInsight
import com.fitlens.app.workout.WorkoutManager
import com.fitlens.app.workout.WorkoutPhase
import com.fitlens.app.workout.WorkoutState
import com.presagetech.smartspectra.SmartSpectraSdk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // The SDK emits a cumulative arterial-pressure trace; cap what we retain
        // per reading so memory and waveform redraw cost stay bounded per session.
        private const val MAX_WAVEFORM_SAMPLES = 120
    }

    private val coachAgent = FitnessCoachAgent()
    private val poseManager = PoseLandmarkManager(application)
    private val vitalsAnalyzer = PresageFrameAnalyzer()
    private val workoutManager = WorkoutManager(coachAgent, poseManager)
    private val progressAnalyzer = ProgressAnalyzer()
    private val audioCoach = FitLensAudioCoach(application)
    private val userPrefsRepo = UserPreferencesRepository(application)

    val dualStreamAnalyzer = DualStreamAnalyzer(
        presageAnalyzer = vitalsAnalyzer,
        poseManager = poseManager,
        onPoseFrame = { frame -> onCameraPoseFrame(frame) }
    )

    private val repository: WorkoutRepository by lazy {
        val db = WorkoutDatabase.getDatabase(application)
        WorkoutRepository(db.workoutDao())
    }

    val workoutState: StateFlow<WorkoutState> = workoutManager.state
    val vitalsFlow = vitalsAnalyzer.vitalsFlow

    private val _isAudioMuted = MutableStateFlow(false)
    val isAudioMuted: StateFlow<Boolean> = _isAudioMuted.asStateFlow()

    private val _currentLandmarks = MutableStateFlow<Map<Int, LandmarkPoint>>(emptyMap())
    val currentLandmarks: StateFlow<Map<Int, LandmarkPoint>> = _currentLandmarks.asStateFlow()

    val workoutHistory: StateFlow<List<WorkoutSessionEntity>> = repository.allSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _latestInsight = MutableStateFlow<ProgressionInsight?>(null)
    val latestInsight: StateFlow<ProgressionInsight?> = _latestInsight.asStateFlow()

    private val _lastSavedSession = MutableStateFlow<WorkoutSessionEntity?>(null)
    val lastSavedSession: StateFlow<WorkoutSessionEntity?> = _lastSavedSession.asStateFlow()

    private var previousRepCount = 0
    private var previousPhase = WorkoutPhase.IDLE

    // Active session local buffer for SQLite persistence
    private val activeVitalsBuffer = mutableListOf<com.fitlens.app.data.model.VitalsLogEntity>()
    // Written from the CameraX analyzer thread; CopyOnWriteArrayList keeps iteration safe.
    private val activeFaultsBuffer = java.util.concurrent.CopyOnWriteArrayList<com.fitlens.app.data.model.BiomechanicalFaultEntity>()

    // Fault-logging dedupe state (camera analyzer thread)
    @Volatile private var lastFaultSignature: String? = null
    @Volatile private var lastFaultLogAt: Long = 0L

    // Guards against persisting the same workout twice (manual finish racing
    // the auto-completion path). Reset on every startWorkout().
    @Volatile private var sessionPersisted = false

    // Observers kept for cleanup in onCleared (ViewModel has no LifecycleOwner)
    private val sdkMetricsObserver = Observer<com.presagetech.smartspectra.proto.MetricsProto.Metrics?> { metrics ->
        if (metrics == null) return@Observer
        var hr = 0.0
        var hrv = 0.0
        var br = 0.0
        var confidence = 0f
        val trace = mutableListOf<Float>()

        if (metrics.hasCardio()) {
            metrics.cardio.pulseRateList.lastOrNull { it.timestamp > 0 }?.let {
                hr = it.value.toDouble()
                confidence = it.confidence
            }
            metrics.cardio.hrvList.lastOrNull { it.rmssd > 0 }?.let { hrv = it.rmssd }
            // Keep only the newest window — the proto trace is cumulative, so
            // copying it wholesale on every emission would grow O(n²) per session.
            metrics.cardio.arterialPressureTraceList
                .takeLast(MAX_WAVEFORM_SAMPLES)
                .forEach { trace.add(it.value) }
        }
        if (metrics.hasBreathing()) {
            metrics.breathing.rateList.lastOrNull()?.let { br = it.value.toDouble() }
        }
        if (hr > 0) {
            vitalsAnalyzer.updateVitalsReading(
                hr = hr,
                hrv = hrv,
                breathingRate = br,
                confidence = confidence,
                pulseTrace = trace
            )
        }
    }

    private val sdkErrorObserver = Observer<com.presagetech.smartspectra.SmartSpectraError?> { error ->
        if (error != null) {
            android.util.Log.e("WorkoutViewModel", "SmartSpectra SDK error: ${error.message}")
        }
    }

    private val sdkValidationObserver = Observer<com.presagetech.smartspectra.ValidationStatus?> { status ->
        status?.hint?.let { hint ->
            if (!hint.isNullOrEmpty()) {
                android.util.Log.d("WorkoutViewModel", "SmartSpectra validation: $hint")
            }
        }
    }

    init {
        val sdk = SmartSpectraSdk.shared
        // Wire SmartSpectraSdk.shared LiveData streams → vitalsAnalyzer → vitalsFlow
        // observeForever is appropriate here because ViewModel has no LifecycleOwner;
        // the observers are removed manually in onCleared().
        sdk.metrics.observeForever(sdkMetricsObserver)
        sdk.error.observeForever(sdkErrorObserver)
        sdk.validationStatus.observeForever(sdkValidationObserver)

        // Push UserProfile updates from DataStore into PresageFrameAnalyzer so that
        // heart rate zone classification always uses the personalized Karvonen model.
        viewModelScope.launch {
            userPrefsRepo.userProfileFlow.collect { profile ->
                vitalsAnalyzer.updateUserProfile(profile)
            }
        }

        // Collect vitals flow into workout manager and local SQLite buffer
        viewModelScope.launch {
            vitalsFlow.collect { vitals ->
                workoutManager.onVitalsReading(vitals)
                if (workoutManager.state.value.phase == WorkoutPhase.ACTIVE_SET || workoutManager.state.value.phase == WorkoutPhase.REST_INTERVAL) {
                    if (vitals.heartRateBpm > 40.0) {
                        activeVitalsBuffer.add(
                            com.fitlens.app.data.model.VitalsLogEntity(
                                sessionId = 0L, // updated upon session completion
                                timestampMs = vitals.timestampMs,
                                heartRateBpm = vitals.heartRateBpm,
                                heartRateVariabilityMs = vitals.heartRateVariabilityMs,
                                breathingRateBpm = vitals.breathingRateBpm,
                                heartRateZone = vitals.heartRateZone.label,
                                signalConfidence = vitals.signalConfidence
                            )
                        )
                    }
                }
            }
        }

        // Periodically refresh insights when history updates
        viewModelScope.launch {
            workoutHistory.collect { sessions ->
                _latestInsight.value = progressAnalyzer.analyzeProgression(sessions)
            }
        }

        // Audio coaching triggers on state changes
        viewModelScope.launch {
            workoutState.collect { state ->
                // Voice rep counter
                if (state.completedReps > previousRepCount && state.phase == WorkoutPhase.ACTIVE_SET) {
                    audioCoach.speakRepCount(state.completedReps)
                    previousRepCount = state.completedReps
                }

                // Voice phase transitions
                if (state.phase != previousPhase) {
                    when (state.phase) {
                        WorkoutPhase.REST_INTERVAL -> {
                            val msg = if (state.isRestExtended) {
                                "Set complete. Heart rate elevated, resting for ${state.restSecondsRemaining} seconds."
                            } else {
                                "Set complete. Rest for ${state.restSecondsRemaining} seconds."
                            }
                            audioCoach.announcePhase(msg)
                        }
                        WorkoutPhase.ACTIVE_SET -> {
                            previousRepCount = 0
                            audioCoach.announcePhase("Begin Set ${state.currentSet}. Focus on form.")
                        }
                        WorkoutPhase.COMPLETED -> {
                            audioCoach.announcePhase("Workout complete! Great effort.")
                        }
                        else -> Unit
                    }
                    previousPhase = state.phase
                }

                // Voice real-time form alert
                state.activeCoachAlert?.let { alert ->
                    if (alert.startsWith("⚠️") || alert.contains("caving") || alert.contains("sagging")) {
                        audioCoach.speakCorrection(alert, isUrgent = true)
                    }
                }
            }
        }
    }

    /**
     * Drops the cached SmartSpectra CustomInput handle. Call before starting a
     * new SDK session so the next frames get a fresh handle for the new pipeline.
     */
    fun resetVitalsInput() {
        vitalsAnalyzer.resetCustomInput()
    }

    /**
     * Toggles audio coaching mute state and propagates it to FitLensAudioCoach.
     * Called from WorkoutSessionScreen's mute button so the UI and TTS stay in sync.
     */
    fun toggleAudioMute() {
        val newMuted = !_isAudioMuted.value
        _isAudioMuted.value = newMuted
        audioCoach.setMuted(newMuted)
    }

    /**
     * Handles each PoseAnalysisFrame emitted by the CameraX analyzer thread:
     * feeds the workout state machine, updates the skeleton overlay landmarks,
     * and buffers critical faults for local SQLite persistence.
     */
    private fun onCameraPoseFrame(frame: PoseAnalysisFrame) {
        // Capture phase before onPoseFrame() — it may transition the workout
        // (e.g. set completion) within this same call.
        val wasActiveSet = workoutManager.state.value.phase == WorkoutPhase.ACTIVE_SET

        _currentLandmarks.value = frame.landmarks
        workoutManager.onPoseFrame(frame)

        val criticalDeviations = frame.activeDeviations.filter {
            it.severity == DeviationSeverity.CRITICAL
        }
        if (criticalDeviations.isEmpty() || !wasActiveSet) return

        // Dedupe: log a fault episode at most once per signature change or every 2s,
        // so sustained bad form doesn't flood the telemetry table.
        val signature = criticalDeviations.joinToString("|") { it.jointName }
        val now = System.currentTimeMillis()
        if (signature == lastFaultSignature && now - lastFaultLogAt < 2000L) return
        lastFaultSignature = signature
        lastFaultLogAt = now

        criticalDeviations.forEach { deviation ->
            activeFaultsBuffer.add(
                com.fitlens.app.data.model.BiomechanicalFaultEntity(
                    sessionId = 0L, // bound to the real session id on finishWorkout()
                    exerciseName = frame.currentExercise,
                    jointName = deviation.jointName,
                    jointAngleDegrees = frame.currentAngle,
                    faultMessage = deviation.message,
                    severity = deviation.severity.name
                )
            )
        }
    }

    fun startWorkout(exercise: String = "Bodyweight Squats", targetReps: Int = 10, sets: Int = 3) {        previousRepCount = 0
        previousPhase = WorkoutPhase.ACTIVE_SET
        lastFaultSignature = null
        lastFaultLogAt = 0L
        sessionPersisted = false
        _lastSavedSession.value = null
        activeVitalsBuffer.clear()
        activeFaultsBuffer.clear()
        workoutManager.startWorkout(exercise, targetReps, sets)
        audioCoach.announcePhase("Starting $exercise. ${sets} sets of $targetReps reps.")
    }

    fun completeSet() {
        workoutManager.completeSet()
    }

    fun skipRest() {
        audioCoach.announcePhase("Rest skipped. Let's go!")
        workoutManager.skipRest()
    }

    fun finishWorkout() {
        // Guard against duplicate/invalid finishes: nothing to save while IDLE,
        // and each workout persists at most once. COMPLETED is intentionally NOT
        // rejected — WorkoutManager.completeSet() can flip the phase to COMPLETED
        // on its own (auto-completion), and this call is what persists that session.
        val phase = workoutState.value.phase
        if (phase == WorkoutPhase.IDLE || sessionPersisted) return
        sessionPersisted = true

        val session = workoutManager.finishWorkout()
        viewModelScope.launch {
            val sessionId = repository.saveSession(session)
            _lastSavedSession.value = session.copy(id = sessionId)

            // Persist time-series vitals & faults strictly in local SQLite
            if (activeVitalsBuffer.isNotEmpty()) {
                val boundVitals = activeVitalsBuffer.map { it.copy(sessionId = sessionId) }
                repository.recordVitalsTelemetry(boundVitals)
            }
            if (activeFaultsBuffer.isNotEmpty()) {
                val boundFaults = activeFaultsBuffer.map { it.copy(sessionId = sessionId) }
                repository.recordBiomechanicalFaults(boundFaults)
            }

            val recent = repository.getRecentSessions()
            _latestInsight.value = progressAnalyzer.analyzeProgression(recent)
        }
    }

    /**
     * Simulates rep cycle and updates live landmarks for SkeletonOverlay and coaching.
     */
    fun simulateRep(withValgusError: Boolean = false) {
        viewModelScope.launch {
            val kneeXVal = if (withValgusError) 0.48f else 0.42f
            val eccentricLandmarks = mapOf(
                PoseLandmarkManager.LEFT_SHOULDER to LandmarkPoint(0.42f, 0.25f, 0f),
                PoseLandmarkManager.RIGHT_SHOULDER to LandmarkPoint(0.58f, 0.25f, 0f),
                PoseLandmarkManager.LEFT_ELBOW to LandmarkPoint(0.38f, 0.35f, 0f),
                PoseLandmarkManager.RIGHT_ELBOW to LandmarkPoint(0.62f, 0.35f, 0f),
                PoseLandmarkManager.LEFT_WRIST to LandmarkPoint(0.40f, 0.45f, 0f),
                PoseLandmarkManager.RIGHT_WRIST to LandmarkPoint(0.60f, 0.45f, 0f),
                PoseLandmarkManager.LEFT_HIP to LandmarkPoint(0.44f, 0.50f, 0f),
                PoseLandmarkManager.LEFT_KNEE to LandmarkPoint(kneeXVal, 0.70f, 0f),
                PoseLandmarkManager.LEFT_ANKLE to LandmarkPoint(0.40f, 0.90f, 0f),
                PoseLandmarkManager.RIGHT_HIP to LandmarkPoint(0.56f, 0.50f, 0f),
                PoseLandmarkManager.RIGHT_KNEE to LandmarkPoint(0.58f, 0.70f, 0f),
                PoseLandmarkManager.RIGHT_ANKLE to LandmarkPoint(0.60f, 0.90f, 0f)
            )

            _currentLandmarks.value = eccentricLandmarks
            val downFrame = poseManager.analyzeLandmarks(eccentricLandmarks)
            workoutManager.onPoseFrame(downFrame)

            // Simulate HR exertion
            vitalsAnalyzer.simulateExerciseExertion(0.75f)

            // Concentric phase: standing upright
            val standingLandmarks = mapOf(
                PoseLandmarkManager.LEFT_SHOULDER to LandmarkPoint(0.42f, 0.20f, 0f),
                PoseLandmarkManager.RIGHT_SHOULDER to LandmarkPoint(0.58f, 0.20f, 0f),
                PoseLandmarkManager.LEFT_ELBOW to LandmarkPoint(0.38f, 0.35f, 0f),
                PoseLandmarkManager.RIGHT_ELBOW to LandmarkPoint(0.62f, 0.35f, 0f),
                PoseLandmarkManager.LEFT_WRIST to LandmarkPoint(0.40f, 0.50f, 0f),
                PoseLandmarkManager.RIGHT_WRIST to LandmarkPoint(0.60f, 0.50f, 0f),
                PoseLandmarkManager.LEFT_HIP to LandmarkPoint(0.45f, 0.45f, 0f),
                PoseLandmarkManager.LEFT_KNEE to LandmarkPoint(0.45f, 0.68f, 0f),
                PoseLandmarkManager.LEFT_ANKLE to LandmarkPoint(0.45f, 0.92f, 0f),
                PoseLandmarkManager.RIGHT_HIP to LandmarkPoint(0.55f, 0.45f, 0f),
                PoseLandmarkManager.RIGHT_KNEE to LandmarkPoint(0.55f, 0.68f, 0f),
                PoseLandmarkManager.RIGHT_ANKLE to LandmarkPoint(0.55f, 0.92f, 0f)
            )

            _currentLandmarks.value = standingLandmarks
            val upFrame = poseManager.analyzeLandmarks(standingLandmarks)
            workoutManager.onPoseFrame(upFrame)
        }
    }

    override fun onCleared() {
        super.onCleared()
        val sdk = SmartSpectraSdk.shared
        sdk.metrics.removeObserver(sdkMetricsObserver)
        sdk.error.removeObserver(sdkErrorObserver)
        sdk.validationStatus.removeObserver(sdkValidationObserver)
        vitalsAnalyzer.resetCustomInput()
        audioCoach.shutdown()
        workoutManager.cleanup()
    }
}
