package com.fitlens.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlens.app.data.model.WorkoutSessionEntity
import com.fitlens.app.workout.WorkoutState
import java.text.SimpleDateFormat
import java.util.*

/**
 * Post-workout summary screen that displays session statistics, physiological trends,
 * and AI-generated coaching recommendations.
 *
 * Shown when the workout phase transitions to COMPLETED.
 */
@Composable
fun WorkoutSummaryScreen(
    workoutState: WorkoutState,
    recentSessions: List<WorkoutSessionEntity>,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lastSession = recentSessions.firstOrNull()
    val fmt = remember { SimpleDateFormat("MMM d • HH:mm", Locale.getDefault()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(FitLensPalette.Obsidian, Color(0xFF0D0D12), FitLensPalette.Obsidian)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header — celebration badge
            Spacer(modifier = Modifier.height(32.dp))
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Workout Complete",
                tint = FitLensPalette.Lime,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Workout Complete",
                color = FitLensPalette.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Great effort!",
                color = FitLensPalette.Zinc,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(32.dp))

            lastSession?.let { session ->
                // Session overview card
                SessionOverviewCard(session, fmt)
                Spacer(modifier = Modifier.height(16.dp))

                // Physiological trends
                PhysiologicalTrendsCard(session)
                Spacer(modifier = Modifier.height(16.dp))

                // AI coaching recommendations
                AiCoachingCard(session)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Recent sessions if there are more
            if (recentSessions.size > 1) {
                RecentSessionsList(recentSessions.drop(1), fmt)
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Back to home button
            Button(
                onClick = onBackToHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FitLensPalette.Lime,
                    contentColor = FitLensPalette.Black
                )
            ) {
                Text(
                    text = "Back to Home",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SessionOverviewCard(session: WorkoutSessionEntity, fmt: SimpleDateFormat) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = FitLensPalette.Card,
        border = androidx.compose.foundation.border(1.dp, FitLensPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = session.exerciseName,
                        color = FitLensPalette.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = fmt.format(Date(session.timestamp)),
                        color = FitLensPalette.ZincMuted,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = "${session.durationSeconds / 60}m ${session.durationSeconds % 60}s",
                    color = FitLensPalette.Lime,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(
                color = FitLensPalette.Border,
                thickness = 1.dp
            )

            // Key stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatPill(label = "Reps", value = "${session.completedReps}")
                StatPill(label = "Avg HR", value = "${session.averageHeartRate.roundToInt()}")
                StatPill(label = "Peak HR", value = "${session.maxHeartRate.roundToInt()}")
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = FitLensPalette.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            color = FitLensPalette.ZincMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PhysiologicalTrendsCard(session: WorkoutSessionEntity) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = FitLensPalette.Card,
        border = androidx.compose.foundation.border(1.dp, FitLensPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Physiological Trends",
                color = FitLensPalette.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            // Heart rate zone breakdown
            val hrZone = when {
                session.averageHeartRate < 100 -> "Resting"
                session.averageHeartRate < 120 -> "Zone 1 · Fat Burn"
                session.averageHeartRate < 140 -> "Zone 2 · Cardio"
                session.averageHeartRate < 160 -> "Zone 3 · Aerobic"
                session.averageHeartRate < 175 -> "Zone 4 · Anaerobic"
                else -> "Zone 5 · Max Effort"
            }
            val zoneColor = when {
                session.averageHeartRate < 120 -> FitLensPalette.Sky
                session.averageHeartRate < 140 -> FitLensPalette.Emerald
                session.averageHeartRate < 160 -> FitLensPalette.Lime
                session.averageHeartRate < 175 -> FitLensPalette.Amber
                else -> FitLensPalette.Rose
            }

            TrendRow(
                label = "Heart Rate Zone",
                value = hrZone,
                accentColor = zoneColor
            )

            // HR recovery indicator
            val hrRecovery = session.maxHeartRate - session.averageHeartRate
            val recoveryLabel = when {
                hrRecovery > 30 -> "Excellent Recovery"
                hrRecovery > 15 -> "Good Recovery"
                hrRecovery > 5 -> "Moderate Recovery"
                else -> "Elevated — Rest Well"
            }
            val recoveryColor = when {
                hrRecovery > 30 -> FitLensPalette.Emerald
                hrRecovery > 15 -> FitLensPalette.Lime
                hrRecovery > 5 -> FitLensPalette.Amber
                else -> FitLensPalette.Rose
            }
            TrendRow(
                label = "HR Recovery",
                value = recoveryLabel,
                accentColor = recoveryColor
            )

            // Extended rest info
            if (session.extendedRestSeconds > 0) {
                TrendRow(
                    label = "Adaptive Rest Added",
                    value = "+${session.extendedRestSeconds}s",
                    accentColor = FitLensPalette.Sky
                )
            }
        }
    }
}

@Composable
private fun TrendRow(label: String, value: String, accentColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = FitLensPalette.Zinc,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = accentColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = value,
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AiCoachingCard(session: WorkoutSessionEntity) {
    AnimatedVisibility(
        visible = session.coachFeedback.isNotBlank(),
        enter = fadeIn() + slideInVertically(initialOffsetY = { it })
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = FitLensPalette.Card,
            border = androidx.compose.foundation.border(1.dp, FitLensPalette.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Coach",
                        tint = FitLensPalette.Lime,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "AI Coach Recommendations",
                        color = FitLensPalette.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = session.coachFeedback,
                    color = FitLensPalette.Zinc,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                // Exertion score badge
                val exertionScore = when {
                    session.averageHeartRate > 160 -> 9
                    session.averageHeartRate > 140 -> 7
                    session.averageHeartRate > 120 -> 5
                    else -> 3
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Exertion Score:",
                        color = FitLensPalette.ZincMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = FitLensPalette.Lime.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "$exertionScore / 10",
                            color = FitLensPalette.Lime,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSessionsList(
    sessions: List<WorkoutSessionEntity>,
    fmt: SimpleDateFormat
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Recent Sessions",
            color = FitLensPalette.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        sessions.take(3).forEach { session ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = FitLensPalette.Card,
                border = androidx.compose.foundation.border(1.dp, FitLensPalette.Border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                FitLensPalette.Lime.copy(alpha = 0.12f),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🏋️",
                            fontSize = 18.sp
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.exerciseName,
                            color = FitLensPalette.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = fmt.format(Date(session.timestamp)),
                            color = FitLensPalette.ZincMuted,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "${session.completedReps} reps",
                        color = FitLensPalette.Lime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
