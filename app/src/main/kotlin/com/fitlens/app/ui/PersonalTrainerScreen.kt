package com.fitlens.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ShowChart
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlens.app.ai.WorkoutPlanResult
import com.fitlens.app.ai.live.AssistantMessage
import com.fitlens.app.ai.live.LiveAssistantState
import com.fitlens.app.ai.live.LiveConnectionState
import com.fitlens.app.ai.live.MoodSpeculation
import com.fitlens.app.ai.live.MoodSpeculator
import com.fitlens.app.camera.CameraPreviewView
import com.fitlens.app.camera.DualStreamAnalyzer
import com.fitlens.app.camera.SkeletonOverlay
import com.fitlens.app.data.model.WorkoutSessionEntity
import com.fitlens.app.data.profile.UserProfile
import com.fitlens.app.pose.PoseAnalysisFrame
import com.fitlens.app.vitals.VitalsReading
import com.fitlens.app.workout.WorkoutPhase
import com.fitlens.app.workout.WorkoutState
import com.presagetech.smartspectra.ProcessingStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val CORAL = Color(0xFFFF6B6B)
private val TEAL = Color(0xFF4FCCC4)
private val VIOLET = Color(0xFFA58CF9)
private val MINT = Color(0xFF6EE7B7)
private val AMBER = Color(0xFFFBBF24)
private val GLASS_DARK = Color(0xE6111827)
private val CARD_DARK = Color(0xFF131C2E)
private val BG_TOP = Color(0xFF0A0F1D)
private val BG_BOT = Color(0xFF05070C)

/**
 * Workout-First AI Personal Trainer & Home Fitness App Screen.
 *
 * Designed like top Google Play fitness apps ("Home Workout", "Six Pack in 30 Days"):
 * 1. The AI is the Personal Trainer & Coach: generates targeted workouts, explains the rationale,
 *    and guides the user through sets, reps, form cues, and recovery.
 * 2. Workout-First Layout (Not a Camera App): the main screen showcases interactive workouts,
 *    animated exercise demonstrations, and step-by-step coaching while the camera feed is minimized
 *    to a small right-corner PiP window or completely hidden in the background.
 * 3. Wearable-Free Vitals at the Bottom: powered by the Presage SmartSpectra SDK, delivering
 *    live Heart Rate + Zone, Breathing Rate, HRV Readiness, and Strain without expensive wearables.
 */
@Composable
fun PersonalTrainerScreen(
    vitals: VitalsReading,
    profile: UserProfile,
    assistantState: LiveAssistantState,
    moodSpeculation: MoodSpeculation,
    processingStatus: ProcessingStatus,
    validationHint: String?,
    sdkErrorMessage: String?,
    isPlaceholderPresageKey: Boolean,
    hasCameraPermission: Boolean,
    useFrontCamera: Boolean,
    dualStreamAnalyzer: DualStreamAnalyzer,
    poseFrame: PoseAnalysisFrame?,
    workoutState: WorkoutState,
    poseCoachEnabled: Boolean,
    generatedPlan: WorkoutPlanResult,
    isGeneratingPlan: Boolean,
    recentSessions: List<WorkoutSessionEntity> = emptyList(),
    onTogglePoseCoach: () -> Unit,
    onSelectExercise: (String) -> Unit,
    onGenerateWorkoutPlan: (goal: String, experience: String, equipment: String, durationMinutes: Int) -> Unit,
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
    var cameraDisplayMode by remember { mutableStateOf(CameraFeedDisplayMode.PIP_RIGHT) }
    var showCoachChatDrawer by remember { mutableStateOf(false) }
    var showVitalsWaveforms by remember { mutableStateOf(false) }

    // Custom AI Workout Generator selections
    var selectedGoal by remember { mutableStateOf(generatedPlan.goal.ifBlank { "Six-Pack Abs" }) }
    var selectedLevel by remember { mutableStateOf("Intermediate") }
    var selectedEquipment by remember { mutableStateOf("Bodyweight") }
    var selectedDuration by remember { mutableIntStateOf(15) }

    val isListening = assistantState.connectionState == LiveConnectionState.LISTENING
    val isConnecting = assistantState.connectionState == LiveConnectionState.CONNECTING
    val isWorkoutActive = workoutState.phase != WorkoutPhase.IDLE &&
        workoutState.phase != WorkoutPhase.COMPLETED
    val canStreamCamera = profile.hasAcceptedSensingConsent && hasCameraPermission

    LaunchedEffect(assistantState.errorMessage) {
        val message = assistantState.errorMessage
        if (message != null) {
            delay(6000L)
            onClearError(message)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BG_TOP, BG_BOT)
                )
            )
    ) {
        // Keep CameraX bound in a 1dp background anchor when camera mode is HIDDEN so
        // SmartSpectraSdk.shared.useCustomInput() continues computing vitals with zero visible camera UI.
        if (canStreamCamera && cameraDisplayMode == CameraFeedDisplayMode.HIDDEN) {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0.01f)
                    .align(Alignment.BottomEnd)
            ) {
                CameraPreviewView(
                    analyzer = dualStreamAnalyzer,
                    useFrontCamera = useFrontCamera,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // =========================================================================
            // 1. TOP APP BAR: Brand, Camera Mode (PiP / Hidden), AI Coach Drawer & Profile
            // =========================================================================
            TopFitnessBar(
                isListening = isListening,
                isConnecting = isConnecting,
                processingStatus = processingStatus,
                cameraDisplayMode = cameraDisplayMode,
                showCoachChatDrawer = showCoachChatDrawer,
                onCycleCameraMode = {
                    cameraDisplayMode = when (cameraDisplayMode) {
                        CameraFeedDisplayMode.PIP_RIGHT -> CameraFeedDisplayMode.HIDDEN
                        CameraFeedDisplayMode.HIDDEN -> CameraFeedDisplayMode.PIP_RIGHT
                        CameraFeedDisplayMode.EXPANDED -> CameraFeedDisplayMode.PIP_RIGHT
                    }
                },
                onToggleCoachDrawer = { showCoachChatDrawer = !showCoachChatDrawer },
                onOpenProfile = onOpenProfile
            )

            // =========================================================================
            // 2. MAIN WORKOUT STAGE (Scrollable Workout Hub OR Active Visual Workout Player)
            // =========================================================================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Optional Expanded Camera Form-Check Card (only if user explicitly expands PiP)
                    if (canStreamCamera && cameraDisplayMode == CameraFeedDisplayMode.EXPANDED) {
                        ExpandedFormCheckCameraCard(
                            dualStreamAnalyzer = dualStreamAnalyzer,
                            useFrontCamera = useFrontCamera,
                            poseFrame = poseFrame,
                            onMinimizeToPip = { cameraDisplayMode = CameraFeedDisplayMode.PIP_RIGHT },
                            onHideCamera = { cameraDisplayMode = CameraFeedDisplayMode.HIDDEN },
                            onToggleCameraFacing = onToggleCameraFacing
                        )
                    }

                    // Active Workout Player vs. AI Workout Generator & Programs Hub
                    if (isWorkoutActive) {
                        ActiveWorkoutPlayerSection(
                            workoutState = workoutState,
                            poseCoachEnabled = poseCoachEnabled,
                            onIncrementRep = onIncrementRep,
                            onCompleteSet = onCompleteSet,
                            onSkipPhase = onSkipPhase,
                            onStopWorkout = onStopWorkout,
                            onTogglePoseCoach = onTogglePoseCoach
                        )
                    } else {
                        // A. AI Personal Trainer Readiness & Live Voice Hero Card
                        AiTrainerHeroCard(
                            profile = profile,
                            vitals = vitals,
                            moodSpeculation = moodSpeculation,
                            workoutState = workoutState,
                            isListening = isListening,
                            isConnecting = isConnecting,
                            latestCoachMessage = assistantState.messages.lastOrNull { !it.isUser }?.text,
                            onToggleLiveVoice = onToggleLiveVoice,
                            onOpenCoachChat = { showCoachChatDrawer = true },
                            onMoodCheckIn = {
                                showCoachChatDrawer = true
                                onMoodCheckIn()
                            }
                        )

                        // B. Featured Play Store-Style Workout Programs ("Six-Pack Abs", "Home Full Body", etc.)
                        FeaturedProgramsCarousel(
                            selectedGoal = selectedGoal,
                            onSelectProgram = { program ->
                                selectedGoal = program.goalKey
                                selectedLevel = program.level
                                selectedDuration = program.durationMinutes
                                onGenerateWorkoutPlan(
                                    program.goalKey,
                                    program.level,
                                    selectedEquipment,
                                    program.durationMinutes
                                )
                            }
                        )

                        // C. AI Workout Customizer & Plan Generator
                        AiWorkoutGeneratorCard(
                            selectedGoal = selectedGoal,
                            selectedLevel = selectedLevel,
                            selectedEquipment = selectedEquipment,
                            selectedDuration = selectedDuration,
                            isGeneratingPlan = isGeneratingPlan,
                            onSelectGoal = { selectedGoal = it },
                            onSelectLevel = { selectedLevel = it },
                            onSelectEquipment = { selectedEquipment = it },
                            onSelectDuration = { selectedDuration = it },
                            onGenerate = {
                                onGenerateWorkoutPlan(
                                    selectedGoal,
                                    selectedLevel,
                                    selectedEquipment,
                                    selectedDuration
                                )
                            }
                        )

                        // D. Generated AI Workout Plan & Visual Exercise List
                        GeneratedRoutineCard(
                            plan = generatedPlan,
                            onStartFullRoutine = { onStartGeneratedRoutine(generatedPlan) },
                            onStartSingleExercise = { ex -> onSelectExercise(ex) }
                        )

                        // E. Recent Workouts & AI Post-Session Progress Vault
                        if (recentSessions.isNotEmpty()) {
                            RecentWorkoutsProgressCard(sessions = recentSessions)
                        }
                    }

                    // Collapsible AI Coach Chat Drawer (when toggled open)
                    AnimatedVisibility(visible = showCoachChatDrawer) {
                        AiCoachConversationCard(
                            assistantState = assistantState,
                            onSendMessage = onSendMessage,
                            onMoodCheckIn = onMoodCheckIn,
                            onClearChat = onClearChat,
                            onClose = { showCoachChatDrawer = false }
                        )
                    }

                    // Bottom padding so the floating right-corner PiP never covers the last item
                    Spacer(modifier = Modifier.height(130.dp))
                }

                // =====================================================================
                // 3. FLOATING RIGHT-CORNER CAMERA PiP WINDOW (OR COMPACT HIDDEN PILL)
                // =====================================================================
                if (canStreamCamera && cameraDisplayMode == CameraFeedDisplayMode.PIP_RIGHT) {
                    FloatingCameraPipWindow(
                        dualStreamAnalyzer = dualStreamAnalyzer,
                        useFrontCamera = useFrontCamera,
                        poseFrame = poseFrame,
                        onToggleCameraFacing = onToggleCameraFacing,
                        onHideCamera = { cameraDisplayMode = CameraFeedDisplayMode.HIDDEN },
                        onExpandCamera = { cameraDisplayMode = CameraFeedDisplayMode.EXPANDED },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 10.dp)
                    )
                } else if (canStreamCamera && cameraDisplayMode == CameraFeedDisplayMode.HIDDEN) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 10.dp)
                            .clickable { cameraDisplayMode = CameraFeedDisplayMode.PIP_RIGHT },
                        shape = RoundedCornerShape(20.dp),
                        color = GLASS_DARK,
                        border = BorderStroke(1.dp, MINT.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = "Camera Hidden",
                                tint = MINT,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Camera Hidden • Show PiP",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 4. BOTTOM ANCHORED BAR: SMARTSPECTRA WEARABLE-FREE VITALS
            // =========================================================================
            SmartSpectraBottomVitalsBar(
                vitals = vitals,
                profile = profile,
                processingStatus = processingStatus,
                validationHint = validationHint,
                sdkErrorMessage = sdkErrorMessage,
                isPlaceholderPresageKey = isPlaceholderPresageKey,
                hasCameraPermission = hasCameraPermission,
                showWaveforms = showVitalsWaveforms,
                onToggleWaveforms = { showVitalsWaveforms = !showVitalsWaveforms },
                onStartOrStopSensing = onStartOrStopSensing,
                onReviewConsent = onReviewConsent,
                onGrantCamera = onGrantCamera,
                onSimulateExertion = onSimulateExertion
            )
        }
    }
}

@Composable
private fun TopFitnessBar(
    isListening: Boolean,
    isConnecting: Boolean,
    processingStatus: ProcessingStatus,
    cameraDisplayMode: CameraFeedDisplayMode,
    showCoachChatDrawer: Boolean,
    onCycleCameraMode: () -> Unit,
    onToggleCoachDrawer: () -> Unit,
    onOpenProfile: () -> Unit
) {
    Surface(
        color = Color(0xFF0A0F1D).copy(alpha = 0.95f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MINT.copy(alpha = 0.18f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = MINT,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FitLens AI Coach",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                val statusSub = when {
                    isListening -> "Gemini Live Coach • Listening"
                    isConnecting -> "Connecting AI Coach…"
                    processingStatus == ProcessingStatus.RUNNING -> "SmartSpectra Vitals Active (${cameraDisplayMode.label})"
                    else -> "AI Home Workout & Vitals Trainer"
                }
                Text(
                    text = statusSub,
                    color = if (processingStatus == ProcessingStatus.RUNNING) MINT else Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Camera Minimize / Hide Toggle Button
            Surface(
                modifier = Modifier.clickable(onClick = onCycleCameraMode),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = when (cameraDisplayMode) {
                            CameraFeedDisplayMode.HIDDEN -> Icons.Default.VisibilityOff
                            CameraFeedDisplayMode.PIP_RIGHT -> Icons.Default.PictureInPictureAlt
                            CameraFeedDisplayMode.EXPANDED -> Icons.Default.Visibility
                        },
                        contentDescription = "Toggle Camera View",
                        tint = TEAL,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = when (cameraDisplayMode) {
                            CameraFeedDisplayMode.HIDDEN -> "Cam Off"
                            CameraFeedDisplayMode.PIP_RIGHT -> "PiP"
                            CameraFeedDisplayMode.EXPANDED -> "Full"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // AI Coach Chat Drawer Toggle
            IconButton(
                onClick = onToggleCoachDrawer,
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        if (showCoachChatDrawer) VIOLET.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.10f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "AI Coach Chat",
                    tint = if (showCoachChatDrawer) VIOLET else Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }

            // Profile, HR Zones & Privacy
            IconButton(
                onClick = onOpenProfile,
                modifier = Modifier
                    .size(34.dp)
                    .background(Color.White.copy(alpha = 0.10f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ManageAccounts,
                    contentDescription = "Profile & Privacy",
                    tint = MINT,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AiTrainerHeroCard(
    profile: UserProfile,
    vitals: VitalsReading,
    moodSpeculation: MoodSpeculation,
    workoutState: WorkoutState,
    isListening: Boolean,
    isConnecting: Boolean,
    latestCoachMessage: String?,
    onToggleLiveVoice: () -> Unit,
    onOpenCoachChat: () -> Unit,
    onMoodCheckIn: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CARD_DARK,
        border = BorderStroke(1.dp, VIOLET.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = moodSpeculation.emoji, fontSize = 22.sp)
                    Column {
                        Text(
                            text = "AI PERSONAL TRAINER & COACH",
                            color = VIOLET,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (workoutState.phase == WorkoutPhase.COMPLETED) {
                                "Workout Complete! Great job, ${profile.name}"
                            } else {
                                "Ready to train, ${profile.name} • ${moodSpeculation.label}"
                            },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    modifier = Modifier.clickable { onMoodCheckIn() },
                    shape = RoundedCornerShape(12.dp),
                    color = VIOLET.copy(alpha = 0.18f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = VIOLET,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Readiness Check",
                            color = VIOLET,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            val coachNote = when {
                workoutState.phase == WorkoutPhase.COMPLETED && workoutState.activeCoachAlert != null ->
                    workoutState.activeCoachAlert
                vitals.heartRateBpm > 0 ->
                    "SmartSpectra Readiness: ${vitals.heartRateBpm.roundToInt()} BPM (${vitals.heartRateZone.label}) • " +
                        (latestCoachMessage ?: "Pick a program below or generate a custom AI workout.")
                else ->
                    latestCoachMessage
                        ?: "Select a workout program below or customize your goal. I'll guide your sets, count reps, and adapt rest intervals using SmartSpectra vitals."
            }

            Text(
                text = coachNote,
                color = Color(0xFFD5DCE6),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val pulseTransition = rememberInfiniteTransition(label = "voice_pulse")
                val pulseAlpha by pulseTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (isListening) 0.7f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "voice_alpha"
                )

                Button(
                    onClick = onToggleLiveVoice,
                    enabled = !isConnecting,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isListening -> CORAL.copy(alpha = pulseAlpha)
                            isConnecting -> AMBER
                            else -> VIOLET
                        }
                    )
                ) {
                    Icon(
                        imageVector = when {
                            isListening -> Icons.Default.GraphicEq
                            isConnecting -> Icons.Default.MicOff
                            else -> Icons.Default.Mic
                        },
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isListening -> "Stop Live Coach"
                            isConnecting -> "Connecting…"
                            else -> "Talk Live to Coach"
                        },
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = onOpenCoachChat,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ask AI Coach",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedProgramsCarousel(
    selectedGoal: String,
    onSelectProgram: (FeaturedWorkoutProgram) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Featured Home Workout Programs",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = "Tap to load",
                color = TEAL,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PLAY_STORE_WORKOUT_PROGRAMS.forEach { program ->
                val accent = Color(program.accentHex)
                val isSelected = selectedGoal.equals(program.goalKey, ignoreCase = true)
                Surface(
                    modifier = Modifier
                        .width(210.dp)
                        .clickable { onSelectProgram(program) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) accent.copy(alpha = 0.18f) else CARD_DARK,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) accent else Color.White.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accent.copy(alpha = 0.22f)
                            ) {
                                Text(
                                    text = program.badge,
                                    color = accent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                            Text(
                                text = "${program.durationMinutes} min",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = program.title,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = program.subtitle,
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Level: ${program.level}",
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiWorkoutGeneratorCard(
    selectedGoal: String,
    selectedLevel: String,
    selectedEquipment: String,
    selectedDuration: Int,
    isGeneratingPlan: Boolean,
    onSelectGoal: (String) -> Unit,
    onSelectLevel: (String) -> Unit,
    onSelectEquipment: (String) -> Unit,
    onSelectDuration: (Int) -> Unit,
    onGenerate: () -> Unit
) {
    var expandedConfigurator by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CARD_DARK,
        border = BorderStroke(1.dp, TEAL.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedConfigurator = !expandedConfigurator },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI WORKOUT BUILDER",
                        color = TEAL,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$selectedGoal • $selectedLevel • $selectedDuration min ($selectedEquipment)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (expandedConfigurator) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Customize Workout",
                    tint = TEAL
                )
            }

            AnimatedVisibility(visible = expandedConfigurator) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Target Focus", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Six-Pack Abs", "Strength", "Chest & Arms", "Legs & Glutes", "HIIT Cardio", "Hypertrophy").forEach { goal ->
                            SelectablePill(
                                label = goal,
                                selected = selectedGoal.equals(goal, ignoreCase = true),
                                onClick = { onSelectGoal(goal) }
                            )
                        }
                    }

                    Text("Difficulty & Equipment", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Beginner", "Intermediate", "Advanced").forEach { level ->
                            SelectablePill(
                                label = level,
                                selected = selectedLevel.equals(level, ignoreCase = true),
                                onClick = { onSelectLevel(level) }
                            )
                        }
                        listOf("Bodyweight", "Dumbbells").forEach { equip ->
                            SelectablePill(
                                label = equip,
                                selected = selectedEquipment.equals(equip, ignoreCase = true),
                                accent = VIOLET,
                                onClick = { onSelectEquipment(equip) }
                            )
                        }
                    }

                    Text("Duration", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(10, 15, 20, 30).forEach { mins ->
                            SelectablePill(
                                label = "$mins min",
                                selected = selectedDuration == mins,
                                onClick = { onSelectDuration(mins) }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onGenerate,
                enabled = !isGeneratingPlan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TEAL)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isGeneratingPlan) "Generating AI Workout Plan…" else "Generate Custom AI Workout",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun GeneratedRoutineCard(
    plan: WorkoutPlanResult,
    onStartFullRoutine: () -> Unit,
    onStartSingleExercise: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CARD_DARK,
        border = BorderStroke(1.dp, MINT.copy(alpha = 0.4f))
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
                        text = "AI COACH GENERATED WORKOUT",
                        color = MINT,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${plan.goal} Circuit (${plan.targetExercises.size} Exercises)",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MINT.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "${plan.targetDurationMinutes}m • ${plan.recommendedRestIntervalSeconds}s rest",
                        color = MINT,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (plan.aiCoachRationale.isNotBlank()) {
                Text(
                    text = plan.aiCoachRationale,
                    color = Color(0xFFD5DCE6),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            // Primary CTA: Start Full Guided Routine
            Button(
                onClick = onStartFullRoutine,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MINT)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Start Guided AI Workout (${plan.targetExercises.size} Exercises)",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }

            // Visual Exercise Preview List (Home Workout style)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plan.targetExercises.forEachIndexed { index, exerciseName ->
                    val meta = getExerciseCoachingMeta(exerciseName)
                    val isIsometric = exerciseName.contains("plank", ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStartSingleExercise(exerciseName) },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0D1424),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Mini animated exercise preview thumbnail
                            AnimatedExerciseIllustration(
                                exerciseName = exerciseName,
                                modifier = Modifier.size(width = 78.dp, height = 68.dp)
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "${index + 1}. $exerciseName",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isIsometric) "3 Sets × 30s Hold" else "3 Sets × 12 Reps",
                                    color = TEAL,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = meta.targetMuscles,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = TEAL.copy(alpha = 0.2f),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Start $exerciseName",
                                        tint = TEAL,
                                        modifier = Modifier.size(18.dp)
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
 * Small Picture-in-Picture (PiP) camera window anchored in the right corner.
 * Lets the user check their position or hide the camera completely so the app
 * never looks like a camera app.
 */
@Composable
private fun FloatingCameraPipWindow(
    dualStreamAnalyzer: DualStreamAnalyzer,
    useFrontCamera: Boolean,
    poseFrame: PoseAnalysisFrame?,
    onToggleCameraFacing: () -> Unit,
    onHideCamera: () -> Unit,
    onExpandCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(114.dp)
            .height(156.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0B1020),
        border = BorderStroke(1.5.dp, TEAL.copy(alpha = 0.7f)),
        shadowElevation = 10.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
        ) {
            CameraPreviewView(
                analyzer = dualStreamAnalyzer,
                useFrontCamera = useFrontCamera,
                modifier = Modifier.fillMaxSize()
            )

            poseFrame?.takeIf { it.landmarks.isNotEmpty() }?.let { frame ->
                SkeletonOverlay(
                    landmarks = frame.landmarks,
                    hasActiveDeviation = frame.activeDeviations.isNotEmpty(),
                    frameWidth = frame.frameWidth,
                    frameHeight = frame.frameHeight,
                    activeDeviations = frame.activeDeviations,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top PiP Controls (Flip Camera & Hide Camera)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onToggleCameraFacing() }
                )
                Text(
                    text = "PiP",
                    color = TEAL,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = "Hide Camera",
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onHideCamera() }
                )
            }

            // Bottom Expand Button
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clickable { onExpandCamera() },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Expand Form Check",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpandedFormCheckCameraCard(
    dualStreamAnalyzer: DualStreamAnalyzer,
    useFrontCamera: Boolean,
    poseFrame: PoseAnalysisFrame?,
    onMinimizeToPip: () -> Unit,
    onHideCamera: () -> Unit,
    onToggleCameraFacing: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0B1020),
        border = BorderStroke(1.5.dp, TEAL)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreviewView(
                analyzer = dualStreamAnalyzer,
                useFrontCamera = useFrontCamera,
                modifier = Modifier.fillMaxSize()
            )
            poseFrame?.takeIf { it.landmarks.isNotEmpty() }?.let { frame ->
                SkeletonOverlay(
                    landmarks = frame.landmarks,
                    hasActiveDeviation = frame.activeDeviations.isNotEmpty(),
                    frameWidth = frame.frameWidth,
                    frameHeight = frame.frameHeight,
                    activeDeviations = frame.activeDeviations,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Biomechanics Form Check",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Flip Camera",
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onToggleCameraFacing() }
                    )
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Minimize to PiP",
                        tint = TEAL,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onMinimizeToPip() }
                    )
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = "Hide Camera",
                        tint = CORAL,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onHideCamera() }
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Bottom Bar for Presage SmartSpectra SDK Wearable-Free Vitals.
 * Provides live Heart Rate + Zone, Breathing Rate, HRV Readiness, Facial Strain,
 * and expandable waveforms/insights without requiring a smartwatch or chest strap.
 */
@Composable
private fun SmartSpectraBottomVitalsBar(
    vitals: VitalsReading,
    profile: UserProfile,
    processingStatus: ProcessingStatus,
    validationHint: String?,
    sdkErrorMessage: String?,
    isPlaceholderPresageKey: Boolean,
    hasCameraPermission: Boolean,
    showWaveforms: Boolean,
    onToggleWaveforms: () -> Unit,
    onStartOrStopSensing: () -> Unit,
    onReviewConsent: () -> Unit,
    onGrantCamera: () -> Unit,
    onSimulateExertion: (Float) -> Unit
) {
    Surface(
        color = GLASS_DARK,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = BorderStroke(1.dp, TEAL.copy(alpha = 0.35f)),
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Wearable-Free Vitals Badge + Sensing Toggle + Waveforms Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = if (processingStatus == ProcessingStatus.RUNNING) MINT else TEAL,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = "SMARTSPECTRA WEARABLE-FREE VITALS",
                            color = MINT,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        val subLabel = when {
                            !profile.hasAcceptedSensingConsent -> "Tap Enable Sensing (Consent-gated, no wearable needed)"
                            !hasCameraPermission -> "Grant camera permission for contactless rPPG vitals"
                            processingStatus == ProcessingStatus.RUNNING && !validationHint.isNullOrBlank() ->
                                "Signal Tip: $validationHint"
                            processingStatus == ProcessingStatus.RUNNING ->
                                "Contactless rPPG Active • ${vitals.heartRateZone.label}"
                            processingStatus == ProcessingStatus.ERROR ->
                                sdkErrorMessage ?: "Sensing paused — tap Start Sensing or use Demo Sim"
                            isPlaceholderPresageKey ->
                                "Add PRESAGE_API_KEY in secrets.properties or tap Demo Vitals"
                            else -> "Replaces chest straps & smartwatches via on-device optical sensing"
                        }
                        Text(
                            text = subLabel,
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onToggleWaveforms,
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                if (showWaveforms) TEAL.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = "Waveforms & Demo",
                            tint = if (showWaveforms) TEAL else Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Button(
                        onClick = {
                            if (!profile.hasAcceptedSensingConsent) {
                                onReviewConsent()
                            } else if (!hasCameraPermission) {
                                onGrantCamera()
                            } else {
                                onStartOrStopSensing()
                            }
                        },
                        enabled = processingStatus != ProcessingStatus.STARTING &&
                            processingStatus != ProcessingStatus.STOPPING,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (processingStatus == ProcessingStatus.RUNNING) CORAL else MINT
                        )
                    ) {
                        Text(
                            text = when {
                                processingStatus == ProcessingStatus.RUNNING -> "Stop Sensing"
                                processingStatus == ProcessingStatus.STARTING -> "Starting…"
                                !profile.hasAcceptedSensingConsent -> "Consent & Sense"
                                !hasCameraPermission -> "Grant Cam"
                                else -> "Start Sensing"
                            },
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // 4-Metric Wearable-Free Vitals Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val pulseQualityColor = when {
                    vitals.heartRateBpm <= 35.0 -> null
                    vitals.signalConfidence >= 0.65f -> MINT
                    vitals.signalConfidence >= 0.35f -> AMBER
                    else -> CORAL
                }
                CompactVitalPill(
                    label = "HEART RATE",
                    value = if (vitals.heartRateBpm > 0) "${vitals.heartRateBpm.roundToInt()} bpm" else "-- bpm",
                    subValue = if (vitals.heartRateBpm > 0) vitals.heartRateZone.label.substringBefore(" ") else "Karvonen Zone",
                    accent = CORAL,
                    qualityDotColor = pulseQualityColor,
                    modifier = Modifier.weight(1f)
                )
                CompactVitalPill(
                    label = "BREATHING",
                    value = if (vitals.breathingRateBpm > 0) "${vitals.breathingRateBpm.roundToInt()} rpm" else "-- rpm",
                    subValue = if (vitals.breathingRateBpm >= 21) "Elevated" else "Cadence",
                    accent = TEAL,
                    modifier = Modifier.weight(1f)
                )
                CompactVitalPill(
                    label = "HRV RMSSD",
                    value = if (vitals.heartRateVariabilityMs > 0) "${vitals.heartRateVariabilityMs.roundToInt()} ms" else "-- ms",
                    subValue = when {
                        vitals.heartRateVariabilityMs >= 50 -> "High Ready"
                        vitals.heartRateVariabilityMs >= 28 -> "Balanced"
                        vitals.heartRateVariabilityMs > 0 -> "High Load"
                        else -> "Readiness"
                    },
                    accent = MINT,
                    modifier = Modifier.weight(1f)
                )
                CompactVitalPill(
                    label = "STRAIN / FACE",
                    value = vitals.facialExpression ?: "--",
                    subValue = if (vitals.facialExpression in setOf("Strain", "Angry", "Fear")) "Relax Jaw" else "Effort",
                    accent = AMBER,
                    modifier = Modifier.weight(1f)
                )
            }

            // Expandable Waveforms, SmartSpectra Insights & Instant Demo Vitals Controls
            AnimatedVisibility(visible = showWaveforms) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactWaveformCard(
                            label = "Arterial Pulse Trace",
                            samples = vitals.arterialPressureTrace,
                            lineColor = CORAL,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        )
                        CompactWaveformCard(
                            label = "Chest Breathing",
                            samples = vitals.chestBreathingTrace,
                            lineColor = TEAL,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        )
                        CompactWaveformCard(
                            label = "Abdomen Trace",
                            samples = vitals.abdomenBreathingTrace,
                            lineColor = VIOLET,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        )
                    }

                    vitals.smartSpectraLlmInsight?.takeIf { it.isNotBlank() }?.let { insight ->
                        Text(
                            text = insight,
                            color = Color(0xFFD5DCE6),
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Instant Simulation Controls for testing Vitals + Adaptive Rest
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Test Vitals:",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        QuickPromptChip("Resting (68 bpm)") { onSimulateExertion(0.15f) }
                        QuickPromptChip("Aerobic (135 bpm)") { onSimulateExertion(0.55f) }
                        QuickPromptChip("Peak HR (172 bpm)") { onSimulateExertion(0.88f) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiCoachConversationCard(
    assistantState: LiveAssistantState,
    onSendMessage: (String) -> Unit,
    onMoodCheckIn: () -> Unit,
    onClearChat: () -> Unit,
    onClose: () -> Unit
) {
    var draftText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val totalItems = assistantState.messages.size +
        (if (assistantState.streamingUserText.isNotBlank()) 1 else 0) +
        (if (assistantState.streamingAssistantText.isNotBlank()) 1 else 0) +
        (if (assistantState.isSendingText) 1 else 0)

    LaunchedEffect(totalItems) {
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CARD_DARK,
        border = BorderStroke(1.dp, VIOLET.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Personal Trainer Chat",
                    color = VIOLET,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onClearChat, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Chat",
                            tint = Color.LightGray,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Chat",
                            tint = Color.LightGray,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(assistantState.messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }
                if (assistantState.streamingUserText.isNotBlank()) {
                    item(key = "streaming_user") {
                        ChatBubble(
                            message = AssistantMessage(
                                isUser = true,
                                text = assistantState.streamingUserText + " …"
                            )
                        )
                    }
                }
                if (assistantState.streamingAssistantText.isNotBlank()) {
                    item(key = "streaming_ai") {
                        ChatBubble(
                            message = AssistantMessage(
                                isUser = false,
                                text = MoodSpeculator.cleanAssistantText(assistantState.streamingAssistantText)
                            )
                        )
                    }
                }
                if (assistantState.isSendingText) {
                    item(key = "thinking_ai") {
                        ChatBubble(
                            message = AssistantMessage(
                                isUser = false,
                                text = "Coach is thinking…"
                            )
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickPromptChip("Check my vitals & readiness") { onMoodCheckIn() }
                QuickPromptChip("Tips for Six-Pack Abs") {
                    onSendMessage("Give me 3 key form and breathing tips for building six-pack abs safely at home.")
                }
                QuickPromptChip("How long should I rest?") {
                    onSendMessage("Based on my heart rate and HRV right now, how long should I rest between sets?")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Ask your AI Trainer about workouts, form, or vitals…",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp
                        )
                    },
                    maxLines = 2,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF0B1020),
                        unfocusedContainerColor = Color(0xFF0B1020),
                        focusedBorderColor = VIOLET,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.18f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (draftText.isNotBlank()) {
                                onSendMessage(draftText)
                                draftText = ""
                            }
                        }
                    )
                )

                IconButton(
                    onClick = {
                        if (draftText.isNotBlank()) {
                            onSendMessage(draftText)
                            draftText = ""
                        }
                    },
                    enabled = draftText.isNotBlank() && !assistantState.isSendingText,
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (draftText.isNotBlank()) VIOLET else Color.White.copy(alpha = 0.10f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (draftText.isNotBlank()) Color.Black else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectablePill(
    label: String,
    selected: Boolean,
    accent: Color = TEAL,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accent.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, if (selected) accent else Color.White.copy(alpha = 0.14f))
    ) {
        Text(
            text = label,
            color = if (selected) accent else Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ChatBubble(message: AssistantMessage) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (message.isUser) 14.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 14.dp
            ),
            color = if (message.isUser) VIOLET.copy(alpha = 0.28f) else Color(0xFF0B1020),
            border = BorderStroke(
                width = 1.dp,
                color = if (message.isUser) VIOLET.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f)
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
                message.moodTag?.takeIf { it.isNotBlank() }?.let { tag ->
                    Text(
                        text = tag,
                        color = MINT,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    text = message.text,
                    color = Color(0xFFF1F5F9),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Text(
                    text = timeFormatter.format(Date(message.timestampMs)),
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickPromptChip(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
    ) {
        Text(
            text = label,
            color = Color(0xFFE2E8F0),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RecentWorkoutsProgressCard(
    sessions: List<WorkoutSessionEntity>
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CARD_DARK,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WORKOUT HISTORY & AI SUMMARY",
                        color = VIOLET,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Recent Completed Sessions (${sessions.size})",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = CORAL,
                    modifier = Modifier.size(18.dp)
                )
            }

            sessions.forEach { session ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0B111E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = session.exerciseName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${session.completedReps} reps • ${session.formAccuracyPercentage.roundToInt()}% form",
                                color = MINT,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = "SmartSpectra Avg HR: ${session.averageHeartRate.roundToInt()} BPM (Peak ${session.maxHeartRate.roundToInt()} BPM) • Adaptive Rest: +${session.extendedRestSeconds}s",
                            color = TEAL,
                            fontSize = 11.sp
                        )
                        if (session.coachFeedback.isNotBlank()) {
                            Text(
                                text = session.coachFeedback,
                                color = Color.White.copy(alpha = 0.68f),
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactVitalPill(
    label: String,
    value: String,
    subValue: String,
    accent: Color,
    modifier: Modifier = Modifier,
    qualityDotColor: Color? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0B111E),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (qualityDotColor != null) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(qualityDotColor, CircleShape)
                    )
                }
                Text(
                    text = label,
                    fontSize = 8.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subValue,
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactWaveformCard(
    label: String,
    samples: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0B111E),
        border = BorderStroke(1.dp, lineColor.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(label, fontSize = 8.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, maxLines = 1)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (samples.size < 2) return@Canvas
                val w = size.width
                val h = size.height
                val inset = 3f
                val minV = samples.min()
                val maxV = samples.max()
                val range = if (maxV - minV == 0f) 1f else maxV - minV

                val path = Path()
                samples.forEachIndexed { idx, v ->
                    val x = inset + (w - 2 * inset) * idx / (samples.size - 1)
                    val y = (h - inset) - (h - 2 * inset) * (v - minV) / range
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = lineColor.copy(alpha = 0.9f),
                    style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
