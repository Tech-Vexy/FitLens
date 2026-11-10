package com.fitlens.app

import com.fitlens.app.ai.FitnessCoachAgent
import com.fitlens.app.ai.FitnessTools
import com.fitlens.app.data.model.WorkoutSessionEntity
import com.fitlens.app.pose.DeviationSeverity
import com.fitlens.app.pose.LandmarkPoint
import com.fitlens.app.pose.PoseLandmarkManager
import com.fitlens.app.pose.RepPhase
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

        // Test with elevated HR (> 135 bpm)
        val elevatedRest = tools.calculateAdaptiveRest(
            baseRestSeconds = 45,
            currentHeartRateBpm = 142.0,
            restingHeartRateBpm = 70.0
        )
        assertTrue(elevatedRest.isExtended)
        assertEquals(75, elevatedRest.adjustedRestSeconds)
        assertTrue(elevatedRest.reason.contains("Extending rest by 30s"))

        // Test with recovered HR (90 bpm)
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

        // 3 consecutive deviations should advise halting the set
        val criticalCheck = tools.evaluateBiomechanicalFatigue(
            consecutiveDeviations = 3,
            currentSetRepsCompleted = 7,
            targetReps = 10
        )
        assertTrue(criticalCheck.shouldHaltSet)
        assertTrue(criticalCheck.correctiveAction.contains("Stop set immediately"))

        // 0 deviations should allow continuing
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

        // Right angle triangle: (0, 1) -> (0, 0) -> (1, 0) => 90 degrees
        val a = LandmarkPoint(0f, 1f, 0f)
        val b = LandmarkPoint(0f, 0f, 0f)
        val c = LandmarkPoint(1f, 0f, 0f)

        val angle = manager.calculateAngle(a, b, c)
        assertEquals(90.0, angle, 0.5)

        // Straight line: (0, 1) -> (0, 0) -> (0, -1) => 180 degrees
        val d = LandmarkPoint(0f, -1f, 0f)
        val straightAngle = manager.calculateAngle(a, b, d)
        assertEquals(180.0, straightAngle, 0.5)
    }

    @Test
    fun testKneeValgusDetection() {
        val manager = PoseLandmarkManager()
        manager.setExercise("Squats")

        // Knees caving in: knee width is much narrower than ankle width at squat depth (< 120 deg)
        val valgusLandmarks = mapOf(
            PoseLandmarkManager.LEFT_HIP to LandmarkPoint(0.40f, 0.50f, 0f),
            PoseLandmarkManager.LEFT_KNEE to LandmarkPoint(0.48f, 0.70f, 0f), // caved inward
            PoseLandmarkManager.LEFT_ANKLE to LandmarkPoint(0.35f, 0.80f, 0f),
            PoseLandmarkManager.RIGHT_HIP to LandmarkPoint(0.60f, 0.50f, 0f),
            PoseLandmarkManager.RIGHT_KNEE to LandmarkPoint(0.52f, 0.70f, 0f), // caved inward
            PoseLandmarkManager.RIGHT_ANKLE to LandmarkPoint(0.65f, 0.80f, 0f)
        )

        val frame = manager.analyzeLandmarks(valgusLandmarks)
        val valgusDeviations = frame.activeDeviations.filter { it.jointName == "Knees" }
        assertEquals(1, valgusDeviations.size)
        assertEquals(DeviationSeverity.CRITICAL, valgusDeviations.first().severity)
    }

    @Test
    fun testPlankSaggingDetection() {
        val manager = PoseLandmarkManager()
        manager.setExercise("Plank")

        // Hips sagging: shoulder-hip-ankle angle < 155 degrees
        val saggingPlankLandmarks = mapOf(
            PoseLandmarkManager.LEFT_SHOULDER to LandmarkPoint(0.20f, 0.40f, 0f),
            PoseLandmarkManager.LEFT_HIP to LandmarkPoint(0.50f, 0.65f, 0f), // sagged down
            PoseLandmarkManager.LEFT_ANKLE to LandmarkPoint(0.85f, 0.45f, 0f)
        )

        val frame = manager.analyzeLandmarks(saggingPlankLandmarks)
        val hipDeviations = frame.activeDeviations.filter { it.jointName == "Hips" }
        assertEquals(1, hipDeviations.size)
        assertEquals(DeviationSeverity.CRITICAL, hipDeviations.first().severity)
        assertTrue(hipDeviations.first().message.contains("Hips sagging"))
    }

    @Test
    fun testLungeExcessiveFlexionDetection() {
        val manager = PoseLandmarkManager()
        manager.setExercise("Lunges")

        // Front knee over-flexed (< 75 deg): knee driven ahead of the trailing ankle
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

        // Default profile: resting 65 bpm, max 190 bpm -> 50/65/80/90% HRR at 127.5/146.25/165/177.5 bpm
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

        // Test Headless Mode exertion simulation (SmartSpectraTestingApi workflow)
        analyzer.simulateExerciseExertion(0.85f)
        val reading = analyzer.vitalsFlow.value

        assertTrue(reading.heartRateBpm > 135.0)
        assertTrue(reading.isReliable)
        assertNotNull(reading.smartSpectraLlmInsight)
        assertTrue(reading.smartSpectraLlmInsight!!.contains("SmartSpectra"))
        assertEquals(HeartRateZone.ANAEROBIC, reading.heartRateZone)
    }

    @Test
    fun testPersonalizedKarvonenHeartRateZones() {
        // User age 30, resting HR 60, max HR 190 => HRR = 130
        val profile = com.fitlens.app.data.profile.UserProfile(
            age = 30,
            restingHeartRate = 60.0,
            maxHeartRate = 190.0
        )

        // 80 bpm => (80 - 60) / 130 = 0.15 intensity (< 0.50) -> RESTING
        assertEquals(HeartRateZone.RESTING, profile.getHeartRateZone(80.0))

        // 135 bpm => (135 - 60) / 130 = 0.57 intensity (0.50 - 0.65) -> WARMUP
        assertEquals(HeartRateZone.WARMUP, profile.getHeartRateZone(135.0))

        // 155 bpm => (155 - 60) / 130 = 0.73 intensity (0.65 - 0.80) -> AEROBIC
        assertEquals(HeartRateZone.AEROBIC, profile.getHeartRateZone(155.0))

        // 172 bpm => (172 - 60) / 130 = 0.86 intensity (0.80 - 0.90) -> ANAEROBIC
        assertEquals(HeartRateZone.ANAEROBIC, profile.getHeartRateZone(172.0))

        // 185 bpm => (185 - 60) / 130 = 0.96 intensity (> 0.90) -> MAX
        assertEquals(HeartRateZone.MAX, profile.getHeartRateZone(185.0))
    }

    @Test
    fun testHybridAgentRouting() = runBlocking {
        val agent = FitnessCoachAgent(
            initialAuthMode = com.fitlens.app.ai.adk.AdkAuthMode.ON_DEVICE_EDGE
        )

        // Edge execution target test
        val edgeRest = agent.calculateAdaptiveRest(
            baseRestSeconds = 45,
            currentHeartRateBpm = 145.0,
            preferLocal = true
        )
        assertTrue(edgeRest.isExtended)
        assertEquals(75, edgeRest.adjustedRestSeconds)

        // Cloud execution target test
        val cloudPlan = agent.requestWorkoutPlan(
            goal = "Mobility",
            experience = "intermediate",
            equipment = "Bodyweight",
            durationMinutes = 25
        )
        assertNotNull(cloudPlan)
        assertEquals(25, cloudPlan.targetDurationMinutes)
    }

    @Test
    fun testAdkAgentWithFirebaseAiLogic() = runBlocking {
        val firebaseAgent = FitnessCoachAgent(
            initialAuthMode = com.fitlens.app.ai.adk.AdkAuthMode.FIREBASE_AI_LOGIC
        )

        assertEquals("fitness_coach", firebaseAgent.rootAgent.name)
        assertEquals(com.fitlens.app.ai.adk.AdkAuthMode.FIREBASE_AI_LOGIC, firebaseAgent.rootAgent.model.authMode)
        assertEquals("gemini-3.8-flash", firebaseAgent.rootAgent.model.modelName)

        assertTrue(firebaseAgent.rootAgent.model is com.fitlens.app.ai.adk.FirebaseGeminiAdapter)
    }

    @Test
    fun testAdkAgentWithGoogleAdc() = runBlocking {
        val adcAgent = FitnessCoachAgent(
            initialAuthMode = com.fitlens.app.ai.adk.AdkAuthMode.GOOGLE_ADC
        )

        assertEquals("fitness_coach", adcAgent.rootAgent.name)
        assertEquals(com.fitlens.app.ai.adk.AdkAuthMode.GOOGLE_ADC, adcAgent.rootAgent.model.authMode)

        val runnerResponse = adcAgent.runner.execute("Evaluate session")
        assertTrue(runnerResponse.contains("Google ADC / Vertex AI"))
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
    }

    @Test
    fun testLocalSqliteHealthDataPrivacyGuarantee() {
        val isLocalOnly = com.fitlens.app.data.sync.LocalHealthDataPrivacyEnforcer.verifyLocalOnlyStorage()
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

        // Week 1 should be Accumulation
        assertTrue(plan.phases[0].phaseName.contains("Accumulation"))
        assertEquals(6, plan.phases[0].targetIntensityRpe)

        // Week 3 should be Realization (Peak)
        assertTrue(plan.phases[2].phaseName.contains("Realization"))
        assertEquals(9, plan.phases[2].targetIntensityRpe)

        // Week 4 should be Deload
        assertTrue(plan.phases[3].phaseName.contains("Deload"))
        assertEquals(5, plan.phases[3].targetIntensityRpe)

        // Agent request verification
        val agent = FitnessCoachAgent(
            initialAuthMode = com.fitlens.app.ai.adk.AdkAuthMode.ON_DEVICE_EDGE
        )
        val agentPlan = agent.requestMesocyclePlan("Strength", 4, 3)
        assertNotNull(agentPlan)
        assertEquals(4, agentPlan.totalWeeks)
    }
}
