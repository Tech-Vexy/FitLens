package com.fitlens.app.ai.media

/**
 * Generator responsible for synthesizing Nanobanana procedure steps and Gemini Omni
 * video walkthroughs during workout generation.
 */
object WorkoutMediaGenerator {

    /**
     * Generates media packs for all exercises in a newly synthesized workout routine.
     */
    fun generateMediaForRoutine(
        exercises: List<String>,
        goal: String = "Fitness",
        experienceLevel: String = "Intermediate"
    ): Map<String, ExerciseMediaPack> {
        return exercises.associateWith { exercise ->
            generateMediaForExercise(exercise, goal, experienceLevel)
        }
    }

    /**
     * Synthesizes Nanobanana procedure steps and Gemini Omni video walkthrough for an exercise.
     */
    fun generateMediaForExercise(
        exerciseName: String,
        goal: String = "General Fitness",
        experienceLevel: String = "Intermediate"
    ): ExerciseMediaPack {
        val lower = exerciseName.lowercase()
        return when {
            lower.contains("squat") -> generateSquatPack(exerciseName, goal)
            lower.contains("push") || lower.contains("press") -> generatePushUpPack(exerciseName, goal)
            lower.contains("plank") -> generatePlankPack(exerciseName, goal)
            lower.contains("lunge") || lower.contains("split") -> generateLungePack(exerciseName, goal)
            lower.contains("crunch") || lower.contains("leg raise") || lower.contains("twist") || lower.contains("abs") ->
                generateCorePack(exerciseName, goal)
            lower.contains("jack") || lower.contains("burpee") || lower.contains("knee") || lower.contains("climber") ->
                generateCardioPack(exerciseName, goal)
            lower.contains("curl") || lower.contains("row") || lower.contains("pull") ->
                generatePullPack(exerciseName, goal)
            else -> generateDefaultPack(exerciseName, goal)
        }
    }

    private fun generateSquatPack(name: String, goal: String) = ExerciseMediaPack(
        exerciseName = name,
        categoryName = "LOWER",
        nanobananaPrompt = "Nanobanana 3D Athletic Studio: High precision athletic figure demonstrating $name procedure steps with neon lime muscle highlights on quadriceps and glutes, clean obsidian studio backdrop, neutral biomechanical spine alignment markers.",
        procedureSteps = listOf(
            NanobananaProcedureStep(
                stepNumber = 1,
                title = "Setup & Stance",
                instruction = "Stand tall with feet shoulder-width apart, toes flared 10-15°. Engage abdominal wall and set arms forward for counterbalance.",
                breathingCue = "Inhale deep diaphragmatic breath into belly",
                biomechanicsCue = "Weight distributed across tripod foot (heel, 1st metatarsal, 5th metatarsal)",
                targetMuscles = listOf("Core", "Quadriceps"),
                progressFraction = 0.0f,
                keyJointAngles = "Knees 0° • Hips 0° • Spine Neutral"
            ),
            NanobananaProcedureStep(
                stepNumber = 2,
                title = "Eccentric Descent",
                instruction = "Initiate movement by sending hips back and spreading knees outward. Descend under full control until hip crease reaches knee level.",
                breathingCue = "Hold breath to maintain intra-abdominal pressure",
                biomechanicsCue = "Knees track over second toe; maintain torso parallel to shins",
                targetMuscles = listOf("Quadriceps", "Glutes", "Adductors"),
                progressFraction = 0.5f,
                keyJointAngles = "Knees 90° • Hips 90° • Ankle Dorsiflexion 25°"
            ),
            NanobananaProcedureStep(
                stepNumber = 3,
                title = "Drive & Lockout",
                instruction = "Push the floor away through midfoot and heels. Accelerate upward while keeping chest proud and fully lock out glutes at apex.",
                breathingCue = "Exhale forcefully past the sticking point",
                biomechanicsCue = "Avoid knee valgus cave; finish tall with neutral pelvis",
                targetMuscles = listOf("Glutes", "Hamstrings", "Quadriceps"),
                progressFraction = 1.0f,
                keyJointAngles = "Knees 0° • Full Hip Extension • Neutral Pelvis"
            )
        ),
        videoWalkthrough = GeminiOmniVideoWalkthrough(
            videoTitle = "Gemini Omni Biomechanics Walkthrough: $name",
            totalDurationSeconds = 12,
            videoPrompt = "Gemini Omni high-definition 60fps workout video walkthrough showing $name with dynamic camera transitions (Side 45°, Sagittal, Frontal), synchronized tempo cadence, and audio coaching narration.",
            phases = listOf(
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 1,
                    title = "Phase 1: Foot Strike & Bracing",
                    startSeconds = 0f,
                    durationSeconds = 3f,
                    cameraPerspective = "Front 45° Perspective",
                    coachingAudioPrompt = "Plant your feet shoulder-width, rotate toes slightly outward, and brace your core.",
                    breathingDirective = "Inhale deeply (3s)",
                    formInspectionCheck = "Pelvis neutral • Core braced"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 2,
                    title = "Phase 2: Controlled Descent",
                    startSeconds = 3f,
                    durationSeconds = 4f,
                    cameraPerspective = "Sagittal Side Plane",
                    coachingAudioPrompt = "Hips back, knees tracking wide. Lower with control for two seconds until thighs hit parallel.",
                    breathingDirective = "Brace and descend (2s)",
                    formInspectionCheck = "Knee-toe alignment checked • Lumbar curve solid"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 3,
                    title = "Phase 3: Parallel Inflection Check",
                    startSeconds = 7f,
                    durationSeconds = 2f,
                    cameraPerspective = "Low Lateral Perspective",
                    coachingAudioPrompt = "Hold tension at the bottom. No bouncing. Keep chest upright and weight centered.",
                    breathingDirective = "Micro pause (1s)",
                    formInspectionCheck = "Depth verified at parallel • Zero pelvic tuck"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 4,
                    title = "Phase 4: Concentric Drive & Reset",
                    startSeconds = 9f,
                    durationSeconds = 3f,
                    cameraPerspective = "Dynamic Orbit Angle",
                    coachingAudioPrompt = "Drive through the floor! Exhale through the top and squeeze your glutes into full extension.",
                    breathingDirective = "Exhale and lock out (2s)",
                    formInspectionCheck = "Full hip extension • Knees soft"
                )
            ),
            audioNarrationScript = "Stand shoulder-width apart. Inhale and brace your core. Hips back first, push knees outward as you descend smoothly to parallel. Drive the floor away through your heels, exhale on the rise, and lock out your hips."
        )
    )

    private fun generatePushUpPack(name: String, goal: String) = ExerciseMediaPack(
        exerciseName = name,
        categoryName = "PUSH",
        nanobananaPrompt = "Nanobanana 3D Athletic Studio: Athletic model performing $name with neon emerald activation on pectoralis major and triceps, pristine dark background, laser-aligned spine line from occiput to heels.",
        procedureSteps = listOf(
            NanobananaProcedureStep(
                stepNumber = 1,
                title = "High Plank Setup",
                instruction = "Place palms slightly wider than shoulders beneath the chest. Establish a straight line from crown to heels with glutes braced.",
                breathingCue = "Inhale and engage lats by rotating elbows back",
                biomechanicsCue = "Fingers splayed for surface area; neutral cervical spine",
                targetMuscles = listOf("Pectorals", "Deltoids", "Core"),
                progressFraction = 0.0f,
                keyJointAngles = "Elbows 0° • Shoulder Protraction • Rigid Spine"
            ),
            NanobananaProcedureStep(
                stepNumber = 2,
                title = "Controlled Descent",
                instruction = "Lower entire torso in unison by bending elbows backward at 45°. Descend until chest hovers one inch above the deck.",
                breathingCue = "Inhale smoothly over 2 seconds",
                biomechanicsCue = "Avoid flared elbows (T-shape); maintain 45° arrow profile",
                targetMuscles = listOf("Pectorals", "Triceps", "Anterior Deltoid"),
                progressFraction = 0.5f,
                keyJointAngles = "Elbows 90° • Scapular Retraction • Plumb Line Core"
            ),
            NanobananaProcedureStep(
                stepNumber = 3,
                title = "Press & Protraction",
                instruction = "Drive palms through the floor to press up in one rigid block. Squeeze chest and protract shoulder blades at full lockout.",
                breathingCue = "Exhale forcefully through pursed lips",
                biomechanicsCue = "No sagging lumbar spine; push upper back toward ceiling",
                targetMuscles = listOf("Triceps", "Pectorals", "Serratus Anterior"),
                progressFraction = 1.0f,
                keyJointAngles = "Elbows 0° • Serratus Active • Flat Lumbar"
            )
        ),
        videoWalkthrough = GeminiOmniVideoWalkthrough(
            videoTitle = "Gemini Omni Biomechanics Walkthrough: $name",
            totalDurationSeconds = 12,
            videoPrompt = "Gemini Omni video demo of $name with top-down arrow angle and side alignment plane showing scapular mechanics and elbow kinematics.",
            phases = listOf(
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 1,
                    title = "Phase 1: Rigid Plank Hand Anchor",
                    startSeconds = 0f,
                    durationSeconds = 3f,
                    cameraPerspective = "Top Down 45° Angle",
                    coachingAudioPrompt = "Stack wrists under shoulders, screw hands into the floor, and lock your glutes.",
                    breathingDirective = "Inhale and brace (3s)",
                    formInspectionCheck = "Arrowhead elbow orientation • Glutes contracted"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 2,
                    title = "Phase 2: 45-Degree Arrow Lowering",
                    startSeconds = 3f,
                    durationSeconds = 4f,
                    cameraPerspective = "Lateral Floor Cam",
                    coachingAudioPrompt = "Lower chest down in one straight piece. Keep elbows tucked at 45 degrees.",
                    breathingDirective = "Controlled inhale (2s)",
                    formInspectionCheck = "No lumbar dip • Chest reaches 1-inch hover"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 3,
                    title = "Phase 3: Bottom Hover & Transition",
                    startSeconds = 7f,
                    durationSeconds = 2f,
                    cameraPerspective = "Front Ground Level",
                    coachingAudioPrompt = "Maintain tension in your chest and triceps. Do not rest on the ground.",
                    breathingDirective = "Hold intra-abdominal pressure",
                    formInspectionCheck = "Chest hovers evenly • Neck stays long"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 4,
                    title = "Phase 4: Explosive Press to Lockout",
                    startSeconds = 9f,
                    durationSeconds = 3f,
                    cameraPerspective = "Diagonal Elevation",
                    coachingAudioPrompt = "Press the floor away explosively! Exhale up and push your upper back tall.",
                    breathingDirective = "Power exhale (2s)",
                    formInspectionCheck = "Full lock without hyper-extension"
                )
            ),
            audioNarrationScript = "Hands under shoulders, rigid plank from head to heel. Inhale and lower your chest with elbows tucked at 45 degrees. Tap bottom tension, then press hard through the palms and exhale to lockout."
        )
    )

    private fun generatePlankPack(name: String, goal: String) = ExerciseMediaPack(
        exerciseName = name,
        categoryName = "CORE",
        nanobananaPrompt = "Nanobanana 3D Athletic Studio: Forearm plank hold demonstration with radiant neon lime lines illuminating the rectus abdominis, obliques, and glutes, clean obsidian studio gradient.",
        procedureSteps = listOf(
            NanobananaProcedureStep(
                stepNumber = 1,
                title = "Forearm Base Alignment",
                instruction = "Rest forearms parallel on the mat with elbows stacked directly beneath shoulder sockets. Clench fists or flatten palms.",
                breathingCue = "Establish steady rhythmic diaphragmatic breathing",
                biomechanicsCue = "Push floor away through forearms to avoid sunken winging scapulae",
                targetMuscles = listOf("Serratus Anterior", "Transverse Abdominis"),
                progressFraction = 0.0f,
                keyJointAngles = "Elbows 90° Under Shoulders"
            ),
            NanobananaProcedureStep(
                stepNumber = 2,
                title = "Posterior Pelvic Brace",
                instruction = "Step feet back hip-width apart. Squeeze glutes and tuck pelvis slightly posterior to flatten lower back completely.",
                breathingCue = "Slow 4-second nasal inhale, 6-second controlled exhale",
                biomechanicsCue = "Draw navel toward spine; maintain unbroken line from crown to ankles",
                targetMuscles = listOf("Rectus Abdominis", "Internal Obliques", "Gluteus Maximus"),
                progressFraction = 0.5f,
                keyJointAngles = "Pelvic Tilt Posterior 5° • Neutral Cervical"
            ),
            NanobananaProcedureStep(
                stepNumber = 3,
                title = "Isometric Anti-Extension Hold",
                instruction = "Pull elbows and toes toward each other isometrically to maximize abdominal irradiation. Gaze down at mat.",
                breathingCue = "Maintain cadence: in for 4, out for 6",
                biomechanicsCue = "Do not allow hips to pike upward or sag downward",
                targetMuscles = listOf("Deep Core Stabilizers", "Quadriceps", "Spinal Erectors"),
                progressFraction = 1.0f,
                keyJointAngles = "Straight 180° Horizontal Kinematic Line"
            )
        ),
        videoWalkthrough = GeminiOmniVideoWalkthrough(
            videoTitle = "Gemini Omni Biomechanics Walkthrough: $name",
            totalDurationSeconds = 12,
            videoPrompt = "Gemini Omni video guide for isometric $name featuring real-time spine alignment laser overlay and breathing rate cadence pacer.",
            phases = listOf(
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 1,
                    title = "Phase 1: Forearm Stack & Elbow Position",
                    startSeconds = 0f,
                    durationSeconds = 3f,
                    cameraPerspective = "Front Low Level",
                    coachingAudioPrompt = "Elbows under shoulders, forearms parallel. Press into the mat.",
                    breathingDirective = "Breathe in deeply through nose",
                    formInspectionCheck = "No shoulder collapse • Neutral shoulder girdle"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 2,
                    title = "Phase 2: Core & Glute Tension Lock",
                    startSeconds = 3f,
                    durationSeconds = 4f,
                    cameraPerspective = "True Side Profile",
                    coachingAudioPrompt = "Squeeze glutes and pull ribs down toward hips. Flatten your lower back.",
                    breathingDirective = "Steady 4s nasal inhalation",
                    formInspectionCheck = "Straight horizontal posture line confirmed"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 3,
                    title = "Phase 3: Active Isometric Squeeze",
                    startSeconds = 7f,
                    durationSeconds = 3f,
                    cameraPerspective = "360 Smooth Rotation",
                    coachingAudioPrompt = "Isometrically drag elbows toward toes. Intensify core brace without holding your breath.",
                    breathingDirective = "Long 6s controlled exhalation",
                    formInspectionCheck = "Zero hip sag • No piking"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 4,
                    title = "Phase 4: Resilient Cadence Hold",
                    startSeconds = 10f,
                    durationSeconds = 2f,
                    cameraPerspective = "Overhead 30°",
                    coachingAudioPrompt = "Hold firm and breathe rhythmically through the burn.",
                    breathingDirective = "Smooth continuous breathing",
                    formInspectionCheck = "Head and neck neutral"
                )
            ),
            audioNarrationScript = "Elbows stacked under shoulders, push the floor away. Squeeze glutes, pull ribs in, and lock into a rigid line. Breathe rhythmically: four seconds in, six seconds out. Zero hip sag."
        )
    )

    private fun generateLungePack(name: String, goal: String) = ExerciseMediaPack(
        exerciseName = name,
        categoryName = "LOWER",
        nanobananaPrompt = "Nanobanana 3D Athletic Studio: Athletic athlete performing $name with neon lime highlights along quads and glutes, high-definition biomechanical posture angles, studio lighting.",
        procedureSteps = listOf(
            NanobananaProcedureStep(
                stepNumber = 1,
                title = "Upright Stance",
                instruction = "Stand tall with feet hip-width apart and hands on hips or at chest for balance. Engage core muscles.",
                breathingCue = "Inhale and brace diaphragm",
                biomechanicsCue = "Keep shoulders stacked directly over pelvis",
                targetMuscles = listOf("Core", "Quadriceps"),
                progressFraction = 0.0f,
                keyJointAngles = "Standing Tall • Spine Vertical"
            ),
            NanobananaProcedureStep(
                stepNumber = 2,
                title = "Stride & Vertical Descent",
                instruction = "Step forward 2 to 3 feet and drop straight down until both knees create clean 90-degree angles. Back knee hovers 1 inch above floor.",
                breathingCue = "Hold breath or slow inhale on descent",
                biomechanicsCue = "Front knee tracks over second toe; avoid forward lunging shearing force",
                targetMuscles = listOf("Quadriceps", "Glutes", "Hamstrings"),
                progressFraction = 0.5f,
                keyJointAngles = "Front Knee 90° • Back Knee 90° • Torso Vertical"
            ),
            NanobananaProcedureStep(
                stepNumber = 3,
                title = "Heel Drive & Return",
                instruction = "Drive firmly through the front heel and midfoot to push back to starting position in one smooth, balanced motion.",
                breathingCue = "Exhale as you push back up",
                biomechanicsCue = "Do not push off back toes; make front leg do 80% of the work",
                targetMuscles = listOf("Glutes", "Hamstrings", "Calves"),
                progressFraction = 1.0f,
                keyJointAngles = "Full Stance Reset • Controlled Balance"
            )
        ),
        videoWalkthrough = GeminiOmniVideoWalkthrough(
            videoTitle = "Gemini Omni Biomechanics Walkthrough: $name",
            totalDurationSeconds = 12,
            videoPrompt = "Gemini Omni walkthrough showing $name with dual 90-degree angle indicators, knee-tracking guides, and step-cadence coaching.",
            phases = listOf(
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 1,
                    title = "Phase 1: Step & Hip Square",
                    startSeconds = 0f,
                    durationSeconds = 3f,
                    cameraPerspective = "Frontal Perspective",
                    coachingAudioPrompt = "Step forward with purpose, keeping hips square like on train tracks.",
                    breathingDirective = "Inhale and stabilize (3s)",
                    formInspectionCheck = "Hips level • Stance width maintained"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 2,
                    title = "Phase 2: 90-Degree Knee Drop",
                    startSeconds = 3f,
                    durationSeconds = 4f,
                    cameraPerspective = "Side Profile View",
                    coachingAudioPrompt = "Drop hips straight down until both knees reach 90 degrees.",
                    breathingDirective = "Smooth controlled descent (2s)",
                    formInspectionCheck = "Back knee hovers • Front knee behind toes"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 3,
                    title = "Phase 3: Bottom Hover Balance",
                    startSeconds = 7f,
                    durationSeconds = 2f,
                    cameraPerspective = "Low Angle Lateral",
                    coachingAudioPrompt = "Hold tension at the bottom for half a second. Keep torso tall.",
                    breathingDirective = "Braced core pause",
                    formInspectionCheck = "Zero torso forward lean"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 4,
                    title = "Phase 4: Heel Drive to Base",
                    startSeconds = 9f,
                    durationSeconds = 3f,
                    cameraPerspective = "45° Elevation",
                    coachingAudioPrompt = "Drive through front heel, exhale, and return smoothly to start.",
                    breathingDirective = "Power exhale back to start (2s)",
                    formInspectionCheck = "Clean single-motion return"
                )
            ),
            audioNarrationScript = "Step forward along train tracks. Drop straight down until both knees bend to 90 degrees. Keep chest proud, pause briefly, then drive hard through the front heel to reset."
        )
    )

    private fun generateCorePack(name: String, goal: String) = ExerciseMediaPack(
        exerciseName = name,
        categoryName = "CORE",
        nanobananaPrompt = "Nanobanana 3D Athletic Studio: Athletic athlete performing $name on yoga mat, neon emerald glow tracing rectus abdominis and obliques with clean vector motion lines.",
        procedureSteps = listOf(
            NanobananaProcedureStep(
                stepNumber = 1,
                title = "Lumbar Mat Anchor",
                instruction = "Lie on back with lower back pressed flush into the mat. Float knees into tabletop position and fingertips lightly touching temples.",
                breathingCue = "Inhale and flatten spine against floor",
                biomechanicsCue = "Ensure zero gap beneath lumbar spine throughout",
                targetMuscles = listOf("Transverse Abdominis", "Hip Flexors"),
                progressFraction = 0.0f,
                keyJointAngles = "Hips 90° • Lower Back Flat"
            ),
            NanobananaProcedureStep(
                stepNumber = 2,
                title = "Rotational Core Contraction",
                instruction = "Rotate shoulder girdle bringing opposite shoulder toward opposite knee while extending other leg straight out at 45°.",
                breathingCue = "Exhale sharply with rotational torque",
                biomechanicsCue = "Lead with the armpit and ribcage, not the elbow or neck",
                targetMuscles = listOf("Internal & External Obliques", "Rectus Abdominis"),
                progressFraction = 0.5f,
                keyJointAngles = "Thoracic Rotation 30° • Extended Leg 45°"
            ),
            NanobananaProcedureStep(
                stepNumber = 3,
                title = "Controlled Lateral Switch",
                instruction = "Pause for a split-second peak contraction, then cycle smoothly through center into the opposite rotation.",
                breathingCue = "Inhale through center, exhale on twist",
                biomechanicsCue = "Keep movement slow and deliberate; avoid momentum jerking",
                targetMuscles = listOf("Entire Abdominal Wall"),
                progressFraction = 1.0f,
                keyJointAngles = "Alternating Core Torque"
            )
        ),
        videoWalkthrough = GeminiOmniVideoWalkthrough(
            videoTitle = "Gemini Omni Biomechanics Walkthrough: $name",
            totalDurationSeconds = 12,
            videoPrompt = "Gemini Omni video walkthrough of $name with rotational angle graphic and lumbar floor pressure feedback sensor visual.",
            phases = listOf(
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 1,
                    title = "Phase 1: Hollow Body Base Setup",
                    startSeconds = 0f,
                    durationSeconds = 3f,
                    cameraPerspective = "Top Down Overhead",
                    coachingAudioPrompt = "Press lower back flat to mat. Tabletop legs and keep elbows wide.",
                    breathingDirective = "Inhale deep (3s)",
                    formInspectionCheck = "Lower back contact confirmed"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 2,
                    title = "Phase 2: Diagonal Thoracic Twist",
                    startSeconds = 3f,
                    durationSeconds = 4f,
                    cameraPerspective = "High Diagonal Orbit",
                    coachingAudioPrompt = "Rotate shoulder to opposite knee. Extend other leg long and strong.",
                    breathingDirective = "Exhale on rotation (2s)",
                    formInspectionCheck = "Neck relaxed • Shoulder blades lifted"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 3,
                    title = "Phase 3: Peak Contraction Hold",
                    startSeconds = 7f,
                    durationSeconds = 2f,
                    cameraPerspective = "Ground Side Angle",
                    coachingAudioPrompt = "Hold and squeeze obliques at the top. Feel the contraction.",
                    breathingDirective = "Full exhalation hold",
                    formInspectionCheck = "Extended leg stable at 45°"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 4,
                    title = "Phase 4: Smooth Controlled Switch",
                    startSeconds = 9f,
                    durationSeconds = 3f,
                    cameraPerspective = "Overhead Angled View",
                    coachingAudioPrompt = "Transition smoothly to the other side. Focus on quality, not speed.",
                    breathingDirective = "Inhale center, exhale twist (2s)",
                    formInspectionCheck = "Zero momentum swinging"
                )
            ),
            audioNarrationScript = "Anchor your lower back flat to the floor. Tabletop your legs and rotate your shoulder toward the opposite knee while extending the other leg. Exhale as you twist and control the cadence."
        )
    )

    private fun generateCardioPack(name: String, goal: String) = ExerciseMediaPack(
        exerciseName = name,
        categoryName = "CARDIO",
        nanobananaPrompt = "Nanobanana 3D Athletic Studio: High dynamic energy athletic athlete performing $name with neon amber and lime speed trails, studio lighting, soft landing foot mechanics.",
        procedureSteps = listOf(
            NanobananaProcedureStep(
                stepNumber = 1,
                title = "Neutral Athletic Stance",
                instruction = "Stand tall with feet together, knees relaxed, and arms hanging naturally by your sides.",
                breathingCue = "Steady rhythmic breathing",
                biomechanicsCue = "Soft knees, core lightly engaged",
                targetMuscles = listOf("Calves", "Core"),
                progressFraction = 0.0f,
                keyJointAngles = "Standing Athletic Base"
            ),
            NanobananaProcedureStep(
                stepNumber = 2,
                title = "Dynamic Spread & Arm Sweep",
                instruction = "Jump smoothly to spread feet slightly wider than shoulders while sweeping arms overhead in an arc.",
                breathingCue = "Inhale on outward jump",
                biomechanicsCue = "Land softly on balls of feet; absorb impact through knees",
                targetMuscles = listOf("Deltoids", "Abductors", "Calves"),
                progressFraction = 0.5f,
                keyJointAngles = "Legs Stride Wide • Arms Overhead"
            ),
            NanobananaProcedureStep(
                stepNumber = 3,
                title = "Spring Return & Cadence",
                instruction = "Spring back inward, bringing feet together and hands to sides with a bouncy, resilient tempo.",
                breathingCue = "Exhale on inward return",
                biomechanicsCue = "Maintain elastic springiness without heavy heel strikes",
                targetMuscles = listOf("Adductors", "Cardiovascular System"),
                progressFraction = 1.0f,
                keyJointAngles = "Athletic Spring Cadence"
            )
        ),
        videoWalkthrough = GeminiOmniVideoWalkthrough(
            videoTitle = "Gemini Omni Biomechanics Walkthrough: $name",
            totalDurationSeconds = 12,
            videoPrompt = "Gemini Omni dynamic workout walkthrough of $name with cardio rhythm pulse bar and ground impact absorption tracker.",
            phases = listOf(
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 1,
                    title = "Phase 1: Elastic Pre-Jump Alignment",
                    startSeconds = 0f,
                    durationSeconds = 3f,
                    cameraPerspective = "Front Wide Camera",
                    coachingAudioPrompt = "Stand tall with relaxed knees and arms at your sides.",
                    breathingDirective = "Find a steady cadence (3s)",
                    formInspectionCheck = "Relaxed shoulders • Soft joints"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 2,
                    title = "Phase 2: Arc & Overhead Reach",
                    startSeconds = 3f,
                    durationSeconds = 4f,
                    cameraPerspective = "3/4 Dynamic Orbit",
                    coachingAudioPrompt = "Jump feet out wide, sweep arms overhead, and land softly on the balls of your feet.",
                    breathingDirective = "Inhale out, exhale in",
                    formInspectionCheck = "Soft knee absorption • Quiet landings"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 3,
                    title = "Phase 3: Fast Spring Return",
                    startSeconds = 7f,
                    durationSeconds = 3f,
                    cameraPerspective = "Front Close Elevation",
                    coachingAudioPrompt = "Spring right back to center and maintain a light, springy rhythm.",
                    breathingDirective = "Nasal or rhythmic breath",
                    formInspectionCheck = "Zero heavy heel strikes"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 4,
                    title = "Phase 4: Sustained Flow State",
                    startSeconds = 10f,
                    durationSeconds = 2f,
                    cameraPerspective = "Dynamic Frontal Wide",
                    coachingAudioPrompt = "Keep the cadence steady and breathe naturally.",
                    breathingDirective = "Continuous flow breathing",
                    formInspectionCheck = "Consistent tempo verified"
                )
            ),
            audioNarrationScript = "Start tall with soft knees. Jump feet wide while sweeping arms overhead, landing quietly on the balls of your feet. Spring right back to center and keep a light, athletic tempo."
        )
    )

    private fun generatePullPack(name: String, goal: String) = ExerciseMediaPack(
        exerciseName = name,
        categoryName = "PULL",
        nanobananaPrompt = "Nanobanana 3D Athletic Studio: Athletic athlete demonstrating $name with neon sky-blue activation on biceps, rhomboids, and latissimus dorsi, clean studio lighting.",
        procedureSteps = listOf(
            NanobananaProcedureStep(
                stepNumber = 1,
                title = "Setup & Elbow Pinning",
                instruction = "Stand tall with elbows pinned securely to your sides and core braced.",
                breathingCue = "Inhale deep diaphragmatic breath",
                biomechanicsCue = "Avoid shoulder shrugging or elbow drift",
                targetMuscles = listOf("Biceps Brachii", "Forearms"),
                progressFraction = 0.0f,
                keyJointAngles = "Elbows 0° • Shoulders Depressed"
            ),
            NanobananaProcedureStep(
                stepNumber = 2,
                title = "Concentric Curl & Squeeze",
                instruction = "Curl weight upward by contracting biceps while keeping upper arms strictly vertical. Squeeze at top.",
                breathingCue = "Exhale on contraction",
                biomechanicsCue = "Zero hip swing; all movement originates from elbow hinge",
                targetMuscles = listOf("Biceps Brachii", "Brachialis"),
                progressFraction = 0.5f,
                keyJointAngles = "Elbows 135° • Peak Bicep Contraction"
            ),
            NanobananaProcedureStep(
                stepNumber = 3,
                title = "Eccentric 3-Second Lower",
                instruction = "Lower weight with strict 3-second control until arms reach full extension at bottom.",
                breathingCue = "Inhale slowly as you lower",
                biomechanicsCue = "Resist gravity for maximum hypertrophic stimulus",
                targetMuscles = listOf("Biceps Eccentric", "Core Stabilizers"),
                progressFraction = 1.0f,
                keyJointAngles = "Full Extension Lock"
            )
        ),
        videoWalkthrough = GeminiOmniVideoWalkthrough(
            videoTitle = "Gemini Omni Biomechanics Walkthrough: $name",
            totalDurationSeconds = 12,
            videoPrompt = "Gemini Omni video walkthrough of $name with elbow stabilization tracker and 3-second eccentric tempo meter.",
            phases = listOf(
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 1,
                    title = "Phase 1: Elbow Pin Anchor",
                    startSeconds = 0f,
                    durationSeconds = 3f,
                    cameraPerspective = "Side 45° View",
                    coachingAudioPrompt = "Lock elbows to your ribcage. Stand tall with neutral posture.",
                    breathingDirective = "Inhale and brace (3s)",
                    formInspectionCheck = "Elbows pinned to sides"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 2,
                    title = "Phase 2: Strict Concentric Curl",
                    startSeconds = 3f,
                    durationSeconds = 4f,
                    cameraPerspective = "Front Diagonal Angle",
                    coachingAudioPrompt = "Curl up without swinging hips. Squeeze biceps hard at the peak.",
                    breathingDirective = "Exhale as you curl (2s)",
                    formInspectionCheck = "Zero body sway detected"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 3,
                    title = "Phase 3: 3-Second Eccentric Descent",
                    startSeconds = 7f,
                    durationSeconds = 3f,
                    cameraPerspective = "True Sagittal View",
                    coachingAudioPrompt = "Lower slowly over three seconds. Fight gravity all the way down.",
                    breathingDirective = "Slow steady inhale (3s)",
                    formInspectionCheck = "Controlled descent cadence"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 4,
                    title = "Phase 4: Reset & Repeat",
                    startSeconds = 10f,
                    durationSeconds = 2f,
                    cameraPerspective = "Diagonal Elevation",
                    coachingAudioPrompt = "Reach full extension at the bottom before starting the next rep.",
                    breathingDirective = "Reset breath",
                    formInspectionCheck = "Full range of motion"
                )
            ),
            audioNarrationScript = "Pin your elbows to your sides. Squeeze your biceps to curl up without swinging your torso. Pause at the top, then lower with strict three-second control to full extension."
        )
    )

    private fun generateDefaultPack(name: String, goal: String) = ExerciseMediaPack(
        exerciseName = name,
        categoryName = "FULL",
        nanobananaPrompt = "Nanobanana 3D Athletic Studio: Athletic athlete performing $name with neon lime biomechanical vector overlays, clean studio lighting.",
        procedureSteps = listOf(
            NanobananaProcedureStep(
                stepNumber = 1,
                title = "Setup & Core Alignment",
                instruction = "Set an athletic stance with feet planted, spine in neutral alignment, and core braced.",
                breathingCue = "Inhale deep diaphragmatic breath",
                biomechanicsCue = "Establish stable balance and focus",
                targetMuscles = listOf("Core", "Full Body"),
                progressFraction = 0.0f,
                keyJointAngles = "Neutral Base"
            ),
            NanobananaProcedureStep(
                stepNumber = 2,
                title = "Controlled Movement Execution",
                instruction = "Execute the primary range of motion for $name with smooth, deliberate muscle tension.",
                breathingCue = "Breathe with cadence through the range of motion",
                biomechanicsCue = "Maintain joint alignment and avoid compensations",
                targetMuscles = listOf("Target Muscle Group"),
                progressFraction = 0.5f,
                keyJointAngles = "Controlled Contraction"
            ),
            NanobananaProcedureStep(
                stepNumber = 3,
                title = "Peak Finish & Reset",
                instruction = "Complete full extension or contraction, then return under control to starting position.",
                breathingCue = "Exhale and reset",
                biomechanicsCue = "Maintain posture integrity throughout the set",
                targetMuscles = listOf("Stabilizers"),
                progressFraction = 1.0f,
                keyJointAngles = "Clean Reset"
            )
        ),
        videoWalkthrough = GeminiOmniVideoWalkthrough(
            videoTitle = "Gemini Omni Biomechanics Walkthrough: $name",
            totalDurationSeconds = 12,
            videoPrompt = "Gemini Omni video walkthrough for $name showing full range of motion, pacing, and form checkpoints.",
            phases = listOf(
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 1,
                    title = "Phase 1: Setup & Alignment",
                    startSeconds = 0f,
                    durationSeconds = 3f,
                    cameraPerspective = "Front 45° Perspective",
                    coachingAudioPrompt = "Establish your stance, brace your core, and set your posture.",
                    breathingDirective = "Inhale and prepare",
                    formInspectionCheck = "Posture aligned"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 2,
                    title = "Phase 2: Range of Motion",
                    startSeconds = 3f,
                    durationSeconds = 4f,
                    cameraPerspective = "Side Sagittal View",
                    coachingAudioPrompt = "Execute the movement smoothly under control.",
                    breathingDirective = "Controlled cadence",
                    formInspectionCheck = "Joint tracking checked"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 3,
                    title = "Phase 3: Contraction & Hold",
                    startSeconds = 7f,
                    durationSeconds = 3f,
                    cameraPerspective = "Dynamic Angle",
                    coachingAudioPrompt = "Hold tension at the peak point.",
                    breathingDirective = "Maintain tension",
                    formInspectionCheck = "Zero form breakdown"
                ),
                GeminiOmniWalkthroughPhase(
                    phaseIndex = 4,
                    title = "Phase 4: Return & Reset",
                    startSeconds = 10f,
                    durationSeconds = 2f,
                    cameraPerspective = "Reset Angle",
                    coachingAudioPrompt = "Return under control and prepare for the next rep.",
                    breathingDirective = "Exhale to finish",
                    formInspectionCheck = "Clean reset"
                )
            ),
            audioNarrationScript = "Set an athletic posture. Inhale and brace, execute with smooth control, squeeze at the peak of the movement, and return under control."
        )
    )
}
