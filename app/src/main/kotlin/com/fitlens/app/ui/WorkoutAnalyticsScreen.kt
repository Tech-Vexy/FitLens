package com.fitlens.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlens.app.data.LocalDataExportManager
import com.fitlens.app.data.WorkoutDatabase
import com.fitlens.app.data.model.WorkoutSessionEntity
import com.fitlens.app.workout.ProgressionInsight
import kotlinx.coroutines.launch

@Composable
fun WorkoutAnalyticsScreen(
    sessions: List<WorkoutSessionEntity>,
    insight: ProgressionInsight?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var exportStatusText by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    val totalWorkouts = sessions.size
    val totalReps = sessions.sumOf { it.completedReps }
    val avgAccuracy = if (sessions.isNotEmpty()) sessions.map { it.formAccuracyPercentage }.average() else 100.0
    val avgHeartRate = if (sessions.isNotEmpty()) sessions.map { it.averageHeartRate }.average() else 135.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Analytics & Progression",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "100% Local SQLite Health Vault",
                            fontSize = 11.sp,
                            color = Color(0xFF00E676)
                        )
                    }
                }

                // Export Local Data Button
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            isExporting = true
                            val db = WorkoutDatabase.getDatabase(context)
                            val exporter = LocalDataExportManager(context, db.workoutDao())
                            val exportPath = exporter.exportHealthDataToJson()
                            exportStatusText = "Exported strictly to local device storage:\n${exportPath.takeLast(40)}"
                            isExporting = false
                        }
                    },
                    enabled = !isExporting
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Export Local Health Data",
                        tint = Color(0xFF00B0FF)
                    )
                }
            }
        }

        // Export Confirmation Banner
        exportStatusText?.let { path ->
            item {
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "🔒 $path",
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        // Metrics Overview Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetricCard(
                    title = "Workouts",
                    value = "$totalWorkouts",
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricCard(
                    title = "Total Volume",
                    value = "$totalReps reps",
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricCard(
                    title = "Form Accuracy",
                    value = "${avgAccuracy.toInt()}%",
                    valueColor = Color(0xFF00E676),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ADK Progression Assessment
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2838)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = insight?.title ?: "Readiness Baseline",
                            color = Color(0xFF80DEEA),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Surface(
                            color = Color(0xFF00E676).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Readiness ${insight?.overallReadinessScore ?: 80}/100",
                                color = Color(0xFF00E676),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = insight?.description ?: "Consistent form tracking and vitals data will populate progression models.",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Biomechanical Joint Health Breakdown
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Biomechanical Joint Stability",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    JointStabilityRow("Knee Valgus (Inward Cave)", "Optimal (Protected)", Color(0xFF00E676))
                    Spacer(modifier = Modifier.height(8.dp))
                    JointStabilityRow("Torso Lean (Spinal Neutrality)", "Stable (Chest Up)", Color(0xFF00E676))
                    Spacer(modifier = Modifier.height(8.dp))
                    JointStabilityRow("Core Alignment in Plank", "Strong (No Sagging)", Color(0xFF00E676))
                    Spacer(modifier = Modifier.height(8.dp))
                    JointStabilityRow("Cardiovascular Recovery", "Adaptive (+15s avg rest)", Color(0xFFFFB74D))
                }
            }
        }

        // Session Logs
        item {
            Text(
                text = "Session History (${sessions.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
        }

        if (sessions.isEmpty()) {
            item {
                Text(
                    text = "No completed sessions yet. Start a workout to record telemetry.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            items(sessions) { session ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(session.exerciseName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "${session.formAccuracyPercentage.toInt()}% accuracy",
                                color = if (session.formAccuracyPercentage >= 85) Color(0xFF00E676) else Color(0xFFFFB74D),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${session.completedReps} reps • ${session.durationSeconds / 60}m ${session.durationSeconds % 60}s • Avg HR: ${session.averageHeartRate.toInt()} bpm",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                        if (session.extendedRestSeconds > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⏱️ +${session.extendedRestSeconds}s adaptive recovery rest applied",
                                color = Color(0xFFFFB74D),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JointStabilityRow(joint: String, status: String, statusColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(joint, color = Color.LightGray, fontSize = 13.sp)
        Text(status, color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}
