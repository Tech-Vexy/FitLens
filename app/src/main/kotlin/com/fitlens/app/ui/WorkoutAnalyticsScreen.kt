package com.fitlens.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import com.fitlens.app.data.model.BiomechanicalFaultEntity
import com.fitlens.app.data.model.WorkoutSessionEntity
import com.fitlens.app.workout.ProgressionInsight
import kotlinx.coroutines.launch

@Composable
fun WorkoutAnalyticsScreen(
    sessions: List<WorkoutSessionEntity>,
    faults: List<BiomechanicalFaultEntity> = emptyList(),
    insight: ProgressionInsight?,
    onDeleteAllData: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var statusBannerText by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val totalWorkouts = sessions.size
    val totalReps = sessions.sumOf { it.completedReps }
    val avgAccuracy = if (sessions.isNotEmpty()) sessions.map { it.formAccuracyPercentage }.average() else 100.0
    val avgExtendedRest = if (sessions.isNotEmpty()) sessions.map { it.extendedRestSeconds }.average().toInt() else 0

    // Real SQLite biomechanical fault counts per joint group
    val kneeFaultCount = faults.count { it.jointName.contains("knee", ignoreCase = true) }
    val torsoFaultCount = faults.count {
        it.jointName.contains("back", ignoreCase = true) || it.jointName.contains("torso", ignoreCase = true)
    }
    val hipCoreFaultCount = faults.count {
        it.jointName.contains("hip", ignoreCase = true) || it.jointName.contains("core", ignoreCase = true)
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = Color(0xFF192131),
            title = { Text("Delete All Local Vault Data?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This permanently erases all ${sessions.size} workout sessions, local rPPG vitals logs, and ${faults.size} biomechanical fault events from fitlens_health_sqlite.db.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllData()
                        showDeleteConfirmDialog = false
                        statusBannerText = "All local SQLite workout, vitals, and fault records were permanently erased."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
                ) {
                    Text("Erase Everything", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E121B))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Local Health Vault",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "100% On-Device SQLite • Export or Erase Anytime",
                            fontSize = 11.sp,
                            color = Color(0xFF6EE7B7)
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isExporting = true
                                val db = WorkoutDatabase.getDatabase(context)
                                val exporter = LocalDataExportManager(context, db.workoutDao())
                                val exportPath = exporter.exportHealthDataToJson()
                                statusBannerText = "Exported locally to device storage:\n${exportPath.takeLast(48)}"
                                isExporting = false
                            }
                        },
                        enabled = !isExporting
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export Local Health Data JSON",
                            tint = Color(0xFF4FCCC4)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        enabled = sessions.isNotEmpty() || faults.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Delete All Local Data",
                            tint = if (sessions.isNotEmpty() || faults.isNotEmpty()) Color(0xFFFF6B6B) else Color.Gray
                        )
                    }
                }
            }
        }

        statusBannerText?.let { msg ->
            item {
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "🔒 $msg",
                        color = Color(0xFF6EE7B7),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

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
                    value = "$totalReps",
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricCard(
                    title = "Avg Form",
                    value = "${avgAccuracy.toInt()}%",
                    valueColor = Color(0xFF00E676),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF192436)),
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
                            fontSize = 15.sp
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
                        text = insight?.description ?: "Complete workouts to populate longitudinal progression insights.",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Dynamic SQLite-Computed Biomechanical Joint Stability Breakdown
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF192131)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Biomechanical Telemetry Breakdown",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    JointStabilityRow(
                        joint = "Knee Valgus Alignment",
                        status = if (kneeFaultCount == 0) "Optimal (0 faults)" else "$kneeFaultCount fault episode(s)",
                        statusColor = if (kneeFaultCount == 0) Color(0xFF00E676) else Color(0xFFFFB74D)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    JointStabilityRow(
                        joint = "Torso & Spinal Neutrality",
                        status = if (torsoFaultCount == 0) "Stable (0 faults)" else "$torsoFaultCount lean warning(s)",
                        statusColor = if (torsoFaultCount == 0) Color(0xFF00E676) else Color(0xFFFFB74D)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    JointStabilityRow(
                        joint = "Core & Hip Line (Plank/Push-Up)",
                        status = if (hipCoreFaultCount == 0) "Braced (0 faults)" else "$hipCoreFaultCount sag episode(s)",
                        statusColor = if (hipCoreFaultCount == 0) Color(0xFF00E676) else Color(0xFFFFB74D)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    JointStabilityRow(
                        joint = "SmartSpectra Adaptive Rest",
                        status = if (avgExtendedRest > 0) "+${avgExtendedRest}s avg HR recovery" else "On-schedule recovery",
                        statusColor = if (avgExtendedRest > 0) Color(0xFF4FCCC4) else Color(0xFF00E676)
                    )
                }
            }
        }

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
                    text = "No stored sessions in fitlens_health_sqlite.db. Complete a workout to log local telemetry.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            items(sessions) { session ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF192131)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                session.exerciseName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${session.formAccuracyPercentage.toInt()}% form",
                                color = if (session.formAccuracyPercentage >= 85) Color(0xFF00E676) else Color(0xFFFFB74D),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Completed: ${session.completedReps} • ${session.durationSeconds / 60}m ${session.durationSeconds % 60}s • Avg HR: ${session.averageHeartRate.toInt()} bpm",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                        if (session.extendedRestSeconds > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⏱️ +${session.extendedRestSeconds}s SmartSpectra adaptive recovery rest",
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
        Text(joint, color = Color.LightGray, fontSize = 12.sp)
        Text(status, color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}
