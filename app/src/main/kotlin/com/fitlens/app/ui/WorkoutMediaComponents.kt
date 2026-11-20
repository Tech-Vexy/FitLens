package com.fitlens.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlens.app.ai.media.ExerciseMediaPack
import com.fitlens.app.ai.media.GeminiOmniVideoWalkthrough
import com.fitlens.app.ai.media.NanobananaProcedureStep
import com.fitlens.app.ai.media.WorkoutMediaSource
import kotlinx.coroutines.delay

/**
 * Nanobanana-styled interactive procedure card displaying step-by-step visual guides
 * for workout execution with biomechanics and active muscle activation.
 */
@Composable
fun NanobananaProcedureCard(
    mediaPack: ExerciseMediaPack,
    modifier: Modifier = Modifier,
    onStepSelected: ((Int) -> Unit)? = null
) {
    var selectedStepIndex by remember { mutableStateOf(0) }
    val steps = mediaPack.procedureSteps
    val currentStep = steps.getOrNull(selectedStepIndex) ?: steps.firstOrNull()
    val hasNanobananaImage = mediaPack.nanobananaImages.isNotEmpty()
    val procedureBadge = if (hasNanobananaImage) "NANOBANANA PROCEDURES" else "LOCAL PROCEDURE GUIDE"
    val procedureAccent = if (hasNanobananaImage) FitLensPalette.Lime else FitLensPalette.Zinc

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = FitLensPalette.Card,
        border = BorderStroke(1.dp, FitLensPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header badge: Nanobanana Branding & Category
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = procedureAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, procedureAccent.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = procedureAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = procedureBadge,
                                color = procedureAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Text(
                    text = "${steps.size} STEPS",
                    color = FitLensPalette.ZincMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Step Selector Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                steps.forEachIndexed { idx, step ->
                    val isSelected = idx == selectedStepIndex
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) FitLensPalette.ElevatedAlt else Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) FitLensPalette.Lime else FitLensPalette.BorderLight
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedStepIndex = idx
                                onStepSelected?.invoke(idx)
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "STEP ${step.stepNumber}",
                                color = if (isSelected) FitLensPalette.Lime else FitLensPalette.ZincMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = step.title.substringBefore(" &").take(12),
                                color = if (isSelected) FitLensPalette.White else FitLensPalette.Zinc,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Nanobanana Vector Illustration Stage
            currentStep?.let { step ->
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                    label = "stepVisual"
                ) { animatedStep ->
                    NanobananaIllustrationStage(
                        exerciseName = mediaPack.exerciseName,
                        step = animatedStep,
                        generatedImage = mediaPack.nanobananaImages[animatedStep.stepNumber],
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(175.dp)
                    )
                }

                // Step Instructions & Biomechanics Checklist
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = FitLensPalette.Elevated,
                    border = BorderStroke(1.dp, FitLensPalette.Border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = step.instruction,
                            color = FitLensPalette.White,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "BREATHE:",
                                    color = FitLensPalette.Sky,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = step.breathingCue,
                                    color = FitLensPalette.Zinc,
                                    fontSize = 11.sp
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = FitLensPalette.Emerald.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = step.keyJointAngles,
                                    color = FitLensPalette.Emerald,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Target muscles pill row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TARGET:",
                                color = FitLensPalette.ZincMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            step.targetMuscles.forEach { muscle ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = FitLensPalette.ElevatedAlt
                                ) {
                                    Text(
                                        text = muscle,
                                        color = FitLensPalette.Lime,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Nanobanana Studio visual stage: Renders high-precision aesthetic poses with muscle activation highlights.
 */
@Composable
private fun NanobananaIllustrationStage(
    exerciseName: String,
    step: NanobananaProcedureStep,
    generatedImage: Bitmap? = null,
    modifier: Modifier = Modifier
) {
    val phaseFraction = step.progressFraction
    val lower = exerciseName.lowercase()
    val isDark = LocalFitLensColors.current.isDark

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = FitLensPalette.ElevatedAlt,
        border = BorderStroke(1.dp, FitLensPalette.Border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(Color(0xFF1E1E26), Color(0xFF121218))
                        } else {
                            listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                        }
                    )
                )
                .padding(10.dp)
        ) {
            generatedImage?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Nano Banana procedure visual for ${step.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 22.dp, bottom = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 22.dp, bottom = 12.dp)
                        .background(Color.Black.copy(alpha = if (isDark) 0.34f else 0.24f))
                )
            }

            // Stage Badge: Step tag
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = FitLensPalette.Lime.copy(alpha = 0.18f),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = "STEP ${step.stepNumber}: ${step.title.uppercase()}",
                    color = FitLensPalette.Lime,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            // Biomechanics cue badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = FitLensPalette.Card.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = step.biomechanicsCue.take(28),
                    color = FitLensPalette.Zinc,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            // Canvas drawing procedure figure
            val boneColor = FitLensPalette.BoneColor
            val muscleColor = FitLensPalette.Lime
            val jointColor = FitLensPalette.Emerald
            val floorColor = FitLensPalette.FloorLineColor
            val headColor = FitLensPalette.White

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 22.dp, bottom = 12.dp)
            ) {
                val w = size.width
                val h = size.height
                val cx = w * 0.5f
                val floorY = h * 0.88f

                // Studio floor line
                drawLine(
                    color = floorColor,
                    start = Offset(w * 0.12f, floorY),
                    end = Offset(w * 0.88f, floorY),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )

                fun bone(a: Offset, b: Offset, highlight: Boolean = false) {
                    drawLine(
                        color = if (highlight) muscleColor else boneColor,
                        start = a,
                        end = b,
                        strokeWidth = if (highlight) 10f else 8f,
                        cap = StrokeCap.Round
                    )
                }

                fun joint(p: Offset, radius: Float = 6f, highlight: Boolean = false) {
                    drawCircle(
                        color = if (highlight) muscleColor else jointColor,
                        radius = radius,
                        center = p
                    )
                }

                when {
                    lower.contains("squat") -> {
                        val squatDepth = phaseFraction * (h * 0.24f)
                        val ankle = Offset(cx + 8f, floorY - 6f)
                        val knee = Offset(cx + 24f + phaseFraction * 16f, floorY - h * 0.28f + squatDepth * 0.4f)
                        val hip = Offset(cx - 18f - phaseFraction * 20f, h * 0.46f + squatDepth)
                        val shoulder = Offset(cx + 2f, h * 0.22f + squatDepth * 0.85f)
                        val head = Offset(shoulder.x + 4f, shoulder.y - 18f)
                        val wrist = Offset(shoulder.x + 38f, shoulder.y + 4f)

                        bone(ankle, knee, highlight = true)
                        bone(knee, hip, highlight = true)
                        bone(hip, shoulder, highlight = true)
                        bone(shoulder, wrist)
                        drawCircle(color = headColor, radius = 12f, center = head)
                        joint(ankle); joint(knee, 8f, true); joint(hip, 8f, true); joint(shoulder)
                    }

                    lower.contains("push") || lower.contains("press") -> {
                        val drop = phaseFraction * 36f
                        val ankle = Offset(w * 0.22f, floorY - 10f)
                        val hip = Offset(w * 0.48f, floorY - 50f + drop * 0.5f)
                        val shoulder = Offset(w * 0.72f, floorY - 80f + drop)
                        val elbow = Offset(w * 0.65f, floorY - 42f + drop * 0.5f)
                        val wrist = Offset(w * 0.73f, floorY - 10f)
                        val head = Offset(w * 0.80f, shoulder.y - 6f)

                        bone(ankle, hip)
                        bone(hip, shoulder, highlight = true)
                        bone(shoulder, elbow, highlight = true)
                        bone(elbow, wrist, highlight = true)
                        drawCircle(color = headColor, radius = 12f, center = head)
                        joint(ankle); joint(hip, 7f, true); joint(shoulder, 8f, true); joint(elbow, 8f, true); joint(wrist)
                    }

                    lower.contains("plank") -> {
                        val ankle = Offset(w * 0.24f, floorY - 10f)
                        val hip = Offset(w * 0.50f, floorY - 46f)
                        val shoulder = Offset(w * 0.73f, floorY - 68f)
                        val elbow = Offset(w * 0.73f, floorY - 10f)
                        val wrist = Offset(w * 0.80f, floorY - 10f)
                        val head = Offset(w * 0.80f, floorY - 76f)

                        bone(ankle, hip, highlight = true)
                        bone(hip, shoulder, highlight = true)
                        bone(shoulder, elbow)
                        bone(elbow, wrist)
                        drawCircle(color = headColor, radius = 12f, center = head)
                        joint(ankle); joint(hip, 8f, true); joint(shoulder, 8f, true); joint(elbow)
                    }

                    lower.contains("lunge") -> {
                        val stepForward = phaseFraction * (w * 0.08f)
                        val head = Offset(w * 0.60f + stepForward * 0.3f, h * 0.16f)
                        val shoulder = Offset(w * 0.58f + stepForward * 0.3f, h * 0.32f)
                        val hip = Offset(w * 0.54f + stepForward * 0.4f, h * 0.52f)
                        val frontKnee = Offset(w * 0.68f + stepForward, floorY - h * 0.26f)
                        val frontAnkle = Offset(w * 0.72f + stepForward, floorY - 8f)
                        val backKnee = Offset(w * 0.38f - stepForward * 0.3f, floorY - h * 0.22f)
                        val backAnkle = Offset(w * 0.28f - stepForward * 0.4f, floorY - 8f)

                        bone(backAnkle, backKnee)
                        bone(backKnee, hip)
                        bone(hip, frontKnee, highlight = true)
                        bone(frontKnee, frontAnkle, highlight = true)
                        bone(hip, shoulder, highlight = true)
                        drawCircle(color = headColor, radius = 12f, center = head)
                        joint(hip, 8f, true); joint(frontKnee, 8f, true); joint(shoulder, 7f)
                    }

                    else -> {
                        // Core & cardio generic
                        val spread = phaseFraction * (w * 0.12f)
                        val head = Offset(cx, h * 0.20f)
                        val shoulder = Offset(cx, h * 0.34f)
                        val hip = Offset(cx, h * 0.56f)
                        val lAnkle = Offset(cx - 14f - spread, floorY - 8f)
                        val rAnkle = Offset(cx + 14f + spread, floorY - 8f)
                        val lWrist = Offset(cx - 28f - spread * 0.6f, h * (0.50f - phaseFraction * 0.30f))
                        val rWrist = Offset(cx + 28f + spread * 0.6f, h * (0.50f - phaseFraction * 0.30f))

                        bone(hip, lAnkle, highlight = true)
                        bone(hip, rAnkle, highlight = true)
                        bone(hip, shoulder, highlight = true)
                        bone(shoulder, lWrist)
                        bone(shoulder, rWrist)
                        drawCircle(color = headColor, radius = 12f, center = head)
                        joint(hip, 8f, true); joint(shoulder, 8f, true); joint(lAnkle); joint(rAnkle)
                    }
                }
            }
        }
    }
}

/**
 * Gemini Omni Video Walkthrough Player:
 * Complete video walkthrough with scrubbing, multi-phase navigation, tempo pacing, and audio narration.
 */
@Composable
fun GeminiOmniVideoWalkthroughPlayer(
    walkthrough: GeminiOmniVideoWalkthrough,
    exerciseName: String,
    modifier: Modifier = Modifier,
    mediaSource: WorkoutMediaSource = WorkoutMediaSource.LOCAL_SYNTHESIS,
    onSpeakNarration: ((String) -> Unit)? = null
) {
    var isPlaying by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var currentSeconds by remember { mutableFloatStateOf(0.0f) }

    val totalDuration = walkthrough.totalDurationSeconds.toFloat().coerceAtLeast(1f)
    val phases = walkthrough.phases
    if (phases.isEmpty()) return
    val activePhaseIndex = phases.indexOfLast { currentSeconds >= it.startSeconds }.coerceAtLeast(0)
    val activePhase = phases.getOrNull(activePhaseIndex) ?: phases.first()
    val isCloudWalkthrough = mediaSource == WorkoutMediaSource.HYBRID_FIREBASE_AI_LOGIC ||
        mediaSource == WorkoutMediaSource.GEMINI_OMNI_WALKTHROUGH
    val walkthroughBadge = if (isCloudWalkthrough) "GEMINI OMNI WALKTHROUGH" else "INTERACTIVE WALKTHROUGH"
    val walkthroughAccent = if (isCloudWalkthrough) FitLensPalette.Sky else FitLensPalette.Zinc

    // Smooth playback loop
    LaunchedEffect(isPlaying, playbackSpeed) {
        if (!isPlaying) return@LaunchedEffect
        val tickIntervalMs = 50L
        while (true) {
            delay(tickIntervalMs)
            val delta = (tickIntervalMs / 1000f) * playbackSpeed
            val next = currentSeconds + delta
            currentSeconds = if (next >= totalDuration) 0.0f else next
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = FitLensPalette.Card,
        border = BorderStroke(1.dp, FitLensPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Gemini Omni Video Walkthrough Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = walkthroughAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, walkthroughAccent.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = walkthroughAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = walkthroughBadge,
                                color = walkthroughAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // Camera Angle Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = FitLensPalette.ZincMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = activePhase.cameraPerspective,
                        color = FitLensPalette.Zinc,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Video Screen Stage: Omni Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(FitLensPalette.ElevatedAlt)
            ) {
                // Animated Motion Biomechanics Visual
                val progressInPhase = ((currentSeconds - activePhase.startSeconds) / activePhase.durationSeconds).coerceIn(0f, 1f)
                WalkthroughVideoScreen(
                    exerciseName = exerciseName,
                    phaseFraction = progressInPhase,
                    coverImage = walkthrough.coverImage,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Form Inspection Check Overlay
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = FitLensPalette.Emerald,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = activePhase.formInspectionCheck,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Breathing Pacer Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = FitLensPalette.Sky.copy(alpha = 0.25f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = activePhase.breathingDirective,
                        color = FitLensPalette.Sky,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Timecode Pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    val mins = (currentSeconds / 60).toInt()
                    val secs = (currentSeconds % 60).toInt()
                    val totalSecs = totalDuration.toInt()
                    Text(
                        text = String.format("%02d:%02d / 00:%02d", mins, secs, totalSecs),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Timeline Scrubber Slider
            Slider(
                value = currentSeconds,
                onValueChange = { currentSeconds = it },
                valueRange = 0f..totalDuration,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = FitLensPalette.Lime,
                    activeTrackColor = FitLensPalette.Lime,
                    inactiveTrackColor = FitLensPalette.BorderLight
                )
            )

            // Video Controls: Play/Pause, Step Prev/Next, Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prev Phase
                IconButton(
                    onClick = {
                        val prevIdx = (activePhaseIndex - 1).coerceAtLeast(0)
                        currentSeconds = phases[prevIdx].startSeconds
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Previous Phase",
                        tint = FitLensPalette.Zinc,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Play / Pause Button
                Button(
                    onClick = { isPlaying = !isPlaying },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = FitLensPalette.Lime),
                    modifier = Modifier.size(42.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Next Phase
                IconButton(
                    onClick = {
                        val nextIdx = (activePhaseIndex + 1).coerceAtMost(phases.lastIndex)
                        currentSeconds = phases[nextIdx].startSeconds
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Next Phase",
                        tint = FitLensPalette.Zinc,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Playback Speed Toggle (0.5x, 1.0x, 1.5x)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = FitLensPalette.ElevatedAlt,
                    border = BorderStroke(1.dp, FitLensPalette.BorderLight),
                    modifier = Modifier.clickable {
                        playbackSpeed = when (playbackSpeed) {
                            1.0f -> 1.5f
                            1.5f -> 0.5f
                            else -> 1.0f
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = FitLensPalette.Lime,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${playbackSpeed}x",
                            color = FitLensPalette.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Live Coaching Prompt & Narration Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = FitLensPalette.Elevated,
                border = BorderStroke(1.dp, FitLensPalette.Border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = FitLensPalette.Sky,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activePhase.title.uppercase(),
                            color = FitLensPalette.Sky,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = activePhase.coachingAudioPrompt,
                            color = FitLensPalette.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                    }
                    IconButton(
                        onClick = { onSpeakNarration?.invoke(activePhase.coachingAudioPrompt) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Speak Cue",
                            tint = FitLensPalette.Lime,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Screen rendering animated exercise biomechanics for the video walkthrough.
 */
@Composable
private fun WalkthroughVideoScreen(
    exerciseName: String,
    phaseFraction: Float,
    coverImage: Bitmap? = null,
    modifier: Modifier = Modifier
) {
    val lower = exerciseName.lowercase()
    val isDark = LocalFitLensColors.current.isDark

    val boneColor = FitLensPalette.BoneColor
    val muscleColor = FitLensPalette.Lime
    val jointColor = FitLensPalette.Emerald
    val floorColor = FitLensPalette.FloorLineColor
    val headColor = FitLensPalette.White

    Box(modifier = modifier) {
        coverImage?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Gemini Omni workout cover for $exerciseName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isDark) 0.38f else 0.28f))
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val floorY = h * 0.88f

        // Grid floor lines for depth
        drawLine(
            color = floorColor,
            start = Offset(w * 0.08f, floorY),
            end = Offset(w * 0.92f, floorY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        fun bone(a: Offset, b: Offset, highlight: Boolean = false) {
            drawLine(
                color = if (highlight) muscleColor else boneColor,
                start = a,
                end = b,
                strokeWidth = if (highlight) 10f else 8f,
                cap = StrokeCap.Round
            )
        }

        fun joint(p: Offset, radius: Float = 6.5f, highlight: Boolean = false) {
            drawCircle(
                color = if (highlight) muscleColor else jointColor,
                radius = radius,
                center = p
            )
        }

        when {
            lower.contains("squat") -> {
                val squatDepth = phaseFraction * (h * 0.22f)
                val ankle = Offset(cx + 8f, floorY - 6f)
                val knee = Offset(cx + 24f + phaseFraction * 14f, floorY - h * 0.26f + squatDepth * 0.35f)
                val hip = Offset(cx - 16f - phaseFraction * 18f, h * 0.48f + squatDepth)
                val shoulder = Offset(cx + 4f, h * 0.24f + squatDepth * 0.85f)
                val head = Offset(shoulder.x + 4f, shoulder.y - 18f)
                val wrist = Offset(shoulder.x + 36f, shoulder.y + 6f)

                bone(ankle, knee, highlight = true)
                bone(knee, hip, highlight = true)
                bone(hip, shoulder, highlight = true)
                bone(shoulder, wrist)
                drawCircle(color = headColor, radius = 12f, center = head)
                joint(ankle); joint(knee, 8f, true); joint(hip, 8f, true); joint(shoulder)
            }

            lower.contains("push") || lower.contains("press") -> {
                val drop = phaseFraction * 34f
                val ankle = Offset(w * 0.22f, floorY - 10f)
                val hip = Offset(w * 0.48f, floorY - 50f + drop * 0.5f)
                val shoulder = Offset(w * 0.72f, floorY - 80f + drop)
                val elbow = Offset(w * 0.65f, floorY - 42f + drop * 0.5f)
                val wrist = Offset(w * 0.73f, floorY - 10f)
                val head = Offset(w * 0.80f, shoulder.y - 6f)

                bone(ankle, hip)
                bone(hip, shoulder, highlight = true)
                bone(shoulder, elbow, highlight = true)
                bone(elbow, wrist, highlight = true)
                drawCircle(color = headColor, radius = 12f, center = head)
                joint(ankle); joint(hip, 7f, true); joint(shoulder, 8f, true); joint(elbow, 8f, true); joint(wrist)
            }

            lower.contains("plank") -> {
                val ankle = Offset(w * 0.24f, floorY - 10f)
                val hip = Offset(w * 0.50f, floorY - 46f)
                val shoulder = Offset(w * 0.73f, floorY - 68f)
                val elbow = Offset(w * 0.73f, floorY - 10f)
                val wrist = Offset(w * 0.80f, floorY - 10f)
                val head = Offset(w * 0.80f, floorY - 76f)

                bone(ankle, hip, highlight = true)
                bone(hip, shoulder, highlight = true)
                bone(shoulder, elbow)
                bone(elbow, wrist)
                drawCircle(color = headColor, radius = 12f, center = head)
                joint(ankle); joint(hip, 8f, true); joint(shoulder, 8f, true); joint(elbow)
            }

            lower.contains("lunge") -> {
                val stepForward = phaseFraction * (w * 0.07f)
                val head = Offset(w * 0.62f + stepForward * 0.3f, h * 0.16f)
                val shoulder = Offset(w * 0.60f + stepForward * 0.3f, h * 0.32f)
                val hip = Offset(w * 0.55f + stepForward * 0.4f, h * 0.52f)
                val frontKnee = Offset(w * 0.68f + stepForward, floorY - h * 0.26f)
                val frontAnkle = Offset(w * 0.72f + stepForward, floorY - 8f)
                val backKnee = Offset(w * 0.40f - stepForward * 0.3f, floorY - h * 0.22f)
                val backAnkle = Offset(w * 0.30f - stepForward * 0.4f, floorY - 8f)

                bone(backAnkle, backKnee)
                bone(backKnee, hip)
                bone(hip, frontKnee, highlight = true)
                bone(frontKnee, frontAnkle, highlight = true)
                bone(hip, shoulder, highlight = true)
                drawCircle(color = headColor, radius = 12f, center = head)
                joint(hip, 8f, true); joint(frontKnee, 8f, true); joint(shoulder, 7f)
            }

            else -> {
                val spread = phaseFraction * (w * 0.12f)
                val head = Offset(cx, h * 0.20f)
                val shoulder = Offset(cx, h * 0.34f)
                val hip = Offset(cx, h * 0.56f)
                val lAnkle = Offset(cx - 14f - spread, floorY - 8f)
                val rAnkle = Offset(cx + 14f + spread, floorY - 8f)
                val lWrist = Offset(cx - 28f - spread * 0.6f, h * (0.50f - phaseFraction * 0.30f))
                val rWrist = Offset(cx + 28f + spread * 0.6f, h * (0.50f - phaseFraction * 0.30f))

                bone(hip, lAnkle, highlight = true)
                bone(hip, rAnkle, highlight = true)
                bone(hip, shoulder, highlight = true)
                bone(shoulder, lWrist)
                bone(shoulder, rWrist)
                drawCircle(color = headColor, radius = 12f, center = head)
                joint(hip, 8f, true); joint(shoulder, 8f, true); joint(lAnkle); joint(rAnkle)
            }
        }
        }
    }
}
