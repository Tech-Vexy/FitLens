package com.fitlens.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlens.app.data.profile.UserProfile
import com.fitlens.app.testing.SmokeTestGateResult
import kotlin.math.roundToInt

/**
 * Informed Consent Modal required before camera & contactless physiological sensing starts.
 * Covers camera usage, local-only storage, retention/deletion, SDK diagnostic telemetry opt-out,
 * optional non-biometric AI usage, and the non-medical wellness disclaimer.
 */
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
        containerColor = Color(0xFF151B28),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFD5DCE6),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = Color(0xFF6EE7B7)
                )
                Text(
                    text = "Camera & Biometric Sensing Consent",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ConsentBullet(
                    title = "Live On-Device Camera Signals",
                    body = "FitLens AI uses your camera only while active on screen to compute pulse, breathing rate, HRV, and facial expressions via the Presage SmartSpectra SDK. Raw video frames stay in volatile memory unless you start a Gemini Live voice/video session."
                )
                ConsentBullet(
                    title = "100% Local Vault, Export & Deletion",
                    body = "Local records are saved strictly on your device in local SQLite (fitlens_health_sqlite.db). You can permanently delete all local data anytime in Profile & Privacy."
                )
                ConsentBullet(
                    title = "Presage SDK Gating & Telemetry",
                    body = "SmartSpectra connects to Presage to authenticate the API key and optionally reports aggregate per-session stability diagnostics (containing zero video frames, zero vital values, and zero user/device IDs). You can opt out below."
                )
                ConsentBullet(
                    title = "Gemini Live Personal AI Trainer",
                    body = "When you tap Talk Live or send a message, your personal AI trainer uses Firebase AI Logic (Gemini Live API) with your microphone and current mood/vitals context to converse with you, or falls back to local on-device responses if offline mode is enabled."
                )

                Surface(
                    color = Color(0xFF1F293D),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "SmartSpectra Diagnostic Telemetry",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Aggregate SDK quality stats only (no vitals/frames)",
                                    color = Color.Gray,
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
                            color = Color.White.copy(alpha = 0.08f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "100% Offline On-Device Edge AI",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Disable Firebase Cloud AI (use local assistant responses)",
                                    color = Color.Gray,
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

                Surface(
                    color = Color(0xFFFBBF24).copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Personal AI Trainer & Wellness Only: FitLens AI is not a medical device and does not diagnose, treat, prevent disease, or support medical decision-making.",
                        color = Color(0xFFFDE68A),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAccept(telemetryEnabled, preferLocalEdgeAi) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6EE7B7))
            ) {
                Text("Agree & Enable Sensing", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now", color = Color.LightGray)
            }
        }
    )
}

@Composable
private fun ConsentBullet(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color(0xFF6EE7B7), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(body, color = Color(0xFFD5DCE6), fontSize = 11.sp, lineHeight = 15.sp)
    }
}

/**
 * Athlete Profile, Karvonen HR Zones, Privacy Controls, Hard Gate Diagnostics & Data Deletion Sheet.
 */
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
    var dataDeletedBanner by remember { mutableStateOf(false) }

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
        containerColor = Color(0xFF131926),
        contentColor = Color.White
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
                Column {
                    Text("Profile, Zones & Privacy", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Personalized Karvonen HRR & Responsible Data Controls",
                        fontSize = 11.sp,
                        color = Color(0xFF6EE7B7)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            // Athlete physiology sliders
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2436)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        label = { Text("Athlete Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "Age: ${age.roundToInt()} yrs  •  Tanaka Est. Max: ${(208 - 0.7 * age.roundToInt()).roundToInt()} bpm",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Slider(
                        value = age,
                        onValueChange = {
                            age = it
                            maxHr = (208f - 0.7f * it.roundToInt()).coerceIn(130f, 210f)
                        },
                        valueRange = 18f..80f
                    )

                    Text("Resting Heart Rate: ${restingHr.roundToInt()} bpm", fontSize = 12.sp, color = Color.LightGray)
                    Slider(
                        value = restingHr,
                        onValueChange = { restingHr = it },
                        valueRange = 42f..100f
                    )

                    Text("Max Heart Rate: ${maxHr.roundToInt()} bpm", fontSize = 12.sp, color = Color.LightGray)
                    Slider(
                        value = maxHr,
                        onValueChange = { maxHr = it },
                        valueRange = 130f..210f
                    )

                    // Live Karvonen Zones Preview
                    Text(
                        "Personalized Karvonen HR Zones",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA58CF9)
                    )
                    previewProfile.getKarvonenZoneThresholds().forEach { (zone, range) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(zone.label, fontSize = 11.sp, color = Color(zone.colorHex))
                            Text("${range.first}–${range.last} bpm", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }

            // Privacy, Consent & SDK Telemetry Controls
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2436)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Consent, Telemetry & AI Routing", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Camera & Human-Sensing Consent", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Required before live camera vitals/pose capture", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(checked = consentAccepted, onCheckedChange = { consentAccepted = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SmartSpectra Aggregate Telemetry", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Opt-out SDK release stability diagnostics (zero vitals/frames)", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(checked = telemetryEnabled, onCheckedChange = { telemetryEnabled = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("100% On-Device Edge AI Mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Keep even workout plan generation offline via LocalEdgeEngine", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(checked = preferLocalEdgeAi, onCheckedChange = { preferLocalEdgeAi = it })
                    }
                }
            }

            // Headless Day 1 Hard Gate Smoke Test Runner (for Judges & CI verification)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2436)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Automated Pipeline Verification", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Verifies headless SmartSpectra vitals + MediaPipe 33-landmark pipeline without requiring live lighting.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    OutlinedButton(
                        onClick = onRunSmokeTest,
                        enabled = !isRunningSmokeTest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isRunningSmokeTest) "Running 30-Frame Verification…" else "Run Day 1 Hard Gate Smoke Test",
                            color = Color(0xFF4FCCC4),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    smokeTestResult?.let { res ->
                        Surface(
                            color = if (res.presageGatePassed && res.mediaPipeGatePassed)
                                Color(0xFF00E676).copy(alpha = 0.16f)
                            else Color(0xFFFF6B6B).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = res.message,
                                fontSize = 11.sp,
                                color = if (res.presageGatePassed && res.mediaPipeGatePassed) Color(0xFF6EE7B7) else Color(0xFFFF6B6B),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // Local Vault Data Deletion
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF281822)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Local Health Data Deletion", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFFF8A80))
                    Text(
                        "Permanently erases all workout sessions, rPPG vitals logs, and biomechanical fault records from fitlens_health_sqlite.db.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    if (!confirmDelete) {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            border = BorderStroke(1.dp, Color(0xFFFF6B6B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFFF6B6B))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete All Local Vault Data", color = Color(0xFFFF6B6B))
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onDeleteAllData()
                                    confirmDelete = false
                                    dataDeletedBanner = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Confirm Erase", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { confirmDelete = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel", color = Color.White)
                            }
                        }
                    }
                    if (dataDeletedBanner) {
                        Text(
                            "All local SQLite sessions, vitals, and fault records have been permanently deleted.",
                            fontSize = 11.sp,
                            color = Color(0xFF6EE7B7)
                        )
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
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6EE7B7))
            ) {
                Text("Save Profile & Privacy Preferences", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
