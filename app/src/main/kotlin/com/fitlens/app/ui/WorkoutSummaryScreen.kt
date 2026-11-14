package com.fitlens.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlens.app.data.model.WorkoutSessionEntity
import com.fitlens.app.workout.ProgressionInsight

@Composable
fun WorkoutSummaryScreen(
    session: WorkoutSessionEntity?,
    insight: ProgressionInsight?,
    onReturnToDashboard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E121B))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(36.dp)
                )
                Column {
                    Text(
                        text = "Session Complete!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = session?.exerciseName ?: "Workout",
                        fontSize = 14.sp,
                        color = Color(0xFF6EE7B7)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetricCard(
                    title = "Completed",
                    value = "${session?.completedReps ?: 0}",
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricCard(
                    title = "Form Score",
                    value = "${session?.formAccuracyPercentage?.toInt() ?: 100}%",
                    valueColor = Color(0xFF00E676),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricCard(
                    title = "Avg / Peak HR",
                    value = "${session?.averageHeartRate?.toInt() ?: 0}/${session?.maxHeartRate?.toInt() ?: 0}",
                    valueColor = Color(0xFFFF6B6B),
                    modifier = Modifier.weight(1f)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF192131)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ADK Coach Biomechanical & Vitals Review",
                        color = Color(0xFF4FCCC4),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = session?.coachFeedback ?: "Session saved strictly to your on-device SQLite vault.",
                        color = Color.White,
                        fontSize = 13.sp
                    )

                    if ((session?.extendedRestSeconds ?: 0) > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "⏱️ SmartSpectra Vitals Intervention: +${session?.extendedRestSeconds}s adaptive rest added during elevated heart rate.",
                            color = Color(0xFFFFB74D),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            insight?.let { ins ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2B3D)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ins.title,
                                color = Color(0xFF80DEEA),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Surface(
                                color = Color(0xFF00E676).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Readiness ${ins.overallReadinessScore}/100",
                                    color = Color(0xFF00E676),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = ins.description,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Text(
                text = "Saved strictly on-device in fitlens_health_sqlite.db • Fitness & wellness coaching only (not medical advice).",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = onReturnToDashboard,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
        ) {
            Text("Return to Vitals Dashboard", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SummaryMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF192131)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
        }
    }
}
