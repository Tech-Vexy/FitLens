package com.fitlens.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fitlens.app.camera.CameraPreviewView
import com.fitlens.app.camera.SkeletonOverlay
import com.fitlens.app.pose.LandmarkPoint
import com.fitlens.app.vitals.PulseWaveformView
import com.fitlens.app.workout.WorkoutPhase
import com.fitlens.app.workout.WorkoutState

@Composable
fun WorkoutSessionScreen(
    state: WorkoutState,
    landmarks: Map<Int, LandmarkPoint>,
    frameWidth: Int = 0,
    frameHeight: Int = 0,
    cameraAnalyzer: ImageAnalysis.Analyzer,
    pulseWaveform: List<Float> = emptyList(),
    hasAcceptedConsent: Boolean = true,
    onRequestConsent: () -> Unit = {},
    isAudioMuted: Boolean = false,
    onToggleAudio: () -> Unit = {},
    onSkipWarmup: () -> Unit = {},
    onSimulateRep: (withValgusError: Boolean) -> Unit,
    onSimulateExertion: () -> Unit = {},
    onCompleteSet: () -> Unit,
    onSkipRest: () -> Unit,
    onFinishWorkout: () -> Unit,
    onBack: () -> Unit,
    autoRequestCamera: Boolean = false
) {
    val context = LocalContext.current
    val minutes = state.elapsedSeconds / 60
    val seconds = state.elapsedSeconds % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)

    var showLiveCamera by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(true) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        showLiveCamera = isGranted && hasAcceptedConsent
    }

    LaunchedEffect(hasAcceptedConsent, autoRequestCamera) {
        if (autoRequestCamera && hasAcceptedConsent && !showLiveCamera) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                showLiveCamera = true
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } else if (!hasAcceptedConsent) {
            showLiveCamera = false
        }
    }

    val hasCriticalDeviation = state.activeCoachAlert?.startsWith("⚠️") == true ||
        state.activeCoachAlert?.contains("caving") == true ||
        state.activeCoachAlert?.contains("sagging") == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C1018))
    ) {
        if (showLiveCamera && hasAcceptedConsent) {
            CameraPreviewView(
                analyzer = cameraAnalyzer,
                useFrontCamera = useFrontCamera,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Live 33-point Skeleton Overlay with FILL_CENTER projection & fault highlights
        SkeletonOverlay(
            landmarks = landmarks,
            hasActiveDeviation = hasCriticalDeviation,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            activeDeviations = state.activeDeviations,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Back, Exercise + Routine Progress, Audio, Camera Flip, Camera On/Off, Finish
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121722).copy(alpha = 0.88f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = state.exerciseName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                    val routinePrefix = if (state.routineExercises.size > 1) {
                        "Ex ${state.currentExerciseIndex + 1}/${state.routineExercises.size} • "
                    } else ""
                    Text(
                        text = "${routinePrefix}Set ${state.currentSet}/${state.totalSets} • $formattedTime",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleAudio, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = if (isAudioMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Toggle Audio",
                            tint = if (isAudioMuted) Color.Gray else Color(0xFF00E676),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (showLiveCamera) {
                        IconButton(
                            onClick = { useFrontCamera = !useFrontCamera },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch Front/Back Camera",
                                tint = Color(0xFF4FCCC4),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (!hasAcceptedConsent) {
                                onRequestConsent()
                            } else if (!showLiveCamera) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    showLiveCamera = true
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            } else {
                                showLiveCamera = false
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (showLiveCamera) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Toggle Camera",
                            tint = if (showLiveCamera) Color(0xFF00E676) else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onFinishWorkout, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Finish Workout",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Middle HUD: Multi-Signal SmartSpectra Biofeedback + Phase Centerpiece + Coach Banner
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Multi-Signal SmartSpectra Biofeedback Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171E2E).copy(alpha = 0.90f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Heart Rate",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = if (state.heartRateBpm > 0) "${state.heartRateBpm.toInt()} BPM" else "-- BPM",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = state.autonomicReadinessLabel,
                                        color = Color(0xFF6EE7B7),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Surface(
                                color = Color(state.heartRateZone.colorHex).copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, Color(state.heartRateZone.colorHex)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = state.heartRateZone.label,
                                    color = Color(state.heartRateZone.colorHex),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Secondary SmartSpectra signals: Breathing Rate • HRV • Expression • Confidence
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SignalPill(
                                label = "Breath",
                                value = if (state.breathingRateBpm > 0) "${state.breathingRateBpm.toInt()} brpm" else "--",
                                color = Color(0xFF4FCCC4)
                            )
                            SignalPill(
                                label = "HRV",
                                value = if (state.hrvRmssdMs > 0) "${state.hrvRmssdMs.toInt()} ms" else "--",
                                color = Color(0xFF6EE7B7)
                            )
                            SignalPill(
                                label = "Face",
                                value = state.facialExpression ?: "--",
                                color = Color(0xFFFBBF24)
                            )
                            SignalPill(
                                label = "Signal",
                                value = if (state.signalConfidence > 0f) "${(state.signalConfidence * 100).toInt()}%" else "--",
                                color = Color(0xFFA58CF9)
                            )
                        }

                        if (pulseWaveform.isNotEmpty()) {
                            PulseWaveformView(
                                waveformSamples = pulseWaveform,
                                lineColor = Color(state.heartRateZone.colorHex),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (state.validationHint.isNotBlank()) {
                            Text(
                                text = "📷 SmartSpectra Hint: ${state.validationHint}",
                                color = Color(0xFFFBBF24),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Phase-Specific Centerpiece
                when (state.phase) {
                    WorkoutPhase.WARMUP -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.94f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("DYNAMIC MOBILITY WARMUP", color = Color(0xFF6EE7B7), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("${state.phaseCountdownSeconds}s", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    "Center your face & body in frame while mobilizing joints before Set 1.",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = onSkipWarmup,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                                ) {
                                    Text("Begin Set 1 Now", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    WorkoutPhase.REST_INTERVAL, WorkoutPhase.COOLDOWN -> {
                        val isCooldown = state.phase == WorkoutPhase.COOLDOWN
                        val secondsLeft = if (isCooldown) state.phaseCountdownSeconds else state.restSecondsRemaining
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2836).copy(alpha = 0.95f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isCooldown) "RECOVERY COOLDOWN" else "VITALS-GUIDED REST INTERVAL",
                                    color = Color(0xFF4FCCC4),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${secondsLeft}s",
                                    color = Color.White,
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (state.isRestExtended && !isCooldown) {
                                    Surface(
                                        color = Color(0xFFFF9800).copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "ADK Coach: Adaptive Vitals Extension Applied",
                                            color = Color(0xFFFFB74D),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (isCooldown && state.heartRateRecoveryDropBpm > 0.0) {
                                    Text(
                                        text = "HR Recovery Drop: -${state.heartRateRecoveryDropBpm.toInt()} BPM from peak (${state.peakHeartRateBpm.toInt()} BPM)",
                                        color = Color(0xFF6EE7B7),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // Animated Coherent Breathing Pacer driven by live SmartSpectra breathing rate
                                CoherentBreathingPacerRing(
                                    inhaleSeconds = state.breathingInhaleSeconds,
                                    exhaleSeconds = state.breathingExhaleSeconds,
                                    currentBreathingBpm = state.breathingRateBpm
                                )

                                Button(
                                    onClick = if (isCooldown) onFinishWorkout else onSkipRest,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isCooldown) Color(0xFF00E676) else Color(0xFF4FCCC4)
                                    )
                                ) {
                                    Text(
                                        if (isCooldown) "Save & View Summary" else "Skip Rest & Next Set",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        // ACTIVE_SET Counter (Dynamic Reps OR Timed Isometric Hold)
                        Box(
                            modifier = Modifier
                                .size(148.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 4.dp,
                                    color = if (hasCriticalDeviation) Color(0xFFFF5252) else Color(0xFF00E676),
                                    shape = CircleShape
                                )
                                .background(Color(0xFF171E2E).copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (state.isIsometricHold) "${state.completedReps}s" else "${state.completedReps}",
                                    color = Color.White,
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (state.isIsometricHold) "OF ${state.targetReps}s HOLD" else "OF ${state.targetReps} REPS",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF171E2E).copy(alpha = 0.85f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        if (state.isIsometricHold) "Alignment Angle" else "Joint Angle",
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        "${state.currentJointAngle.toInt()}°",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF171E2E).copy(alpha = 0.85f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Form Accuracy", color = Color.Gray, fontSize = 10.sp)
                                    Text(
                                        "${state.formAccuracyPercentage.toInt()}%",
                                        color = if (state.formAccuracyPercentage > 85f) Color(0xFF00E676) else Color(0xFFFFB74D),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Real-time Coach Guidance + Expression Cue Banner
                val bannerText = state.activeCoachAlert ?: state.expressionCoachCue
                bannerText?.let { alert ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasCriticalDeviation) Color(0xFF4A148C).copy(alpha = 0.94f)
                            else Color(0xFF1A237E).copy(alpha = 0.88f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (hasCriticalDeviation) Icons.Default.Warning else Icons.Default.Psychology,
                                contentDescription = null,
                                tint = if (hasCriticalDeviation) Color(0xFFFFB74D) else Color(0xFF82B1FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = alert,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Bottom Controls: Demo Simulation & Set Actions
            if (state.phase == WorkoutPhase.ACTIVE_SET) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF121722).copy(alpha = 0.88f), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSimulateRep(false) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E676))
                        ) {
                            Text(
                                if (state.isIsometricHold) "Sim +5s Hold" else "Simulate Rep",
                                fontSize = 10.sp
                            )
                        }
                        OutlinedButton(
                            onClick = { onSimulateRep(true) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A80))
                        ) {
                            Text(
                                if (state.isIsometricHold) "Sim Hip Sag" else "Simulate Valgus",
                                fontSize = 10.sp
                            )
                        }
                        OutlinedButton(
                            onClick = onSimulateExertion,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFBBF24))
                        ) {
                            Text("Sim High HR", fontSize = 10.sp)
                        }
                    }

                    Button(
                        onClick = onCompleteSet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        val suffix = if (state.isIsometricHold) "s" else ""
                        Text(
                            "Complete Set (${state.completedReps}$suffix/${state.targetReps}$suffix)",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalPill(label: String, value: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("$label:", fontSize = 10.sp, color = Color.Gray)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/**
 * Animated coherent recovery breathing guide responding to SmartSpectra's live breathing rate.
 */
@Composable
private fun CoherentBreathingPacerRing(
    inhaleSeconds: Int,
    exhaleSeconds: Int,
    currentBreathingBpm: Double
) {
    val totalCycleMs = ((inhaleSeconds + exhaleSeconds).coerceAtLeast(4)) * 1000
    val inhaleFraction = inhaleSeconds.toFloat() / (inhaleSeconds + exhaleSeconds).coerceAtLeast(1)

    val transition = rememberInfiniteTransition(label = "breathing_pacer")
    val cycleProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = totalCycleMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "breath_phase"
    )

    val isInhaling = cycleProgress < inhaleFraction
    val scaleFactor = if (isInhaling) {
        val t = (cycleProgress / inhaleFraction).coerceIn(0f, 1f)
        0.78f + 0.28f * t
    } else {
        val t = ((cycleProgress - inhaleFraction) / (1f - inhaleFraction).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
        1.06f - 0.28f * t
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(scaleFactor)
                .clip(CircleShape)
                .background(Color(0xFF4FCCC4).copy(alpha = 0.22f))
                .border(2.dp, Color(0xFF4FCCC4), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isInhaling) "INHALE\n${inhaleSeconds}s" else "EXHALE\n${exhaleSeconds}s",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp
            )
        }
        Text(
            text = if (currentBreathingBpm > 0)
                "Live Breathing: ${currentBreathingBpm.toInt()} brpm → Target 6–10 brpm"
            else
                "Paced Coherent Breathing (${inhaleSeconds}s in / ${exhaleSeconds}s out)",
            color = Color(0xFF94A3B8),
            fontSize = 10.sp
        )
    }
}
