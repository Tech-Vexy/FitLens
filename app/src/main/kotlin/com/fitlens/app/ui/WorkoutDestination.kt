package com.fitlens.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.fitlens.app.ai.MesocyclePlanResult
import com.fitlens.app.ai.WorkoutPlanResult
import com.fitlens.app.pose.PoseLandmarkManager
import com.fitlens.app.workout.WorkoutPhase

/**
 * Workout flow destination: pre-workout setup (Single Exercise OR ADK AI Routine & Mesocycle)
 * -> live multi-signal session HUD -> post-workout summary.
 */
@Composable
fun WorkoutDestination(
    navController: NavHostController,
    viewModel: WorkoutViewModel,
    onStartVitals: () -> Unit,
    onStopVitals: () -> Unit
) {
    val state by viewModel.workoutState.collectAsState()
    val landmarks by viewModel.currentLandmarks.collectAsState()
    val frameSize by viewModel.poseFrameSize.collectAsState()
    val vitals by viewModel.vitalsFlow.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val lastSavedSession by viewModel.lastSavedSession.collectAsState()
    val isAudioMuted by viewModel.isAudioMuted.collectAsState()
    val generatedPlan by viewModel.generatedPlan.collectAsState()
    val generatedMesocycle by viewModel.generatedMesocycle.collectAsState()
    val isGeneratingAiPlan by viewModel.isGeneratingAiPlan.collectAsState()

    var showConsentDialog by remember { mutableStateOf(false) }

    // Enable concurrent MediaPipe + SmartSpectra while inside WorkoutDestination
    LaunchedEffect(profile.hasAcceptedSensingConsent) {
        viewModel.setPoseAnalysisEnabled(true)
        if (profile.hasAcceptedSensingConsent) {
            onStartVitals()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.setPoseAnalysisEnabled(false)
            onStopVitals()
        }
    }

    LaunchedEffect(state.phase, lastSavedSession) {
        if (
            state.phase == WorkoutPhase.COMPLETED &&
            lastSavedSession == null &&
            navController.currentBackStackEntry?.destination?.route == "workout"
        ) {
            viewModel.finishWorkout()
            navController.navigate("summary")
        }
    }

    if (showConsentDialog) {
        SensingConsentDialog(
            initialTelemetryEnabled = profile.smartSpectraTelemetryEnabled,
            initialPreferLocalEdgeAi = profile.preferLocalEdgeAi,
            onAccept = { telemetryEnabled, preferLocalEdge ->
                viewModel.acceptSensingConsent(telemetryEnabled, preferLocalEdge)
                showConsentDialog = false
                onStartVitals()
            },
            onDismiss = { showConsentDialog = false }
        )
    }

    if (state.phase == WorkoutPhase.IDLE || state.phase == WorkoutPhase.COMPLETED) {
        WorkoutSetupScreen(
            hasAcceptedConsent = profile.hasAcceptedSensingConsent,
            preferLocalEdgeAi = profile.preferLocalEdgeAi,
            generatedPlan = generatedPlan,
            generatedMesocycle = generatedMesocycle,
            isGeneratingAiPlan = isGeneratingAiPlan,
            onRequestConsent = { showConsentDialog = true },
            onGenerateAiPlan = { goal, experience, equipment, duration ->
                viewModel.generateAiPlanAndMesocycle(goal, experience, equipment, duration)
            },
            onStartSingle = { exercise, targetValue, sets, includeWarmupCooldown ->
                viewModel.startWorkout(
                    exercise = exercise,
                    targetReps = targetValue,
                    sets = sets,
                    includeWarmupAndCooldown = includeWarmupCooldown
                )
            },
            onStartRoutine = { exercises, targetReps, setsPerEx, restSec ->
                viewModel.startRoutine(
                    exercises = exercises,
                    targetReps = targetReps,
                    setsPerExercise = setsPerEx,
                    baseRestSeconds = restSec,
                    includeWarmupAndCooldown = true
                )
            },
            onBack = { navController.popBackStack() }
        )
    } else {
        WorkoutSessionScreen(
            state = state,
            landmarks = landmarks,
            frameWidth = frameSize.first,
            frameHeight = frameSize.second,
            cameraAnalyzer = viewModel.dualStreamAnalyzer,
            pulseWaveform = vitals.arterialPressureTrace,
            hasAcceptedConsent = profile.hasAcceptedSensingConsent,
            onRequestConsent = { showConsentDialog = true },
            isAudioMuted = isAudioMuted,
            onToggleAudio = viewModel::toggleAudioMute,
            onSkipWarmup = viewModel::skipWarmup,
            onSimulateRep = viewModel::simulateRep,
            onSimulateExertion = viewModel::simulateHighExertionAndStrain,
            onCompleteSet = viewModel::completeSet,
            onSkipRest = viewModel::skipRest,
            onFinishWorkout = {
                viewModel.finishWorkout()
                navController.navigate("summary")
            },
            onBack = { navController.popBackStack() },
            autoRequestCamera = profile.hasAcceptedSensingConsent
        )
    }
}

@Composable
private fun WorkoutSetupScreen(
    hasAcceptedConsent: Boolean,
    preferLocalEdgeAi: Boolean,
    generatedPlan: WorkoutPlanResult?,
    generatedMesocycle: MesocyclePlanResult?,
    isGeneratingAiPlan: Boolean,
    onRequestConsent: () -> Unit,
    onGenerateAiPlan: (goal: String, experience: String, equipment: String, durationMinutes: Int) -> Unit,
    onStartSingle: (exercise: String, targetValue: Int, sets: Int, includeWarmupCooldown: Boolean) -> Unit,
    onStartRoutine: (exercises: List<String>, reps: Int, sets: Int, restSec: Int) -> Unit,
    onBack: () -> Unit
) {
    var isAiMode by remember { mutableStateOf(false) }

    // Single exercise state
    val exercises = listOf("Bodyweight Squats", "Push-Ups", "Lunges", "Plank")
    var selectedExercise by remember { mutableStateOf(exercises.first()) }
    val isPlank = PoseLandmarkManager.isIsometricExercise(selectedExercise)
    var targetReps by remember { mutableIntStateOf(10) }
    var targetHoldSec by remember { mutableIntStateOf(30) }
    var totalSets by remember { mutableIntStateOf(3) }
    var includeWarmupCooldown by remember { mutableStateOf(true) }

    // ADK AI Generator state
    val goals = listOf("Mobility & Core", "Hypertrophy", "Cardio Endurance")
    val equipments = listOf("Bodyweight", "Dumbbells", "Barbell")
    val experiences = listOf("Beginner", "Intermediate", "Advanced")
    var selectedGoal by remember { mutableStateOf(goals.first()) }
    var selectedEquipment by remember { mutableStateOf(equipments.first()) }
    var selectedExperience by remember { mutableStateOf(experiences[1]) }
    var durationMinutes by remember { mutableIntStateOf(30) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E121B))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = "Configure Session",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "MediaPipe 3D Pose + SmartSpectra Multi-Signal Biofeedback",
                    fontSize = 11.sp,
                    color = Color(0xFF6EE7B7)
                )
            }
        }

        if (!hasAcceptedConsent) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRequestConsent() },
                color = Color(0xFFFBBF24).copy(alpha = 0.14f),
                border = BorderStroke(1.dp, Color(0xFFFBBF24)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFFFBBF24))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Camera Sensing Consent Not Yet Enabled", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Tap to review privacy & enable live camera rPPG + pose tracking (or test in simulation mode).", color = Color.LightGray, fontSize = 11.sp)
                    }
                }
            }
        }

        // Mode Switcher: Single Exercise vs ADK AI Coach Routine
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF192131), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ModeTabButton(
                title = "Quick Exercise",
                selected = !isAiMode,
                onClick = { isAiMode = false },
                modifier = Modifier.weight(1f)
            )
            ModeTabButton(
                title = "ADK AI Coach Plan",
                selected = isAiMode,
                onClick = { isAiMode = true },
                modifier = Modifier.weight(1f)
            )
        }

        if (!isAiMode) {
            Text("Select Movement", color = Color.LightGray, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                exercises.chunked(2).forEach { rowExercises ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowExercises.forEach { exercise ->
                            val selected = exercise == selectedExercise
                            val isometricTag = PoseLandmarkManager.isIsometricExercise(exercise)
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedExercise = exercise },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) Color(0xFF00E676).copy(alpha = 0.18f) else Color(0xFF192131),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) Color(0xFF00E676) else Color.Transparent
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        if (isometricTag) Icons.Default.Timer else Icons.Default.FitnessCenter,
                                        contentDescription = null,
                                        tint = if (selected) Color(0xFF00E676) else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            exercise,
                                            color = if (selected) Color.White else Color.LightGray,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            if (isometricTag) "Timed Hold" else "Rep Tracked",
                                            color = Color.Gray,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                        if (rowExercises.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isPlank) {
                    StepperCard(
                        label = "Hold sec / set",
                        value = "${targetHoldSec}s",
                        onDecrement = { if (targetHoldSec > 15) targetHoldSec -= 15 },
                        onIncrement = { if (targetHoldSec < 90) targetHoldSec += 15 },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    StepperCard(
                        label = "Reps / set",
                        value = "$targetReps",
                        onDecrement = { if (targetReps > 5) targetReps -= 5 },
                        onIncrement = { if (targetReps < 30) targetReps += 5 },
                        modifier = Modifier.weight(1f)
                    )
                }
                StepperCard(
                    label = "Sets",
                    value = "$totalSets",
                    onDecrement = { if (totalSets > 1) totalSets -= 1 },
                    onIncrement = { if (totalSets < 6) totalSets += 1 },
                    modifier = Modifier.weight(1f)
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF192131)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Guided Warmup & Recovery Cooldown", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Includes SmartSpectra coherent breathing pacer & HR recovery drop", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(checked = includeWarmupCooldown, onCheckedChange = { includeWarmupCooldown = it })
                }
            }

            val activeTarget = if (isPlank) targetHoldSec else targetReps
            val unitSuffix = if (isPlank) "s Hold" else " Reps"
            Button(
                onClick = {
                    onStartSingle(selectedExercise, activeTarget, totalSets, includeWarmupCooldown)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
            ) {
                Text(
                    "Begin $totalSets × $activeTarget$unitSuffix",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        } else {
            // ADK AI Coach Routine & 4-Week Mesocycle Builder
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF192131)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ADK Kotlin Coach Generator", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Surface(
                            color = Color(0xFFA58CF9).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (preferLocalEdgeAi) "On-Device Edge" else "Zero-Key Firebase + Edge",
                                color = Color(0xFFA58CF9),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text("Goal", color = Color.Gray, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        goals.forEach { goal ->
                            ChoiceChipPill(
                                label = goal,
                                selected = goal == selectedGoal,
                                onClick = { selectedGoal = goal },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text("Equipment", color = Color.Gray, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        equipments.forEach { eq ->
                            ChoiceChipPill(
                                label = eq,
                                selected = eq == selectedEquipment,
                                onClick = { selectedEquipment = eq },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        StepperCard(
                            label = "Duration (min)",
                            value = "${durationMinutes}m",
                            onDecrement = { if (durationMinutes > 15) durationMinutes -= 15 },
                            onIncrement = { if (durationMinutes < 60) durationMinutes += 15 },
                            modifier = Modifier.weight(1f)
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Experience", color = Color.Gray, fontSize = 11.sp)
                            experiences.forEach { exp ->
                                ChoiceChipPill(
                                    label = exp,
                                    selected = exp == selectedExperience,
                                    onClick = { selectedExperience = exp },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            onGenerateAiPlan(selectedGoal, selectedExperience, selectedEquipment, durationMinutes)
                        },
                        enabled = !isGeneratingAiPlan,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA58CF9))
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isGeneratingAiPlan) "Generating via ADK Agent…" else "Generate Plan & 4-Week Mesocycle",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            generatedPlan?.let { plan ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2538)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Generated ${plan.goal} Circuit (${plan.targetDurationMinutes}m)",
                            color = Color(0xFF6EE7B7),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        if (plan.aiCoachRationale.isNotBlank()) {
                            Text(plan.aiCoachRationale, color = Color.LightGray, fontSize = 12.sp)
                        }
                        Text(
                            "Exercises: ${plan.targetExercises.joinToString("  →  ")}",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            "Base Rest: ${plan.recommendedRestIntervalSeconds}s (auto-extended by live SmartSpectra HR)",
                            color = Color(0xFF4FCCC4),
                            fontSize = 11.sp
                        )

                        generatedMesocycle?.let { meso ->
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            Text(
                                "4-Week Mesocycle Periodization (${meso.daysPerWeek}d/wk)",
                                color = Color(0xFFA58CF9),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            meso.phases.forEach { wk ->
                                Text(
                                    "• W${wk.weekNumber}: ${wk.phaseName} — ${wk.targetVolumeSetsPerMuscle} sets @ RPE ${wk.targetIntensityRpe}",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onStartRoutine(
                                    plan.targetExercises,
                                    10,
                                    2,
                                    plan.recommendedRestIntervalSeconds
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                        ) {
                            Text(
                                "Start Full AI Routine (${plan.targetExercises.size} Exercises)",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeTabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) Color(0xFF00E676) else Color.Transparent
    ) {
        Text(
            text = title,
            color = if (selected) Color.Black else Color.LightGray,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 10.dp)
        )
    }
}

@Composable
private fun ChoiceChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFFA58CF9).copy(alpha = 0.25f) else Color(0xFF0E121B),
        border = BorderStroke(1.dp, if (selected) Color(0xFFA58CF9) else Color.White.copy(alpha = 0.1f))
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.Gray,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun StepperCard(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF192131)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = Color.Gray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrement, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Text(
                    value,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.width(46.dp),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = onIncrement, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
