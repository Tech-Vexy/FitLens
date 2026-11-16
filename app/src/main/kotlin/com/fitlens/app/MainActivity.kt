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
    }

    private val sdk by lazy { SmartSpectraSdk.shared }
    private val sdkLifecycleMutex = Mutex()
    private val workoutViewModel: WorkoutViewModel by viewModels()
    private val hasCameraPermissionState = mutableStateOf(false)
    private var resumeSensingAfterPause = false

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

        setContent { FitLensNavHost() }
    }

    override fun onResume() {
        super.onResume()
        hasCameraPermissionState.value = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val shouldResume = resumeSensingAfterPause
        resumeSensingAfterPause = false
        if (shouldResume &&
            workoutViewModel.userProfile.value.hasAcceptedSensingConsent &&
            hasCameraPermissionState.value
        ) {
            startMeasurement()
        }
    }

    override fun onPause() {
        super.onPause()
        resumeSensingAfterPause =
            sdk.processingStatus.value == ProcessingStatus.RUNNING ||
                sdk.processingStatus.value == ProcessingStatus.STARTING
        stopMeasurement()
        workoutViewModel.stopLiveVoiceAssistant()
    }

    private fun stopMeasurement() {
        lifecycleScope.launch {
            sdkLifecycleMutex.withLock {
                when (sdk.processingStatus.value) {
                    ProcessingStatus.RUNNING,
                    ProcessingStatus.STARTING,
                    ProcessingStatus.STOPPING -> {
                        runCatching { sdk.stop() }
                        workoutViewModel.resetVitalsInput()
                        workoutViewModel.clearVitalsSession()
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun ensurePermissionAndStartMeasurement() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            hasCameraPermissionState.value = true
            startMeasurement()
        }
    }

    private fun startMeasurement() {
        if (!workoutViewModel.userProfile.value.hasAcceptedSensingConsent) return
        lifecycleScope.launch {
            sdkLifecycleMutex.withLock {
                if (!workoutViewModel.userProfile.value.hasAcceptedSensingConsent) return@withLock
                if (sdk.processingStatus.value == ProcessingStatus.RUNNING ||
                    sdk.processingStatus.value == ProcessingStatus.STARTING ||
                    sdk.processingStatus.value == ProcessingStatus.STOPPING
                ) {
                    return@withLock
                }
                runCatching {
                    workoutViewModel.clearVitalsSession()
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
        val hasCameraPerm by hasCameraPermissionState

        var useFrontCamera by remember { mutableStateOf(true) }
        var showConsentDialog by remember { mutableStateOf(false) }
        var pendingStartAfterConsent by remember { mutableStateOf(false) }
        var showProfileSheet by remember { mutableStateOf(false) }

        LaunchedEffect(profile.hasAcceptedSensingConsent) {
            if (!profile.hasAcceptedSensingConsent &&
                (processingStatus == ProcessingStatus.RUNNING ||
                    processingStatus == ProcessingStatus.STARTING ||
                    processingStatus == ProcessingStatus.STOPPING)
            ) {
                stopMeasurement()
                viewModel.clearVitalsSession()
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
                onSaveProfile = { updated -> viewModel.updateUserProfile(updated) },
                onDeleteAllData = { viewModel.deleteAllLocalData() },
                onDismiss = { showProfileSheet = false }
            )
        }

        PersonalTrainerScreen(
            vitals = vitals,
            profile = profile,
            assistantState = assistantState,
            moodSpeculation = moodSpeculation,
            processingStatus = processingStatus,
            validationHint = validationStatus?.hint,
            sdkErrorMessage = sdkError?.message,
            isPlaceholderPresageKey = viewModel.presageManager.isPlaceholderKey(),
            hasCameraPermission = hasCameraPerm,
            useFrontCamera = useFrontCamera,
            dualStreamAnalyzer = viewModel.dualStreamAnalyzer,
            onToggleCameraFacing = {
                useFrontCamera = !useFrontCamera
                viewModel.dualStreamAnalyzer.isFrontCamera = useFrontCamera
            },
            onStartOrStopSensing = {
                when (processingStatus) {
                    ProcessingStatus.RUNNING -> stopMeasurement()
                    ProcessingStatus.STARTING, ProcessingStatus.STOPPING -> Unit
                    else -> {
                        if (!profile.hasAcceptedSensingConsent) {
                            pendingStartAfterConsent = true
                            showConsentDialog = true
                        } else {
                            ensurePermissionAndStartMeasurement()
                        }
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
