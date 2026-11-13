package com.fitlens.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.fitlens.app.workout.WorkoutPhase

/**
 * Workout flow destination: pre-workout setup -> live session HUD -> summary.
 *
 * The ViewModel is activity-scoped, so workout state survives navigation
 * (e.g. backing out mid-set and returning resumes the session).
 *
 * SmartSpectra lifecycle: entering this destination (re)starts the SDK pipeline
 * — the camera analyzer only submits frames while it reports RUNNING/STARTING.
 * Leaving the destination stops it again so vitals don't drain battery in
 * the background.
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
    val vitals by viewModel.vitalsFlow.collectAsState()
    val lastSavedSession by viewModel.lastSavedSession.collectAsState()
    val isAudioMuted by viewModel.isAudioMuted.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.resetVitalsInput()
        onStartVitals()
    }
    DisposableEffect(Unit) {
        onDispose { onStopVitals() }
    }

    // Auto-completion path: WorkoutManager.completeSet() flips the phase to
    // COMPLETED without going through the ViewModel, so persist + navigate here.
    // Manual finishes go through onFinishWorkout below, which lands on the same
    // effect once the phase flips.
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

    if (state.phase == WorkoutPhase.IDLE || state.phase == WorkoutPhase.COMPLETED) {
        WorkoutSetupScreen(
            onStart = { exercise, reps, sets ->
                viewModel.startWorkout(exercise, reps, sets)
            },
            onBack = { navController.popBackStack() }
        )
    } else {
        WorkoutSessionScreen(
            state = state,
            landmarks = landmarks,
            cameraAnalyzer = viewModel.dualStreamAnalyzer,
            pulseWaveform = vitals.arterialPressureTrace,
            isAudioMuted = isAudioMuted,
            onToggleAudio = viewModel::toggleAudioMute,
            onSimulateRep = viewModel::simulateRep,
            onCompleteSet = viewModel::completeSet,
            onSkipRest = viewModel::skipRest,
            onFinishWorkout = viewModel::finishWorkout,
            onBack = { navController.popBackStack() },
            autoRequestCamera = true
        )
    }
}

/**
 * Pre-workout configuration: exercise picker (must match the dispatch names in
 * PoseLandmarkManager.analyzeLandmarks), rep/set steppers, and start button.
 */
@Composable
private fun WorkoutSetupScreen(
    onStart: (exercise: String, reps: Int, sets: Int) -> Unit,
    onBack: () -> Unit
) {
    val exercises = listOf("Bodyweight Squats", "Push-Ups", "Lunges", "Plank")
    var selectedExercise by remember { mutableStateOf(exercises.first()) }
    var targetReps by remember { mutableIntStateOf(10) }
    var totalSets by remember { mutableIntStateOf(3) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Start Workout",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Camera pose tracking + SmartSpectra vitals",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        // Exercise picker
        Text("Exercise", color = Color.LightGray, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            exercises.chunked(2).forEach { rowExercises ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowExercises.forEach { exercise ->
                        val selected = exercise == selectedExercise
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedExercise = exercise },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) Color(0xFF00E676).copy(alpha = 0.18f) else Color(0xFF1E1E1E),
                            border = androidx.compose.foundation.BorderStroke(
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
                                    Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = if (selected) Color(0xFF00E676) else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    exercise,
                                    color = if (selected) Color.White else Color.LightGray,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    // Keep the row balanced when chunked(2) yields a single item.
                    if (rowExercises.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Rep / set steppers
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepperCard(
                label = "Reps / set",
                value = "$targetReps",
                onDecrement = { if (targetReps > 5) targetReps -= 5 },
                onIncrement = { if (targetReps < 30) targetReps += 5 },
                modifier = Modifier.weight(1f)
            )
            StepperCard(
                label = "Sets",
                value = "$totalSets",
                onDecrement = { if (totalSets > 1) totalSets -= 1 },
                onIncrement = { if (totalSets < 6) totalSets += 1 },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onStart(selectedExercise, targetReps, totalSets) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
        ) {
            Text(
                "Begin $totalSets × $targetReps",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = Color.Gray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrement, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Text(
                    value,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.width(40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(onClick = onIncrement, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
