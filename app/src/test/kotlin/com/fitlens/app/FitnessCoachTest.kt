package com.fitlens.app

import com.fitlens.app.ai.FitnessCoachAgent
import com.fitlens.app.ai.FitnessTools
import com.fitlens.app.ai.adk.AdkAuthMode
import com.fitlens.app.data.model.WorkoutSessionEntity
import com.fitlens.app.data.profile.UserProfile
import com.fitlens.app.pose.DeviationSeverity
import com.fitlens.app.pose.LandmarkPoint
import com.fitlens.app.pose.PoseLandmarkManager
import com.fitlens.app.vitals.HeartRateZone
import com.fitlens.app.vitals.PresageFrameAnalyzer
import com.fitlens.app.workout.ProgressAnalyzer
import com.fitlens.app.workout.WorkoutManager
import com.fitlens.app.workout.WorkoutPhase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FitnessCoachTest {

    @Test
    fun testGenerateWorkoutPlanForHypertrophy() {
        val tools = FitnessTools()
        val plan = tools.generateWorkoutPlan(
            goal = "Hypertrophy & Strength",
            experience = "intermediate",
            equipment = "Dumbbells",
            durationMinutes = 45
        )

        assertEquals("Hypertrophy & Strength", plan.goal)
        assertEquals(45, plan.targetDurationMinutes)
        assertEquals(75, plan.recommendedRestIntervalSeconds)
        assertTrue(plan.targetExercises.contains("Barbell Squat"))
        assertTrue(plan.aiCoachRationale.isNotBlank())
    }

    @Test
    fun testGenerateWorkoutPlanForEndurance() {
        val tools = FitnessTools()
        val plan = tools.generateWorkoutPlan(
            goal = "Cardio & Endurance",
            experience = "advanced",
            equipment = "Bodyweight",
            durationMinutes = 30
        )

        assertEquals(30, plan.targetDurationMinutes)
        assertEquals(45, plan.recommendedRestIntervalSeconds)
        assertTrue(plan.targetExercises.contains("Burpees"))
    }

    @Test
    fun testAdaptiveRestCalculation() {
        val tools = FitnessTools()

        val elevatedRest = tools.calculateAdaptiveRest(
            baseRestSeconds = 45,
            currentHeartRateBpm = 142.0,
            restingHeartRateBpm = 70.0
        )
        assertTrue(elevatedRest.isExtended)
        assertEquals(75, elevatedRest.adjustedRestSeconds)
        assertTrue(elevatedRest.reason.contains("Extending rest by 30s"))

        val normalRest = tools.calculateAdaptiveRest(
            baseRestSeconds = 45,
            currentHeartRateBpm = 88.0,
            restingHeartRateBpm = 70.0
        )
        assertFalse(normalRest.isExtended)
        assertEquals(45, normalRest.adjustedRestSeconds)
    }

    @Test
    fun testBiomechanicalFatigueEvaluation() {
        val tools = FitnessTools()

        val criticalCheck = tools.evaluateBiomechanicalFatigue(
            consecutiveDeviations = 3,
            currentSetRepsCompleted = 7,
            targetReps = 10
        )
        assertTrue(criticalCheck.shouldHaltSet)
        assertTrue(criticalCheck.correctiveAction.contains("Stop set immediately"))

        val cleanCheck = tools.evaluateBiomechanicalFatigue(
            consecutiveDeviations = 0,
            currentSetRepsCompleted = 5,
            targetReps = 10
        )
        assertFalse(cleanCheck.shouldHaltSet)
    }

    @Test
    fun testPoseAngleCalculation() {
        val manager = PoseLandmarkManager()

        val a = LandmarkPoint(0f, 1f, 0f)
        val b = LandmarkPoint(0f, 0f, 0f)
        val c = LandmarkPoint(1f, 0f, 0f)

        val angle = manager.calculateAngle(a, b, c)
        assertEquals(90.0, angle, 0.5)

        val d = LandmarkPoint(0f, -1f, 0f)
        val straightAngle = manager.calculateAngle(a, b, d)
        assertEquals(180.0, straightAngle, 0.5)
    }

    @Test
    fun testKneeValgusDetection() {
        val manager = PoseLandmarkManager()
        manager.setExercise("Squats")

        val valgusLandmarks = mapOf(
            PoseLandmarkManager.LEFT_HIP to LandmarkPoint(0.40f, 0.50f, 0f),
            PoseLandmarkManager.LEFT_KNEE to LandmarkPoint(0.48f, 0.70f, 0f),
            PoseLandmarkManager.LEFT_ANKLE to LandmarkPoint(0.35f, 0.80f, 0f),
            PoseLandmarkManager.RIGHT_HIP to LandmarkPoint(0.60f, 0.50f, 0f),
            PoseLandmarkManager.RIGHT_KNEE to LandmarkPoint(0.52f, 0.70f, 0f),
            PoseLandmarkManager.RIGHT_ANKLE to LandmarkPoint(0.65f, 0.80f, 0f)
        )

        val frame = manager.analyzeLandmarks(valgusLandmarks)
        val valgusDeviations = frame.activeDeviations.filter { it.jointName == "Knees" }
        assertEquals(1, valgusDeviations.size)
        assertEquals(DeviationSeverity.CRITICAL, valgusDeviations.first().severity)
    }

    @Test
    fun testPlankSaggingAndPikingDetection() {
        val manager = PoseLandmarkManager()
        manager.setExercise("Plank")

        // Hips sagging below shoulder-ankle midpoint: CRITICAL
        val saggingPlankLandmarks = mapOf(
            PoseLandmarkManager.LEFT_SHOULDER to LandmarkPoint(0.20f, 0.40f, 0f),
            PoseLandmarkManager.LEFT_HIP to LandmarkPoint(0.50f, 0.65f, 0f),
            PoseLandmarkManager.LEFT_ANKLE to LandmarkPoint(0.85f, 0.45f, 0f)
        )

        val sagFrame = manager.analyzeLandmarks(saggingPlankLandmarks)
        val hipDeviations = sagFrame.activeDeviations.filter { it.jointName == "Hips" }
        assertEquals(1, hipDeviations.size)
        assertEquals(DeviationSeverity.CRITICAL, hipDeviations.first().severity)
        assertTrue(hipDeviations.first().message.contains("Hips sagging"))

        // Hips piked high above shoulder-ankle midpoint: WARNING
        val pikedPlankLandmarks = mapOf(
            PoseLandmarkManager.LEFT_SHOULDER to LandmarkPoint(0.20f, 0.55f, 0f),
            PoseLandmarkManager.LEFT_HIP to LandmarkPoint(0.50f, 0.28f, 0f),
            PoseLandmarkManager.LEFT_ANKLE to LandmarkPoint(0.85f, 0.55f, 0f)
        )
        val pikeFrame = manager.analyzeLandmarks(pikedPlankLandmarks)
        val pikeDeviations = pikeFrame.activeDeviations.filter { it.jointName == "Hips" }
        assertEquals(1, pikeDeviations.size)
        assertEquals(DeviationSeverity.WARNING, pikeDeviations.first().severity)
        assertTrue(pikeDeviations.first().message.contains("piked"))
    }

    @Test
    fun testLungeExcessiveFlexionDetection() {
        val manager = PoseLandmarkManager()
        manager.setExercise("Lunges")

        val deepLungeLandmarks = mapOf(
            PoseLandmarkManager.LEFT_HIP to LandmarkPoint(0.30f, 0.55f, 0f),
            PoseLandmarkManager.LEFT_KNEE to LandmarkPoint(0.55f, 0.55f, 0f),
            PoseLandmarkManager.LEFT_ANKLE to LandmarkPoint(0.45f, 0.75f, 0f),
            PoseLandmarkManager.RIGHT_HIP to LandmarkPoint(0.60f, 0.40f, 0f),
            PoseLandmarkManager.RIGHT_KNEE to LandmarkPoint(0.70f, 0.80f, 0f),
            PoseLandmarkManager.RIGHT_ANKLE to LandmarkPoint(0.85f, 0.90f, 0f)
        )

        val frame = manager.analyzeLandmarks(deepLungeLandmarks)
        assertNotNull(frame)
        assertTrue(frame.currentAngle < 100.0)
    }

    @Test
    fun testProgressAnalyzerReadinessAndOverload() {
        val analyzer = ProgressAnalyzer()

        val highQualitySessions = listOf(
            WorkoutSessionEntity(
                exerciseName = "Bodyweight Squats",
                targetReps = 30,
                completedReps = 30,
                durationSeconds = 600,
                averageHeartRate = 138.0,
                maxHeartRate = 158.0,
                formAccuracyPercentage = 94.0f,
                totalFormDeviations = 1,
                extendedRestSeconds = 0,
                coachFeedback = "Great stability"
            )
        )

        val insight = analyzer.analyzeProgression(highQualitySessions)
        assertTrue(insight.overallReadinessScore >= 90)
        assertEquals(2, insight.recommendedRepsDelta)
        assertEquals(-10, insight.recommendedRestAdjustmentSeconds)
        assertTrue(insight.title.contains("Progressive Overload"))
    }

    @Test
    fun testPresageHeartRateZones() {
        val analyzer = PresageFrameAnalyzer()

        analyzer.updateVitalsReading(hr = 85.0, hrv = 60.0, breathingRate = 14.0, confidence = 0.9f)
        assertEquals(HeartRateZone.RESTING, analyzer.vitalsFlow.value.heartRateZone)

        analyzer.updateVitalsReading(hr = 135.0, hrv = 45.0, breathingRate = 18.0, confidence = 0.9f)
        assertEquals(HeartRateZone.WARMUP, analyzer.vitalsFlow.value.heartRateZone)

        analyzer.updateVitalsReading(hr = 155.0, hrv = 30.0, breathingRate = 24.0, confidence = 0.9f)
        assertEquals(HeartRateZone.AEROBIC, analyzer.vitalsFlow.value.heartRateZone)

        analyzer.updateVitalsReading(hr = 170.0, hrv = 20.0, breathingRate = 30.0, confidence = 0.9f)
        assertEquals(HeartRateZone.ANAEROBIC, analyzer.vitalsFlow.value.heartRateZone)

        analyzer.updateVitalsReading(hr = 182.0, hrv = 15.0, breathingRate = 34.0, confidence = 0.9f)
        assertEquals(HeartRateZone.MAX, analyzer.vitalsFlow.value.heartRateZone)
    }

    @Test
    fun testWorkoutManagerLifecycle() = runBlocking {
        val manager = WorkoutManager()
        manager.startWorkout(exercise = "Bodyweight Squats", targetReps = 10, totalSets = 2)

        assertEquals(WorkoutPhase.ACTIVE_SET, manager.state.value.phase)
        assertEquals(1, manager.state.value.currentSet)

        manager.completeSet()
        assertEquals(WorkoutPhase.REST_INTERVAL, manager.state.value.phase)

        manager.skipRest()
        assertEquals(WorkoutPhase.ACTIVE_SET, manager.state.value.phase)
        assertEquals(2, manager.state.value.currentSet)

        val finishedSession = manager.finishWorkout()
        assertEquals("Bodyweight Squats", finishedSession.exerciseName)
        assertEquals(WorkoutPhase.COMPLETED, manager.state.value.phase)
        manager.cleanup()
    }

    @Test
    fun testSmartSpectraHeadlessConfigurationAndInsights() {
        val analyzer = PresageFrameAnalyzer()

        analyzer.simulateExerciseExertion(0.85f)
        val reading = analyzer.vitalsFlow.value

        assertTrue(reading.heartRateBpm > 135.0)
        assertTrue(reading.isReliable)
        assertNotNull(reading.smartSpectraLlmInsight)
        assertTrue(reading.smartSpectraLlmInsight!!.contains("SmartSpectra"))
        assertEquals("Strain", reading.facialExpression)
        assertTrue(reading.chestBreathingTrace.isNotEmpty())
        assertEquals(HeartRateZone.ANAEROBIC, reading.heartRateZone)
    }

    @Test
    fun testPersonalizedKarvonenHeartRateZones() {
        val profile = UserProfile(
            age = 30,
            restingHeartRate = 60.0,
            maxHeartRate = 190.0
        )

        assertEquals(HeartRateZone.RESTING, profile.getHeartRateZone(80.0))
        assertEquals(HeartRateZone.WARMUP, profile.getHeartRateZone(135.0))
        assertEquals(HeartRateZone.AEROBIC, profile.getHeartRateZone(155.0))
        assertEquals(HeartRateZone.ANAEROBIC, profile.getHeartRateZone(172.0))
        assertEquals(HeartRateZone.MAX, profile.getHeartRateZone(185.0))

        // Verify consent-first default & Karvonen threshold table
        assertFalse(profile.hasAcceptedSensingConsent)
        val thresholds = profile.getKarvonenZoneThresholds()
        assertEquals(5, thresholds.size)
        assertEquals(HeartRateZone.RESTING, thresholds.first().first)
        assertEquals(HeartRateZone.MAX, thresholds.last().first)
    }

    @Test
    fun testHybridAgentRouting() = runBlocking {
        val agent = FitnessCoachAgent(
            initialAuthMode = AdkAuthMode.ON_DEVICE_EDGE
        )

        val edgeRest = agent.calculateAdaptiveRest(
            baseRestSeconds = 45,
            currentHeartRateBpm = 145.0,
            preferLocal = true
        )
        assertTrue(edgeRest.isExtended)
        assertEquals(75, edgeRest.adjustedRestSeconds)

        val cloudPlan = agent.requestWorkoutPlan(
            goal = "Mobility",
            experience = "intermediate",
            equipment = "Bodyweight",
            durationMinutes = 25
        )
        assertNotNull(cloudPlan)
        assertEquals(25, cloudPlan.targetDurationMinutes)
        assertTrue(cloudPlan.aiCoachRationale.isNotBlank())
    }

    @Test
    fun testAdkAgentWithFirebaseAiLogicAndModeSwitch() = runBlocking {
        val firebaseAgent = FitnessCoachAgent(
            initialAuthMode = AdkAuthMode.FIREBASE_AI_LOGIC
        )

        assertEquals("fitness_coach", firebaseAgent.rootAgent.name)
        assertEquals(AdkAuthMode.FIREBASE_AI_LOGIC, firebaseAgent.rootAgent.model.authMode)
        assertEquals("gemini-3.8-flash", firebaseAgent.rootAgent.model.modelName)
        assertTrue(firebaseAgent.rootAgent.model is com.fitlens.app.ai.adk.FirebaseGeminiAdapter)

        // Switch dynamically to On-Device Edge
        firebaseAgent.setAuthMode(AdkAuthMode.ON_DEVICE_EDGE)
        assertEquals(AdkAuthMode.ON_DEVICE_EDGE, firebaseAgent.rootAgent.model.authMode)
    }

    @Test
    fun testAdkToolAnnotationsPresent() {
        val toolsClass = FitnessTools::class.java
        val methodNamesWithTool = toolsClass.methods
            .filter { it.isAnnotationPresent(com.fitlens.app.ai.adk.Tool::class.java) }
            .map { it.name }

        assertTrue(methodNamesWithTool.contains("generateWorkoutPlan"))
        assertTrue(methodNamesWithTool.contains("analyzeSession"))
        assertTrue(methodNamesWithTool.contains("calculateAdaptiveRest"))
        assertTrue(methodNamesWithTool.contains("evaluateBiomechanicalFatigue"))
        assertTrue(methodNamesWithTool.contains("evaluateHolisticBiofeedback"))
    }

    @Test
    fun testLocalSqliteHealthDataPrivacyGuarantee() {
        val isLocalOnly = com.fitlens.app.data.privacy.LocalHealthDataPrivacyEnforcer.verifyLocalOnlyStorage()
        assertTrue("Health vitals must be confined strictly to local SQLite", isLocalOnly)
    }

    @Test
    fun testLocalVitalsLogEntityCreation() {
        val vitalsEntity = com.fitlens.app.data.model.VitalsLogEntity(
            sessionId = 1L,
            heartRateBpm = 142.5,
            heartRateVariabilityMs = 45.0,
            breathingRateBpm = 22.0,
            heartRateZone = "Aerobic",
            signalConfidence = 0.94f
        )
        assertEquals(1L, vitalsEntity.sessionId)
        assertEquals(142.5, vitalsEntity.heartRateBpm, 0.1)
        assertEquals("Aerobic", vitalsEntity.heartRateZone)
        assertTrue(vitalsEntity.signalConfidence > 0.90f)
    }

    @Test
    fun testLocalBiomechanicalFaultEntityCreation() {
        val faultEntity = com.fitlens.app.data.model.BiomechanicalFaultEntity(
            sessionId = 1L,
            exerciseName = "Bodyweight Squats",
            jointName = "Knees",
            jointAngleDegrees = 92.0,
            faultMessage = "Knees caving inward (valgus)",
            severity = "CRITICAL"
        )
        assertEquals(1L, faultEntity.sessionId)
        assertEquals("Knees", faultEntity.jointName)
        assertEquals("CRITICAL", faultEntity.severity)
    }

    @Test
    fun testLocalHealthDataExportFormat() {
        val rootJson = org.json.JSONObject()
        rootJson.put("exportTimestamp", System.currentTimeMillis())
        rootJson.put("exportSource", "FitLens Local SQLite Health Vault")
        rootJson.put("privacyStandard", "100% On-Device Storage - Zero Cloud Sync")

        val sessionArray = org.json.JSONArray()
        val s = org.json.JSONObject().apply {
            put("id", 1L)
            put("exerciseName", "Bodyweight Squats")
            put("completedReps", 30)
            put("averageHeartRateBpm", 136.0)
            put("formAccuracyPercentage", 93.0)
        }
        sessionArray.put(s)
        rootJson.put("sessions", sessionArray)

        assertEquals("FitLens Local SQLite Health Vault", rootJson.getString("exportSource"))
        assertTrue(rootJson.getString("privacyStandard").contains("Zero Cloud Sync"))
        assertEquals(1, rootJson.getJSONArray("sessions").length())
    }

    @Test
    fun testMesocyclePeriodizationPlan() = runBlocking {
        val tools = FitnessTools()
        val plan = tools.generateMesocyclePeriodization(
            goal = "Hypertrophy",
            weeksCount = 4,
            daysPerWeek = 4
        )

        assertEquals("Hypertrophy", plan.goal)
        assertEquals(4, plan.totalWeeks)
        assertEquals(4, plan.phases.size)
        assertTrue(plan.phases[0].phaseName.contains("Accumulation"))
        assertEquals(6, plan.phases[0].targetIntensityRpe)
        assertTrue(plan.phases[2].phaseName.contains("Realization"))
        assertEquals(9, plan.phases[2].targetIntensityRpe)
        assertTrue(plan.phases[3].phaseName.contains("Deload"))
        assertEquals(5, plan.phases[3].targetIntensityRpe)

        val agent = FitnessCoachAgent(
            initialAuthMode = AdkAuthMode.ON_DEVICE_EDGE
        )
        val agentPlan = agent.requestMesocyclePlan("Strength", 4, 3)
        assertNotNull(agentPlan)
        assertEquals(4, agentPlan.totalWeeks)
    }

    @Test
    fun testLocalEdgeEngineRoutesWorkoutPlan() {
        val engine = com.fitlens.app.ai.adk.LocalEdgeEngine()
        val response = engine.generate(
            toolObjects = listOf(FitnessTools()),
            prompt = "Generate a hypertrophy workout plan for 30 minutes"
        )
        assertTrue(response.contains("Workout plan"))
        assertTrue(response.contains("Hypertrophy"))
        assertTrue(response.contains("30 min"))
    }

    @Test
    fun testLocalEdgeEngineRoutesAdaptiveRest() {
        val engine = com.fitlens.app.ai.adk.LocalEdgeEngine()
        val response = engine.generate(
            toolObjects = listOf(FitnessTools()),
            prompt = "Calculate adaptive rest for 45 seconds at heart rate 155 bpm"
        )
        assertTrue(response.contains("Rest adjusted"))
        assertTrue(response.contains("75s"))
    }

    @Test
    fun testLocalEdgeEngineRoutesFatigueCheck() {
        val engine = com.fitlens.app.ai.adk.LocalEdgeEngine()
        val response = engine.generate(
            toolObjects = listOf(FitnessTools()),
            prompt = "Evaluate biomechanical fatigue with 4 consecutive deviations at rep 6 of 10"
        )
        assertTrue(response.contains("Set halted"))
    }

    @Test
    fun testLocalEdgeEngineRoutesMesocycle() {
        val engine = com.fitlens.app.ai.adk.LocalEdgeEngine()
        val response = engine.generate(
            toolObjects = listOf(FitnessTools()),
            prompt = "Generate a 6 week strength mesocycle"
        )
        assertTrue(response.contains("mesocycle"))
        assertTrue(response.contains("6-week"))
    }

    @Test
    fun testLocalEdgeEngineRoutesHolisticBiofeedback() {
        val engine = com.fitlens.app.ai.adk.LocalEdgeEngine()
        val response = engine.generate(
            toolObjects = listOf(FitnessTools()),
            prompt = "Evaluate holistic biofeedback at heart rate 162 bpm and breathing 26 brpm with strain expression"
        )
        assertTrue(response.contains("Recovery Breathing"))
        assertTrue(response.contains("unclench your jaw"))
    }

    @Test
    fun testLocalEdgeEngineFallbackOnNoMatch() {
        val engine = com.fitlens.app.ai.adk.LocalEdgeEngine()
        val response = engine.generate(
            toolObjects = listOf(FitnessTools()),
            prompt = "xyzzy plugh frobnicate"
        )
        assertTrue(response.contains("On-Device Edge"))
    }

    @Test
    fun testEdgeAdapterExecuteReturnsRealToolResult() = runBlocking {
        val agent = FitnessCoachAgent(
            initialAuthMode = AdkAuthMode.ON_DEVICE_EDGE
        )
        val response = agent.runner.execute(
            "Evaluate biomechanical fatigue with 3 consecutive deviations at rep 8 of 10"
        )
        assertFalse(response.contains("Local edge inference"))
        assertTrue(response.contains("Set halted") || response.contains("Form check"))
    }

    // ------------------------------------------------------------------
    // New Upgrades: Camera Rotation/Mirroring, EMA Smoothing, Biofeedback & Routines
    // ------------------------------------------------------------------

    @Test
    fun testSensorRotationAndFrontCameraMirroring() {
        // 4x2 sensor image rotated 270 deg CW -> upright 2x4 output
        // Without mirror: upright top-left (0, 0) comes from sensor top-right (3, 0)
        val unmirrored270 = PoseLandmarkManager.mapOutputToSensorCoords(
            ox = 0, oy = 0, outW = 2, outH = 4, stride = 1,
            srcW = 4, srcH = 2, rotationDegrees = 270, mirrorHorizontally = false
        )
        assertEquals(3 to 0, unmirrored270)

        // With front-camera mirror: upright top-left (ox=0 -> unmirrored ox=1, oy=0) comes from (3, 1)
        val mirrored270 = PoseLandmarkManager.mapOutputToSensorCoords(
            ox = 0, oy = 0, outW = 2, outH = 4, stride = 1,
            srcW = 4, srcH = 2, rotationDegrees = 270, mirrorHorizontally = true
        )
        assertEquals(3 to 1, mirrored270)

        // 90 deg CW without mirror: upright top-right (ox=1, oy=0) comes from sensor top-left (0, 0)
        val unmirrored90 = PoseLandmarkManager.mapOutputToSensorCoords(
            ox = 1, oy = 0, outW = 2, outH = 4, stride = 1,
            srcW = 4, srcH = 2, rotationDegrees = 90, mirrorHorizontally = false
        )
        assertEquals(0 to 0, unmirrored90)
    }

    @Test
    fun testLandmarkTemporalSmoothing() {
        val manager = PoseLandmarkManager()
        val first = mapOf(PoseLandmarkManager.LEFT_KNEE to LandmarkPoint(0.20f, 0.50f, 0f))
        val second = mapOf(PoseLandmarkManager.LEFT_KNEE to LandmarkPoint(0.60f, 0.50f, 0f))

        val s1 = manager.smoothLandmarks(first, alpha = 0.5f)
        assertEquals(0.20f, s1[PoseLandmarkManager.LEFT_KNEE]!!.x, 0.001f)

        val s2 = manager.smoothLandmarks(second, alpha = 0.5f)
        assertEquals(0.40f, s2[PoseLandmarkManager.LEFT_KNEE]!!.x, 0.001f)

        // Empty landmarks reset temporal smoothing buffer cleanly
        assertTrue(manager.smoothLandmarks(emptyMap()).isEmpty())
        val afterReset = manager.smoothLandmarks(second, alpha = 0.5f)
        assertEquals(0.60f, afterReset[PoseLandmarkManager.LEFT_KNEE]!!.x, 0.001f)
    }

    @Test
    fun testHolisticMultiSignalBiofeedbackTool() {
        val tools = FitnessTools()
        val stressed = tools.evaluateHolisticBiofeedback(
            heartRateBpm = 165.0,
            breathingRateBpm = 28.0,
            hrvRmssdMs = 22.0,
            facialExpression = "Strain"
        )
        assertTrue(stressed.shouldTriggerBreathingPacer)
        assertEquals(4, stressed.inhaleSeconds)
        assertEquals(6, stressed.exhaleSeconds)
        assertNotNull(stressed.expressionCue)
        assertTrue(stressed.expressionCue!!.contains("unclench your jaw"))
        assertTrue(stressed.autonomicReadinessLabel.contains("High Exertion"))
    }

    @Test
    fun testTimeBasedIsometricPlankLifecycle() {
        val manager = WorkoutManager()
        manager.startWorkout(exercise = "Plank", targetReps = 15, totalSets = 1)

        assertTrue(manager.state.value.isIsometricHold)
        assertEquals(WorkoutPhase.ACTIVE_SET, manager.state.value.phase)

        manager.advanceIsometricHold(10)
        assertEquals(10, manager.state.value.completedReps)
        assertEquals(WorkoutPhase.ACTIVE_SET, manager.state.value.phase)

        // Reaching 15s auto-completes the single set -> COMPLETED
        manager.advanceIsometricHold(5)
        assertEquals(WorkoutPhase.COMPLETED, manager.state.value.phase)
        manager.cleanup()
    }

    @Test
    fun testMultiExerciseRoutineAndWarmupCooldownTransitions() {
        val manager = WorkoutManager()
        manager.startRoutine(
            exercises = listOf("Bodyweight Squats", "Plank"),
            targetReps = 10,
            setsPerExercise = 1,
            baseRestSeconds = 30,
            includeWarmup = true,
            includeCooldown = true
        )

        assertEquals(WorkoutPhase.WARMUP, manager.state.value.phase)
        manager.skipWarmup()
        assertEquals(WorkoutPhase.ACTIVE_SET, manager.state.value.phase)
        assertEquals("Bodyweight Squats", manager.state.value.exerciseName)
        assertFalse(manager.state.value.isIsometricHold)

        // Finish Exercise 1 -> enters REST_INTERVAL before Exercise 2
        manager.completeSet()
        assertEquals(WorkoutPhase.REST_INTERVAL, manager.state.value.phase)
        manager.skipRest()

        // Now in Exercise 2 (Plank -> isometric hold automatically enabled!)
        assertEquals(WorkoutPhase.ACTIVE_SET, manager.state.value.phase)
        assertEquals("Plank", manager.state.value.exerciseName)
        assertTrue(manager.state.value.isIsometricHold)

        // Complete Exercise 2 -> enters COOLDOWN
        manager.completeSet()
        assertEquals(WorkoutPhase.COOLDOWN, manager.state.value.phase)

        val session = manager.finishWorkout()
        assertEquals(WorkoutPhase.COMPLETED, manager.state.value.phase)
        assertTrue(session.exerciseName.contains("Bodyweight Squats"))
        assertTrue(session.exerciseName.contains("Plank"))
        manager.cleanup()
    }
}
