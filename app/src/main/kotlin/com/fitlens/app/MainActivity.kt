package com.fitlens.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fitlens.app.camera.CameraPreviewView
import com.fitlens.app.camera.DualStreamAnalyzer
import com.fitlens.app.pose.PoseLandmarkManager
import com.fitlens.app.ui.WorkoutAnalyticsScreen
import com.fitlens.app.ui.WorkoutDestination
import com.fitlens.app.ui.WorkoutSummaryScreen
import com.fitlens.app.ui.WorkoutViewModel
import com.fitlens.app.vitals.PresageFrameAnalyzer
import com.fitlens.app.vitals.PresageManager
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.proto.MetricTypesProto.MetricType
import com.presagetech.smartspectra.proto.MetricsProto.ExpressionType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Colour palette (matches the official SmartSpectra sample look)
// ─────────────────────────────────────────────────────────────────────────────
private val CORAL   = Color(0xFFFF6B6B)
private val TEAL    = Color(0xFF4FCCC4)
private val VIOLET  = Color(0xFFA58CF9)
private val MINT    = Color(0xFF6EE7B7)
private val AMBER   = Color(0xFFFBBF24)
private val CARD_BG = Color(0xE61A2233)
private val BG_TOP  = Color(0xFF0B1020)
private val BG_BOT  = Color(0xFF05070C)

/**
 * Single-screen Activity — CameraX preview fills the screen; metric cards
 * live at the bottom. No navigation stack, no multi-screen enum.
 *
 * Architecture: Uses CameraX for preview + ImageAnalysis, with DualStreamAnalyzer
 * dispatching each frame to both SmartSpectra (vitals) and MediaPipe (pose).
 * SmartSpectra receives frames via useCustomInput() (headless mode) — the SDK's
 * built-in camera is NOT used, avoiding camera device lock contention.
 *
 * SDK lifecycle:
 *   onCreate  → configure SmartSpectraSdk (api key, metrics) via PresageManager
 *   onPause   → stop the SDK pipeline (camera releases)
 *   Start btn → request camera permission if needed, then sdk.start()
 *   Stop btn  → sdk.stop() + resetCustomInput() for clean next session
 *
 * All metric values come from sdk.metrics LiveData, observed as Compose State
 * via observeAsState().
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val WAVEFORM_BUFFER_SIZE = 200
    }

    private val sdk by lazy { SmartSpectraSdk.shared }
    private val presageManager = PresageManager()
    private val presageAnalyzer by lazy { PresageFrameAnalyzer() }
    private val poseManager by lazy { PoseLandmarkManager(this) }
    // This screen only displays SmartSpectra vitals — pose analysis is disabled
    // to avoid running MediaPipe inference (and discarding the results) every frame.
    private val dualStreamAnalyzer by lazy {
        DualStreamAnalyzer(
            presageAnalyzer = presageAnalyzer,
            poseManager = poseManager,
            enablePoseAnalysis = false
        )
    }

    // Camera permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startMeasurement()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Configure SDK once via PresageManager ─────────────────────────
        presageManager.initializeSdk()

        setContent { FitLensNavHost() }
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch {
            when (sdk.processingStatus.value) {
                ProcessingStatus.RUNNING,
                ProcessingStatus.STARTING,
                ProcessingStatus.STOPPING -> {
                    runCatching { sdk.stop() }
                    // Reset the cached CustomInput handle so the next session gets a fresh one
                    presageAnalyzer.resetCustomInput()
                }
                else -> Unit
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // SDK control helpers
    // ─────────────────────────────────────────────────────────────────────

    private fun toggleMeasurement() {
        when (sdk.processingStatus.value) {
            ProcessingStatus.RUNNING -> lifecycleScope.launch {
                runCatching { sdk.stop() }
                presageAnalyzer.resetCustomInput()
            }
            ProcessingStatus.STARTING, ProcessingStatus.STOPPING -> Unit
            else -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    startMeasurement()
                }
            }
        }
    }

    private fun startMeasurement() {
        lifecycleScope.launch {
            runCatching {
                // Reset the custom input handle before starting a new session
                presageAnalyzer.resetCustomInput()
                sdk.start()
                Log.d(TAG, "SmartSpectra SDK started (custom input mode)")
            }.onFailure { e ->
                Log.e(TAG, "Failed to start SmartSpectra SDK", e)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Top-level navigation: vitals dashboard -> workout flow / analytics / summary.
     * WorkoutViewModel is activity-scoped so workout state survives navigation.
     */
    @Composable
    private fun FitLensNavHost() {
        val navController = rememberNavController()
        val workoutViewModel: WorkoutViewModel = viewModel()

        NavHost(navController = navController, startDestination = "vitals") {
            composable("vitals") {
                FitLensScreen(
                    onStartWorkout = { navController.navigate("workout") },
                    onOpenAnalytics = { navController.navigate("analytics") }
                )
            }
            composable("workout") {
                WorkoutDestination(
                    navController = navController,
                    viewModel = workoutViewModel,
                    onStartVitals = { startMeasurement() },
                    onStopVitals = {
                        lifecycleScope.launch {
                            runCatching { sdk.stop() }
                            presageAnalyzer.resetCustomInput()
                        }
                    }
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
                val insight by workoutViewModel.latestInsight.collectAsState()
                WorkoutAnalyticsScreen(
                    sessions = sessions,
                    insight = insight,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Compose UI
    // ─────────────────────────────────────────────────────────────────────

    @Composable
    private fun FitLensScreen(
        onStartWorkout: () -> Unit = {},
        onOpenAnalytics: () -> Unit = {}
    ) {
        // ── Observe SDK LiveData as Compose State ─────────────────────────
        val processingStatus by sdk.processingStatus.observeAsState(ProcessingStatus.IDLE)
        val validationStatus by sdk.validationStatus.observeAsState(null)
        val sdkError         by sdk.error.observeAsState(null)
        val metrics          by sdk.metrics.observeAsState(null)

        // Retain last valid metric values (SDK docs recommend this pattern)
        var pulseRate    by remember { mutableStateOf<Double?>(null) }
        var breathRate   by remember { mutableStateOf<Double?>(null) }
        var hrvRmssd     by remember { mutableStateOf<Double?>(null) }
        var expression   by remember { mutableStateOf<String?>(null) }
        val pressureBuf  = remember { mutableStateListOf<Float>() }
        val chestBuf     = remember { mutableStateListOf<Float>() }
        val abdomenBuf   = remember { mutableStateListOf<Float>() }
        var latestPressureTs by remember { mutableLongStateOf(Long.MIN_VALUE) }
        var latestChestTs    by remember { mutableLongStateOf(Long.MIN_VALUE) }
        var latestAbdomenTs  by remember { mutableLongStateOf(Long.MIN_VALUE) }

        // When processing restarts, clear retained values
        LaunchedEffect(processingStatus) {
            if (processingStatus == ProcessingStatus.STARTING) {
                pulseRate = null; breathRate = null; hrvRmssd = null; expression = null
                pressureBuf.clear(); chestBuf.clear(); abdomenBuf.clear()
                latestPressureTs = Long.MIN_VALUE
                latestChestTs    = Long.MIN_VALUE
                latestAbdomenTs  = Long.MIN_VALUE
            }
        }

        // Decode sdk.metrics payload (follows official recommended pattern)
        LaunchedEffect(metrics) {
            val m = metrics ?: return@LaunchedEffect
            if (m.hasCardio()) {
                m.cardio.pulseRateList.lastOrNull { it.timestamp > 0 }?.value?.let { pulseRate = it.toDouble() }
                m.cardio.hrvList.lastOrNull { it.rmssd > 0 }?.rmssd?.let { hrvRmssd = it }
                latestPressureTs = drainTraceWindow(
                    samples = m.cardio.arterialPressureTraceList,
                    latestTimestamp = latestPressureTs,
                    buffer = pressureBuf,
                    timestampOf = { it.timestamp },
                    valueOf = { it.value }
                )
            }
            if (m.hasBreathing()) {
                m.breathing.rateList.lastOrNull()?.value?.let { breathRate = it.toDouble() }
                latestChestTs = drainTraceWindow(
                    samples = m.breathing.upperTraceList,
                    latestTimestamp = latestChestTs,
                    buffer = chestBuf,
                    timestampOf = { it.timestamp },
                    valueOf = { it.value }
                )
                latestAbdomenTs = drainTraceWindow(
                    samples = m.breathing.lowerTraceList,
                    latestTimestamp = latestAbdomenTs,
                    buffer = abdomenBuf,
                    timestampOf = { it.timestamp },
                    valueOf = { it.value }
                )
            }
            if (m.hasFace()) {
                m.face.expressionList.lastOrNull()?.scoresList
                    ?.filter { it.confidence > 0f }
                    ?.maxByOrNull { it.confidence }
                    ?.let { top -> expression = top.type.friendlyName() }
            }
        }

        // ── Layout ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BG_BOT)
        ) {
            // Camera preview fills the screen using CameraX + DualStreamAnalyzer
            CameraPreviewView(
                analyzer = dualStreamAnalyzer,
                useFrontCamera = true,
                modifier = Modifier.fillMaxSize()
            )

            // Dark gradient scrim so cards are readable
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to BG_BOT.copy(alpha = 0.97f)
                        )
                    )
            )

            // Status + control bar at the top
            StatusBar(
                status = processingStatus,
                validationHint = validationStatus?.hint.orEmpty(),
                errorMessage = sdkError?.message,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )

            // Metric cards at the bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1 — Pulse + Breathing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        label = "Pulse Rate",
                        value = pulseRate?.let { "${it.roundToInt()} bpm" } ?: "-- bpm",
                        accent = CORAL,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "Breathing Rate",
                        value = breathRate?.let { "${it.roundToInt()} bpm" } ?: "-- bpm",
                        accent = TEAL,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2 — HRV + Expression
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        label = "HRV RMSSD",
                        value = hrvRmssd?.let { "${"%.1f".format(it)} ms" } ?: "-- ms",
                        accent = MINT,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "Expression",
                        value = expression ?: "--",
                        accent = AMBER,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Arterial pressure waveform
                WaveformCard(
                    label = "Arterial Pressure",
                    samples = pressureBuf,
                    lineColor = VIOLET,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )

                // Breathing waveforms
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WaveformCard(
                        label = "Chest",
                        samples = chestBuf,
                        lineColor = TEAL,
                        modifier = Modifier
                            .weight(1f)
                            .height(70.dp)
                    )
                    WaveformCard(
                        label = "Abdomen",
                        samples = abdomenBuf,
                        lineColor = Color(0xFF60A5FA),
                        modifier = Modifier
                            .weight(1f)
                            .height(70.dp)
                    )
                }

                // App navigation: Workout flow + Analytics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onStartWorkout,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
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
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Analytics", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }
                }

                // Start / Stop button
                Button(
                    onClick = { toggleMeasurement() },
                    enabled = processingStatus != ProcessingStatus.STARTING &&
                              processingStatus != ProcessingStatus.STOPPING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (processingStatus) {
                            ProcessingStatus.RUNNING -> CORAL
                            else -> MINT
                        }
                    )
                ) {
                    Text(
                        text = when (processingStatus) {
                            ProcessingStatus.IDLE, ProcessingStatus.ERROR -> "Start Measurement"
                            ProcessingStatus.STARTING -> "Starting…"
                            ProcessingStatus.RUNNING  -> "Stop"
                            ProcessingStatus.STOPPING -> "Stopping…"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Trace draining helper
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Appends only the samples newer than [latestTimestamp] to [buffer] and
     * returns the newest timestamp seen. SDK traces are cumulative and
     * time-ordered, so scanning backwards and stopping at the first already-seen
     * timestamp costs O(new samples) per emission instead of O(entire trace).
     */
    private fun <T> drainTraceWindow(
        samples: List<T>,
        latestTimestamp: Long,
        buffer: MutableList<Float>,
        timestampOf: (T) -> Long,
        valueOf: (T) -> Float
    ): Long {
        if (samples.isEmpty()) return latestTimestamp

        val fresh = ArrayList<Float>(16)
        var newestTs = latestTimestamp
        var i = samples.size - 1
        while (i >= 0) {
            val ts = timestampOf(samples[i])
            if (ts <= latestTimestamp) break
            newestTs = maxOf(newestTs, ts)
            fresh.add(valueOf(samples[i]))
            i--
        }
        if (fresh.isEmpty()) return latestTimestamp

        // fresh holds newest→oldest; append oldest→newest to keep the waveform ordered.
        for (j in fresh.indices.reversed()) {
            buffer.add(fresh[j])
        }
        while (buffer.size > WAVEFORM_BUFFER_SIZE) buffer.removeAt(0)
        return newestTs
    }

    // ─────────────────────────────────────────────────────────────────────
    // Reusable composables
    // ─────────────────────────────────────────────────────────────────────

    @Composable
    private fun StatusBar(
        status: ProcessingStatus,
        validationHint: String,
        errorMessage: String?,
        modifier: Modifier = Modifier
    ) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(
                label = when (status) {
                    ProcessingStatus.IDLE     -> "Idle"
                    ProcessingStatus.STARTING -> "Starting"
                    ProcessingStatus.RUNNING  -> "Running"
                    ProcessingStatus.STOPPING -> "Stopping"
                    ProcessingStatus.ERROR    -> errorMessage ?: "Error"
                },
                accent = when (status) {
                    ProcessingStatus.RUNNING  -> MINT
                    ProcessingStatus.ERROR    -> CORAL
                    ProcessingStatus.STARTING,
                    ProcessingStatus.STOPPING -> AMBER
                    else -> Color.Gray
                },
                modifier = Modifier.weight(1f)
            )
            if (validationHint.isNotEmpty()) {
                StatusChip(
                    label = validationHint,
                    accent = AMBER,
                    modifier = Modifier.weight(1f)
                )
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
                fontSize = 12.sp,
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
            shape = RoundedCornerShape(18.dp),
            color = CARD_BG
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accent)
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
            shape = RoundedCornerShape(18.dp),
            color = CARD_BG
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
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

            // Subtle grid
            val gridPaint = androidx.compose.ui.graphics.Paint().apply {
                color = Color.White.copy(alpha = 0.06f)
            }
            repeat(3) { i ->
                val y = inset + (h - 2 * inset) * (1f - (i + 1) / 4f)
                drawLine(
                    color = Color.White.copy(alpha = 0.07f),
                    start = Offset(inset, y),
                    end = Offset(w - inset, y),
                    strokeWidth = 1f
                )
            }

            // Signal line
            val path = Path()
            samples.forEachIndexed { idx, v ->
                val x = inset + (w - 2 * inset) * idx / (samples.size - 1)
                val y = (h - inset) - (h - 2 * inset) * (v - minV) / range
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor.copy(alpha = 0.85f),
                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Extension helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun ExpressionType.friendlyName(): String? = when (this) {
    ExpressionType.HAPPY     -> "Happy"
    ExpressionType.NEUTRAL   -> "Neutral"
    ExpressionType.SAD       -> "Sad"
    ExpressionType.ANGRY     -> "Angry"
    ExpressionType.SURPRISE  -> "Surprise"
    ExpressionType.FEAR      -> "Fear"
    ExpressionType.DISGUST   -> "Disgust"
    ExpressionType.CONTEMPT  -> "Contempt"
    else -> null
}
