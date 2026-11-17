package com.fitlens.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlens.app.data.profile.UserProfile
import com.fitlens.app.testing.SmokeTestGateResult
import kotlin.math.roundToInt

private val SHEET_BG = Color(0xFF09090B)
private val SHEET_CARD = Color(0xFF121216)
private val SHEET_ELEVATED = Color(0xFF1A1A20)
private val SHEET_BORDER = Color(0xFF27272A)
private val SHEET_LIME = Color(0xFFA3E635)
private val SHEET_EMERALD = Color(0xFF10B981)
private val SHEET_ROSE = Color(0xFFF43F5E)
private val SHEET_WHITE = Color(0xFFFAFAFA)
private val SHEET_ZINC = Color(0xFFA1A1AA)

@Composable
fun SensingConsentDialog(
    initialTelemetryEnabled: Boolean,
    initialPreferLocalEdgeAi: Boolean,
    onAccept: (telemetryEnabled: Boolean, preferLocalEdgeAi: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var telemetryEnabled by remember { mutableStateOf(initialTelemetryEnabled) }
    var preferLocalEdgeAi by remember { mutableStateOf(initialPreferLocalEdgeAi) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SHEET_CARD,
        titleContentColor = SHEET_WHITE,
        textContentColor = SHEET_ZINC,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = SHEET_LIME
                )
                Text(
                    text = "Camera & Vitals Consent",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ConsentBullet(
                    title = "Contactless Vitals & Pose",
                    body = "Uses the camera while active to measure pulse, breathing, HRV, and exercise reps on-device."
                )
                ConsentBullet(
                    title = "Local Storage",
                    body = "Workout history stays on your device and can be erased anytime."
                )

                Surface(
                    color = SHEET_ELEVATED,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SHEET_BORDER)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "SDK Diagnostic Telemetry",
                                    color = SHEET_WHITE,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Anonymous stability stats (no video or vitals)",
                                    color = SHEET_ZINC,
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = telemetryEnabled,
                                onCheckedChange = { telemetryEnabled = it }
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = SHEET_BORDER
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Offline Edge AI Mode",
                                    color = SHEET_WHITE,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Keep AI coaching 100% on-device",
                                    color = SHEET_ZINC,
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = preferLocalEdgeAi,
                                onCheckedChange = { preferLocalEdgeAi = it }
                            )
                        }
                    }
                }

                Text(
                    text = "Wellness use only — not a medical device.",
                    color = SHEET_ZINC,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAccept(telemetryEnabled, preferLocalEdgeAi) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SHEET_LIME)
            ) {
                Text("Enable Sensing", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SHEET_ZINC)
            }
        }
    )
}

@Composable
private fun ConsentBullet(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = SHEET_WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(body, color = SHEET_ZINC, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileAndPrivacySheet(
    profile: UserProfile,
    smokeTestResult: SmokeTestGateResult?,
    isRunningSmokeTest: Boolean,
    onSaveProfile: (UserProfile) -> Unit,
    onRunSmokeTest: () -> Unit,
    onDeleteAllData: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(profile.name) { mutableStateOf(profile.name) }
    var age by remember(profile.age) { mutableFloatStateOf(profile.age.toFloat()) }
    var restingHr by remember(profile.restingHeartRate) { mutableFloatStateOf(profile.restingHeartRate.toFloat()) }
    var maxHr by remember(profile.maxHeartRate) { mutableFloatStateOf(profile.maxHeartRate.toFloat()) }
    var telemetryEnabled by remember(profile.smartSpectraTelemetryEnabled) {
        mutableStateOf(profile.smartSpectraTelemetryEnabled)
    }
    var preferLocalEdgeAi by remember(profile.preferLocalEdgeAi) {
        mutableStateOf(profile.preferLocalEdgeAi)
    }
    var consentAccepted by remember(profile.hasAcceptedSensingConsent) {
        mutableStateOf(profile.hasAcceptedSensingConsent)
    }
    var confirmDelete by remember { mutableStateOf(false) }

    val previewProfile = profile.copy(
        name = name.ifBlank { "Athlete" },
        age = age.roundToInt(),
        restingHeartRate = restingHr.toDouble(),
        maxHeartRate = maxOf((restingHr + 30f).toDouble(), maxHr.toDouble()),
        hasAcceptedSensingConsent = consentAccepted,
        smartSpectraTelemetryEnabled = telemetryEnabled,
        preferLocalEdgeAi = preferLocalEdgeAi
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SHEET_BG,
        contentColor = SHEET_WHITE
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Profile & Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = SHEET_ZINC)
                }
            }

            Surface(
                color = SHEET_CARD,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SHEET_BORDER)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Age: ${age.roundToInt()} yrs", fontSize = 12.sp, color = SHEET_ZINC)
                    Slider(
                        value = age,
                        onValueChange = {
                            age = it
                            maxHr = (208f - 0.7f * it.roundToInt()).coerceIn(130f, 210f)
                        },
                        valueRange = 18f..80f
                    )

                    Text("Resting HR: ${restingHr.roundToInt()} bpm • Max HR: ${maxHr.roundToInt()} bpm", fontSize = 12.sp, color = SHEET_ZINC)
                    Slider(
                        value = restingHr,
                        onValueChange = { restingHr = it },
                        valueRange = 42f..100f
                    )
                }
            }

            Surface(
                color = SHEET_CARD,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SHEET_BORDER)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Camera Sensing Consent", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Switch(checked = consentAccepted, onCheckedChange = { consentAccepted = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SDK Diagnostic Telemetry", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Switch(checked = telemetryEnabled, onCheckedChange = { telemetryEnabled = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Offline Edge AI Mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Switch(checked = preferLocalEdgeAi, onCheckedChange = { preferLocalEdgeAi = it })
                    }
                }
            }

            Surface(
                color = SHEET_CARD,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SHEET_BORDER)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRunSmokeTest,
                        enabled = !isRunningSmokeTest,
                        border = BorderStroke(1.dp, SHEET_BORDER),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isRunningSmokeTest) "Verifying Pipeline…" else "Run Pipeline Smoke Test",
                            color = SHEET_LIME,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    smokeTestResult?.let { res ->
                        Text(
                            text = res.message,
                            fontSize = 11.sp,
                            color = if (res.presageGatePassed && res.mediaPipeGatePassed) SHEET_EMERALD else SHEET_ROSE
                        )
                    }

                    if (!confirmDelete) {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            border = BorderStroke(1.dp, SHEET_ROSE.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = SHEET_ROSE, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Local Data", color = SHEET_ROSE)
                        }
                    } else {
                        Button(
                            onClick = {
                                onDeleteAllData()
                                confirmDelete = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SHEET_ROSE),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Confirm Delete All Data", color = SHEET_WHITE, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onSaveProfile(previewProfile)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SHEET_LIME)
            ) {
                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
