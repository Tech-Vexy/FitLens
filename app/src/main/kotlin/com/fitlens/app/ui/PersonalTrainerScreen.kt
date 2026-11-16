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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.fitlens.app.ai.live.AssistantMessage
import com.fitlens.app.ai.live.LiveAssistantState
import com.fitlens.app.ai.live.LiveConnectionState
import com.fitlens.app.ai.live.MoodSpeculation
import com.fitlens.app.ai.live.MoodSpeculator
import com.fitlens.app.camera.CameraPreviewView
import com.fitlens.app.camera.DualStreamAnalyzer
import com.fitlens.app.data.profile.UserProfile
import com.fitlens.app.vitals.VitalsReading
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
private val GLASS_DARK = Color(0xD9121826)
private val BG_BOT = Color(0xFF05070C)

/**
 * Face-first Personal AI Trainer & Emotional Companion screen.
 *
 * Layout keeps the upper-middle camera area completely open so the user's face
 * is never blocked by text walls or stacked metric cards, while anchoring
 * compact vitals, mood speculation, and the Gemini Live conversation at the bottom.
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
    var draftText by remember { mutableStateOf("") }
    var showWaveforms by remember { mutableStateOf(false) }
    var showBreathingGuide by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val isListening = assistantState.connectionState == LiveConnectionState.LISTENING
    val isConnecting = assistantState.connectionState == LiveConnectionState.CONNECTING

    fun totalItemCount() = assistantState.messages.size +
        (if (assistantState.streamingUserText.isNotBlank()) 1 else 0) +
        (if (assistantState.streamingAssistantText.isNotBlank()) 1 else 0) +
        (if (assistantState.isSendingText) 1 else 0)

    LaunchedEffect(assistantState.messages.size, assistantState.isSendingText) {
        val total = totalItemCount()
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    LaunchedEffect(assistantState.streamingUserText, assistantState.streamingAssistantText) {
        val total = totalItemCount()
        if (total > 0) {
            listState.scrollToItem(total - 1)
        }
    }

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
            .background(BG_BOT)
    ) {
        // Live camera feed (consent + permission gated)
        if (profile.hasAcceptedSensingConsent && hasCameraPermission) {
            CameraPreviewView(
                analyzer = dualStreamAnalyzer,
                useFrontCamera = useFrontCamera,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            TrainerConsentBackdrop(
                hasAcceptedConsent = profile.hasAcceptedSensingConsent,
                onReviewConsent = onReviewConsent,
                onGrantCamera = onGrantCamera
            )
        }

        // Subtle top/bottom gradient scrim keeping the center face area completely clear
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to BG_BOT.copy(alpha = 0.55f),
                        0.22f to Color.Transparent,
                        0.58f to Color.Transparent,
                        0.72f to BG_BOT.copy(alpha = 0.60f),
                        1f to BG_BOT.copy(alpha = 0.94f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // 1. Slim Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText = when {
                    isListening -> "Gemini Live • Listening"
                    isConnecting -> "Connecting Gemini Live…"
                    processingStatus == ProcessingStatus.RUNNING && profile.preferLocalEdgeAi -> "Sensing (Offline AI)"
                    processingStatus == ProcessingStatus.RUNNING -> "Sensing Face & Vitals"
                    processingStatus == ProcessingStatus.STARTING -> "Starting Sensing…"
                    processingStatus == ProcessingStatus.ERROR -> sdkErrorMessage ?: "Sensing Error"
                    profile.preferLocalEdgeAi -> "Offline Trainer Ready"
                    else -> "AI Trainer Ready"
                }
                val statusAccent = when {
                    isListening -> VIOLET
                    isConnecting -> AMBER
                    processingStatus == ProcessingStatus.RUNNING -> MINT
                    processingStatus == ProcessingStatus.ERROR -> CORAL
                    else -> Color.LightGray
                }

                TrainerStatusChip(
                    label = statusText,
                    accent = statusAccent,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onToggleCameraFacing,
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { showWaveforms = !showWaveforms },
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            if (showWaveforms) TEAL.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = "Toggle Waveforms",
                        tint = if (showWaveforms) TEAL else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onOpenProfile,
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ManageAccounts,
                        contentDescription = "Profile & Privacy",
                        tint = MINT,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Compact 1-line Mood Speculation Pill (never blocks the face)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMoodCheckIn() },
                shape = RoundedCornerShape(14.dp),
                color = GLASS_DARK,
                border = BorderStroke(1.dp, VIOLET.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = moodSpeculation.emoji, fontSize = 20.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = moodSpeculation.label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = moodSpeculation.incongruenceHint ?: moodSpeculation.confidenceNote,
                            color = if (moodSpeculation.incongruenceHint != null) AMBER else Color.White.copy(alpha = 0.68f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VIOLET.copy(alpha = 0.20f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = VIOLET,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                "Check In",
                                color = VIOLET,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Optional single-line framing / key hint
            val activeHint = validationHint?.takeIf { it.isNotEmpty() }
                ?: vitals.validationHint.takeIf { it.isNotEmpty() }
            if (activeHint != null) {
                Spacer(modifier = Modifier.height(4.dp))
                TrainerStatusChip(
                    label = "Framing: $activeHint",
                    accent = AMBER,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (isPlaceholderPresageKey) {
                Spacer(modifier = Modifier.height(4.dp))
                TrainerStatusChip(
                    label = "Setup: Add PRESAGE_API_KEY in secrets.properties for live vitals",
                    accent = AMBER,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Compact Single-Row Vitals Strip
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
                    label = "Pulse",
                    value = if (vitals.heartRateBpm > 0) "${vitals.heartRateBpm.roundToInt()} bpm" else "--",
                    accent = CORAL,
                    qualityDotColor = pulseQualityColor,
                    modifier = Modifier.weight(1f)
                )
                CompactVitalPill(
                    label = "Breath",
                    value = if (vitals.breathingRateBpm > 0) "${vitals.breathingRateBpm.roundToInt()} rpm" else "--",
                    accent = TEAL,
                    modifier = Modifier.weight(1f)
                )
                CompactVitalPill(
                    label = "HRV",
                    value = if (vitals.heartRateVariabilityMs > 0) "${vitals.heartRateVariabilityMs.roundToInt()} ms" else "--",
                    accent = MINT,
                    modifier = Modifier.weight(1f)
                )
                CompactVitalPill(
                    label = "Face",
                    value = vitals.facialExpression ?: "--",
                    accent = AMBER,
                    modifier = Modifier.weight(1f)
                )
            }

            // Optional expandable waveforms drawer (collapsed by default so face stays clear)
            AnimatedVisibility(visible = showWaveforms) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactWaveformCard(
                        label = "Pulse Wave",
                        samples = vitals.arterialPressureTrace,
                        lineColor = VIOLET,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactWaveformCard(
                            label = "Chest",
                            samples = vitals.chestBreathingTrace,
                            lineColor = TEAL,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        )
                        CompactWaveformCard(
                            label = "Abdomen",
                            samples = vitals.abdomenBreathingTrace,
                            lineColor = Color(0xFF60A5FA),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        )
                    }
                }
            }

            // Guided 4-2-6 Breathing Reset Pacer (toggleable)
            AnimatedVisibility(visible = showBreathingGuide) {
                GuidedBreathingCard(
                    vitals = vitals,
                    onClose = { showBreathingGuide = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
            }

            // Unobstructed Face Zone: pushes chat cleanly to the bottom of the screen
            Spacer(modifier = Modifier.weight(1f))

            // 4. Bottom-Anchored Scrollable Chat & Live Transcript Feed (capped height so face is never covered)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp, max = 210.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
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
                                text = "Thinking…"
                            )
                        )
                    }
                }
            }

            assistantState.errorMessage?.let { err ->
                Text(
                    text = err,
                    color = CORAL,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Quick conversation prompts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickPromptChip("How am I feeling?") { onMoodCheckIn() }
                QuickPromptChip(
                    label = if (showBreathingGuide) "Hide Breathing" else "4-2-6 Breathing"
                ) {
                    showBreathingGuide = !showBreathingGuide
                }
                QuickPromptChip("Check stress & energy") {
                    onSendMessage("What do my heart rate, HRV, and expression say about my stress or energy?")
                }
                QuickPromptChip("Help me unwind") {
                    showBreathingGuide = true
                    onSendMessage("Can you guide me through a quick calming reset?")
                }
                IconButton(
                    onClick = onClearChat,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "New Conversation",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 5. Text Input + Voice Live & Sensing Controls
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
                            "Message your AI trainer…",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 13.sp
                        )
                    },
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = GLASS_DARK,
                        unfocusedContainerColor = GLASS_DARK,
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
                        .size(46.dp)
                        .background(
                            if (draftText.isNotBlank()) VIOLET else Color.White.copy(alpha = 0.10f),
                            CircleShape
                        )
                ) {
                    if (assistantState.isSendingText) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (draftText.isNotBlank()) Color.Black else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val pulseTransition = rememberInfiniteTransition(label = "live_pulse")
                val pulseAlpha by pulseTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (isListening) 0.72f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "live_alpha"
                )

                Button(
                    onClick = onToggleLiveVoice,
                    enabled = !isConnecting,
                    modifier = Modifier
                        .weight(1.25f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
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
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isListening -> "Stop Live Voice"
                            isConnecting -> "Connecting…"
                            else -> "Talk Live"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                }

                Button(
                    onClick = onStartOrStopSensing,
                    enabled = processingStatus != ProcessingStatus.STARTING &&
                        processingStatus != ProcessingStatus.STOPPING,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (processingStatus) {
                            ProcessingStatus.RUNNING -> Color(0xFF1F293D)
                            else -> MINT
                        }
                    ),
                    border = if (processingStatus == ProcessingStatus.RUNNING) {
                        BorderStroke(1.dp, CORAL.copy(alpha = 0.7f))
                    } else null
                ) {
                    Text(
                        text = when {
                            processingStatus == ProcessingStatus.RUNNING -> "Stop Sensing"
                            processingStatus == ProcessingStatus.STARTING -> "Starting…"
                            processingStatus == ProcessingStatus.STOPPING -> "Stopping…"
                            !profile.hasAcceptedSensingConsent -> "Consent & Sense"
                            else -> "Start Sensing"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (processingStatus == ProcessingStatus.RUNNING) CORAL else Color.Black,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Personal AI Trainer • On-device vitals • Not a medical device",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ChatBubble(message: AssistantMessage) {
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
            color = if (message.isUser) {
                VIOLET.copy(alpha = 0.28f)
            } else {
                GLASS_DARK
            },
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
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(Date(message.timestampMs)),
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
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Text(
            text = label,
            color = Color(0xFFE2E8F0),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun CompactVitalPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    qualityDotColor: Color? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = GLASS_DARK
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
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
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TrainerStatusChip(label: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TrainerConsentBackdrop(
    hasAcceptedConsent: Boolean,
    onReviewConsent: () -> Unit,
    onGrantCamera: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1020)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .offset(y = (-60).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (hasAcceptedConsent) Icons.Default.Videocam else Icons.Default.Shield,
                contentDescription = null,
                tint = MINT,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = if (!hasAcceptedConsent)
                    "Consent-First Personal Sensing"
                else
                    "Camera Permission Needed",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = if (!hasAcceptedConsent)
                    "Camera remains off until you review data handling and enable on-device facial & vitals sensing."
                else
                    "Grant camera permission so your AI trainer can read facial cues, pulse, and breathing.",
                color = Color.LightGray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            OutlinedButton(
                onClick = if (!hasAcceptedConsent) onReviewConsent else onGrantCamera,
                border = BorderStroke(1.dp, MINT)
            ) {
                Text(
                    if (!hasAcceptedConsent) "Review Consent & Privacy" else "Grant Camera Access",
                    color = MINT,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
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
        shape = RoundedCornerShape(12.dp),
        color = GLASS_DARK
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(label, fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (samples.size < 2) return@Canvas
                val w = size.width
                val h = size.height
                val inset = 4f
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
                    color = lineColor.copy(alpha = 0.88f),
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

@Composable
private fun GuidedBreathingCard(
    vitals: VitalsReading,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val tickMs = 50L
        while (true) {
            delay(tickMs)
            elapsedMs = (elapsedMs + tickMs) % 12_000L
        }
    }

    val (phaseLabel, phaseProgress, phaseColor) = when {
        elapsedMs < 4_000L -> Triple("Inhale slowly", elapsedMs / 4_000f, TEAL)
        elapsedMs < 6_000L -> Triple("Hold gently", (elapsedMs - 4_000L) / 2_000f, VIOLET)
        else -> Triple("Exhale & soften", (elapsedMs - 6_000L) / 6_000f, MINT)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = GLASS_DARK,
        border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "4-2-6 Calm Reset • $phaseLabel",
                    color = phaseColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (vitals.breathingRateBpm > 3.0) {
                        Text(
                            text = "${vitals.breathingRateBpm.roundToInt()} rpm",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "Close",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        modifier = Modifier.clickable(onClick = onClose)
                    )
                }
            }
            LinearProgressIndicator(
                progress = { phaseProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = phaseColor,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
        }
    }
}
