package com.fitlens.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
            .background(Color(0xFF101010))
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
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
                        color = Color.LightGray
                    )
                }
            }

            // Key Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetricCard(
                    title = "Total Reps",
                    value = "${session?.completedReps ?: 0}",
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricCard(
                    title = "Form Accuracy",
                    value = "${session?.formAccuracyPercentage?.toInt() ?: 100}%",
                    valueColor = Color(0xFF00E676),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricCard(
                    title = "Avg Heart Rate",
                    value = "${session?.averageHeartRate?.toInt() ?: 0} bpm",
                    valueColor = Color(0xFFFF5252),
                    modifier = Modifier.weight(1f)
                )
            }

            // Coach Biomechanical & Physiological Summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ADK Coach Evaluation",
                        color = Color(0xFF00B0FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = session?.coachFeedback ?: "Session logged successfully with zero API keys.",
                        color = Color.White,
                        fontSize = 13.sp
                    )

                    if ((session?.extendedRestSeconds ?: 0) > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "⏱️ Vitals Intervention: +${session?.extendedRestSeconds}s adaptive rest added for cardiovascular recovery.",
                            color = Color(0xFFFFB74D),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Progressive Overload Next Step
            insight?.let { ins ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
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
        }

        Button(
            onClick = onReturnToDashboard,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
        ) {
            Text("Return to Dashboard", color = Color.Black, fontWeight = FontWeight.Bold)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.Gray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
