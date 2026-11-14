package com.fitlens.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fitlens.app.camera.CameraPreviewView
import com.fitlens.app.ui.ProfileAndPrivacySheet
import com.fitlens.app.ui.SensingConsentDialog
import com.fitlens.app.ui.WorkoutAnalyticsScreen
import com.fitlens.app.ui.WorkoutDestination
import com.fitlens.app.ui.WorkoutSummaryScreen
import com.fitlens.app.ui.WorkoutViewModel
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraSdk
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

private val CORAL   = Color(0xFFFF6B6B)
private val TEAL    = Color(0xFF4FCCC4)
private val VIOLET  = Color(0xFFA58CF9)
private val MINT    = Color(0xFF6EE7B7)
private val AMBER   = Color(0xFFFBBF24)
private val CARD_BG = Color(0xE61A2233)
private val BG_BOT  = Color(0xFF05070C)

/**
 * Single-Activity host sharing a unified CameraX + DualStreamAnalyzer + SmartSpectra
 * pipeline through [WorkoutViewModel].
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

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermissionState.value = granted
        if (granted && workoutViewModel.userProfile.value.hasAcceptedSensingConsent) {
            startMeasurement()
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
    }

    override fun onPause() {
        super.onPause()
        stopMeasurement()
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
                    sdk.processingStatus.value == ProcessingStatus.STARTING
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

        NavHost(navController = navController, startDestination = "vitals") {
            composable("vitals") {
                FitLensScreen(
                    viewModel = workoutViewModel,
                    onStartWorkout = { navController.navigate("workout") },
                    onOpenAnalytics = { navController.navigate("analytics") }
                )
            }
            composable("workout") {
                WorkoutDestination(
                    navController = navController,
                    viewModel = workoutViewModel,
                    onStartVitals = { ensurePermissionAndStartMeasurement() },
                    onStopVitals = { stopMeasurement() }
                )
            }
            composable("summary") {
                val session by workoutViewModel.lastSavedSession.collectAsState()
                val insight by workoutViewModel.latestInsight.collectAsState()
                WorkoutSummaryScreen(
                    session = session,
                    insight = insight,
                    onReturnToDashboard = {
                        navController.navigate("vitals") {
                            popUpTo("vitals") { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("analytics") {
                val sessions by workoutViewModel.workoutHistory.collectAsState()
                val faults by workoutViewModel.allBiomechanicalFaults.collectAsState()
                val insight by workoutViewModel.latestInsight.collectAsState()
                WorkoutAnalyticsScreen(
                    sessions = sessions,
                    faults = faults,
                    insight = insight,
                    onDeleteAllData = { workoutViewModel.deleteAllLocalData() },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    @Composable
    private fun FitLensScreen(
        viewModel: WorkoutViewModel,
        onStartWorkout: () -> Unit = {},
        onOpenAnalytics: () -> Unit = {}
    ) {
        val processingStatus by sdk.processingStatus.observeAsState(ProcessingStatus.IDLE)
        val validationStatus by sdk.validationStatus.observeAsState(null)
        val sdkError by sdk.error.observeAsState(null)

        val vitals by viewModel.vitalsFlow.collectAsState()
        val profile by viewModel.userProfile.collectAsState()
        val smokeTestResult by viewModel.smokeTestResult.collectAsState()
        val isRunningSmokeTest by viewModel.isRunningSmokeTest.collectAsState()
        val hasCameraPerm by hasCameraPermissionState

        var showConsentDialog by remember { mutableStateOf(false) }
        var pendingStartAfterConsent by remember { mutableStateOf(false) }
        var showProfileSheet by remember { mutableStateOf(false) }

        // Disable MediaPipe pose inference on the vitals-only dashboard screen
        LaunchedEffect(Unit) {
            viewModel.setPoseAnalysisEnabled(false)
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
                onRunSmokeTest = { viewModel.runSmokeTest() },
                onDeleteAllData = { viewModel.deleteAllLocalData() },
                onDismiss = { showProfileSheet = false }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BG_BOT)
        ) {
            // Camera preview binds strictly after explicit consent + camera permission
            if (profile.hasAcceptedSensingConsent && hasCameraPerm) {
                CameraPreviewView(
                    analyzer = viewModel.dualStreamAnalyzer,
                    useFrontCamera = true,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ConsentGatedCameraBackdrop(
                    hasAcceptedConsent = profile.hasAcceptedSensingConsent,
                    onReviewConsent = {
                        pendingStartAfterConsent = true
                        showConsentDialog = true
                    },
                    onGrantCamera = { ensurePermissionAndStartMeasurement() }
                )
            }

            // Gradient scrim for legible metric cards
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to BG_BOT.copy(alpha = 0.55f),
                            0.35f to Color.Transparent,
                            1f to BG_BOT.copy(alpha = 0.97f)
                        )
                    )
            )

            // Top bar: SDK status, validation hint, AI Insight button & Profile/Privacy sheet
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(
                        label = when (processingStatus) {
                            ProcessingStatus.IDLE     -> "SmartSpectra Idle"
                            ProcessingStatus.STARTING -> "Starting…"
                            ProcessingStatus.RUNNING  -> "Live rPPG Sensing"
                            ProcessingStatus.STOPPING -> "Stopping…"
                            ProcessingStatus.ERROR    -> sdkError?.message ?: "SDK Error"
                        },
                        accent = when (processingStatus) {
                            ProcessingStatus.RUNNING  -> MINT
                            ProcessingStatus.ERROR    -> CORAL
                            ProcessingStatus.STARTING,
                            ProcessingStatus.STOPPING -> AMBER
                            else -> Color.LightGray
                        },
                        modifier = Modifier.weight(1f)
                    )

                    Surface(
                        modifier = Modifier.clickable { viewModel.requestSmartSpectraInsight() },
                        shape = RoundedCornerShape(18.dp),
                        color = VIOLET.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, VIOLET.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = VIOLET, modifier = Modifier.size(14.dp))
                            Text("Insight", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = VIOLET)
                        }
                    }

                    IconButton(
                        onClick = { showProfileSheet = true },
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(17.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ManageAccounts,
                            contentDescription = "Profile & Privacy Settings",
                            tint = MINT,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                val activeHint = validationStatus?.hint?.takeIf { it.isNotEmpty() }
                    ?: vitals.validationHint.takeIf { it.isNotEmpty() }
                if (activeHint != null) {
                    StatusChip(
                        label = "Framing: $activeHint",
                        accent = AMBER,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (viewModel.presageManager.isPlaceholderKey()) {
                    StatusChip(
                        label = "Setup note: Add PRESAGE_API_KEY in secrets.properties (or run Smoke Test in Profile)",
                        accent = AMBER,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Bottom telemetry & navigation deck
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                vitals.smartSpectraLlmInsight?.takeIf { it.isNotBlank() }?.let { insightText ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xE61F293D),
                        border = BorderStroke(1.dp, VIOLET.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "✨ $insightText",
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                // Row 1 — Pulse + Karvonen Zone & Breathing Rate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        label = "Pulse • ${vitals.heartRateZone.label}",
                        value = if (vitals.heartRateBpm > 0) "${vitals.heartRateBpm.roundToInt()} bpm" else "-- bpm",
                        accent = CORAL,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "Breathing Rate",
                        value = if (vitals.breathingRateBpm > 0) "${vitals.breathingRateBpm.roundToInt()} brpm" else "-- brpm",
                        accent = TEAL,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2 — HRV RMSSD + Facial Expression
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        label = "HRV RMSSD",
                        value = if (vitals.heartRateVariabilityMs > 0) "${"%.1f".format(vitals.heartRateVariabilityMs)} ms" else "-- ms",
                        accent = MINT,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "Expression",
                        value = vitals.facialExpression ?: "--",
                        accent = AMBER,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Arterial pressure waveform
                WaveformCard(
                    label = "Arterial Pressure Trace (Relative Pulse Shape)",
                    samples = vitals.arterialPressureTrace,
                    lineColor = VIOLET,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                )

                // Breathing waveforms
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WaveformCard(
                        label = "Chest Trace",
                        samples = vitals.chestBreathingTrace,
                        lineColor = TEAL,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    )
                    WaveformCard(
                        label = "Abdomen Trace",
                        samples = vitals.abdomenBreathingTrace,
                        lineColor = Color(0xFF60A5FA),
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    )
                }

                // Navigation: Workout Flow + Analytics Vault
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onStartWorkout,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VIOLET)
                    ) {
                        Text(
                            "Start Workout",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenAnalytics,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Local Vault & Stats", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    }
                }

                // Start / Stop Measurement button (consent-gated)
                Button(
                    onClick = {
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
                    enabled = processingStatus != ProcessingStatus.STARTING &&
                              processingStatus != ProcessingStatus.STOPPING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (processingStatus) {
                            ProcessingStatus.RUNNING -> CORAL
                            else -> MINT
                        }
                    )
                ) {
                    Text(
                        text = when {
                            processingStatus == ProcessingStatus.RUNNING -> "Stop Measurement"
                            processingStatus == ProcessingStatus.STARTING -> "Starting…"
                            processingStatus == ProcessingStatus.STOPPING -> "Stopping…"
                            !profile.hasAcceptedSensingConsent -> "Consent & Start Measurement"
                            else -> "Start Measurement"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.Black
                    )
                }

                Text(
                    text = "Wellness & fitness coaching only • Not a medical device • 100% local SQLite storage",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    @Composable
    private fun ConsentGatedCameraBackdrop(
        hasAcceptedConsent: Boolean,
        onReviewConsent: () -> Unit,
        onGrantCamera: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B1020)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .offset(y = (-90).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (hasAcceptedConsent) Icons.Default.Videocam else Icons.Default.Shield,
                    contentDescription = null,
                    tint = MINT,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = if (!hasAcceptedConsent)
                        "Consent-First Human Sensing"
                    else
                        "Camera Permission Needed",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = if (!hasAcceptedConsent)
                        "Camera remains off until you review data handling and enable on-device SmartSpectra vitals sensing."
                    else
                        "Grant camera permission to stream frames locally to SmartSpectra and MediaPipe.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(
                    onClick = if (!hasAcceptedConsent) onReviewConsent else onGrantCamera,
                    border = BorderStroke(1.dp, MINT)
                ) {
                    Text(
                        if (!hasAcceptedConsent) "Review Consent & Privacy" else "Grant Camera Access",
                        color = MINT,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    @Composable
    private fun StatusChip(label: String, accent: Color, modifier: Modifier = Modifier) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.12f),
            tonalElevation = 0.dp
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                maxLines = 1
            )
        }
    }

    @Composable
    private fun MetricCard(
        label: String,
        value: String,
        accent: Color,
        modifier: Modifier = Modifier
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            color = CARD_BG
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text(
                    label,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.62f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent)
            }
        }
    }

    @Composable
    private fun WaveformCard(
        label: String,
        samples: List<Float>,
        lineColor: Color,
        modifier: Modifier = Modifier
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            color = CARD_BG
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(3.dp))
                WaveformCanvas(
                    samples = samples,
                    lineColor = lineColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }

    @Composable
    private fun WaveformCanvas(
        samples: List<Float>,
        lineColor: Color,
        modifier: Modifier = Modifier
    ) {
        Canvas(modifier = modifier) {
            if (samples.size < 2) return@Canvas
            val w = size.width
            val h = size.height
            val inset = 6f
            val minV = samples.min()
            val maxV = samples.max()
            val range = if (maxV - minV == 0f) 1f else maxV - minV

            repeat(3) { i ->
                val y = inset + (h - 2 * inset) * (1f - (i + 1) / 4f)
                drawLine(
                    color = Color.White.copy(alpha = 0.07f),
                    start = Offset(inset, y),
                    end = Offset(w - inset, y),
                    strokeWidth = 1f
                )
            }

            val path = Path()
            samples.forEachIndexed { idx, v ->
                val x = inset + (w - 2 * inset) * idx / (samples.size - 1)
                val y = (h - inset) - (h - 2 * inset) * (v - minV) / range
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor.copy(alpha = 0.88f),
                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
