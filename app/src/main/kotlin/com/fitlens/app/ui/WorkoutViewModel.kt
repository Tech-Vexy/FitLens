package com.fitlens.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.fitlens.app.ai.FitnessCoachAgent
import com.fitlens.app.ai.MesocyclePlanResult
import com.fitlens.app.ai.WorkoutPlanResult
import com.fitlens.app.ai.adk.AdkAuthMode
import com.fitlens.app.audio.FitLensAudioCoach
import com.fitlens.app.camera.DualStreamAnalyzer
import com.fitlens.app.data.WorkoutDatabase
import com.fitlens.app.data.WorkoutRepository
import com.fitlens.app.data.model.BiomechanicalFaultEntity
import com.fitlens.app.data.model.VitalsLogEntity
import com.fitlens.app.data.model.WorkoutSessionEntity
import com.fitlens.app.data.profile.UserPreferencesRepository
import com.fitlens.app.data.profile.UserProfile
import com.fitlens.app.pose.DeviationSeverity
import com.fitlens.app.pose.LandmarkPoint
import com.fitlens.app.pose.PoseAnalysisFrame
import com.fitlens.app.pose.PoseLandmarkManager
import com.fitlens.app.testing.HeadlessVideoFeeder
import com.fitlens.app.testing.SmokeTestGateResult
import com.fitlens.app.vitals.PresageFrameAnalyzer
import com.fitlens.app.vitals.PresageManager
import com.fitlens.app.workout.ProgressAnalyzer
import com.fitlens.app.workout.ProgressionInsight
import com.fitlens.app.workout.WorkoutManager
import com.fitlens.app.workout.WorkoutPhase
import com.fitlens.app.workout.WorkoutState
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.proto.InsightsProto
import com.presagetech.smartspectra.proto.MetricsProto.ExpressionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "WorkoutViewModel"
        private const val MAX_WAVEFORM_SAMPLES = 150
    }

    val presageManager = PresageManager()
    private val coachAgent = FitnessCoachAgent()
    private val poseManager = PoseLandmarkManager(application)
    private val vitalsAnalyzer = PresageFrameAnalyzer()
    private val workoutManager = WorkoutManager(coachAgent, poseManager)
    private val progressAnalyzer = ProgressAnalyzer()
    private val audioCoach = FitLensAudioCoach(application)
    private val userPrefsRepo = UserPreferencesRepository(application)
    private val headlessFeeder = HeadlessVideoFeeder(vitalsAnalyzer, poseManager)

    /**
     * Single shared CameraX analyzer used by both the Vitals Dashboard (enablePoseAnalysis=false)
     * and the Live Workout HUD (enablePoseAnalysis=true).
     */
    val dualStreamAnalyzer = DualStreamAnalyzer(
        presageAnalyzer = vitalsAnalyzer,
        poseManager = poseManager,
        enablePoseAnalysis = false,
        onPoseFrame = { frame -> onCameraPoseFrame(frame) }
    )

    private val repository: WorkoutRepository by lazy {
        val db = WorkoutDatabase.getDatabase(application)
        WorkoutRepository(db.workoutDao())
    }

    val workoutState: StateFlow<WorkoutState> = workoutManager.state
    val vitalsFlow = vitalsAnalyzer.vitalsFlow

    val userProfile: StateFlow<UserProfile> = userPrefsRepo.userProfileFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserProfile()
    )

    private val _isAudioMuted = MutableStateFlow(false)
    val isAudioMuted: StateFlow<Boolean> = _isAudioMuted.asStateFlow()

    private val _currentLandmarks = MutableStateFlow<Map<Int, LandmarkPoint>>(emptyMap())
    val currentLandmarks: StateFlow<Map<Int, LandmarkPoint>> = _currentLandmarks.asStateFlow()

    private val _poseFrameSize = MutableStateFlow(0 to 0)
    val poseFrameSize: StateFlow<Pair<Int, Int>> = _poseFrameSize.asStateFlow()

    val workoutHistory: StateFlow<List<WorkoutSessionEntity>> = repository.allSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allBiomechanicalFaults: StateFlow<List<BiomechanicalFaultEntity>> = repository.allFaults.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _latestInsight = MutableStateFlow<ProgressionInsight?>(null)
    val latestInsight: StateFlow<ProgressionInsight?> = _latestInsight.asStateFlow()

    private val _lastSavedSession = MutableStateFlow<WorkoutSessionEntity?>(null)
    val lastSavedSession: StateFlow<WorkoutSessionEntity?> = _lastSavedSession.asStateFlow()

    private val _generatedPlan = MutableStateFlow<WorkoutPlanResult?>(null)
    val generatedPlan: StateFlow<WorkoutPlanResult?> = _generatedPlan.asStateFlow()

    private val _generatedMesocycle = MutableStateFlow<MesocyclePlanResult?>(null)
    val generatedMesocycle: StateFlow<MesocyclePlanResult?> = _generatedMesocycle.asStateFlow()

    private val _isGeneratingAiPlan = MutableStateFlow(false)
    val isGeneratingAiPlan: StateFlow<Boolean> = _isGeneratingAiPlan.asStateFlow()

    private val _smokeTestResult = MutableStateFlow<SmokeTestGateResult?>(null)
    val smokeTestResult: StateFlow<SmokeTestGateResult?> = _smokeTestResult.asStateFlow()

    private val _isRunningSmokeTest = MutableStateFlow(false)
    val isRunningSmokeTest: StateFlow<Boolean> = _isRunningSmokeTest.asStateFlow()

    private var previousRepCount = 0
    private var previousPhase = WorkoutPhase.IDLE

    // Thread-safe active session buffers for local SQLite persistence
    private val activeVitalsBuffer = CopyOnWriteArrayList<VitalsLogEntity>()
    private val activeFaultsBuffer = CopyOnWriteArrayList<BiomechanicalFaultEntity>()

    @Volatile private var lastFaultSignature: String? = null
    @Volatile private var lastFaultLogAt: Long = 0L
    @Volatile private var sessionPersisted = false

    private val sdkMetricsObserver = Observer<com.presagetech.smartspectra.proto.MetricsProto.Metrics?> { metrics ->
        if (metrics == null) return@Observer
        var hr = vitalsFlow.value.heartRateBpm
        var hrv = vitalsFlow.value.heartRateVariabilityMs
        var br = vitalsFlow.value.breathingRateBpm
        var confidence = vitalsFlow.value.signalConfidence
        var expression = vitalsFlow.value.facialExpression
        val pulseTrace = ArrayList<Float>(MAX_WAVEFORM_SAMPLES)
        val chestTrace = ArrayList<Float>(MAX_WAVEFORM_SAMPLES)
        val abdomenTrace = ArrayList<Float>(MAX_WAVEFORM_SAMPLES)

        if (metrics.hasCardio()) {
            metrics.cardio.pulseRateList.lastOrNull { it.timestamp > 0 }?.let {
                hr = it.value.toDouble()
                confidence = it.confidence
            }
            metrics.cardio.hrvList.lastOrNull { it.rmssd > 0 }?.let { hrv = it.rmssd }
            metrics.cardio.arterialPressureTraceList
                .takeLast(MAX_WAVEFORM_SAMPLES)
                .forEach { pulseTrace.add(it.value) }
        }
        if (metrics.hasBreathing()) {
            metrics.breathing.rateList.lastOrNull()?.let { br = it.value.toDouble() }
            metrics.breathing.upperTraceList
                .takeLast(MAX_WAVEFORM_SAMPLES)
                .forEach { chestTrace.add(it.value) }
            metrics.breathing.lowerTraceList
                .takeLast(MAX_WAVEFORM_SAMPLES)
                .forEach { abdomenTrace.add(it.value) }
        }
        if (metrics.hasFace()) {
            metrics.face.expressionList.lastOrNull()?.scoresList
                ?.filter { it.confidence > 0f }
                ?.maxByOrNull { it.confidence }
                ?.let { top -> top.type.toReadableLabel()?.let { expression = it } }
        }

        if (hr > 0.0 || br > 0.0 || pulseTrace.isNotEmpty()) {
            vitalsAnalyzer.updateVitalsReading(
                hr = hr,
                hrv = hrv,
                breathingRate = br,
                confidence = confidence,
                pulseTrace = if (pulseTrace.isNotEmpty()) pulseTrace else vitalsFlow.value.arterialPressureTrace,
                chestTrace = if (chestTrace.isNotEmpty()) chestTrace else vitalsFlow.value.chestBreathingTrace,
                abdomenTrace = if (abdomenTrace.isNotEmpty()) abdomenTrace else vitalsFlow.value.abdomenBreathingTrace,
                expression = expression
            )
        }
    }

    private val sdkInsightObserver = Observer<InsightsProto.Insight?> { insight ->
        if (insight == null) return@Observer
        if (insight.hasAnalysis() && insight.analysis.isNotBlank()) {
            vitalsAnalyzer.updateSmartSpectraInsight(insight.analysis)
        } else if (insight.hasError() && insight.error.isNotBlank()) {
            Log.w(TAG, "SmartSpectra insight warning: ${insight.error}")
        }
    }

    private val sdkErrorObserver = Observer<com.presagetech.smartspectra.SmartSpectraError?> { error ->
        if (error != null) {
            Log.e(TAG, "SmartSpectra SDK error: ${error.message}")
        }
    }

    private val sdkValidationObserver = Observer<com.presagetech.smartspectra.ValidationStatus?> { status ->
        val hint = status?.hint.orEmpty()
        vitalsAnalyzer.updateValidationHint(hint)
        if (hint.isNotEmpty()) {
            Log.d(TAG, "SmartSpectra validation: $hint")
        }
    }

    init {
        val sdk = SmartSpectraSdk.shared
        sdk.metrics.observeForever(sdkMetricsObserver)
        sdk.insight.observeForever(sdkInsightObserver)
        sdk.error.observeForever(sdkErrorObserver)
        sdk.validationStatus.observeForever(sdkValidationObserver)

        // Propagate UserProfile, Karvonen thresholds, SDK telemetry opt-out & AI mode
        viewModelScope.launch {
            userPrefsRepo.userProfileFlow.collect { profile ->
                vitalsAnalyzer.updateUserProfile(profile)
                workoutManager.updateRestingHeartRate(profile.restingHeartRate)
                presageManager.setTelemetryEnabled(profile.smartSpectraTelemetryEnabled)
                coachAgent.setAuthMode(
                    if (profile.preferLocalEdgeAi) AdkAuthMode.ON_DEVICE_EDGE
                    else AdkAuthMode.FIREBASE_AI_LOGIC
                )
            }
        }

        // Collect vitals flow into workout manager and local SQLite buffer
        viewModelScope.launch {
            vitalsFlow.collect { vitals ->
                workoutManager.onVitalsReading(vitals)
                val phase = workoutManager.state.value.phase
                if (phase == WorkoutPhase.ACTIVE_SET ||
                    phase == WorkoutPhase.REST_INTERVAL ||
                    phase == WorkoutPhase.COOLDOWN
                ) {
                    if (vitals.heartRateBpm > 40.0) {
                        activeVitalsBuffer.add(
                            VitalsLogEntity(
                                sessionId = 0L,
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

        viewModelScope.launch {
            workoutHistory.collect { sessions ->
                _latestInsight.value = progressAnalyzer.analyzeProgression(sessions)
            }
        }

        // Hands-free audio coaching triggers on state transitions
        viewModelScope.launch {
            workoutState.collect { state ->
                if (!state.isIsometricHold &&
                    state.completedReps > previousRepCount &&
                    state.phase == WorkoutPhase.ACTIVE_SET
                ) {
                    audioCoach.speakRepCount(state.completedReps)
                    previousRepCount = state.completedReps
                } else if (state.isIsometricHold &&
                    state.completedReps > 0 &&
                    state.completedReps != previousRepCount &&
                    state.completedReps % 10 == 0 &&
                    state.phase == WorkoutPhase.ACTIVE_SET
                ) {
                    audioCoach.speakCorrection("${state.completedReps} seconds held. Keep core braced.")
                    previousRepCount = state.completedReps
                }

                if (state.phase != previousPhase) {
                    when (state.phase) {
                        WorkoutPhase.WARMUP -> {
                            audioCoach.announcePhase("Dynamic warmup started. Breathe steadily.")
                        }
                        WorkoutPhase.REST_INTERVAL -> {
                            val msg = if (state.isRestExtended) {
                                "Set complete. Heart rate elevated, resting for ${state.restSecondsRemaining} seconds. Follow the breathing ring."
                            } else {
                                "Set complete. Rest for ${state.restSecondsRemaining} seconds."
                            }
                            audioCoach.announcePhase(msg)
                        }
                        WorkoutPhase.ACTIVE_SET -> {
                            previousRepCount = 0
                            audioCoach.announcePhase("Begin ${state.exerciseName}, Set ${state.currentSet}. Focus on form.")
                        }
                        WorkoutPhase.COOLDOWN -> {
                            audioCoach.announcePhase("Final set complete! Entering recovery breathing cooldown.")
                        }
                        WorkoutPhase.COMPLETED -> {
                            audioCoach.announcePhase("Workout complete! Great effort.")
                        }
                        else -> Unit
                    }
                    previousPhase = state.phase
                }

                state.activeCoachAlert?.let { alert ->
                    if (alert.startsWith("⚠️") || alert.contains("caving") || alert.contains("sagging")) {
                        audioCoach.speakCorrection(alert, isUrgent = true)
                    }
                }
            }
        }
    }

    fun setPoseAnalysisEnabled(enabled: Boolean) {
        dualStreamAnalyzer.enablePoseAnalysis = enabled
        if (!enabled) {
            _currentLandmarks.value = emptyMap()
        }
    }

    fun prepareVitalsCustomInput() {
        vitalsAnalyzer.ensureCustomInput()
    }

    fun resetVitalsInput() {
        vitalsAnalyzer.resetCustomInput()
    }

    fun requestSmartSpectraInsight() {
        val prompt = "Summarize current pulse, breathing cadence, and autonomic recovery readiness in one friendly wellness coaching sentence (non-medical)."
        val reqId = runCatching {
            SmartSpectraSdk.shared.requestInsight(prompt)
        }.getOrDefault(-1)

        if (reqId <= 0 || vitalsFlow.value.smartSpectraLlmInsight.isNullOrBlank()) {
            val v = vitalsFlow.value
            val bio = coachAgent.evaluateBiofeedback(
                heartRateBpm = v.heartRateBpm,
                breathingRateBpm = v.breathingRateBpm,
                hrvRmssdMs = v.heartRateVariabilityMs,
                facialExpression = v.facialExpression
            )
            vitalsAnalyzer.updateSmartSpectraInsight(
                "${bio.autonomicReadinessLabel} • ${bio.breathingDirective}"
            )
        }
    }

    fun toggleAudioMute() {
        val newMuted = !_isAudioMuted.value
        _isAudioMuted.value = newMuted
        audioCoach.setMuted(newMuted)
    }

    fun updateUserProfile(updated: UserProfile) {
        viewModelScope.launch {
            userPrefsRepo.updateProfile(updated)
        }
    }

    fun acceptSensingConsent(telemetryEnabled: Boolean, preferLocalEdgeAi: Boolean) {
        viewModelScope.launch {
            val current = userProfile.value
            userPrefsRepo.updateProfile(
                current.copy(
                    hasAcceptedSensingConsent = true,
                    smartSpectraTelemetryEnabled = telemetryEnabled,
                    preferLocalEdgeAi = preferLocalEdgeAi
                )
            )
        }
    }

    fun deleteAllLocalData() {
        viewModelScope.launch {
            repository.deleteAllLocalHealthData()
            activeVitalsBuffer.clear()
            activeFaultsBuffer.clear()
            _lastSavedSession.value = null
        }
    }

    fun generateAiPlanAndMesocycle(
        goal: String,
        experience: String,
        equipment: String,
        durationMinutes: Int
    ) {
        viewModelScope.launch {
            _isGeneratingAiPlan.value = true
            runCatching {
                val plan = coachAgent.requestWorkoutPlan(
                    goal = goal,
                    experience = experience,
                    equipment = equipment,
                    durationMinutes = durationMinutes
                )
                val mesocycle = coachAgent.requestMesocyclePlan(
                    goal = goal,
                    weeks = 4,
                    frequencyPerWeek = if (durationMinutes >= 40) 4 else 3
                )
                _generatedPlan.value = plan
                _generatedMesocycle.value = mesocycle
            }.onFailure { e ->
                Log.w(TAG, "AI plan generation failed: ${e.message}")
            }
            _isGeneratingAiPlan.value = false
        }
    }

    fun runSmokeTest() {
        viewModelScope.launch {
            _isRunningSmokeTest.value = true
            _smokeTestResult.value = headlessFeeder.runSmokeTest(totalFrames = 20, frameIntervalMs = 20L)
            _isRunningSmokeTest.value = false
        }
    }

    private fun onCameraPoseFrame(frame: PoseAnalysisFrame) {
        val wasActiveSet = workoutManager.state.value.phase == WorkoutPhase.ACTIVE_SET

        _currentLandmarks.value = frame.landmarks
        if (frame.frameWidth > 0 && frame.frameHeight > 0) {
            _poseFrameSize.value = frame.frameWidth to frame.frameHeight
        }
        workoutManager.onPoseFrame(frame)

        val criticalDeviations = frame.activeDeviations.filter {
            it.severity == DeviationSeverity.CRITICAL
        }
        if (criticalDeviations.isEmpty() || !wasActiveSet) return

        val signature = criticalDeviations.joinToString("|") { it.jointName }
        val now = System.currentTimeMillis()
        if (signature == lastFaultSignature && now - lastFaultLogAt < 2000L) return
        lastFaultSignature = signature
        lastFaultLogAt = now

        criticalDeviations.forEach { deviation ->
            activeFaultsBuffer.add(
                BiomechanicalFaultEntity(
                    sessionId = 0L,
                    exerciseName = frame.currentExercise,
                    jointName = deviation.jointName,
                    jointAngleDegrees = frame.currentAngle,
                    faultMessage = deviation.message,
                    severity = deviation.severity.name
                )
            )
        }
    }

    fun startWorkout(
        exercise: String = "Bodyweight Squats",
        targetReps: Int = 10,
        sets: Int = 3,
        includeWarmupAndCooldown: Boolean = false
    ) {
        startRoutine(
            exercises = listOf(exercise),
            targetReps = targetReps,
            setsPerExercise = sets,
            baseRestSeconds = 45,
            includeWarmupAndCooldown = includeWarmupAndCooldown
        )
    }

    fun startRoutine(
        exercises: List<String>,
        targetReps: Int = 10,
        setsPerExercise: Int = 2,
        baseRestSeconds: Int = 45,
        includeWarmupAndCooldown: Boolean = true
    ) {
        previousRepCount = 0
        previousPhase = if (includeWarmupAndCooldown) WorkoutPhase.WARMUP else WorkoutPhase.ACTIVE_SET
        lastFaultSignature = null
        lastFaultLogAt = 0L
        sessionPersisted = false
        _lastSavedSession.value = null
        activeVitalsBuffer.clear()
        activeFaultsBuffer.clear()

        workoutManager.startRoutine(
            exercises = exercises,
            targetReps = targetReps,
            setsPerExercise = setsPerExercise,
            baseRestSeconds = baseRestSeconds,
            includeWarmup = includeWarmupAndCooldown,
            includeCooldown = includeWarmupAndCooldown
        )
        val label = if (exercises.size > 1) "${exercises.size}-exercise routine" else exercises.firstOrNull() ?: "Workout"
        audioCoach.announcePhase("Starting $label.")
    }

    fun skipWarmup() {
        workoutManager.skipWarmup()
    }

    fun completeSet() {
        workoutManager.completeSet()
    }

    fun skipRest() {
        audioCoach.announcePhase("Rest skipped. Let's go!")
        workoutManager.skipRest()
    }

    fun finishWorkout() {
        val phase = workoutState.value.phase
        if (phase == WorkoutPhase.IDLE || sessionPersisted) return
        sessionPersisted = true

        val session = workoutManager.finishWorkout()
        viewModelScope.launch {
            val sessionId = repository.saveSession(session)
            _lastSavedSession.value = session.copy(id = sessionId)

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
     * Simulates high aerobic/anaerobic exertion + facial strain so judges can see
     * adaptive rest (+30s), coherent breathing pacer, and expression cues immediately.
     */
    fun simulateHighExertionAndStrain() {
        vitalsAnalyzer.simulateExerciseExertion(0.88f)
    }

    /**
     * Simulates exercise-appropriate movement or isometric hold progression, with optional
     * biomechanical form fault (e.g., Squat knee valgus or Plank hip sagging).
     */
    fun simulateRep(withValgusError: Boolean = false) {
        viewModelScope.launch {
            val state = workoutState.value
            _poseFrameSize.value = 0 to 0

            if (state.isIsometricHold) {
                val plankLandmarks = if (withValgusError) {
                    mapOf(
                        PoseLandmarkManager.LEFT_SHOULDER to LandmarkPoint(0.22f, 0.42f, 0f),
                        PoseLandmarkManager.RIGHT_SHOULDER to LandmarkPoint(0.25f, 0.42f, 0f),
                        PoseLandmarkManager.LEFT_ELBOW to LandmarkPoint(0.22f, 0.62f, 0f),
                        PoseLandmarkManager.RIGHT_ELBOW to LandmarkPoint(0.25f, 0.62f, 0f),
                        PoseLandmarkManager.LEFT_WRIST to LandmarkPoint(0.14f, 0.62f, 0f),
                        PoseLandmarkManager.RIGHT_WRIST to LandmarkPoint(0.16f, 0.62f, 0f),
                        PoseLandmarkManager.LEFT_HIP to LandmarkPoint(0.50f, 0.68f, 0f),
                        PoseLandmarkManager.RIGHT_HIP to LandmarkPoint(0.52f, 0.68f, 0f),
                        PoseLandmarkManager.LEFT_KNEE to LandmarkPoint(0.68f, 0.60f, 0f),
                        PoseLandmarkManager.RIGHT_KNEE to LandmarkPoint(0.70f, 0.60f, 0f),
                        PoseLandmarkManager.LEFT_ANKLE to LandmarkPoint(0.86f, 0.48f, 0f),
                        PoseLandmarkManager.RIGHT_ANKLE to LandmarkPoint(0.88f, 0.48f, 0f)
                    )
                } else {
                    mapOf(
                        PoseLandmarkManager.LEFT_SHOULDER to LandmarkPoint(0.22f, 0.44f, 0f),
                        PoseLandmarkManager.RIGHT_SHOULDER to LandmarkPoint(0.25f, 0.44f, 0f),
                        PoseLandmarkManager.LEFT_ELBOW to LandmarkPoint(0.22f, 0.62f, 0f),
                        PoseLandmarkManager.RIGHT_ELBOW to LandmarkPoint(0.25f, 0.62f, 0f),
                        PoseLandmarkManager.LEFT_WRIST to LandmarkPoint(0.14f, 0.62f, 0f),
                        PoseLandmarkManager.RIGHT_WRIST to LandmarkPoint(0.16f, 0.62f, 0f),
                        PoseLandmarkManager.LEFT_HIP to LandmarkPoint(0.52f, 0.47f, 0f),
                        PoseLandmarkManager.RIGHT_HIP to LandmarkPoint(0.54f, 0.47f, 0f),
                        PoseLandmarkManager.LEFT_KNEE to LandmarkPoint(0.70f, 0.49f, 0f),
                        PoseLandmarkManager.RIGHT_KNEE to LandmarkPoint(0.72f, 0.49f, 0f),
                        PoseLandmarkManager.LEFT_ANKLE to LandmarkPoint(0.86f, 0.50f, 0f),
                        PoseLandmarkManager.RIGHT_ANKLE to LandmarkPoint(0.88f, 0.50f, 0f)
                    )
                }
                val frame = poseManager.analyzeLandmarks(plankLandmarks)
                onCameraPoseFrame(frame)
                if (!withValgusError) {
                    workoutManager.advanceIsometricHold(secondsDelta = 5)
                }
                vitalsAnalyzer.simulateExerciseExertion(if (withValgusError) 0.84f else 0.68f)
                return@launch
            }

            val kneeXVal = if (withValgusError) 0.485f else 0.41f
            val eccentricLandmarks = mapOf(
                PoseLandmarkManager.LEFT_SHOULDER to LandmarkPoint(0.42f, 0.25f, 0f),
                PoseLandmarkManager.RIGHT_SHOULDER to LandmarkPoint(0.58f, 0.25f, 0f),
                PoseLandmarkManager.LEFT_ELBOW to LandmarkPoint(0.35f, 0.36f, 0f),
                PoseLandmarkManager.RIGHT_ELBOW to LandmarkPoint(0.65f, 0.36f, 0f),
                PoseLandmarkManager.LEFT_WRIST to LandmarkPoint(0.41f, 0.28f, 0f),
                PoseLandmarkManager.RIGHT_WRIST to LandmarkPoint(0.59f, 0.28f, 0f),
                PoseLandmarkManager.LEFT_HIP to LandmarkPoint(0.42f, 0.52f, 0f),
                PoseLandmarkManager.LEFT_KNEE to LandmarkPoint(kneeXVal, 0.70f, 0f),
                PoseLandmarkManager.LEFT_ANKLE to LandmarkPoint(0.36f, 0.88f, 0f),
                PoseLandmarkManager.RIGHT_HIP to LandmarkPoint(0.58f, 0.52f, 0f),
                PoseLandmarkManager.RIGHT_KNEE to LandmarkPoint(if (withValgusError) 0.515f else 0.59f, 0.70f, 0f),
                PoseLandmarkManager.RIGHT_ANKLE to LandmarkPoint(0.64f, 0.88f, 0f)
            )

            val downFrame = poseManager.analyzeLandmarks(eccentricLandmarks)
            onCameraPoseFrame(downFrame)
            vitalsAnalyzer.simulateExerciseExertion(if (withValgusError) 0.86f else 0.72f)

            if (!withValgusError) {
                val standingLandmarks = mapOf(
                    PoseLandmarkManager.LEFT_SHOULDER to LandmarkPoint(0.42f, 0.20f, 0f),
                    PoseLandmarkManager.RIGHT_SHOULDER to LandmarkPoint(0.58f, 0.20f, 0f),
                    PoseLandmarkManager.LEFT_ELBOW to LandmarkPoint(0.38f, 0.34f, 0f),
                    PoseLandmarkManager.RIGHT_ELBOW to LandmarkPoint(0.62f, 0.34f, 0f),
                    PoseLandmarkManager.LEFT_WRIST to LandmarkPoint(0.40f, 0.48f, 0f),
                    PoseLandmarkManager.RIGHT_WRIST to LandmarkPoint(0.60f, 0.48f, 0f),
                    PoseLandmarkManager.LEFT_HIP to LandmarkPoint(0.45f, 0.46f, 0f),
                    PoseLandmarkManager.LEFT_KNEE to LandmarkPoint(0.45f, 0.68f, 0f),
                    PoseLandmarkManager.LEFT_ANKLE to LandmarkPoint(0.45f, 0.91f, 0f),
                    PoseLandmarkManager.RIGHT_HIP to LandmarkPoint(0.55f, 0.46f, 0f),
                    PoseLandmarkManager.RIGHT_KNEE to LandmarkPoint(0.55f, 0.68f, 0f),
                    PoseLandmarkManager.RIGHT_ANKLE to LandmarkPoint(0.55f, 0.91f, 0f)
                )
                val upFrame = poseManager.analyzeLandmarks(standingLandmarks)
                onCameraPoseFrame(upFrame)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val sdk = SmartSpectraSdk.shared
        sdk.metrics.removeObserver(sdkMetricsObserver)
        sdk.insight.removeObserver(sdkInsightObserver)
        sdk.error.removeObserver(sdkErrorObserver)
        sdk.validationStatus.removeObserver(sdkValidationObserver)
        vitalsAnalyzer.resetCustomInput()
        poseManager.close()
        audioCoach.shutdown()
        workoutManager.cleanup()
    }
}

private fun ExpressionType.toReadableLabel(): String? = when (this) {
    ExpressionType.HAPPY    -> "Happy"
    ExpressionType.NEUTRAL  -> "Neutral"
    ExpressionType.SAD      -> "Sad"
    ExpressionType.ANGRY    -> "Angry"
    ExpressionType.SURPRISE -> "Surprise"
    ExpressionType.FEAR     -> "Fear"
    ExpressionType.DISGUST  -> "Disgust"
    ExpressionType.CONTEMPT -> "Contempt"
    else -> null
}
