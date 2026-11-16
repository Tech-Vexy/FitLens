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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FitnessCenter
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

/**
 * Controls how the camera feed is displayed so FitLens looks and feels like a dedicated
 * workout app (e.g., Home Workout / Six Pack in 30 Days) rather than a camera app.
 */
enum class CameraFeedDisplayMode(val label: String) {
    PIP_RIGHT("PiP Corner"),
    HIDDEN("Camera Hidden"),
    EXPANDED("Full Form Check")
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
        title = "Six-Pack Abs Shred",
        subtitle = "Core definition, lower abs & obliques",
        goalKey = "Six-Pack Abs",
        durationMinutes = 12,
        level = "Intermediate",
        badge = "CORE & ABS",
        accentHex = 0xFFFF6B6B
    ),
    FeaturedWorkoutProgram(
        title = "Home Full Body Burn",
        subtitle = "Zero-equipment compound conditioning",
        goalKey = "Strength",
        durationMinutes = 15,
        level = "Beginner",
        badge = "NO EQUIPMENT",
        accentHex = 0xFF4FCCC4
    ),
    FeaturedWorkoutProgram(
        title = "Chest & Arm Sculpt",
        subtitle = "Upper body push power & triceps",
        goalKey = "Chest & Arms",
        durationMinutes = 14,
        level = "Intermediate",
        badge = "UPPER BODY",
        accentHex = 0xFFA58CF9
    ),
    FeaturedWorkoutProgram(
        title = "Legs & Glutes Power",
        subtitle = "Quad, hamstring & hip stability",
        goalKey = "Legs & Glutes",
        durationMinutes = 15,
        level = "Intermediate",
        badge = "LOWER BODY",
        accentHex = 0xFF6EE7B7
    ),
    FeaturedWorkoutProgram(
        title = "HIIT Fat Burner",
        subtitle = "High-tempo metabolic intervals",
        goalKey = "HIIT Cardio",
        durationMinutes = 10,
        level = "Advanced",
        badge = "CARDIO HIIT",
        accentHex = 0xFFFBBF24
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
            targetMuscles = "Transverse Abdominis • Obliques • Glutes",
            tempoCue = "Isometric Hold • Steady 4s nasal breathing",
            formChecklist = listOf(
                "Stack shoulders directly over elbows or wrists",
                "Squeeze glutes & brace core in a straight line",
                "Keep neck neutral — do not let hips sag or pike"
            )
        )
        lower.contains("crunch") || lower.contains("leg raise") || lower.contains("twist") || lower.contains("abs") -> ExerciseCoachingMeta(
            targetMuscles = "Rectus Abdominis (Six-Pack) • Obliques",
            tempoCue = "Exhale on crunch • 2s controlled eccentric lower",
            formChecklist = listOf(
                "Press lower back gently into the floor",
                "Exhale sharply at peak abdominal contraction",
                "Avoid pulling on your neck — lead with ribs"
            )
        )
        lower.contains("push") || lower.contains("press") -> ExerciseCoachingMeta(
            targetMuscles = "Pectorals • Anterior Delts • Triceps • Core",
            tempoCue = "2s inhale down • 1s forceful exhale press",
            formChecklist = listOf(
                "Tuck elbows at ~45° to protect shoulder joints",
                "Maintain rigid plank line from head to heels",
                "Lower chest to elbow height before pressing up"
            )
        )
        lower.contains("lunge") || lower.contains("split") -> ExerciseCoachingMeta(
            targetMuscles = "Quadriceps • Gluteus Maximus • Hamstrings",
            tempoCue = "2s step & lower • Drive through front heel",
            formChecklist = listOf(
                "Keep torso tall and core braced",
                "Lower until both knees reach ~90°",
                "Track front knee in line with second toe"
            )
        )
        lower.contains("jack") || lower.contains("burpee") || lower.contains("knee") || lower.contains("climber") -> ExerciseCoachingMeta(
            targetMuscles = "Full Body Conditioning • Cardio • Core",
            tempoCue = "Rhythmic athletic cadence • Soft landings",
            formChecklist = listOf(
                "Land softly on the balls of your feet",
                "Keep core engaged and shoulders relaxed",
                "Maintain rhythmic nasal-to-mouth breathing"
            )
        )
        else -> ExerciseCoachingMeta(
            targetMuscles = "Quadriceps • Glutes • Core Stabilizers",
            tempoCue = "2s inhale descent • 1s drive up through mid-foot",
            formChecklist = listOf(
                "Hinge hips back and down until thighs are parallel",
                "Drive knees outward in line with toes (no valgus)",
                "Keep chest proud and spine neutral throughout"
            )
        )
    }
}

/**
 * Animated visual exercise trainer canvas (Home Workout / Six-Pack App style).
 * Shows the user exactly how to perform the exercise with animated body mechanics
 * and highlighted target muscle groups so the app never relies on showing a giant camera feed.
 */
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
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101829),
        border = BorderStroke(1.dp, Color(0xFF4FCCC4).copy(alpha = 0.35f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            Color(0xFF0B111E)
                        )
                    )
                )
                .padding(12.dp)
        ) {
            // Top-left Target Muscle Tag
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF05070C).copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = meta.targetMuscles,
                    color = Color(0xFF6EE7B7),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Top-right Live Tempo Phase Cue
            val phaseText = when {
                lower.contains("plank") -> "HOLD & BRACE CORE"
                phase < 0.5f -> "INHALE • CONTROLLED"
                else -> "EXHALE • ENGAGE"
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFA58CF9).copy(alpha = 0.2f),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = phaseText,
                    color = Color(0xFFA58CF9),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Animated Biomechanical Figure
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp, bottom = 18.dp)
            ) {
                val w = size.width
                val h = size.height
                val cx = w * 0.5f
                val floorY = h * 0.88f

                // Floor reference line
                drawLine(
                    color = Color.White.copy(alpha = 0.16f),
                    start = Offset(w * 0.15f, floorY),
                    end = Offset(w * 0.85f, floorY),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )

                val boneColor = Color(0xFF4FCCC4)
                val activeMuscleColor = Color(0xFFFF6B6B)
                val jointColor = Color(0xFF6EE7B7)
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

                fun joint(p: Offset, radius: Float = 7f, highlight: Boolean = false) {
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
                        drawCircle(color = boneColor, radius = 14f, center = head)
                        joint(ankle); joint(knee); joint(hip, 9f, true); joint(shoulder, 8f, true); joint(elbow)
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
                        drawCircle(color = boneColor, radius = 14f, center = head)
                        joint(hip, 9f, true); joint(shoulder, 8f, true); joint(knee); joint(ankle)
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
                        drawCircle(color = boneColor, radius = 14f, center = head)
                        joint(ankle); joint(hip); joint(shoulder, 9f, true); joint(elbow, 8f, true); joint(wrist)
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
                        drawCircle(color = boneColor, radius = 14f, center = head)
                        joint(hip, 8f, true); joint(shoulder, 8f, true); joint(lAnkle); joint(rAnkle)
                    }

                    else -> {
                        // Squat / Lunge lower-body animation
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
                        drawCircle(color = boneColor, radius = 14f, center = head)
                        joint(ankle); joint(knee, 9f, true); joint(hip, 9f, true); joint(shoulder)
                    }
                }
            }

            // Bottom Tempo Guidance Bar
            Text(
                text = meta.tempoCue,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * Full workout player card displayed when a workout or AI routine is active.
 * Centers the exercise demonstration, AI Coach cues, Set/Rep/Timer progress,
 * and SmartSpectra Vitals-Guided Rest intervals.
 */
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
    val meta = getExerciseCoachingMeta(workoutState.exerciseName)
    val totalRoutineCount = maxOf(1, workoutState.routineExercises.size)
    val currentExerciseNum = workoutState.currentExerciseIndex + 1

    val criticalDeviation = workoutState.activeDeviations.firstOrNull {
        it.severity == DeviationSeverity.CRITICAL
    }
    val coachBannerText = criticalDeviation?.message
        ?: workoutState.activeCoachAlert
        ?: workoutState.expressionCoachCue
        ?: "AI Coach: Keep steady breathing and controlled tempo."

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Live AI Trainer Guidance Banner
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (criticalDeviation != null) Color(0xFF3B1219) else Color(0xFF141E33),
            border = BorderStroke(
                1.dp,
                if (criticalDeviation != null) Color(0xFFFF6B6B) else Color(0xFFA58CF9).copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (criticalDeviation != null) Color(0xFFFF6B6B) else Color(0xFFA58CF9),
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (criticalDeviation != null) "AI FORM CORRECTION" else "AI PERSONAL TRAINER CUE",
                        color = if (criticalDeviation != null) Color(0xFFFF6B6B) else Color(0xFFA58CF9),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = coachBannerText,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.clickable { onTogglePoseCoach() }
                ) {
                    Text(
                        text = if (poseCoachEnabled) "Form AI: ${workoutState.formAccuracyPercentage.roundToInt()}%" else "Enable Form AI",
                        color = Color(0xFF6EE7B7),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // 2. Routine Progress & Exercise Title Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF111827),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "EXERCISE $currentExerciseNum OF $totalRoutineCount • SET ${workoutState.currentSet}/${workoutState.totalSets}",
                            color = Color(0xFF4FCCC4),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = workoutState.exerciseName,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        val mins = workoutState.elapsedSeconds / 60
                        val secs = workoutState.elapsedSeconds % 60
                        Text(
                            text = String.format("%02d:%02d", mins, secs),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Routine Stepper Pills
                if (workoutState.routineExercises.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        workoutState.routineExercises.forEachIndexed { idx, exName ->
                            val isCurrent = idx == workoutState.currentExerciseIndex
                            val isDone = idx < workoutState.currentExerciseIndex
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    isCurrent -> Color(0xFF4FCCC4).copy(alpha = 0.25f)
                                    isDone -> Color(0xFF6EE7B7).copy(alpha = 0.20f)
                                    else -> Color.White.copy(alpha = 0.06f)
                                }
                            ) {
                                Text(
                                    text = "${idx + 1}. $exName",
                                    color = when {
                                        isCurrent -> Color(0xFF4FCCC4)
                                        isDone -> Color(0xFF6EE7B7)
                                        else -> Color.White.copy(alpha = 0.5f)
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Phase-specific Visual Stage
                when (workoutState.phase) {
                    WorkoutPhase.REST_INTERVAL, WorkoutPhase.COOLDOWN -> {
                        SmartSpectraRecoveryStage(
                            workoutState = workoutState,
                            onSkipPhase = onSkipPhase
                        )
                    }
                    WorkoutPhase.WARMUP -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnimatedExerciseIllustration(
                                exerciseName = workoutState.exerciseName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(175.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Dynamic Warmup • ${workoutState.phaseCountdownSeconds}s",
                                        color = Color(0xFFFBBF24),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "Loosen joints and establish steady nasal breathing.",
                                        color = Color.LightGray,
                                        fontSize = 12.sp
                                    )
                                }
                                Button(
                                    onClick = onSkipPhase,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FCCC4))
                                ) {
                                    Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Start Set 1", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    else -> {
                        // ACTIVE_SET Visual Trainer + Rep Counter
                        AnimatedExerciseIllustration(
                            exerciseName = workoutState.exerciseName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(185.dp)
                        )

                        // Rep / Hold Progress Bar & Counter
                        val progress = (workoutState.completedReps.toFloat() / workoutState.targetReps.coerceAtLeast(1)).coerceIn(0f, 1f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = if (workoutState.isIsometricHold) "HOLD TIMER" else "TARGET REPS",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (workoutState.isIsometricHold) {
                                        "${workoutState.completedReps} / ${workoutState.targetReps} sec"
                                    } else {
                                        "${workoutState.completedReps} / ${workoutState.targetReps} reps"
                                    },
                                    color = Color(0xFF6EE7B7),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = "Autonomic Load: ${workoutState.autonomicReadinessLabel}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = Color(0xFF6EE7B7),
                            trackColor = Color.White.copy(alpha = 0.12f)
                        )

                        // Pro Form Checklist
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            meta.formChecklist.forEach { tip ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4FCCC4),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = tip,
                                        color = Color(0xFFD5DCE6),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Action Buttons (+1 Rep, Complete Set, End Workout)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onIncrementRep,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FCCC4))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (workoutState.isIsometricHold) "+5s Hold" else "+1 Rep",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = onCompleteSet,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA58CF9))
                            ) {
                                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Done Set",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            OutlinedButton(
                                onClick = onStopWorkout,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.7f))
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("End", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Displayed during REST_INTERVAL and COOLDOWN.
 * Highlights how SmartSpectra replaces expensive wearables by automatically extending
 * recovery intervals when heart rate is elevated and pacing coherent nasal breathing.
 */
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
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0D1526),
        border = BorderStroke(1.dp, Color(0xFF4FCCC4).copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (workoutState.isRestExtended) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFBBF24).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "⚡ SmartSpectra Adaptive Rest: Extended for HR Recovery",
                        color = Color(0xFFFBBF24),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0xFF4FCCC4).copy(alpha = 0.18f),
                        radius = size.minDimension * 0.48f * ringScale
                    )
                    drawCircle(
                        color = Color(0xFF6EE7B7),
                        radius = size.minDimension * 0.44f * ringScale,
                        style = Stroke(width = 5f)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${countdown}s",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (workoutState.phase == WorkoutPhase.COOLDOWN) "COOLDOWN" else "RECOVERY",
                        color = Color(0xFF4FCCC4),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = workoutState.breathingDirective.ifBlank {
                    "Coherent Recovery Pacer: Inhale 4s through nose • Exhale 6s slowly"
                },
                color = Color(0xFFD5DCE6),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val nextIdx = if (workoutState.currentSet >= workoutState.totalSets) {
                    workoutState.currentExerciseIndex + 1
                } else {
                    workoutState.currentExerciseIndex
                }
                val nextExercise = workoutState.routineExercises.getOrNull(nextIdx) ?: workoutState.exerciseName
                Text(
                    text = "Next up: $nextExercise",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    onClick = onSkipPhase,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6EE7B7))
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
}
