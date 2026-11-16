package com.fitlens.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.fitlens.app.ai.live.BiometricTrendTracker
import com.fitlens.app.ai.live.GeminiLiveAssistant
import com.fitlens.app.ai.live.LiveAssistantState
import com.fitlens.app.ai.live.LiveConnectionState
import com.fitlens.app.ai.live.MoodSpeculation
import com.fitlens.app.ai.live.MoodSpeculator
import com.fitlens.app.camera.DualStreamAnalyzer
import com.fitlens.app.data.WorkoutDatabase
import com.fitlens.app.data.WorkoutRepository
import com.fitlens.app.data.profile.UserPreferencesRepository
import com.fitlens.app.data.profile.UserProfile
import com.fitlens.app.vitals.PresageFrameAnalyzer
import com.fitlens.app.vitals.PresageManager
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.proto.InsightsProto
import com.presagetech.smartspectra.proto.MetricsProto.ExpressionType
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

    val vitalsFlow = vitalsAnalyzer.vitalsFlow
    val assistantState: StateFlow<LiveAssistantState> = liveAssistant.state

    private val _moodSpeculation = MutableStateFlow(MoodSpeculator.speculate(vitalsAnalyzer.vitalsFlow.value))
    val moodSpeculation: StateFlow<MoodSpeculation> = _moodSpeculation.asStateFlow()

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    @Volatile private var lastBiometricSyncAtMs = 0L
    @Volatile private var lastInsightRequestAtMs = 0L
    @Volatile private var lastVitalsPublishAtMs = 0L

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

    private fun handleSdkMetrics(metrics: com.presagetech.smartspectra.proto.MetricsProto.Metrics) {
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
            Log.e(TAG, "SmartSpectra SDK error: ${error.message}")
        }
    }

    private val sdkValidationObserver = Observer<com.presagetech.smartspectra.ValidationStatus?> { status ->
        val hint = status?.hint.orEmpty()
        vitalsAnalyzer.updateValidationHint(hint)
    }

    init {
        val sdk = SmartSpectraSdk.shared
        sdk.insight.observeForever(sdkInsightObserver)
        sdk.error.observeForever(sdkErrorObserver)
        sdk.validationStatus.observeForever(sdkValidationObserver)

        viewModelScope.launch {
            sdk.metricsFlow.collect { metrics ->
                handleSdkMetrics(metrics)
            }
        }

        viewModelScope.launch {
            userPrefsRepo.userProfileFlow.collect { profile ->
                _userProfile.value = profile
                vitalsAnalyzer.updateUserProfile(profile)
                presageManager.setTelemetryEnabled(profile.smartSpectraTelemetryEnabled)
            }
        }

        viewModelScope.launch {
            vitalsFlow.collect { vitals ->
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
            }
        }

        viewModelScope.launch {
            assistantState.collect { state ->
                dualStreamAnalyzer.enableLiveVideoFrames =
                    state.connectionState == LiveConnectionState.LISTENING
            }
        }
    }

    fun prepareVitalsCustomInput() {
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
        vitalsAnalyzer.resetCustomInput()
        dualStreamAnalyzer.shutdown()
        liveAssistant.shutdown()
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
