package com.fitlens.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlens.app.pose.DeviationSeverity
import com.fitlens.app.workout.WorkoutPhase
import com.fitlens.app.workout.WorkoutState
import kotlin.math.roundToInt

private val PLAYER_CARD_BG = Color(0xFF121216)
private val PLAYER_STAGE_BG = Color(0xFF16161C)
private val PLAYER_BORDER = Color(0xFF27272A)
private val ACCENT_LIME = Color(0xFFA3E635)
private val ACCENT_EMERALD = Color(0xFF10B981)
private val ACCENT_ROSE = Color(0xFFF43F5E)
private val ACCENT_SKY = Color(0xFF38BDF8)
private val TEXT_WHITE = Color(0xFFFAFAFA)
private val TEXT_ZINC = Color(0xFFA1A1AA)

/**
 * Controls how the camera feed is displayed.
 */
enum class CameraFeedDisplayMode(val label: String) {
    PIP_RIGHT("PiP"),
    HIDDEN("Hidden"),
    EXPANDED("Full")
}

data class FeaturedWorkoutProgram(
    val title: String,
    val subtitle: String,
    val goalKey: String,
    val durationMinutes: Int,
    val level: String,
    val badge: String,
    val accentHex: Long
)

val PLAY_STORE_WORKOUT_PROGRAMS = listOf(
    FeaturedWorkoutProgram(
        title = "Core & Abs",
        subtitle = "12 min • Bodyweight",
        goalKey = "Six-Pack Abs",
        durationMinutes = 12,
        level = "Intermediate",
        badge = "CORE",
        accentHex = 0xFFA3E635
    ),
    FeaturedWorkoutProgram(
        title = "Full Body",
        subtitle = "15 min • No equipment",
        goalKey = "Strength",
        durationMinutes = 15,
        level = "Beginner",
        badge = "STRENGTH",
        accentHex = 0xFF10B981
    ),
    FeaturedWorkoutProgram(
        title = "Upper Push",
        subtitle = "14 min • Chest & arms",
        goalKey = "Chest & Arms",
        durationMinutes = 14,
        level = "Intermediate",
        badge = "UPPER",
        accentHex = 0xFF38BDF8
    ),
    FeaturedWorkoutProgram(
        title = "Lower Power",
        subtitle = "15 min • Legs & glutes",
        goalKey = "Legs & Glutes",
        durationMinutes = 15,
        level = "Intermediate",
        badge = "LEGS",
        accentHex = 0xFFA3E635
    ),
    FeaturedWorkoutProgram(
        title = "HIIT Burn",
        subtitle = "10 min • High tempo",
        goalKey = "HIIT Cardio",
        durationMinutes = 10,
        level = "Advanced",
        badge = "HIIT",
        accentHex = 0xFFF43F5E
    )
)

data class ExerciseCoachingMeta(
    val targetMuscles: String,
    val tempoCue: String,
    val formChecklist: List<String>
)

fun getExerciseCoachingMeta(exerciseName: String): ExerciseCoachingMeta {
    val lower = exerciseName.lowercase()
    return when {
        lower.contains("plank") -> ExerciseCoachingMeta(
            targetMuscles = "Core • Glutes",
            tempoCue = "Hold steady • Nasal breathing",
            formChecklist = listOf("Shoulders over elbows, straight body line")
        )
        lower.contains("crunch") || lower.contains("leg raise") || lower.contains("twist") || lower.contains("abs") -> ExerciseCoachingMeta(
            targetMuscles = "Abs • Obliques",
            tempoCue = "Exhale up • 2s lower",
            formChecklist = listOf("Press lower back down, lead with ribs")
        )
        lower.contains("push") || lower.contains("press") -> ExerciseCoachingMeta(
            targetMuscles = "Chest • Triceps",
            tempoCue = "2s down • 1s press",
            formChecklist = listOf("Elbows at 45°, rigid plank line")
        )
        lower.contains("lunge") || lower.contains("split") -> ExerciseCoachingMeta(
            targetMuscles = "Quads • Glutes",
            tempoCue = "2s step • Drive through heel",
            formChecklist = listOf("Torso tall, both knees to 90°")
        )
        lower.contains("jack") || lower.contains("burpee") || lower.contains("knee") || lower.contains("climber") -> ExerciseCoachingMeta(
            targetMuscles = "Full Body • Cardio",
            tempoCue = "Steady pace • Soft landings",
            formChecklist = listOf("Land softly on balls of feet")
        )
        else -> ExerciseCoachingMeta(
            targetMuscles = "Quads • Glutes",
            tempoCue = "2s down • Drive up",
            formChecklist = listOf("Hips back to parallel, chest proud")
        )
    }
}

@Composable
fun AnimatedExerciseIllustration(
    exerciseName: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "exercise_demo")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rep_motion"
    )

    val lower = exerciseName.lowercase()
    val meta = getExerciseCoachingMeta(exerciseName)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = PLAYER_STAGE_BG,
        border = BorderStroke(1.dp, PLAYER_BORDER)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1F222A),
                            Color(0xFF121216)
                        )
                    )
                )
                .padding(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = meta.targetMuscles,
                    color = ACCENT_LIME,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            val phaseText = when {
                lower.contains("plank") -> "HOLD"
                phase < 0.5f -> "INHALE"
                else -> "EXHALE"
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ACCENT_EMERALD.copy(alpha = 0.14f),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = phaseText,
                    color = ACCENT_EMERALD,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp, bottom = 20.dp)
            ) {
                val w = size.width
                val h = size.height
                val cx = w * 0.5f
                val floorY = h * 0.88f

                drawLine(
                    color = Color.White.copy(alpha = 0.12f),
                    start = Offset(w * 0.18f, floorY),
                    end = Offset(w * 0.82f, floorY),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )

                val boneColor = Color(0xFF71717A)
                val activeMuscleColor = ACCENT_LIME
                val jointColor = ACCENT_EMERALD
                val strokeW = 9f

                fun bone(a: Offset, b: Offset, highlight: Boolean = false) {
                    drawLine(
                        color = if (highlight) activeMuscleColor else boneColor,
                        start = a,
                        end = b,
                        strokeWidth = if (highlight) strokeW + 2f else strokeW,
                        cap = StrokeCap.Round
                    )
                }

                fun joint(p: Offset, radius: Float = 6.5f, highlight: Boolean = false) {
                    drawCircle(
                        color = if (highlight) activeMuscleColor else jointColor,
                        radius = radius,
                        center = p
                    )
                }

                when {
                    lower.contains("plank") || lower.contains("climber") -> {
                        val breatheOffset = (phase - 0.5f) * 4f
                        val ankle = Offset(w * 0.24f, floorY - 10f)
                        val hip = Offset(w * 0.50f, floorY - 46f + breatheOffset)
                        val shoulder = Offset(w * 0.73f, floorY - 68f)
                        val elbow = Offset(w * 0.73f, floorY - 10f)
                        val wrist = Offset(w * 0.80f, floorY - 10f)
                        val head = Offset(w * 0.80f, floorY - 76f)
                        val knee = if (lower.contains("climber")) {
                            Offset(
                                x = w * (0.37f + phase * 0.18f),
                                y = floorY - (26f + phase * 18f)
                            )
                        } else {
                            Offset(w * 0.37f, floorY - 28f)
                        }

                        bone(ankle, knee)
                        bone(knee, hip, highlight = true)
                        bone(hip, shoulder, highlight = true)
                        bone(shoulder, elbow)
                        bone(elbow, wrist)
                        drawCircle(color = TEXT_WHITE, radius = 13f, center = head)
                        joint(ankle); joint(knee); joint(hip, 8f, true); joint(shoulder, 8f, true); joint(elbow)
                    }

                    lower.contains("crunch") || lower.contains("leg raise") || lower.contains("twist") || lower.contains("abs") -> {
                        val hip = Offset(w * 0.48f, floorY - 12f)
                        val torsoLift = phase * 34f
                        val shoulder = Offset(w * 0.30f + phase * 14f, floorY - 18f - torsoLift)
                        val head = Offset(shoulder.x - 10f, shoulder.y - 16f)
                        val knee = Offset(w * 0.64f - phase * 16f, floorY - 48f - phase * 18f)
                        val ankle = Offset(w * 0.78f - phase * 12f, floorY - 22f - phase * 24f)
                        val elbow = Offset(shoulder.x + 18f, shoulder.y - 8f)

                        bone(hip, shoulder, highlight = true)
                        bone(shoulder, elbow)
                        bone(hip, knee, highlight = true)
                        bone(knee, ankle)
                        drawCircle(color = TEXT_WHITE, radius = 13f, center = head)
                        joint(hip, 8f, true); joint(shoulder, 8f, true); joint(knee); joint(ankle)
                    }

                    lower.contains("push") || lower.contains("press") -> {
                        val drop = phase * 36f
                        val ankle = Offset(w * 0.24f, floorY - 10f)
                        val hip = Offset(w * 0.49f, floorY - 52f + drop * 0.55f)
                        val shoulder = Offset(w * 0.72f, floorY - 82f + drop)
                        val elbow = Offset(w * 0.66f, floorY - 44f + drop * 0.5f)
                        val wrist = Offset(w * 0.73f, floorY - 10f)
                        val head = Offset(w * 0.79f, shoulder.y - 8f)

                        bone(ankle, hip)
                        bone(hip, shoulder, highlight = true)
                        bone(shoulder, elbow, highlight = true)
                        bone(elbow, wrist, highlight = true)
                        drawCircle(color = TEXT_WHITE, radius = 13f, center = head)
                        joint(ankle); joint(hip); joint(shoulder, 8f, true); joint(elbow, 8f, true); joint(wrist)
                    }

                    lower.contains("jack") || lower.contains("burpee") || lower.contains("hiit") || lower.contains("knee") -> {
                        val spread = phase * (w * 0.13f)
                        val head = Offset(cx, h * 0.20f - phase * 6f)
                        val shoulder = Offset(cx, h * 0.34f - phase * 6f)
                        val hip = Offset(cx, h * 0.56f - phase * 6f)
                        val lAnkle = Offset(cx - 14f - spread, floorY - 8f)
                        val rAnkle = Offset(cx + 14f + spread, floorY - 8f)
                        val lWrist = Offset(cx - 28f - spread * 0.6f, h * (0.52f - phase * 0.36f))
                        val rWrist = Offset(cx + 28f + spread * 0.6f, h * (0.52f - phase * 0.36f))

                        bone(hip, lAnkle, highlight = true)
                        bone(hip, rAnkle, highlight = true)
                        bone(hip, shoulder, highlight = true)
                        bone(shoulder, lWrist)
                        bone(shoulder, rWrist)
                        drawCircle(color = TEXT_WHITE, radius = 13f, center = head)
                        joint(hip, 8f, true); joint(shoulder, 8f, true); joint(lAnkle); joint(rAnkle)
                    }

                    else -> {
                        val squatDepth = phase * (h * 0.22f)
                        val ankle = Offset(cx + 10f, floorY - 8f)
                        val knee = Offset(cx + 26f + phase * 14f, floorY - h * 0.26f + squatDepth * 0.35f)
                        val hip = Offset(cx - 16f - phase * 18f, h * 0.48f + squatDepth)
                        val shoulder = Offset(cx + 4f, h * 0.24f + squatDepth * 0.85f)
                        val head = Offset(shoulder.x + 4f, shoulder.y - 20f)
                        val wrist = Offset(shoulder.x + 36f, shoulder.y + 6f)

                        bone(ankle, knee, highlight = true)
                        bone(knee, hip, highlight = true)
                        bone(hip, shoulder, highlight = true)
                        bone(shoulder, wrist)
                        drawCircle(color = TEXT_WHITE, radius = 13f, center = head)
                        joint(ankle); joint(knee, 8f, true); joint(hip, 8f, true); joint(shoulder)
                    }
                }
            }

            Text(
                text = meta.tempoCue,
                color = TEXT_ZINC,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun ActiveWorkoutPlayerSection(
    workoutState: WorkoutState,
    poseCoachEnabled: Boolean,
    onIncrementRep: () -> Unit,
    onCompleteSet: () -> Unit,
    onSkipPhase: () -> Unit,
    onStopWorkout: () -> Unit,
    onTogglePoseCoach: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalRoutineCount = maxOf(1, workoutState.routineExercises.size)
    val currentExerciseNum = workoutState.currentExerciseIndex + 1

    val criticalDeviation = workoutState.activeDeviations.firstOrNull {
        it.severity == DeviationSeverity.CRITICAL
    }
    val coachBannerText = criticalDeviation?.message
        ?: workoutState.activeCoachAlert
        ?: getExerciseCoachingMeta(workoutState.exerciseName).formChecklist.firstOrNull()
        ?: "Steady cadence"

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PLAYER_CARD_BG,
        border = BorderStroke(1.dp, PLAYER_BORDER)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row: Exercise title, Set badge, Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SET ${workoutState.currentSet}/${workoutState.totalSets} • $currentExerciseNum OF $totalRoutineCount",
                        color = ACCENT_LIME,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = workoutState.exerciseName,
                        color = TEXT_WHITE,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PLAYER_STAGE_BG,
                        border = BorderStroke(1.dp, PLAYER_BORDER),
                        modifier = Modifier.clickable { onTogglePoseCoach() }
                    ) {
                        Text(
                            text = if (poseCoachEnabled) "Form ${workoutState.formAccuracyPercentage.roundToInt()}%" else "Form AI Off",
                            color = if (poseCoachEnabled) ACCENT_EMERALD else TEXT_ZINC,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PLAYER_STAGE_BG
                    ) {
                        val mins = workoutState.elapsedSeconds / 60
                        val secs = workoutState.elapsedSeconds % 60
                        Text(
                            text = String.format("%02d:%02d", mins, secs),
                            color = TEXT_WHITE,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Compact 1-line cue banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (criticalDeviation != null) ACCENT_ROSE.copy(alpha = 0.14f) else PLAYER_STAGE_BG
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (criticalDeviation != null) ACCENT_ROSE else ACCENT_LIME,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = coachBannerText,
                        color = if (criticalDeviation != null) ACCENT_ROSE else TEXT_WHITE,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            when (workoutState.phase) {
                WorkoutPhase.REST_INTERVAL, WorkoutPhase.COOLDOWN -> {
                    SmartSpectraRecoveryStage(
                        workoutState = workoutState,
                        onSkipPhase = onSkipPhase
                    )
                }
                WorkoutPhase.WARMUP -> {
                    AnimatedExerciseIllustration(
                        exerciseName = workoutState.exerciseName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Warmup • ${workoutState.phaseCountdownSeconds}s",
                            color = TEXT_WHITE,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Button(
                            onClick = onSkipPhase,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ACCENT_LIME)
                        ) {
                            Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Start", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                else -> {
                    AnimatedExerciseIllustration(
                        exerciseName = workoutState.exerciseName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(165.dp)
                    )

                    val progress = (workoutState.completedReps.toFloat() / workoutState.targetReps.coerceAtLeast(1)).coerceIn(0f, 1f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = if (workoutState.isIsometricHold) {
                                "${workoutState.completedReps} / ${workoutState.targetReps}s"
                            } else {
                                "${workoutState.completedReps} / ${workoutState.targetReps} reps"
                            },
                            color = TEXT_WHITE,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = workoutState.autonomicReadinessLabel,
                            color = TEXT_ZINC,
                            fontSize = 12.sp
                        )
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = ACCENT_LIME,
                        trackColor = Color.White.copy(alpha = 0.08f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onIncrementRep,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ACCENT_LIME)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (workoutState.isIsometricHold) "+5s" else "+1 Rep",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Button(
                            onClick = onCompleteSet,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ACCENT_EMERALD)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Next Set",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        OutlinedButton(
                            onClick = onStopWorkout,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PLAYER_BORDER)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = ACCENT_ROSE, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("End", color = TEXT_WHITE, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartSpectraRecoveryStage(
    workoutState: WorkoutState,
    onSkipPhase: () -> Unit
) {
    val countdown = if (workoutState.phase == WorkoutPhase.COOLDOWN) {
        workoutState.phaseCountdownSeconds
    } else {
        workoutState.restSecondsRemaining
    }

    val transition = rememberInfiniteTransition(label = "breathing_ring")
    val ringScale by transition.animateFloat(
        initialValue = 0.76f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PLAYER_STAGE_BG,
        border = BorderStroke(1.dp, PLAYER_BORDER)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (workoutState.isRestExtended) {
                Text(
                    text = "Rest extended for HR recovery",
                    color = ACCENT_SKY,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier.size(104.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = ACCENT_EMERALD.copy(alpha = 0.14f),
                        radius = size.minDimension * 0.48f * ringScale
                    )
                    drawCircle(
                        color = ACCENT_LIME,
                        radius = size.minDimension * 0.44f * ringScale,
                        style = Stroke(width = 4.5f)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${countdown}s",
                        color = TEXT_WHITE,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (workoutState.phase == WorkoutPhase.COOLDOWN) "COOLDOWN" else "REST",
                        color = TEXT_ZINC,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Inhale 4s • Exhale 6s",
                color = TEXT_ZINC,
                fontSize = 12.sp
            )

            Button(
                onClick = onSkipPhase,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ACCENT_LIME)
            ) {
                Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (workoutState.phase == WorkoutPhase.COOLDOWN) "Finish" else "Skip Rest",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
