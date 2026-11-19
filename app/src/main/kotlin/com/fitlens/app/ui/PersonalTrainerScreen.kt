package com.fitlens.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.fitlens.app.ai.WorkoutPlanResult
import com.fitlens.app.ai.live.AssistantMessage
import com.fitlens.app.ai.live.LiveAssistantState
import com.fitlens.app.ai.live.LiveConnectionState
import com.fitlens.app.ai.live.MoodSpeculation
import com.fitlens.app.ai.live.MoodSpeculator
import com.fitlens.app.camera.CameraPreviewView
import com.fitlens.app.camera.DualStreamAnalyzer
import com.fitlens.app.data.model.WorkoutSessionEntity
import com.fitlens.app.data.profile.UserProfile
import com.fitlens.app.vitals.VitalsReading
import com.fitlens.app.workout.WorkoutPhase
import com.fitlens.app.workout.WorkoutState
import com.presagetech.smartspectra.ProcessingStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Cohesive Modern Obsidian + Athletic Lime / Emerald Palette (FitLensPalette)
private val BG_OBSIDIAN = FitLensPalette.Obsidian
private val SURFACE_CARD = FitLensPalette.Card
private val SURFACE_ELEVATED = FitLensPalette.Elevated
private val BORDER_ZINC = FitLensPalette.Border
private val ACCENT_LIME = FitLensPalette.Lime
private val ACCENT_EMERALD = FitLensPalette.Emerald
private val ACCENT_ROSE = FitLensPalette.Rose
private val ACCENT_SKY = FitLensPalette.Sky
private val ACCENT_AMBER = FitLensPalette.Amber
private val TEXT_PRIMARY = FitLensPalette.White
private val TEXT_SECONDARY = FitLensPalette.Zinc
private val TEXT_MUTED = FitLensPalette.ZincMuted

private enum class FitLensTab(val label: String) {
    TRAIN("Train"),
    VITALS("Vitals"),
    COACH("Coach")
}

@Composable
fun PersonalTrainerScreen(
    vitals: VitalsReading,
    profile: UserProfile,
    assistantState: LiveAssistantState,
    moodSpeculation: MoodSpeculation,
    aiCoachingPrompt: String?,
    processingStatus: ProcessingStatus,
    validationHint: String?,
    sdkErrorMessage: String?,
    isPlaceholderPresageKey: Boolean,
    hasCameraPermission: Boolean,
    useFrontCamera: Boolean,
    dualStreamAnalyzer: DualStreamAnalyzer,
    workoutState: WorkoutState,
    generatedPlan: WorkoutPlanResult,
    isGeneratingPlan: Boolean,
    recentSessions: List<WorkoutSessionEntity>,
    onSelectExercise: (String) -> Unit,
    onGenerateWorkoutPlan: (String, String, String, Int) -> Unit,
    onStartGeneratedRoutine: (WorkoutPlanResult) -> Unit,
    onIncrementRep: () -> Unit,
    onCompleteSet: () -> Unit,
    onSkipPhase: () -> Unit,
    onSimulateExertion: (Float) -> Unit,
    onStopWorkout: () -> Unit,
    onToggleCameraFacing: () -> Unit,
    onStartOrStopSensing: () -> Unit,
    onReviewConsent: () -> Unit,
    onGrantCamera: () -> Unit,
    onToggleLiveVoice: () -> Unit,
    onSendMessage: (String) -> Unit,
    onMoodCheckIn: () -> Unit,
    onClearChat: () -> Unit,
    onClearError: (String) -> Unit,
    onOpenProfile: () -> Unit
) {
    var activeTab by remember { mutableStateOf(FitLensTab.TRAIN) }
    var cameraDisplayMode by remember { mutableStateOf(CameraFeedDisplayMode.HIDDEN) }
    var showCustomizePlan by remember { mutableStateOf(false) }

    val canUseCamera = profile.hasAcceptedSensingConsent && hasCameraPermission
    val isSensingRunning = processingStatus == ProcessingStatus.RUNNING ||
        processingStatus == ProcessingStatus.STARTING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BG_OBSIDIAN, Color(0xFF0D0D12), BG_OBSIDIAN)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Persistent single CameraPreviewView instance: never leaves composition when
        // switching tabs or display modes, preventing CameraX unbind/TIMESTAMP_GAP interruptions.
        if (canUseCamera) {
            val cameraContainerModifier = when (cameraDisplayMode) {
                CameraFeedDisplayMode.HIDDEN -> Modifier
                    .align(Alignment.BottomEnd)
                    .size(1.dp)
                    .alpha(0.01f)
                CameraFeedDisplayMode.PIP_RIGHT -> Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 16.dp)
                    .size(width = 104.dp, height = 144.dp)
                    .zIndex(10f)
                CameraFeedDisplayMode.EXPANDED -> Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .height(240.dp)
                    .zIndex(10f)
            }

            Surface(
                modifier = cameraContainerModifier,
                shape = RoundedCornerShape(16.dp),
                color = SURFACE_CARD,
                border = if (cameraDisplayMode == CameraFeedDisplayMode.HIDDEN) {
                    null
                } else {
                    BorderStroke(1.dp, BORDER_ZINC)
                },
                shadowElevation = if (cameraDisplayMode == CameraFeedDisplayMode.HIDDEN) 0.dp else 10.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CameraPreviewView(
                        analyzer = dualStreamAnalyzer,
                        useFrontCamera = useFrontCamera,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                    )
                    if (cameraDisplayMode != CameraFeedDisplayMode.HIDDEN) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SmallCameraActionIcon(
                                icon = Icons.Default.Cameraswitch,
                                onClick = onToggleCameraFacing
                            )
                            SmallCameraActionIcon(
                                icon = if (cameraDisplayMode == CameraFeedDisplayMode.EXPANDED) {
                                    Icons.Default.FullscreenExit
                                } else {
                                    Icons.Default.Fullscreen
                                },
                                onClick = {
                                    cameraDisplayMode = if (cameraDisplayMode == CameraFeedDisplayMode.EXPANDED) {
                                        CameraFeedDisplayMode.PIP_RIGHT
                                    } else {
                                        CameraFeedDisplayMode.EXPANDED
                                    }
                                }
                            )
                            SmallCameraActionIcon(
                                icon = Icons.Default.VisibilityOff,
                                onClick = { cameraDisplayMode = CameraFeedDisplayMode.HIDDEN }
                            )
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Minimal Top Header
            MinimalTopBar(
                vitals = vitals,
                isSensingRunning = isSensingRunning,
                canUseCamera = canUseCamera,
                cameraDisplayMode = cameraDisplayMode,
                onToggleCameraView = {
                    cameraDisplayMode = when (cameraDisplayMode) {
                        CameraFeedDisplayMode.HIDDEN -> CameraFeedDisplayMode.PIP_RIGHT
                        CameraFeedDisplayMode.PIP_RIGHT -> CameraFeedDisplayMode.EXPANDED
                        CameraFeedDisplayMode.EXPANDED -> CameraFeedDisplayMode.HIDDEN
                    }
                },
                onStartOrStopSensing = onStartOrStopSensing,
                onOpenProfile = onOpenProfile
            )

            if (cameraDisplayMode == CameraFeedDisplayMode.EXPANDED && canUseCamera) {
                Spacer(modifier = Modifier.height(248.dp))
            }

            // Consent / Permission Banner (only shown if not yet granted)
            if (!canUseCamera) {
                PermissionPromptCard(
                    hasConsent = profile.hasAcceptedSensingConsent,
                    onReviewConsent = onReviewConsent,
                    onGrantCamera = onGrantCamera,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Main Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(tween(280)) { it / 12 * direction } + fadeIn(tween(220)))
                            .togetherWith(
                                slideOutHorizontally(tween(280)) { -it / 12 * direction } + fadeOut(tween(180))
                            )
                    },
                    label = "tabTransition"
                ) { tab ->
                when (tab) {
                    FitLensTab.TRAIN -> TrainTabContent(
                        workoutState = workoutState,
                        generatedPlan = generatedPlan,
                        aiCoachingPrompt = aiCoachingPrompt,
                        isGeneratingPlan = isGeneratingPlan,
                        showCustomizePlan = showCustomizePlan,
                        recentSessions = recentSessions,
                        onToggleCustomizePlan = { showCustomizePlan = !showCustomizePlan },
                        onSelectExercise = onSelectExercise,
                        onGenerateWorkoutPlan = onGenerateWorkoutPlan,
                        onStartGeneratedRoutine = onStartGeneratedRoutine,
                        onIncrementRep = onIncrementRep,
                        onCompleteSet = onCompleteSet,
                        onSkipPhase = onSkipPhase,
                        onStopWorkout = onStopWorkout
                    )

                    FitLensTab.VITALS -> VitalsTabContent(
                        vitals = vitals,
                        moodSpeculation = moodSpeculation,
                        isSensingRunning = isSensingRunning,
                        validationHint = validationHint,
                        recentSessions = recentSessions,
                        onStartOrStopSensing = onStartOrStopSensing,
                        onSimulateExertion = onSimulateExertion
                    )

                    FitLensTab.COACH -> CoachTabContent(
                        vitals = vitals,
                        moodSpeculation = moodSpeculation,
                        assistantState = assistantState,
                        onToggleLiveVoice = onToggleLiveVoice,
                        onSendMessage = onSendMessage,
                        onMoodCheckIn = onMoodCheckIn,
                        onClearChat = onClearChat,
                        onClearError = onClearError
                    )
                }
                }
            }

            // Bottom Compact Vitals Strip + Navigation Bar
            BottomVitalsAndNavBar(
                vitals = vitals,
                isSensingRunning = isSensingRunning,
                activeTab = activeTab,
                onSelectTab = { activeTab = it },
                onStartOrStopSensing = onStartOrStopSensing
            )
        }
    }
}

@Composable
private fun SmallCameraActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.6f),
        modifier = Modifier
            .size(26.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TEXT_PRIMARY,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun MinimalTopBar(
    vitals: VitalsReading,
    isSensingRunning: Boolean,
    canUseCamera: Boolean,
    cameraDisplayMode: CameraFeedDisplayMode,
    onToggleCameraView: () -> Unit,
    onStartOrStopSensing: () -> Unit,
    onOpenProfile: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = ACCENT_LIME,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "FitLens",
                color = TEXT_PRIMARY,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )

            // Live Sensing Pill
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSensingRunning) ACCENT_EMERALD.copy(alpha = 0.14f) else SURFACE_ELEVATED,
                border = BorderStroke(
                    1.dp,
                    if (isSensingRunning) ACCENT_EMERALD.copy(alpha = 0.4f) else BORDER_ZINC
                ),
                modifier = Modifier.clickable { onStartOrStopSensing() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SensingStatusDot(isSensingRunning = isSensingRunning)
                    Text(
                        text = if (isSensingRunning) {
                            if (vitals.heartRateBpm > 0) "${vitals.heartRateBpm.roundToInt()} BPM" else "Calibrating"
                        } else {
                            "Sense Off"
                        },
                        color = if (isSensingRunning) ACCENT_EMERALD else TEXT_SECONDARY,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (canUseCamera) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SURFACE_ELEVATED,
                    border = BorderStroke(1.dp, BORDER_ZINC),
                    modifier = Modifier.clickable { onToggleCameraView() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (cameraDisplayMode == CameraFeedDisplayMode.HIDDEN) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = "Camera View",
                            tint = if (cameraDisplayMode == CameraFeedDisplayMode.HIDDEN) TEXT_SECONDARY else ACCENT_LIME,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = cameraDisplayMode.label,
                            color = TEXT_PRIMARY,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            IconButton(
                onClick = onOpenProfile,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(SURFACE_ELEVATED)
            ) {
                Icon(
                    imageVector = Icons.Default.ManageAccounts,
                    contentDescription = "Profile",
                    tint = TEXT_PRIMARY,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Pulsing status dot: breathes gently while sensing is live. */
@Composable
private fun SensingStatusDot(isSensingRunning: Boolean) {
    val transition = rememberInfiniteTransition(label = "sensingDot")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sensingDotAlpha"
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(
                if (isSensingRunning) ACCENT_EMERALD.copy(alpha = dotAlpha)
                else TEXT_MUTED.copy(alpha = 0.6f)
            )
    )
}

@Composable
private fun PermissionPromptCard(
    hasConsent: Boolean,
    onReviewConsent: () -> Unit,
    onGrantCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SURFACE_CARD,
        border = BorderStroke(1.dp, BORDER_ZINC)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (!hasConsent) "Enable Vitals & Pose Sensing" else "Camera Permission Needed",
                    color = TEXT_PRIMARY,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Contactless heart rate, breathing & rep counting.",
                    color = TEXT_SECONDARY,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = if (!hasConsent) onReviewConsent else onGrantCamera,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ACCENT_LIME)
            ) {
                Text(
                    text = if (!hasConsent) "Enable" else "Grant",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ============================================================================
// TAB 1: TRAIN (Workouts, Active Player, Quick Exercises)
// ============================================================================

@Composable
private fun TrainTabContent(
    workoutState: WorkoutState,
    generatedPlan: WorkoutPlanResult,
    aiCoachingPrompt: String?,
    isGeneratingPlan: Boolean,
    showCustomizePlan: Boolean,
    recentSessions: List<WorkoutSessionEntity>,
    onToggleCustomizePlan: () -> Unit,
    onSelectExercise: (String) -> Unit,
    onGenerateWorkoutPlan: (String, String, String, Int) -> Unit,
    onStartGeneratedRoutine: (WorkoutPlanResult) -> Unit,
    onIncrementRep: () -> Unit,
    onCompleteSet: () -> Unit,
    onSkipPhase: () -> Unit,
    onStopWorkout: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isWorkoutActive = workoutState.phase != WorkoutPhase.IDLE &&
        workoutState.phase != WorkoutPhase.COMPLETED
    val isWorkoutCompleted = workoutState.phase == WorkoutPhase.COMPLETED

    var selectedGoal by remember { mutableStateOf("Six-Pack Abs") }
    var selectedLevel by remember { mutableStateOf("Intermediate") }
    var selectedEquipment by remember { mutableStateOf("Bodyweight") }
    var selectedDuration by remember { mutableStateOf(15) }
    var detailExercise by remember { mutableStateOf<String?>(null) }
    var showPlanInfo by remember { mutableStateOf(false) }
    val warmUpItems = listOf(
        "Arm Circles" to "x10",
        "Leg Swings" to "x10",
        "Jumping Jacks" to "00:30"
    )

    if (isWorkoutCompleted) {
        WorkoutSummaryScreen(
            workoutState = workoutState,
            recentSessions = recentSessions,
            onBackToHome = onStopWorkout
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isWorkoutActive) {
                ActiveWorkoutPlayerSection(
                    workoutState = workoutState,
                    aiCoachingPrompt = aiCoachingPrompt,
                    onIncrementRep = onIncrementRep,
                    onCompleteSet = onCompleteSet,
                    onSkipPhase = onSkipPhase,
                    onStopWorkout = onStopWorkout
                )
            } else {
            // Workout detail page (program header, stats, settings, warm-up, exercises)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Program header: DAY eyebrow, title, FAQ pill
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DAY 1",
                            color = ACCENT_LIME,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = SURFACE_ELEVATED,
                            border = BorderStroke(1.dp, BORDER_ZINC),
                            modifier = Modifier.clickable { showPlanInfo = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = "Workout info",
                                    tint = TEXT_SECONDARY,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "FAQ",
                                    color = TEXT_SECONDARY,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Text(
                        text = selectedGoal.uppercase(),
                        color = TEXT_PRIMARY,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${selectedLevel} • ${selectedDuration} min • Bodyweight",
                        color = TEXT_SECONDARY,
                        fontSize = 13.sp
                    )
                }

                // 2. Meta row: program badge + focus areas
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ACCENT_LIME.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, ACCENT_LIME.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = programBadgeFor(selectedGoal),
                                color = ACCENT_LIME,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = "Focus Areas",
                            color = TEXT_SECONDARY,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        focusAreasFor(selectedGoal).forEach { area ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = SURFACE_ELEVATED,
                                border = BorderStroke(1.dp, BORDER_ZINC)
                            ) {
                                Text(
                                    text = area,
                                    color = TEXT_SECONDARY,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Stats card: calories, duration, level
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SURFACE_CARD,
                    border = BorderStroke(1.dp, BORDER_ZINC)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        WorkoutStatRow(
                            icon = Icons.Default.LocalFireDepartment,
                            iconTint = ACCENT_ROSE,
                            value = String.format(Locale.US, "%.1f kcal", selectedDuration * 8.5)
                        )
                        WorkoutStatRow(
                            icon = Icons.Default.AccessTime,
                            iconTint = ACCENT_SKY,
                            value = "${selectedDuration} min"
                        )
                        WorkoutStatRow(
                            icon = Icons.Default.EmojiEvents,
                            iconTint = ACCENT_AMBER,
                            value = selectedLevel
                        )
                    }
                }

                // 4. Workout settings row + collapsible customizer
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SURFACE_CARD,
                    border = BorderStroke(1.dp, BORDER_ZINC),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleCustomizePlan() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = TEXT_PRIMARY,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Workout settings",
                                color = TEXT_PRIMARY,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = if (showCustomizePlan) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = null,
                            tint = TEXT_MUTED,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = showCustomizePlan) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SelectorRow(
                            options = listOf("Six-Pack Abs", "Strength", "Chest & Arms", "Legs & Glutes", "HIIT Cardio"),
                            selected = selectedGoal,
                            onSelect = { selectedGoal = it }
                        )
                        SelectorRow(
                            options = listOf("Beginner", "Intermediate", "Advanced"),
                            selected = selectedLevel,
                            onSelect = { selectedLevel = it }
                        )
                        SelectorRow(
                            options = listOf("10m", "15m", "20m", "30m"),
                            selected = "${selectedDuration}m",
                            onSelect = { selectedDuration = it.removeSuffix("m").toIntOrNull() ?: 15 }
                        )
                        Button(
                            onClick = {
                                onGenerateWorkoutPlan(
                                    selectedGoal,
                                    selectedLevel,
                                    selectedEquipment,
                                    selectedDuration
                                )
                                onToggleCustomizePlan()
                            },
                            enabled = !isGeneratingPlan,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SURFACE_ELEVATED)
                        ) {
                            Text(
                                text = if (isGeneratingPlan) "Generating..." else "Update Routine",
                                color = ACCENT_LIME,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 5. Warm-up section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Warm-up",
                        color = TEXT_PRIMARY,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SURFACE_CARD,
                        border = BorderStroke(1.dp, BORDER_ZINC)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            warmUpItems.forEach { (name, dose) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = name,
                                        color = TEXT_PRIMARY,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = dose,
                                        color = TEXT_SECONDARY,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Exercises list
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Exercises (${generatedPlan.targetExercises.size})",
                            color = TEXT_PRIMARY,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Edit>",
                            color = ACCENT_LIME,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onToggleCustomizePlan() }
                        )
                    }

                    generatedPlan.targetExercises.forEach { exercise ->
                        val isHold = exercise.contains("Plank", ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SURFACE_CARD,
                            border = BorderStroke(1.dp, BORDER_ZINC),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectExercise(exercise) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AnimatedExerciseIllustration(
                                    exerciseName = exercise,
                                    modifier = Modifier
                                        .size(width = 104.dp, height = 76.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = exercise,
                                        color = TEXT_PRIMARY,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isHold) "00:30" else "x12",
                                        color = TEXT_SECONDARY,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Exercise details",
                                    tint = TEXT_MUTED,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { detailExercise = exercise }
                                )
                            }
                        }
                    }
                }

                // 7. Start button
                Button(
                    onClick = { onStartGeneratedRoutine(generatedPlan) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ACCENT_LIME)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Start",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
            }
        }
    }

    if (showPlanInfo) {
        AlertDialog(
            onDismissRequest = { showPlanInfo = false },
            containerColor = SURFACE_CARD,
            title = {
                Text(
                    text = "Workout info",
                    color = TEXT_PRIMARY,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = generatedPlan.aiCoachRationale.ifBlank {
                        "${generatedPlan.goal} • ${generatedPlan.targetDurationMinutes} min • ${generatedPlan.recommendedRestIntervalSeconds}s rest between sets."
                    },
                    color = TEXT_SECONDARY,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showPlanInfo = false }) {
                    Text("OK", color = ACCENT_LIME, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    detailExercise?.let { name ->
        ExerciseDetailSheet(exerciseName = name, onDismiss = { detailExercise = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDetailSheet(
    exerciseName: String,
    onDismiss: () -> Unit
) {
    val meta = getExerciseCoachingMeta(exerciseName)
    val isHold = exerciseName.contains("Plank", ignoreCase = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SURFACE_CARD
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExerciseGlyphBadge(exerciseName = exerciseName, modifier = Modifier.size(48.dp))
                Column {
                    Text(
                        text = "EXERCISE",
                        color = TEXT_MUTED,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = exerciseName,
                        color = TEXT_PRIMARY,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SURFACE_ELEVATED
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("TARGET", color = TEXT_MUTED, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(meta.targetMuscles, color = TEXT_PRIMARY, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("TEMPO", color = TEXT_MUTED, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(meta.tempoCue, color = ACCENT_LIME, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("SETS × REPS", color = TEXT_MUTED, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(if (isHold) "3 × 30s" else "3 × 12", color = TEXT_PRIMARY, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Text(
                text = "FORM CHECKLIST",
                color = TEXT_MUTED,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            meta.formChecklist.forEach { cue ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = ACCENT_EMERALD,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(cue, color = TEXT_SECONDARY, fontSize = 13.sp)
                }
            }
        }
    }
}


@Composable
private fun programBadgeFor(goal: String): String = when {
    goal.contains("abs", ignoreCase = true) || goal.contains("core", ignoreCase = true) -> "CORE"
    goal.contains("chest", ignoreCase = true) || goal.contains("arm", ignoreCase = true) -> "UPPER"
    goal.contains("leg", ignoreCase = true) || goal.contains("glute", ignoreCase = true) -> "LEGS"
    goal.contains("hiit", ignoreCase = true) || goal.contains("cardio", ignoreCase = true) -> "HIIT"
    else -> "FULL"
}

private fun focusAreasFor(goal: String): List<String> = when {
    goal.contains("abs", ignoreCase = true) || goal.contains("core", ignoreCase = true) ||
        goal.contains("six", ignoreCase = true) -> listOf("Abs", "Obliques", "Core")
    goal.contains("chest", ignoreCase = true) || goal.contains("arm", ignoreCase = true) ||
        goal.contains("upper", ignoreCase = true) -> listOf("Chest", "Shoulders", "Arms")
    goal.contains("leg", ignoreCase = true) || goal.contains("glute", ignoreCase = true) ||
        goal.contains("lower", ignoreCase = true) -> listOf("Quads", "Glutes", "Hamstrings")
    goal.contains("hiit", ignoreCase = true) || goal.contains("cardio", ignoreCase = true) ->
        listOf("Full Body", "Cardio")
    else -> listOf("Full Body", "Core")
}

@Composable
private fun WorkoutStatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = iconTint.copy(alpha = 0.14f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = value,
            color = TEXT_PRIMARY,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun SelectorRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            val isSelected = option.equals(selected, ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) ACCENT_LIME.copy(alpha = 0.16f) else SURFACE_ELEVATED,
                border = BorderStroke(1.dp, if (isSelected) ACCENT_LIME else BORDER_ZINC),
                modifier = Modifier.clickable { onSelect(option) }
            ) {
                Text(
                    text = option,
                    color = if (isSelected) ACCENT_LIME else TEXT_SECONDARY,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ============================================================================
// TAB 2: VITALS (SmartSpectra Live Biometrics, Waveforms, History)
// ============================================================================

@Composable
private fun VitalsTabContent(
    vitals: VitalsReading,
    moodSpeculation: MoodSpeculation,
    isSensingRunning: Boolean,
    validationHint: String?,
    recentSessions: List<WorkoutSessionEntity>,
    onStartOrStopSensing: () -> Unit,
    onSimulateExertion: (Float) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Sensing Control Header Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SURFACE_CARD,
            border = BorderStroke(1.dp, BORDER_ZINC)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSensingRunning) "SMARTSPECTRA ACTIVE" else "SMARTSPECTRA PAUSED",
                        color = if (isSensingRunning) ACCENT_EMERALD else TEXT_MUTED,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = validationHint?.takeIf { it.isNotBlank() }
                            ?: if (isSensingRunning) "Optical pulse & breathing lock" else "Tap Start to measure vitals",
                        color = TEXT_PRIMARY,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = onStartOrStopSensing,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSensingRunning) SURFACE_ELEVATED else ACCENT_LIME
                    )
                ) {
                    Text(
                        text = if (isSensingRunning) "Stop" else "Start",
                        color = if (isSensingRunning) TEXT_PRIMARY else Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 2. 2x2 Big Scannable Biometrics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VitalsMetricCard(
                label = "HEART RATE",
                value = if (vitals.heartRateBpm > 0) "${vitals.heartRateBpm.roundToInt()}" else "--",
                unit = "BPM",
                sublabel = vitals.heartRateZone.label,
                accentColor = ACCENT_ROSE,
                modifier = Modifier.weight(1f)
            )
            VitalsMetricCard(
                label = "BREATHING",
                value = if (vitals.breathingRateBpm > 0) "${vitals.breathingRateBpm.roundToInt()}" else "--",
                unit = "BRPM",
                sublabel = "Respiration",
                accentColor = ACCENT_SKY,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VitalsMetricCard(
                label = "HRV (RMSSD)",
                value = if (vitals.heartRateVariabilityMs > 0) "${vitals.heartRateVariabilityMs.roundToInt()}" else "--",
                unit = "ms",
                sublabel = "Recovery",
                accentColor = ACCENT_EMERALD,
                modifier = Modifier.weight(1f)
            )
            VitalsMetricCard(
                label = "READINESS",
                value = moodSpeculation.emoji,
                unit = moodSpeculation.label,
                sublabel = vitals.facialExpression ?: "Balanced",
                accentColor = ACCENT_LIME,
                modifier = Modifier.weight(1f)
            )
        }

        // 3. Live Waveform Trace Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SURFACE_CARD,
            border = BorderStroke(1.dp, BORDER_ZINC)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PPG & Respiratory Trace",
                        color = TEXT_PRIMARY,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(vitals.signalConfidence * 100).roundToInt()}% lock",
                        color = ACCENT_EMERALD,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                WaveformTraceCanvas(
                    pulseTrace = vitals.arterialPressureTrace,
                    breathTrace = vitals.chestBreathingTrace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                )

                // Quick Exertion Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Rest" to 0.15f,
                        "Warmup" to 0.35f,
                        "Cardio" to 0.65f,
                        "Peak" to 0.90f
                    ).forEach { (label, factor) ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSimulateExertion(factor) },
                            shape = RoundedCornerShape(10.dp),
                            color = SURFACE_ELEVATED,
                            border = BorderStroke(1.dp, BORDER_ZINC)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = TEXT_SECONDARY,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Weekly Activity + Recent Workouts
        if (recentSessions.isNotEmpty()) {
            WeeklyActivityCard(sessions = recentSessions)
        }
        if (recentSessions.isNotEmpty()) {
            val fmt = remember { SimpleDateFormat("MMM d • HH:mm", Locale.getDefault()) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Recent Sessions",
                    color = TEXT_PRIMARY,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                recentSessions.take(4).forEach { session ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SURFACE_CARD,
                        border = BorderStroke(1.dp, BORDER_ZINC)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExerciseGlyphBadge(
                                exerciseName = session.exerciseName,
                                modifier = Modifier.size(32.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.exerciseName,
                                    color = TEXT_PRIMARY,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = fmt.format(Date(session.timestamp)),
                                    color = TEXT_MUTED,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = "${session.completedReps} reps • ${session.averageHeartRate.roundToInt()} BPM",
                                color = ACCENT_LIME,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 7-day training activity bar chart built from local session history. */
@Composable
private fun WeeklyActivityCard(sessions: List<WorkoutSessionEntity>) {
    val dayMillis = 24L * 60 * 60 * 1000
    val (counts, repTotals, dayLetters) = remember(sessions) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        val counts = IntArray(7)
        val reps = IntArray(7)
        sessions.forEach { s ->
            val dayIndex = ((todayStart - s.timestamp) / dayMillis).toInt()
            val bucket = 6 - dayIndex
            if (bucket in 0..6 && dayIndex >= 0) {
                counts[bucket]++
                reps[bucket] += s.completedReps
            }
        }
        val letters = CharArray(7) { 'M' }
        // Labels: oldest -> today
        val sdf = SimpleDateFormat("EEEEE", Locale.getDefault())
        for (i in 0 until 7) {
            cal.timeInMillis = todayStart - (6 - i) * dayMillis
            letters[i] = sdf.format(cal.time).first().uppercaseChar()
        }
        Triple(counts, reps, letters)
    }
    val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    val weekReps = repTotals.sum()
    val weekSessions = counts.sum()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SURFACE_CARD,
        border = BorderStroke(1.dp, BORDER_ZINC)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "THIS WEEK",
                    color = ACCENT_LIME,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "$weekSessions session${if (weekSessions == 1) "" else "s"} • $weekReps reps",
                    color = TEXT_SECONDARY,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                counts.forEachIndexed { i, count ->
                    val isToday = i == 6
                    val fraction = count.toFloat() / maxCount
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(6.dp + 52.dp * fraction)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    when {
                                        count == 0 -> SURFACE_ELEVATED
                                        isToday -> ACCENT_LIME
                                        else -> ACCENT_LIME.copy(alpha = 0.45f)
                                    }
                                )
                        )
                        Text(
                            text = dayLetters[i].toString(),
                            color = if (isToday) ACCENT_LIME else TEXT_MUTED,
                            fontSize = 10.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VitalsMetricCard(
    label: String,
    value: String,
    unit: String,
    sublabel: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = SURFACE_CARD,
        border = BorderStroke(1.dp, BORDER_ZINC)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                color = TEXT_MUTED,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = value,
                    color = TEXT_PRIMARY,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = unit,
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Text(
                text = sublabel,
                color = TEXT_SECONDARY,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WaveformTraceCanvas(
    pulseTrace: List<Float>,
    breathTrace: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SURFACE_ELEVATED)
            .padding(8.dp)
    ) {
        val w = size.width
        val h = size.height
        drawLine(
            color = Color.White.copy(alpha = 0.06f),
            start = androidx.compose.ui.geometry.Offset(0f, h * 0.5f),
            end = androidx.compose.ui.geometry.Offset(w, h * 0.5f),
            strokeWidth = 1f
        )

        fun drawSignal(samples: List<Float>, color: Color) {
            if (samples.size < 2) return
            val min = samples.minOrNull() ?: 0f
            val max = samples.maxOrNull() ?: 1f
            val span = (max - min).takeIf { it > 1e-4f } ?: 1f
            val path = Path()
            samples.forEachIndexed { idx, sample ->
                val x = (idx.toFloat() / (samples.size - 1)) * w
                val norm = (sample - min) / span
                val y = h - (norm * (h * 0.8f) + h * 0.1f)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        drawSignal(breathTrace.takeLast(64), ACCENT_SKY.copy(alpha = 0.6f))
        drawSignal(pulseTrace.takeLast(64), ACCENT_ROSE)
    }
}

// ============================================================================
// TAB 3: COACH (AI Personal Trainer & Voice Assistant)
// ============================================================================

@Composable
private fun CoachTabContent(
    vitals: VitalsReading,
    moodSpeculation: MoodSpeculation,
    assistantState: LiveAssistantState,
    onToggleLiveVoice: () -> Unit,
    onSendMessage: (String) -> Unit,
    onMoodCheckIn: () -> Unit,
    onClearChat: () -> Unit,
    onClearError: (String) -> Unit
) {
    var draftText by remember { mutableStateOf("") }
    val isVoiceActive = assistantState.connectionState == LiveConnectionState.LISTENING ||
        assistantState.connectionState == LiveConnectionState.CONNECTING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Readiness & Live Voice Header
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SURFACE_CARD,
            border = BorderStroke(1.dp, BORDER_ZINC)
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
                            text = "${moodSpeculation.emoji} ${moodSpeculation.label}",
                            color = TEXT_PRIMARY,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = vitals.smartSpectraLlmInsight?.takeIf { it.isNotBlank() }
                                ?: moodSpeculation.summary,
                            color = TEXT_SECONDARY,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onToggleLiveVoice,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVoiceActive) ACCENT_ROSE else ACCENT_LIME
                        )
                    ) {
                        Icon(
                            imageVector = if (isVoiceActive) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isVoiceActive) TEXT_PRIMARY else Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isVoiceActive) "End Voice" else "Live Voice",
                            color = if (isVoiceActive) TEXT_PRIMARY else Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Quick Prompt Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickCoachChip("Readiness Check", onClick = onMoodCheckIn)
                    QuickCoachChip("Modify Intensity") {
                        onSendMessage("How should I adjust my workout intensity based on my current heart rate and HRV?")
                    }
                    QuickCoachChip("Recovery Breathing") {
                        onSendMessage("Give me a quick 30-second breathing cue to lower my heart rate.")
                    }
                    if (assistantState.messages.isNotEmpty()) {
                        QuickCoachChip("Clear", onClick = onClearChat)
                    }
                }
            }
        }

        assistantState.errorMessage?.let { err ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ACCENT_ROSE.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, ACCENT_ROSE.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = err,
                        color = TEXT_PRIMARY,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onClearError(err) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TEXT_PRIMARY, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Messages Area
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SURFACE_CARD,
            border = BorderStroke(1.dp, BORDER_ZINC)
        ) {
            if (assistantState.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ask your AI Coach about form, pacing, or live vitals.",
                        color = TEXT_MUTED,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(assistantState.messages, key = { it.id }) { msg ->
                        ChatBubble(msg)
                    }
                }
            }
        }

        // Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draftText,
                onValueChange = { draftText = it },
                placeholder = {
                    Text("Message AI Coach...", color = TEXT_MUTED, fontSize = 13.sp)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (draftText.isNotBlank()) {
                            onSendMessage(draftText)
                            draftText = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SURFACE_CARD,
                    unfocusedContainerColor = SURFACE_CARD,
                    focusedBorderColor = ACCENT_LIME,
                    unfocusedBorderColor = BORDER_ZINC,
                    focusedTextColor = TEXT_PRIMARY,
                    unfocusedTextColor = TEXT_PRIMARY
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = {
                    if (draftText.isNotBlank()) {
                        onSendMessage(draftText)
                        draftText = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ACCENT_LIME)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickCoachChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SURFACE_ELEVATED,
        border = BorderStroke(1.dp, BORDER_ZINC),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = TEXT_PRIMARY,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ChatBubble(message: AssistantMessage) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isUser) ACCENT_LIME.copy(alpha = 0.18f) else SURFACE_ELEVATED,
            border = BorderStroke(1.dp, if (isUser) ACCENT_LIME.copy(alpha = 0.4f) else BORDER_ZINC),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = MoodSpeculator.cleanAssistantText(message.text),
                color = TEXT_PRIMARY,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

// ============================================================================
// BOTTOM COMPACT VITALS BAR + TAB BAR
// ============================================================================

@Composable
private fun BottomVitalsAndNavBar(
    vitals: VitalsReading,
    isSensingRunning: Boolean,
    activeTab: FitLensTab,
    onSelectTab: (FitLensTab) -> Unit,
    onStartOrStopSensing: () -> Unit
) {
    Surface(
        color = SURFACE_CARD,
        border = BorderStroke(1.dp, BORDER_ZINC)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Compact 1-Line Live Vitals Summary Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactVitalStat(
                        label = "HR",
                        value = if (vitals.heartRateBpm > 0) "${vitals.heartRateBpm.roundToInt()}" else "--",
                        unit = "bpm",
                        color = ACCENT_ROSE
                    )
                    CompactVitalStat(
                        label = "BR",
                        value = if (vitals.breathingRateBpm > 0) "${vitals.breathingRateBpm.roundToInt()}" else "--",
                        unit = "rpm",
                        color = ACCENT_SKY
                    )
                    CompactVitalStat(
                        label = "HRV",
                        value = if (vitals.heartRateVariabilityMs > 0) "${vitals.heartRateVariabilityMs.roundToInt()}" else "--",
                        unit = "ms",
                        color = ACCENT_EMERALD
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSensingRunning) SURFACE_ELEVATED else ACCENT_LIME,
                    border = BorderStroke(1.dp, if (isSensingRunning) BORDER_ZINC else ACCENT_LIME),
                    modifier = Modifier.clickable { onStartOrStopSensing() }
                ) {
                    Text(
                        text = if (isSensingRunning) "Stop" else "Sense",
                        color = if (isSensingRunning) TEXT_SECONDARY else Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Material 3 Navigation Bar with animated selection indicator
            NavigationBar(
                containerColor = SURFACE_CARD,
                tonalElevation = 0.dp
            ) {
                FitLensTab.entries.forEach { tab ->
                    val selected = tab == activeTab
                    val icon = when (tab) {
                        FitLensTab.TRAIN -> Icons.Default.FitnessCenter
                        FitLensTab.VITALS -> Icons.Default.Favorite
                        FitLensTab.COACH -> Icons.AutoMirrored.Filled.Chat
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onSelectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ACCENT_LIME,
                            selectedTextColor = TEXT_PRIMARY,
                            indicatorColor = ACCENT_LIME.copy(alpha = 0.14f),
                            unselectedIconColor = TEXT_MUTED,
                            unselectedTextColor = TEXT_MUTED
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactVitalStat(
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alignByBaseline()
        )
        Text(
            text = value,
            color = TEXT_PRIMARY,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.alignByBaseline()
        )
        Text(
            text = unit,
            color = TEXT_MUTED,
            fontSize = 10.sp,
            modifier = Modifier.alignByBaseline()
        )
    }
}
