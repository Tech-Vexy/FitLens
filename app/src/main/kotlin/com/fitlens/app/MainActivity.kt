package com.fitlens.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fitlens.app.ai.live.LiveConnectionState
import com.fitlens.app.ui.FitLensTheme
import com.fitlens.app.ui.PersonalTrainerScreen
import com.fitlens.app.ui.ProfileAndPrivacySheet
import com.fitlens.app.ui.SensingConsentDialog
import com.fitlens.app.ui.WorkoutViewModel
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraSdk
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single-Activity host sharing a unified CameraX + DualStreamAnalyzer + SmartSpectra
 * + Gemini Live pipeline through [WorkoutViewModel].
 *
 * Camera & physiological sensing is strictly gated behind explicit user consent
 * ([UserProfile.hasAcceptedSensingConsent]) and runtime CAMERA permission.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val AUTO_RECOVER_MIN_UPTIME_MS = 15_000L
    }

    private val sdk by lazy { SmartSpectraSdk.shared }
    private val sdkLifecycleMutex = Mutex()
    private val workoutViewModel: WorkoutViewModel by viewModels()
    private val hasCameraPermissionState = mutableStateOf(false)
    private var resumeSensingAfterPause = false
    private var userManuallyStoppedSensing = false

    // Model artifacts (phasic-bp, expressions, ...) download+decrypt asynchronously
    // after start() returns. Restarting the SDK while that fetch is in flight cancels
    // it ("Artifact request cancelled" / "Device pairing cancelled"), so auto-recover
    // must not fire until a start has had time to settle.
    private var lastSdkStartLaunchedAtMs = 0L
    private var autoRecoverAttempts = 0

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermissionState.value = granted
        if (granted && workoutViewModel.userProfile.value.hasAcceptedSensingConsent) {
            startMeasurement()
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            workoutViewModel.startLiveVoiceAssistant()
        }
    }

    private fun toggleLiveVoiceWithPermission() {
        val state = workoutViewModel.assistantState.value.connectionState
        if (state == LiveConnectionState.LISTENING || state == LiveConnectionState.CONNECTING) {
            workoutViewModel.stopLiveVoiceAssistant()
            return
        }
        if (workoutViewModel.userProfile.value.preferLocalEdgeAi) {
            workoutViewModel.startLiveVoiceAssistant()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            workoutViewModel.startLiveVoiceAssistant()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasCameraPermissionState.value = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        workoutViewModel.presageManager.initializeSdk(
            enableTelemetry = workoutViewModel.userProfile.value.smartSpectraTelemetryEnabled
        )

        // NOTE: sdk.checkDeviceReadiness() is intentionally NOT called here — it
        // opens the NDK camera for its own test, which collides with the CameraX
        // pipeline and makes useCustomInput() throw "Device readiness is running".

        setContent {
            val userProfile by workoutViewModel.userProfile.collectAsState()
            val isDark = when (userProfile.themePreference) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            FitLensTheme(darkTheme = isDark) {
                FitLensNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasCameraPermissionState.value = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val shouldResume = resumeSensingAfterPause
        resumeSensingAfterPause = false
        if ((shouldResume || !userManuallyStoppedSensing) &&
            workoutViewModel.userProfile.value.hasAcceptedSensingConsent &&
            hasCameraPermissionState.value
        ) {
            startMeasurement(clearPreviousReadings = false, allowAutoRecover = true)
        }
    }

    override fun onPause() {
        super.onPause()
        resumeSensingAfterPause =
            workoutViewModel.isSensingActive.value ||
                sdk.processingStatus.value == ProcessingStatus.RUNNING ||
                sdk.processingStatus.value == ProcessingStatus.STARTING
        // STARTING covers device pairing and encrypted model-artifact fetches.
        // Stopping now cancels them ("Artifact request cancelled" /
        // "Device pairing cancelled") and the restart can hit
        // "model load already in progress" timeouts, so let provisioning finish.
        if (sdk.processingStatus.value != ProcessingStatus.STARTING) {
            stopMeasurement(userInitiated = false)
        }
        workoutViewModel.stopLiveVoiceAssistant()
    }

    private fun stopMeasurement(userInitiated: Boolean = true) {
        if (userInitiated) {
            userManuallyStoppedSensing = true
        }
        workoutViewModel.setSensingActive(false)
        lifecycleScope.launch {
            sdkLifecycleMutex.withLock {
                when (sdk.processingStatus.value) {
                    ProcessingStatus.RUNNING,
                    ProcessingStatus.STARTING,
                    ProcessingStatus.STOPPING -> {
                        runCatching { sdk.stop() }
                        workoutViewModel.resetVitalsInput()
                        if (userInitiated) {
                            workoutViewModel.clearVitalsSession()
                        }
                    }
                    ProcessingStatus.ERROR -> {
                        runCatching { sdk.reset() }
                        workoutViewModel.resetVitalsInput()
                        if (userInitiated) {
                            workoutViewModel.clearVitalsSession()
                        }
                    }
                    else -> {
                        if (userInitiated) {
                            workoutViewModel.clearVitalsSession()
                        }
                    }
                }
            }
        }
    }

    private fun ensurePermissionAndStartMeasurement() {
        userManuallyStoppedSensing = false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            hasCameraPermissionState.value = true
            startMeasurement(clearPreviousReadings = true)
        }
    }

    private fun startMeasurement(clearPreviousReadings: Boolean = true, allowAutoRecover: Boolean = false) {
        if (!workoutViewModel.userProfile.value.hasAcceptedSensingConsent) return
        userManuallyStoppedSensing = false
        workoutViewModel.setSensingActive(true)
        lifecycleScope.launch {
            sdkLifecycleMutex.withLock {
                if (!workoutViewModel.userProfile.value.hasAcceptedSensingConsent) return@withLock
                if (sdk.processingStatus.value == ProcessingStatus.RUNNING ||
                    sdk.processingStatus.value == ProcessingStatus.STARTING ||
                    sdk.processingStatus.value == ProcessingStatus.STOPPING
                ) {
                    return@withLock
                }
                // Auto-recover only: a recent start may still be downloading models
                // in the background; restarting now would cancel those fetches.
                if (allowAutoRecover &&
                    System.currentTimeMillis() - lastSdkStartLaunchedAtMs < AUTO_RECOVER_MIN_UPTIME_MS
                ) {
                    return@withLock
                }
                lastSdkStartLaunchedAtMs = System.currentTimeMillis()
                if (!allowAutoRecover) {
                    autoRecoverAttempts = 0
                }
                runCatching {
                    if (clearPreviousReadings) {
                        workoutViewModel.clearVitalsSession()
                    }
                    if (sdk.processingStatus.value == ProcessingStatus.ERROR) {
                        runCatching { sdk.reset() }
                        workoutViewModel.resetVitalsInput()
                    }
                    workoutViewModel.prepareVitalsCustomInput()
                    sdk.start()
                    Log.d(TAG, "SmartSpectra SDK started (custom input mode)")
                }.onFailure { e ->
                    Log.e(TAG, "Failed to start SmartSpectra SDK", e)
                }
            }
        }
    }

    @Composable
    private fun FitLensNavHost() {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = "trainer") {
            composable("trainer") {
                TrainerHostScreen(viewModel = workoutViewModel)
            }
        }
    }

    @Composable
    private fun TrainerHostScreen(viewModel: WorkoutViewModel) {
        val processingStatus by sdk.processingStatus.observeAsState(ProcessingStatus.IDLE)
        val validationStatus by sdk.validationStatus.observeAsState(null)
        val sdkError by sdk.error.observeAsState(null)

        val vitals by viewModel.vitalsFlow.collectAsState()
        val profile by viewModel.userProfile.collectAsState()
        val assistantState by viewModel.assistantState.collectAsState()
        val moodSpeculation by viewModel.moodSpeculation.collectAsState()
        val aiCoachingPrompt by viewModel.aiCoachingPrompt.collectAsState()
        val workoutState by viewModel.workoutState.collectAsState()
        val generatedPlan by viewModel.generatedWorkoutPlan.collectAsState()
        val isGeneratingPlan by viewModel.isGeneratingPlan.collectAsState()
        val recentSessions by viewModel.recentSessions.collectAsState()
        val smokeTestResult by viewModel.smokeTestResult.collectAsState()
        val isRunningSmokeTest by viewModel.isRunningSmokeTest.collectAsState()
        val isSensingActive by viewModel.isSensingActive.collectAsState()
        val workoutMediaPacks by viewModel.workoutMediaPacks.collectAsState()
        val generationStatusMessage by viewModel.generationStatusMessage.collectAsState()
        val hasCameraPerm by hasCameraPermissionState

        var useFrontCamera by remember { mutableStateOf(true) }
        var showConsentDialog by remember { mutableStateOf(false) }
        var pendingStartAfterConsent by remember { mutableStateOf(false) }
        var showProfileSheet by remember { mutableStateOf(false) }

        LaunchedEffect(profile.hasAcceptedSensingConsent, hasCameraPerm) {
            if (!profile.hasAcceptedSensingConsent &&
                (isSensingActive ||
                    processingStatus == ProcessingStatus.RUNNING ||
                    processingStatus == ProcessingStatus.STARTING ||
                    processingStatus == ProcessingStatus.STOPPING)
            ) {
                stopMeasurement(userInitiated = false)
                viewModel.clearVitalsSession()
            } else if (profile.hasAcceptedSensingConsent &&
                hasCameraPerm &&
                !isSensingActive &&
                !userManuallyStoppedSensing
            ) {
                startMeasurement(clearPreviousReadings = false, allowAutoRecover = true)
            }
        }

        // Auto-recover if SmartSpectra stops or enters ERROR while sensing is active.
        // Backoff + minimum uptime guard: restarting while the SDK is still fetching
        // model artifacts cancels those downloads, so wait for the last start to
        // settle before trying again.
        LaunchedEffect(isSensingActive, processingStatus, profile.hasAcceptedSensingConsent, hasCameraPerm) {
            if (processingStatus == ProcessingStatus.RUNNING) {
                autoRecoverAttempts = 0
            }
            if (isSensingActive &&
                profile.hasAcceptedSensingConsent &&
                hasCameraPerm &&
                (processingStatus == ProcessingStatus.ERROR || processingStatus == ProcessingStatus.IDLE)
            ) {
                val uptimeRemaining = AUTO_RECOVER_MIN_UPTIME_MS -
                    (System.currentTimeMillis() - lastSdkStartLaunchedAtMs)
                val backoffMs = 1200L shl autoRecoverAttempts.coerceAtMost(3)
                kotlinx.coroutines.delay(maxOf(uptimeRemaining, backoffMs).coerceAtLeast(0L))
                if (viewModel.isSensingActive.value &&
                    profile.hasAcceptedSensingConsent &&
                    hasCameraPerm
                ) {
                    autoRecoverAttempts++
                    startMeasurement(clearPreviousReadings = false, allowAutoRecover = true)
                }
            }
        }

        if (showConsentDialog) {
            SensingConsentDialog(
                initialTelemetryEnabled = profile.smartSpectraTelemetryEnabled,
                initialPreferLocalEdgeAi = profile.preferLocalEdgeAi,
                onAccept = { telemetryEnabled, preferLocalEdge ->
                    viewModel.acceptSensingConsent(telemetryEnabled, preferLocalEdge)
                    showConsentDialog = false
                    if (pendingStartAfterConsent) {
                        pendingStartAfterConsent = false
                        ensurePermissionAndStartMeasurement()
                    }
                },
                onDismiss = {
                    showConsentDialog = false
                    pendingStartAfterConsent = false
                }
            )
        }

        if (showProfileSheet) {
            ProfileAndPrivacySheet(
                profile = profile,
                smokeTestResult = smokeTestResult,
                isRunningSmokeTest = isRunningSmokeTest,
                onSaveProfile = { updated -> viewModel.updateUserProfile(updated) },
                onRunSmokeTest = { viewModel.runHardGateSmokeTest() },
                onDeleteAllData = { viewModel.deleteAllLocalData() },
                onDismiss = { showProfileSheet = false }
            )
        }

        val effectiveProcessingStatus = if (isSensingActive) {
            ProcessingStatus.RUNNING
        } else {
            processingStatus
        }

        PersonalTrainerScreen(
            vitals = vitals,
            profile = profile,
            assistantState = assistantState,
            moodSpeculation = moodSpeculation,
            aiCoachingPrompt = aiCoachingPrompt,
            processingStatus = effectiveProcessingStatus,
            validationHint = validationStatus?.hint,
            sdkErrorMessage = sdkError?.message,
            isPlaceholderPresageKey = viewModel.presageManager.isPlaceholderKey(),
            hasCameraPermission = hasCameraPerm,
            useFrontCamera = useFrontCamera,
            dualStreamAnalyzer = viewModel.dualStreamAnalyzer,
            workoutState = workoutState,
            generatedPlan = generatedPlan,
            isGeneratingPlan = isGeneratingPlan,
            workoutMediaPacks = workoutMediaPacks,
            generationStatusMessage = generationStatusMessage,
            recentSessions = recentSessions,
            onSelectExercise = { exercise -> viewModel.selectExercise(exercise) },
            onEnsureMediaForExercise = { exercise -> viewModel.ensureMediaForExercise(exercise) },
            onSpeakNarration = { cue -> viewModel.speakNarration(cue) },
            onGenerateWorkoutPlan = { goal, exp, equip, duration ->
                viewModel.generateAiWorkoutPlan(goal, exp, equip, duration)
            },
            onStartGeneratedRoutine = { plan -> viewModel.startGeneratedRoutine(plan) },
            onIncrementRep = { viewModel.incrementManualRep() },
            onCompleteSet = { viewModel.completeCurrentSet() },
            onSkipPhase = { viewModel.skipCurrentPhase() },
            onSimulateExertion = { factor -> viewModel.simulateVitalsExertion(factor) },
            onStopWorkout = { viewModel.stopWorkout() },
            onToggleCameraFacing = {
                useFrontCamera = !useFrontCamera
                viewModel.dualStreamAnalyzer.isFrontCamera = useFrontCamera
            },
            onStartOrStopSensing = {
                if (isSensingActive || processingStatus == ProcessingStatus.RUNNING) {
                    stopMeasurement(userInitiated = true)
                } else if (processingStatus == ProcessingStatus.STARTING ||
                    processingStatus == ProcessingStatus.STOPPING
                ) {
                    Unit
                } else {
                    if (!profile.hasAcceptedSensingConsent) {
                        pendingStartAfterConsent = true
                        showConsentDialog = true
                    } else {
                        ensurePermissionAndStartMeasurement()
                    }
                }
            },
            onReviewConsent = {
                pendingStartAfterConsent = true
                showConsentDialog = true
            },
            onGrantCamera = { ensurePermissionAndStartMeasurement() },
            onToggleLiveVoice = { toggleLiveVoiceWithPermission() },
            onSendMessage = { text -> viewModel.sendAssistantMessage(text) },
            onMoodCheckIn = { viewModel.requestMoodCheckIn() },
            onClearChat = { viewModel.clearAssistantChat() },
            onClearError = { message -> viewModel.clearAssistantError(message) },
            onOpenProfile = { showProfileSheet = true }
        )
    }
}
