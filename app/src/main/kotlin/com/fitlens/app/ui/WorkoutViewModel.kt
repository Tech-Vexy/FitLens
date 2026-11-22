package com.fitlens.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.fitlens.app.ai.FitnessCoachAgent
import com.fitlens.app.ai.FitnessTools
import com.fitlens.app.ai.WorkoutPlanResult
import com.fitlens.app.ai.adk.AdkAuthMode
import com.fitlens.app.ai.media.ExerciseMediaPack
import com.fitlens.app.ai.media.WorkoutMediaCloudGenerator
import com.fitlens.app.ai.media.WorkoutMediaGenerator
import com.fitlens.app.ai.media.WorkoutMediaSource
import com.fitlens.app.ai.live.BiometricTrendTracker
import com.fitlens.app.ai.live.GeminiLiveAssistant
import com.fitlens.app.ai.live.LiveAssistantState
import com.fitlens.app.ai.live.LiveConnectionState
import com.fitlens.app.ai.live.MoodSpeculation
import com.fitlens.app.ai.live.MoodSpeculator
import com.fitlens.app.audio.FitLensAudioCoach
import com.fitlens.app.camera.DualStreamAnalyzer
import com.fitlens.app.data.WorkoutDatabase
import com.fitlens.app.data.WorkoutRepository
import com.fitlens.app.data.model.WorkoutSessionEntity
import com.fitlens.app.data.profile.UserPreferencesRepository
import com.fitlens.app.data.profile.UserProfile
import com.fitlens.app.testing.HeadlessVideoFeeder
import com.fitlens.app.testing.SmokeTestGateResult
import com.fitlens.app.vitals.PresageFrameAnalyzer
import com.fitlens.app.vitals.PresageManager
import com.fitlens.app.workout.WorkoutManager
import com.fitlens.app.workout.WorkoutPhase
import com.fitlens.app.workout.WorkoutState
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.proto.InsightsProto
import com.presagetech.smartspectra.proto.MetricsProto.ExpressionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "WorkoutViewModel"
        private const val MAX_WAVEFORM_SAMPLES = 200
        private const val INSIGHT_INTERVAL_MS = 25_000L
        private const val VITALS_PUBLISH_INTERVAL_MS = 120L
        private const val SMARTSPECTRA_INSIGHT_PROMPT =
            "Analyze the user's current pulse, HRV, breathing cadence, and facial expression to infer their likely emotional state, stress level, and energy in 2 concise plain-text sentences without any Markdown."
    }

    val presageManager = PresageManager()
    private val vitalsAnalyzer = PresageFrameAnalyzer()
    private val userPrefsRepo = UserPreferencesRepository(application)
    private val liveAssistant = GeminiLiveAssistant()
    private val trendTracker = BiometricTrendTracker()

    // Biomechanics coach: deterministic workout state machine + on-device TTS cues.
    private val coachAgent = FitnessCoachAgent()
    private val workoutMediaCloudGenerator = WorkoutMediaCloudGenerator()
    private val workoutMediaPacksLock = Any()
    private val mediaEnrichmentRequested = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    val workoutManager = WorkoutManager(coachAgent = coachAgent)
    private val audioCoach = FitLensAudioCoach(application)
    private val headlessVideoFeeder = HeadlessVideoFeeder(vitalsAnalyzer)

    val vitalsFlow = vitalsAnalyzer.vitalsFlow
    val assistantState: StateFlow<LiveAssistantState> = liveAssistant.state
    val workoutState: StateFlow<WorkoutState> = workoutManager.state

    private val defaultWorkoutPlan = FitnessTools().generateWorkoutPlan(
        goal = "Six-Pack Abs",
        experience = "Intermediate",
        equipment = "Bodyweight",
        durationMinutes = 15
    )
    private val _generatedWorkoutPlan = MutableStateFlow(defaultWorkoutPlan)
    val generatedWorkoutPlan: StateFlow<WorkoutPlanResult> = _generatedWorkoutPlan.asStateFlow()

    private val _isGeneratingPlan = MutableStateFlow(false)
    val isGeneratingPlan: StateFlow<Boolean> = _isGeneratingPlan.asStateFlow()

    private val _generationStatusMessage = MutableStateFlow<String?>(null)
    val generationStatusMessage: StateFlow<String?> = _generationStatusMessage.asStateFlow()

    private val _workoutMediaPacks = MutableStateFlow<Map<String, ExerciseMediaPack>>(
        WorkoutMediaGenerator.generateMediaForRoutine(
            exercises = defaultWorkoutPlan.targetExercises,
            goal = defaultWorkoutPlan.goal,
            experienceLevel = "Intermediate"
        )
    )
    val workoutMediaPacks: StateFlow<Map<String, ExerciseMediaPack>> = _workoutMediaPacks.asStateFlow()

    private val _recentSessions = MutableStateFlow<List<WorkoutSessionEntity>>(emptyList())
    val recentSessions: StateFlow<List<WorkoutSessionEntity>> = _recentSessions.asStateFlow()

    private val _smokeTestResult = MutableStateFlow<SmokeTestGateResult?>(null)
    val smokeTestResult: StateFlow<SmokeTestGateResult?> = _smokeTestResult.asStateFlow()

    private val _isRunningSmokeTest = MutableStateFlow(false)
    val isRunningSmokeTest: StateFlow<Boolean> = _isRunningSmokeTest.asStateFlow()

    private val _moodSpeculation = MutableStateFlow(MoodSpeculator.speculate(vitalsAnalyzer.vitalsFlow.value))
    val moodSpeculation: StateFlow<MoodSpeculation> = _moodSpeculation.asStateFlow()

    private val _aiCoachingPrompt = MutableStateFlow<String?>(null)
    val aiCoachingPrompt: StateFlow<String?> = _aiCoachingPrompt.asStateFlow()

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()


    private val _isSensingActive = MutableStateFlow(false)
    val isSensingActive: StateFlow<Boolean> = _isSensingActive.asStateFlow()

    @Volatile private var lastBiometricSyncAtMs = 0L
    @Volatile private var lastInsightRequestAtMs = 0L
    @Volatile private var lastVitalsPublishAtMs = 0L
    @Volatile private var lastWorkoutPhase: WorkoutPhase? = null
    @Volatile private var completionSessionSaved = false
    @Volatile private var lastAiCoachingAtMs = 0L
    private val AI_COACHING_INTERVAL_MS = 15_000L

    val dualStreamAnalyzer = DualStreamAnalyzer(
        presageAnalyzer = vitalsAnalyzer,
        enableLiveVideoFrames = false,
        onVideoFrameForAi = { jpeg -> liveAssistant.sendVideoFrame(jpeg) }
    )

    private val repository: WorkoutRepository by lazy {
        val db = WorkoutDatabase.getDatabase(application)
        WorkoutRepository(db.workoutDao())
    }

    private val pulseTraceBuffer = ArrayDeque<Float>(MAX_WAVEFORM_SAMPLES)
    private val chestTraceBuffer = ArrayDeque<Float>(MAX_WAVEFORM_SAMPLES)
    private val abdomenTraceBuffer = ArrayDeque<Float>(MAX_WAVEFORM_SAMPLES)

    @Volatile private var latestHr: Double = 0.0
    @Volatile private var latestHrv: Double = 0.0
    @Volatile private var latestBr: Double = 0.0
    @Volatile private var latestConfidence: Float = 0.0f
    @Volatile private var latestExpression: String? = null

    private fun appendToTraceBuffer(buffer: ArrayDeque<Float>, newValues: List<Float>) {
        for (v in newValues) {
            if (buffer.size >= MAX_WAVEFORM_SAMPLES) buffer.removeFirst()
            buffer.addLast(v)
        }
    }

    @Volatile private var metricsCallCount = 0L
    @Volatile private var lastMetricsDiagAtMs = 0L

    private fun handleSdkMetrics(metrics: com.presagetech.smartspectra.proto.MetricsProto.Metrics) {
        metricsCallCount++
        val diagNow = System.currentTimeMillis()
        if (diagNow - lastMetricsDiagAtMs >= 3000L) {
            lastMetricsDiagAtMs = diagNow
            Log.d(
                TAG,
                "SDK metrics #$metricsCallCount: cardio=${metrics.hasCardio()} " +
                    "pulseN=${metrics.cardio.pulseRateCount} pulseLastTs=${metrics.cardio.pulseRateList.lastOrNull()?.timestamp} " +
                    "hrvN=${metrics.cardio.hrvCount} traceN=${metrics.cardio.arterialPressureTraceCount} | " +
                    "breathing=${metrics.hasBreathing()} brN=${metrics.breathing.rateCount} " +
                    "upN=${metrics.breathing.upperTraceCount} | " +
                    "face=${metrics.hasFace()} exprN=${metrics.face.expressionCount} | " +
                    "mapped hr=$latestHr br=$latestBr hrv=$latestHrv expr=$latestExpression"
            )
        }
        var scalarChanged = false
        var hasNewTrace = false

        if (metrics.hasCardio()) {
            metrics.cardio.pulseRateList.lastOrNull { it.timestamp > 0 }?.let {
                val newHr = it.value.toDouble()
                if (newHr != latestHr || it.confidence != latestConfidence) {
                    latestHr = newHr
                    latestConfidence = it.confidence
                    scalarChanged = true
                }
            }
            metrics.cardio.hrvList.lastOrNull { it.rmssd > 0 }?.let {
                if (it.rmssd != latestHrv) {
                    latestHrv = it.rmssd
                    scalarChanged = true
                }
            }
            val newPulse = metrics.cardio.arterialPressureTraceList.map { it.value }
            if (newPulse.isNotEmpty()) {
                appendToTraceBuffer(pulseTraceBuffer, newPulse)
                hasNewTrace = true
            }
        }
        if (metrics.hasBreathing()) {
            metrics.breathing.rateList.lastOrNull { it.value > 0 }?.let {
                val newBr = it.value.toDouble()
                if (newBr != latestBr) {
                    latestBr = newBr
                    scalarChanged = true
                }
            }
            val newChest = metrics.breathing.upperTraceList.map { it.value }
            if (newChest.isNotEmpty()) {
                appendToTraceBuffer(chestTraceBuffer, newChest)
                hasNewTrace = true
            }
            val newAbdomen = metrics.breathing.lowerTraceList.map { it.value }
            if (newAbdomen.isNotEmpty()) {
                appendToTraceBuffer(abdomenTraceBuffer, newAbdomen)
                hasNewTrace = true
            }
        }
        if (metrics.hasFace()) {
            metrics.face.expressionList.lastOrNull()?.scoresList
                ?.filter { it.confidence > 0f }
                ?.maxByOrNull { it.confidence }
                ?.let { top ->
                    top.type.toReadableLabel()?.let { label ->
                        if (label != latestExpression) {
                            latestExpression = label
                            scalarChanged = true
                        }
                    }
                }
        }

        val now = System.currentTimeMillis()
        val publishNow = scalarChanged || (now - lastVitalsPublishAtMs >= VITALS_PUBLISH_INTERVAL_MS)
        if (publishNow && (latestHr > 0.0 || latestBr > 0.0 || hasNewTrace || latestExpression != null)) {
            lastVitalsPublishAtMs = now
            vitalsAnalyzer.updateVitalsReading(
                hr = latestHr,
                hrv = latestHrv,
                breathingRate = latestBr,
                confidence = latestConfidence,
                pulseTrace = pulseTraceBuffer.toList(),
                chestTrace = chestTraceBuffer.toList(),
                abdomenTrace = abdomenTraceBuffer.toList(),
                expression = latestExpression
            )
        }
    }

    private val sdkInsightObserver = Observer<InsightsProto.Insight?> { insight ->
        if (insight == null) return@Observer
        if (insight.hasAnalysis() && insight.analysis.isNotBlank()) {
            val cleaned = MoodSpeculator.cleanAssistantText(insight.analysis)
            vitalsAnalyzer.updateSmartSpectraInsight(cleaned)
            if (liveAssistant.isLiveVoiceActive && cleaned.isNotBlank()) {
                liveAssistant.sendBiometricContext("[SMARTSPECTRA LLM INSIGHT] $cleaned")
            }
        } else if (insight.hasError() && insight.error.isNotBlank()) {
            Log.w(TAG, "SmartSpectra insight warning: ${insight.error}")
        }
    }

    private val sdkErrorObserver = Observer<com.presagetech.smartspectra.SmartSpectraError?> { error ->
        if (error != null) {
            Log.e(
                TAG,
                "SmartSpectra SDK error [${error.code}] retryable=${error.retryable}: ${error.message}"
            )
        }
    }

    private val sdkValidationObserver = Observer<com.presagetech.smartspectra.ValidationStatus?> { status ->
        val hint = status?.hint.orEmpty()
        vitalsAnalyzer.updateValidationHint(hint)
    }

    private var lastStatusLogged: Any? = null
    private val sdkStatusObserver = Observer<com.presagetech.smartspectra.ProcessingStatus?> { status ->
        if (status != null && status != lastStatusLogged) {
            lastStatusLogged = status
            Log.d(TAG, "SmartSpectra processingStatus -> $status")
            // Wire SDK lifecycle to frame analyzer's stopped-state guard so frame
            // forwarding stops immediately when the low-FPS watchdog (error 14) kills
            // the session, and resumes when a new session starts.
            when (status) {
                com.presagetech.smartspectra.ProcessingStatus.RUNNING ->
                    vitalsAnalyzer.markSdkStarted()
                com.presagetech.smartspectra.ProcessingStatus.ERROR,
                com.presagetech.smartspectra.ProcessingStatus.IDLE ->
                    vitalsAnalyzer.markSdkStopped()
                else -> Unit
            }
        }
    }

    init {
        val sdk = SmartSpectraSdk.shared
        sdk.insight.observeForever(sdkInsightObserver)
        sdk.error.observeForever(sdkErrorObserver)
        sdk.validationStatus.observeForever(sdkValidationObserver)
        sdk.processingStatus.observeForever(sdkStatusObserver)

        viewModelScope.launch {
            sdk.metricsFlow.collect { metrics ->
                handleSdkMetrics(metrics)
            }
        }

        viewModelScope.launch {
            userPrefsRepo.userProfileFlow.collect { profile ->
                _userProfile.value = profile
                vitalsAnalyzer.updateUserProfile(profile)
                workoutManager.updateRestingHeartRate(profile.restingHeartRate)
                presageManager.setTelemetryEnabled(profile.smartSpectraTelemetryEnabled)
                coachAgent.setAuthMode(
                    if (profile.preferLocalEdgeAi) AdkAuthMode.ON_DEVICE_EDGE
                    else AdkAuthMode.HYBRID_INFERENCE
                )
            }
        }

        viewModelScope.launch {
            vitalsFlow.collect { vitals ->
                val workoutPhase = workoutManager.state.value.phase
                if (workoutPhase != WorkoutPhase.IDLE && workoutPhase != WorkoutPhase.COMPLETED) {
                    workoutManager.onVitalsReading(vitals)
                }
                val restingHr = userProfile.value.restingHeartRate
                val shift = trendTracker.record(vitals)
                val trendNote = trendTracker.computeTrendNote(vitals)
                val speculation = MoodSpeculator.speculate(vitals, restingHr, trendNote)
                _moodSpeculation.value = speculation

                if (shift != null) {
                    liveAssistant.notifyBiometricShift(
                        shift = shift,
                        biometricContext = buildCurrentBiometricContext(trendNote),
                        moodTag = "${speculation.emoji} ${speculation.label}"
                    )
                }

                val now = System.currentTimeMillis()
                if (vitals.heartRateBpm > 35.0 && now - lastInsightRequestAtMs >= INSIGHT_INTERVAL_MS) {
                    lastInsightRequestAtMs = now
                    requestSmartSpectraInsight()
                }
                if (liveAssistant.isLiveVoiceActive && now - lastBiometricSyncAtMs >= 8000L) {
                    lastBiometricSyncAtMs = now
                    liveAssistant.sendBiometricContext(buildCurrentBiometricContext(trendNote))
                }

                // AI coaching: periodically evaluate holistic biofeedback during active workout
                if (workoutPhase != WorkoutPhase.IDLE &&
                    workoutPhase != WorkoutPhase.COMPLETED &&
                    vitals.heartRateBpm > 35.0 &&
                    now - lastAiCoachingAtMs >= AI_COACHING_INTERVAL_MS
                ) {
                    lastAiCoachingAtMs = now
                    evaluateAiCoaching(vitals, workoutPhase)
                }
            }
        }

        viewModelScope.launch {
            assistantState.collect { state ->
                dualStreamAnalyzer.enableLiveVideoFrames =
                    state.connectionState == LiveConnectionState.LISTENING
            }
        }

        viewModelScope.launch {
            workoutManager.state.collect { workout ->
                handleWorkoutState(workout)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.allSessions.collect { sessions ->
                    _recentSessions.value = sessions.take(5)
                }
            }
        }
    }


    private fun handleWorkoutState(workout: WorkoutState) {
        val previous = lastWorkoutPhase
        if (workout.phase != previous) {
            when (workout.phase) {
                WorkoutPhase.REST_INTERVAL ->
                    audioCoach.announcePhase(
                        "Rest ${workout.restSecondsRemaining.coerceAtLeast(1)} seconds. Recover."
                    )
                WorkoutPhase.COOLDOWN ->
                    audioCoach.announcePhase("Cooldown. Follow the breathing pacer.")
                WorkoutPhase.COMPLETED -> {
                    audioCoach.announcePhase("Workout complete. Great work.")
                    persistCompletedSession()
                }
                WorkoutPhase.ACTIVE_SET -> {
                    if (previous == WorkoutPhase.WARMUP ||
                        previous == WorkoutPhase.REST_INTERVAL ||
                        previous == null
                    ) {
                        audioCoach.announcePhase("Set ${workout.currentSet}. ${workout.exerciseName}.")
                    }
                }
                else -> Unit
            }
            lastWorkoutPhase = workout.phase
        }
    }

    private fun persistCompletedSession() {
        if (completionSessionSaved) return
        completionSessionSaved = true
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val entity = workoutManager.finishWorkout()
                repository.saveSession(entity)
            }.onFailure { e ->
                Log.w(TAG, "Failed to persist workout session: ${e.message}")
            }
        }
    }

    /**
     * Evaluates holistic biofeedback from live vitals and produces an AI coaching prompt.
     * Called periodically during active workout phases.
     */
    private fun evaluateAiCoaching(vitals: com.fitlens.app.vitals.VitalsReading, phase: WorkoutPhase) {
        val result = coachAgent.evaluateBiofeedback(
            heartRateBpm = vitals.heartRateBpm,
            breathingRateBpm = vitals.breathingRateBpm,
            hrvRmssdMs = vitals.heartRateVariabilityMs,
            facialExpression = vitals.facialExpression ?: "Neutral"
        )

        val workout = workoutManager.state.value
        val context = buildString {
            append("Phase: ${phase.name}, Exercise: ${workout.exerciseName}, Set: ${workout.currentSet}")
        }

        val prompt = buildString {
            if (result.shouldTriggerBreathingPacer) {
                append("Your breathing is elevated — try the breathing pacer: ${result.breathingDirective}. ")
            }
            if (result.expressionCue != null) {
                append(result.expressionCue).append(" ")
            }
            append("Readiness: ${result.autonomicReadinessLabel}.")
        }

        _aiCoachingPrompt.value = prompt.trim()
        Log.i(TAG, "AI Coaching [$context]: $prompt")
    }


    /**
     * Retrieves local procedure media immediately, then upgrades it in the
     * background with Nano Banana imagery and a Gemini-synthesized walkthrough.
     */
    fun ensureMediaForExercise(exercise: String): ExerciseMediaPack {
        val normalized = exercise.trim()
        val existing = _workoutMediaPacks.value[normalized]
        if (existing != null) {
            requestMediaEnrichment(normalized, existing)
            return existing
        }

        val fallback = WorkoutMediaGenerator.generateMediaForExercise(
            exerciseName = normalized,
            goal = _generatedWorkoutPlan.value.goal,
            experienceLevel = _userProfile.value.experienceLevel
        )
        synchronized(workoutMediaPacksLock) {
            _workoutMediaPacks.value = _workoutMediaPacks.value + (normalized to fallback)
        }
        requestMediaEnrichment(normalized, fallback)
        return fallback
    }

    private fun requestMediaEnrichment(exercise: String, fallback: ExerciseMediaPack) {
        if (_userProfile.value.preferLocalEdgeAi) return
        if (fallback.mediaSource == WorkoutMediaSource.HYBRID_FIREBASE_AI_LOGIC) return
        if (!mediaEnrichmentRequested.add(exercise)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val enriched = runCatching {
                    workoutMediaCloudGenerator.enrichPack(
                        pack = fallback,
                        allowCloudMedia = !_userProfile.value.preferLocalEdgeAi
                    )
                }.onFailure { e ->
                    Log.w(TAG, "Media enrichment failed for $exercise: ${e.message}")
                }.getOrNull()
                if (enriched != null && !_userProfile.value.preferLocalEdgeAi) {
                    synchronized(workoutMediaPacksLock) {
                        _workoutMediaPacks.value = _workoutMediaPacks.value + (exercise to enriched)
                    }
                }
            } finally {
                mediaEnrichmentRequested.remove(exercise)
            }
        }
    }

    private fun ensureMediaForExercises(exercises: List<String>) {
        exercises.forEach { ensureMediaForExercise(it) }
    }

    /**
     * Speaks a coaching narration cue through the audio coach.
     */
    fun speakNarration(cue: String) {
        audioCoach.announcePhase(cue)
    }

    /**
     * Updates theme preference (SYSTEM, LIGHT, DARK).
     */
    fun setThemePreference(theme: String) {
        viewModelScope.launch {
            userPrefsRepo.updateProfile(_userProfile.value.copy(themePreference = theme))
        }
    }

    /**
     * Selects an exercise and starts a fresh workout session.
     */
    fun selectExercise(exercise: String) {
        val normalized = exercise.trim()
        if (normalized.isEmpty()) return
        ensureMediaForExercise(normalized)
        val isometric = com.fitlens.app.workout.isIsometricExercise(normalized)
        completionSessionSaved = false
        lastWorkoutPhase = null
        workoutManager.startWorkout(
            exercise = normalized,
            targetReps = if (isometric) 30 else 10,
            totalSets = 3,
            baseRestSeconds = 45,
            includeWarmup = false,
            includeCooldown = true
        )
    }

    /**
     * Generates a personalized AI workout routine via [FitnessCoachAgent].
     * During workout generation, synthesizes Nanobanana procedure steps and Gemini Omni video walkthroughs!
     */
    fun generateAiWorkoutPlan(
        goal: String,
        experience: String,
        equipment: String,
        durationMinutes: Int
    ) {
        if (_isGeneratingPlan.value) return
        _isGeneratingPlan.value = true
        val preferLocalAi = _userProfile.value.preferLocalEdgeAi
        _generationStatusMessage.value = if (preferLocalAi) {
            "1/3 On-Device Edge synthesizing $goal routine..."
        } else {
            "1/3 AI synthesizing $goal routine..."
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val plan = coachAgent.requestWorkoutPlan(
                    goal = goal,
                    experience = experience,
                    equipment = equipment,
                    durationMinutes = durationMinutes
                )
                _generatedWorkoutPlan.value = plan
                _generationStatusMessage.value = if (preferLocalAi) {
                    "2/3 Synthesizing procedure graphics & biomechanics..."
                } else {
                    "2/3 Nanobanana generating procedure graphics & biomechanics..."
                }
                val missingExercises = plan.targetExercises.filter { exercise ->
                    !_workoutMediaPacks.value.containsKey(exercise)
                }
                if (missingExercises.isNotEmpty()) {
                    val fallbackPacks = WorkoutMediaGenerator.generateMediaForRoutine(
                        exercises = missingExercises,
                        goal = plan.goal,
                        experienceLevel = experience
                    )
                    synchronized(workoutMediaPacksLock) {
                        _workoutMediaPacks.value = _workoutMediaPacks.value + fallbackPacks
                    }
                }

                val packsNeedingEnrichment = plan.targetExercises.mapNotNull { exercise ->
                    _workoutMediaPacks.value[exercise]
                        ?.takeIf {
                            it.mediaSource != WorkoutMediaSource.HYBRID_FIREBASE_AI_LOGIC &&
                                exercise !in mediaEnrichmentRequested
                        }
                        ?.let { exercise to it }
                }.toMap()

                _generationStatusMessage.value = if (preferLocalAi) {
                    "3/3 Building interactive walkthrough demos..."
                } else {
                    "3/3 Gemini Omni building interactive walkthrough demos..."
                }
                if (packsNeedingEnrichment.isNotEmpty()) {
                    val enrichedPacks = workoutMediaCloudGenerator.enrichRoutine(
                        packs = packsNeedingEnrichment,
                        allowCloudMedia = !preferLocalAi
                    )
                    synchronized(workoutMediaPacksLock) {
                        _workoutMediaPacks.value = _workoutMediaPacks.value + enrichedPacks
                    }
                }

                audioCoach.announcePhase(
                    "AI Coach ready: ${plan.goal} routine generated with Nanobanana procedure graphics and Gemini Omni interactive walkthroughs."
                )
            } catch (e: Exception) {
                Log.w(TAG, "AI workout generation fallback: ${e.message}")
            } finally {
                _isGeneratingPlan.value = false
                _generationStatusMessage.value = null
            }
        }
    }

    /**
     * Starts the full multi-exercise AI routine with optional warmup and cooldown.
     */
    fun startGeneratedRoutine(
        plan: WorkoutPlanResult = _generatedWorkoutPlan.value,
        includeWarmup: Boolean = true,
        includeCooldown: Boolean = true
    ) {
        val firstEx = plan.targetExercises.firstOrNull() ?: "Bodyweight Squats"
        val isometric = com.fitlens.app.workout.isIsometricExercise(firstEx)
        ensureMediaForExercises(plan.targetExercises)
        completionSessionSaved = false
        lastWorkoutPhase = null
        workoutManager.startRoutine(
            exercises = plan.targetExercises,
            targetReps = if (isometric) 30 else 12,
            setsPerExercise = 3,
            baseRestSeconds = plan.recommendedRestIntervalSeconds,
            includeWarmup = includeWarmup,
            includeCooldown = includeCooldown
        )
    }

    fun incrementManualRep() {
        val beforeReps = workoutManager.state.value.completedReps
        workoutManager.incrementRep()
        val after = workoutManager.state.value
        if (after.phase == WorkoutPhase.ACTIVE_SET && after.completedReps > beforeReps && !after.isIsometricHold) {
            audioCoach.speakRepCount(after.completedReps)
        }
    }

    fun completeCurrentSet() {
        workoutManager.completeSet()
    }

    fun skipCurrentPhase() {
        when (workoutManager.state.value.phase) {
            WorkoutPhase.WARMUP -> workoutManager.skipWarmup()
            WorkoutPhase.REST_INTERVAL -> workoutManager.skipRest()
            WorkoutPhase.COOLDOWN -> stopWorkout()
            else -> Unit
        }
    }

    fun simulateVitalsExertion(exertionFactor: Float) {
        vitalsAnalyzer.simulateExerciseExertion(exertionFactor)
    }

    fun stopWorkout() {
        val phase = workoutManager.state.value.phase
        if (phase == WorkoutPhase.IDLE || phase == WorkoutPhase.COMPLETED) return
        completionSessionSaved = false
        // Suppress the automatic completion announcement/persist path, then save
        // the partial session exactly once from here.
        lastWorkoutPhase = WorkoutPhase.COMPLETED
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val entity = workoutManager.finishWorkout()
                repository.saveSession(entity)
            }.onFailure { e ->
                Log.w(TAG, "Failed to persist stopped workout: ${e.message}")
            }
        }
    }

    /**
     * Runs the Day 1 Hard Gate smoke test (Presage custom-input pipeline +
     * MediaPipe landmark emission) without requiring a physical camera.
     */
    fun runHardGateSmokeTest() {
        if (_isRunningSmokeTest.value) return
        _isRunningSmokeTest.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _smokeTestResult.value = headlessVideoFeeder.runSmokeTest()
            } catch (e: Exception) {
                Log.w(TAG, "Hard gate smoke test failed: ${e.message}")
            } finally {
                _isRunningSmokeTest.value = false
            }
        }
    }

    fun setSensingActive(active: Boolean) {
        _isSensingActive.value = active
    }

    suspend fun prepareVitalsCustomInput() {
        vitalsAnalyzer.ensureCustomInput()
    }

    fun clearVitalsSession() {
        pulseTraceBuffer.clear()
        chestTraceBuffer.clear()
        abdomenTraceBuffer.clear()
        latestHr = 0.0
        latestHrv = 0.0
        latestBr = 0.0
        latestConfidence = 0.0f
        latestExpression = null
        trendTracker.clear()
        vitalsAnalyzer.clearSessionReadings()
    }

    fun resetVitalsInput() {
        vitalsAnalyzer.resetCustomInput()
    }

    fun startLiveVoiceAssistant() {
        if (userProfile.value.preferLocalEdgeAi) {
            liveAssistant.showErrorMessage(
                "Offline mode is enabled in Profile & Privacy — disable it to use cloud Gemini Live voice."
            )
            return
        }
        liveAssistant.startVoiceSession(buildCurrentBiometricContext())
    }

    fun stopLiveVoiceAssistant() {
        liveAssistant.stopVoiceSession()
    }

    fun sendAssistantMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || liveAssistant.state.value.isSendingText) return

        val profile = userProfile.value
        val vitals = vitalsFlow.value
        val speculation = MoodSpeculator.speculate(
            vitals,
            profile.restingHeartRate,
            trendTracker.computeTrendNote(vitals)
        )
        liveAssistant.sendTextMessage(
            userText = trimmed,
            biometricContext = buildCurrentBiometricContext(),
            fallbackSpeculation = speculation,
            preferOffline = profile.preferLocalEdgeAi
        )
    }

    fun requestSmartSpectraInsight() {
        runCatching {
            SmartSpectraSdk.shared.requestInsight(SMARTSPECTRA_INSIGHT_PROMPT)
        }.onFailure { e ->
            Log.w(TAG, "SmartSpectra requestInsight skipped: ${e.message}")
        }
    }

    fun requestMoodCheckIn() {
        requestSmartSpectraInsight()
        sendAssistantMessage(
            "Based on my facial expression, heart rate, breathing, and HRV right now, how do you think I'm feeling?"
        )
    }

    fun clearAssistantChat() {
        liveAssistant.clearMessages()
    }

    fun clearAssistantError(expectedMessage: String) {
        if (liveAssistant.state.value.errorMessage == expectedMessage) {
            liveAssistant.clearErrorMessage()
        }
    }

    private fun buildCurrentBiometricContext(
        trendNote: String? = trendTracker.computeTrendNote(vitalsFlow.value)
    ): String {
        val profile = userProfile.value
        return MoodSpeculator.buildBiometricContext(
            vitals = vitalsFlow.value,
            userName = profile.name,
            restingHeartRate = profile.restingHeartRate,
            trendNote = trendNote
        )
    }

    fun updateUserProfile(updated: UserProfile) {
        _userProfile.value = updated
        viewModelScope.launch {
            userPrefsRepo.updateProfile(updated)
        }
    }

    fun acceptSensingConsent(telemetryEnabled: Boolean, preferLocalEdgeAi: Boolean) {
        val updated = userProfile.value.copy(
            hasAcceptedSensingConsent = true,
            smartSpectraTelemetryEnabled = telemetryEnabled,
            preferLocalEdgeAi = preferLocalEdgeAi
        )
        _userProfile.value = updated
        viewModelScope.launch {
            userPrefsRepo.updateProfile(updated)
        }
    }

    fun deleteAllLocalData() {
        liveAssistant.clearMessages()
        clearVitalsSession()
        viewModelScope.launch {
            repository.deleteAllLocalHealthData()
        }
    }

    override fun onCleared() {
        super.onCleared()
        val sdk = SmartSpectraSdk.shared
        sdk.insight.removeObserver(sdkInsightObserver)
        sdk.error.removeObserver(sdkErrorObserver)
        sdk.validationStatus.removeObserver(sdkValidationObserver)
        sdk.processingStatus.removeObserver(sdkStatusObserver)
        vitalsAnalyzer.resetCustomInput()
        dualStreamAnalyzer.shutdown()
        liveAssistant.shutdown()
        workoutManager.cleanup()
        audioCoach.shutdown()
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
