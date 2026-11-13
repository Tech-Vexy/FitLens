package com.fitlens.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    cameraAnalyzer: ImageAnalysis.Analyzer,
    pulseWaveform: List<Float> = emptyList(),
    isAudioMuted: Boolean = false,
    onToggleAudio: () -> Unit = {},
    onSimulateRep: (withValgusError: Boolean) -> Unit,
    onCompleteSet: () -> Unit,
    onSkipRest: () -> Unit,
    onFinishWorkout: () -> Unit,
    onBack: () -> Unit,
    autoRequestCamera: Boolean = false
) {
    val context = LocalContext.current
    val isRest = state.phase == WorkoutPhase.REST_INTERVAL
    val minutes = state.elapsedSeconds / 60
    val seconds = state.elapsedSeconds % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)
    var showLiveCamera by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        showLiveCamera = isGranted
    }

    // Pose tracking needs the camera — request permission as soon as the
    // workout screen appears instead of waiting for the camera toggle tap.
    LaunchedEffect(Unit) {
        if (autoRequestCamera && !showLiveCamera) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                showLiveCamera = true
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    val hasCriticalDeviation = state.activeCoachAlert?.startsWith("⚠️") == true ||
            state.activeCoachAlert?.contains("caving") == true ||
            state.activeCoachAlert?.contains("sagging") == true

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101010))) {
        // Background: Live Camera Stream or Dark Canvas
        if (showLiveCamera) {
            CameraPreviewView(
                analyzer = cameraAnalyzer,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Live Skeleton Landmark Overlay
        SkeletonOverlay(
            landmarks = landmarks,
            hasActiveDeviation = hasCriticalDeviation,
            modifier = Modifier.fillMaxSize()
        )

        // Semi-transparent HUD overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Navigation, Exercise Name, Audio & Live Camera Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121212).copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.exerciseName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Set ${state.currentSet} of ${state.totalSets} • $formattedTime",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleAudio) {
                        Icon(
                            imageVector = if (isAudioMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Toggle Audio",
                            tint = if (isAudioMuted) Color.Gray else Color(0xFF00E676)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (!showLiveCamera) {
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
                        }
                    ) {
                        Icon(
                            imageVector = if (showLiveCamera) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Toggle Camera",
                            tint = if (showLiveCamera) Color(0xFF00E676) else Color.Gray
                        )
                    }
                    IconButton(onClick = onFinishWorkout) {
                        Icon(Icons.Default.Check, contentDescription = "Finish Workout", tint = Color(0xFF00E676))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Middle Section: HUD Metrics
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Live Vitals Bar (SmartSpectra rPPG)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E).copy(alpha = 0.90f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Heart Rate",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "${state.heartRateBpm.toInt()} BPM",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = "SmartSpectra rPPG",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Heart Rate Zone Badge
                        Surface(
                            color = Color(state.heartRateZone.colorHex).copy(alpha = 0.25f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(state.heartRateZone.colorHex)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = state.heartRateZone.label,
                                color = Color(state.heartRateZone.colorHex),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Live SmartSpectra Arterial Pressure Trace Waveform
                    if (pulseWaveform.isNotEmpty()) {
                        PulseWaveformView(
                            waveformSamples = pulseWaveform,
                            lineColor = Color(state.heartRateZone.colorHex),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                }

                // Central Display: Rep Counter or Rest Interval Countdown
                if (isRest) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF263238).copy(alpha = 0.95f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "REST INTERVAL",
                                color = Color(0xFF80DEEA),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${state.restSecondsRemaining}s",
                                color = Color.White,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (state.isRestExtended) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = Color(0xFFFF9800).copy(alpha = 0.25f),
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
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onSkipRest,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF))
                            ) {
                                Text("Skip Rest & Begin Set", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Active Set Counter
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .clip(CircleShape)
                            .border(4.dp, Color(0xFF00E676), CircleShape)
                            .background(Color(0xFF1E1E1E).copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${state.completedReps}",
                                color = Color.White,
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "OF ${state.targetReps} REPS",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Joint Angle & Form Accuracy Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E).copy(alpha = 0.85f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Joint Angle", color = Color.Gray, fontSize = 10.sp)
                                Text(
                                    "${state.currentJointAngle.toInt()}°",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E).copy(alpha = 0.85f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Form Accuracy", color = Color.Gray, fontSize = 10.sp)
                                Text(
                                    "${state.formAccuracyPercentage.toInt()}%",
                                    color = if (state.formAccuracyPercentage > 85f) Color(0xFF00E676) else Color(0xFFFFB74D),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            }
                        }
                    }
                }

                // Real-time Coach Guidance Banner
                state.activeCoachAlert?.let { alert ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasCriticalDeviation) Color(0xFF4A148C).copy(alpha = 0.95f) else Color(0xFF1A237E).copy(alpha = 0.90f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (hasCriticalDeviation) Icons.Default.Warning else Icons.Default.Psychology,
                                contentDescription = null,
                                tint = if (hasCriticalDeviation) Color(0xFFFFB74D) else Color(0xFF82B1FF)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = alert,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Bottom Controls: Simulation & Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121212).copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isRest) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSimulateRep(false) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E676))
                        ) {
                            Text("Simulate Rep", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onSimulateRep(true) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A80))
                        ) {
                            Text("Simulate Valgus", fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = onCompleteSet,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text("Complete Set (${state.completedReps}/${state.targetReps})", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
