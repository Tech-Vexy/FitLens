package com.fitlens.app.ai.media

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests that the AI media generation pipeline successfully produces
 * valid [ExerciseMediaPack] objects for every exercise category.
 *
 * Each pack must contain:
 * - A non-blank Nanobanana image prompt
 * - At least 3 procedure steps with biomechanical cues
 * - A Gemini Omni video walkthrough with at least 4 phases
 * - An audio narration script
 */
class WorkoutMediaGeneratorTest {

    // -----------------------------------------------------------------------
    //  Exercise category routing tests
    // -----------------------------------------------------------------------

    @Test
    fun `generateMediaForExercise routes squat exercises to LOWER category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Bodyweight Squats")
        assertEquals("LOWER", pack.categoryName)
        assertEquals("Bodyweight Squats", pack.exerciseName)
    }

    @Test
    fun `generateMediaForExercise routes push-up exercises to PUSH category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Push-ups")
        assertEquals("PUSH", pack.categoryName)
    }

    @Test
    fun `generateMediaForExercise routes press exercises to PUSH category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Overhead Press")
        assertEquals("PUSH", pack.categoryName)
    }

    @Test
    fun `generateMediaForExercise routes plank exercises to CORE category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Plank Hold")
        assertEquals("CORE", pack.categoryName)
    }

    @Test
    fun `generateMediaForExercise routes lunge exercises to LOWER category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Walking Lunges")
        assertEquals("LOWER", pack.categoryName)
    }

    @Test
    fun `generateMediaForExercise routes split exercises to LOWER category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Split Squat")
        assertEquals("LOWER", pack.categoryName)
    }

    @Test
    fun `generateMediaForExercise routes crunch exercises to CORE category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Bicycle Crunches")
        assertEquals("CORE", pack.categoryName)
    }

    @Test
    fun `generateMediaForExercise routes leg raise exercises to CORE category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Hanging Leg Raise")
        assertEquals("CORE", pack.categoryName)
    }

    @Test
    fun `generateMediaForExercise routes twist exercises to CORE category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Seated Twist")
        assertEquals("CORE", pack.categoryName)
    }

    @Test
    fun `generateMediaForExercise routes abs exercises to CORE category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Abs Circuit")
        assertEquals("CORE", pack.categoryName)
    }

    @Test
    fun `generateMediaForExercise routes jack exercises to CARDIO category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Jumping Jacks")
        assertEquals("CARDIO", pack.categoryName)
    }

    @Test
    fun `generateMediaForExercise routes burpee exercises to CARDIO category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Burpees")
        assertEquals("CARDIO", pack.categoryName)
    }

    @Test
    fun `generateMediaForExercise routes knee climber exercises to CARDIO category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Mountain Knee Climbers")
        assertEquals("CARDIO", pack.categoryName)
    }

    @Test
    fun `generateMediaForExercise routes curl exercises to PULL category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Bicep Curls")
        assertEquals("PULL", pack.categoryName)
    }

    @Test
    fun `generateMediaForExercise routes row exercises to PULL category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Dumbbell Row")
        assertEquals("PULL", pack.categoryName)
    }

    @Test
    fun `generateMediaForExercise routes pull exercises to PULL category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Pull-ups")
        assertEquals("PULL", pack.categoryName)
    }

    @Test
    fun `generateMediaForExercise routes unknown exercises to FULL default category`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Dead Bug")
        assertEquals("FULL", pack.categoryName)
    }

    // -----------------------------------------------------------------------
    //  Structural integrity tests — every pack must have valid data
    // -----------------------------------------------------------------------

    private val allExerciseNames = listOf(
        "Bodyweight Squats",
        "Push-ups",
        "Plank Hold",
        "Walking Lunges",
        "Bicycle Crunches",
        "Jumping Jacks",
        "Bicep Curls",
        "Dead Bug"  // triggers default
    )

    @Test
    fun `every exercise generates a valid media pack with non-blank name`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            assertEquals(name, pack.exerciseName)
            assertFalse("Category name must not be blank for $name", pack.categoryName.isBlank())
        }
    }

    @Test
    fun `every exercise generates a non-blank Nanobanana prompt`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            assertFalse("Nanobanana prompt must not be blank for $name", pack.nanobananaPrompt.isBlank())
            assertTrue("Nanobanana prompt should mention the exercise name for $name",
                pack.nanobananaPrompt.contains(name, ignoreCase = true))
        }
    }

    @Test
    fun `every exercise generates at least 3 procedure steps`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            assertTrue("Expected at least 3 procedure steps for $name, got ${pack.procedureSteps.size}",
                pack.procedureSteps.size >= 3)
        }
    }

    @Test
    fun `procedure steps have sequential step numbers starting at 1`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            pack.procedureSteps.forEachIndexed { index, step ->
                assertEquals("Step number mismatch at index $index for $name",
                    index + 1, step.stepNumber)
            }
        }
    }

    @Test
    fun `procedure steps have non-blank titles and instructions`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            pack.procedureSteps.forEach { step ->
                assertFalse("Step ${step.stepNumber} must have a title for $name", step.title.isBlank())
                assertFalse("Step ${step.stepNumber} must have an instruction for $name",
                    step.instruction.isBlank())
            }
        }
    }

    @Test
    fun `procedure steps have non-blank breathing and biomechanics cues`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            pack.procedureSteps.forEach { step ->
                assertFalse("Step ${step.stepNumber} must have a breathing cue for $name",
                    step.breathingCue.isBlank())
                assertFalse("Step ${step.stepNumber} must have a biomechanics cue for $name",
                    step.biomechanicsCue.isBlank())
            }
        }
    }

    @Test
    fun `procedure steps have non-empty target muscle lists`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            pack.procedureSteps.forEach { step ->
                assertTrue("Step ${step.stepNumber} must have target muscles for $name",
                    step.targetMuscles.isNotEmpty())
            }
        }
    }

    @Test
    fun `procedure steps have valid progress fractions from 0.0 to 1.0`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            pack.procedureSteps.forEach { step ->
                assertTrue("Progress fraction out of range for step ${step.stepNumber} in $name",
                    step.progressFraction in 0.0f..1.0f)
            }
            // First step should be 0.0, last step should be 1.0
            assertEquals(0.0f, pack.procedureSteps.first().progressFraction, 0.01f)
            assertEquals(1.0f, pack.procedureSteps.last().progressFraction, 0.01f)
        }
    }

    @Test
    fun `procedure steps have non-blank key joint angles`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            pack.procedureSteps.forEach { step ->
                assertFalse("Step ${step.stepNumber} must have key joint angles for $name",
                    step.keyJointAngles.isBlank())
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Video walkthrough tests
    // -----------------------------------------------------------------------

    @Test
    fun `every exercise generates a video walkthrough with non-blank title`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            val video = pack.videoWalkthrough
            assertFalse("Video title must not be blank for $name", video.videoTitle.isBlank())
            assertTrue("Video title should mention the exercise for $name",
                video.videoTitle.contains(name, ignoreCase = true))
        }
    }

    @Test
    fun `every exercise generates a video walkthrough with positive duration`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            assertTrue("Video duration must be positive for $name",
                pack.videoWalkthrough.totalDurationSeconds > 0)
        }
    }

    @Test
    fun `every exercise generates a video walkthrough with non-blank prompt`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            assertFalse("Video prompt must not be blank for $name",
                pack.videoWalkthrough.videoPrompt.isBlank())
        }
    }

    @Test
    fun `every exercise generates at least 4 walkthrough phases`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            val phases = pack.videoWalkthrough.phases
            assertTrue("Expected at least 4 walkthrough phases for $name, got ${phases.size}",
                phases.size >= 4)
        }
    }

    @Test
    fun `walkthrough phases have sequential phase indices starting at 1`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            pack.videoWalkthrough.phases.forEachIndexed { index, phase ->
                assertEquals("Phase index mismatch at index $index for $name",
                    index + 1, phase.phaseIndex)
            }
        }
    }

    @Test
    fun `walkthrough phases have non-blank titles`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            pack.videoWalkthrough.phases.forEach { phase ->
                assertFalse("Phase ${phase.phaseIndex} must have a title for $name",
                    phase.title.isBlank())
            }
        }
    }

    @Test
    fun `walkthrough phases have non-blank coaching audio prompts`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            pack.videoWalkthrough.phases.forEach { phase ->
                assertFalse("Phase ${phase.phaseIndex} must have a coaching audio prompt for $name",
                    phase.coachingAudioPrompt.isBlank())
            }
        }
    }

    @Test
    fun `walkthrough phases have non-blank breathing directives`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            pack.videoWalkthrough.phases.forEach { phase ->
                assertFalse("Phase ${phase.phaseIndex} must have a breathing directive for $name",
                    phase.breathingDirective.isBlank())
            }
        }
    }

    @Test
    fun `walkthrough phases have non-blank form inspection checks`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            pack.videoWalkthrough.phases.forEach { phase ->
                assertFalse("Phase ${phase.phaseIndex} must have a form inspection check for $name",
                    phase.formInspectionCheck.isBlank())
            }
        }
    }

    @Test
    fun `walkthrough phases have non-blank camera perspectives`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            pack.videoWalkthrough.phases.forEach { phase ->
                assertFalse("Phase ${phase.phaseIndex} must have a camera perspective for $name",
                    phase.cameraPerspective.isBlank())
            }
        }
    }

    @Test
    fun `walkthrough phases have positive duration seconds`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            pack.videoWalkthrough.phases.forEach { phase ->
                assertTrue("Phase ${phase.phaseIndex} duration must be positive for $name",
                    phase.durationSeconds > 0f)
            }
        }
    }

    @Test
    fun `walkthrough phases cover the total video duration`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            val video = pack.videoWalkthrough
            val totalPhaseDuration = video.phases.map { it.durationSeconds }.sum()
            val expectedSeconds = video.totalDurationSeconds.toFloat()
            // Allow small floating-point tolerance
            assertTrue("Phase durations should roughly equal total duration for $name. " +
                    "Total phases: $totalPhaseDuration, Expected: $expectedSeconds",
                Math.abs(totalPhaseDuration - expectedSeconds) < 1f)
        }
    }

    @Test
    fun `walkthrough phases have contiguous start times`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            val phases = pack.videoWalkthrough.phases
            // First phase should start at 0
            assertEquals(0f, phases.first().startSeconds, 0.01f)
            // Each subsequent phase should start where the previous ended
            for (i in 1 until phases.size) {
                val prevEnd = phases[i - 1].startSeconds + phases[i - 1].durationSeconds
                assertEquals("Phase ${phases[i].phaseIndex} start time mismatch for $name",
                    prevEnd, phases[i].startSeconds, 0.01f)
            }
        }
    }

    @Test
    fun `every exercise generates a non-blank audio narration script`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            assertFalse("Audio narration script must not be blank for $name",
                pack.videoWalkthrough.audioNarrationScript.isBlank())
        }
    }

    // -----------------------------------------------------------------------
    //  Media source and default values
    // -----------------------------------------------------------------------

    @Test
    fun `generated packs default to LOCAL_SYNTHESIS media source`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            assertEquals(WorkoutMediaSource.LOCAL_SYNTHESIS, pack.mediaSource)
        }
    }

    @Test
    fun `generated packs have empty nanobanana images map by default`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            assertTrue("Nanobanana images should be empty for local synthesis of $name",
                pack.nanobananaImages.isEmpty())
        }
    }

    @Test
    fun `generated packs have empty generation notes by default`() {
        allExerciseNames.forEach { name ->
            val pack = WorkoutMediaGenerator.generateMediaForExercise(name)
            assertTrue("Generation notes should be empty for local synthesis of $name",
                pack.generationNotes.isEmpty())
        }
    }

    // -----------------------------------------------------------------------
    //  Routine-level generation test
    // -----------------------------------------------------------------------

    @Test
    fun `generateMediaForRoutine produces a pack for each exercise`() {
        val exercises = listOf("Bodyweight Squats", "Push-ups", "Plank Hold", "Jumping Jacks")
        val packs = WorkoutMediaGenerator.generateMediaForRoutine(exercises)

        assertEquals(exercises.size, packs.size)
        exercises.forEach { name ->
            assertTrue("Routine should contain pack for $name", packs.containsKey(name))
            assertEquals(name, packs[name]?.exerciseName)
        }
    }

    @Test
    fun `generateMediaForRoutine passes goal parameter`() {
        val exercises = listOf("Bodyweight Squats")
        val packs = WorkoutMediaGenerator.generateMediaForRoutine(
            exercises = exercises,
            goal = "Hypertrophy",
            experienceLevel = "Advanced"
        )

        val pack = packs["Bodyweight Squats"]
        assertNotNull(pack)
        // The pack's nanobanana prompt should contain exercise-specific content
        assertFalse(pack!!.nanobananaPrompt.isBlank())
    }

    @Test
    fun `generateMediaForRoutine with empty list returns empty map`() {
        val packs = WorkoutMediaGenerator.generateMediaForRoutine(emptyList())
        assertTrue(packs.isEmpty())
    }

    // -----------------------------------------------------------------------
    //  Case-insensitive exercise name matching
    // -----------------------------------------------------------------------

    @Test
    fun `exercise name matching is case insensitive`() {
        val packLower = WorkoutMediaGenerator.generateMediaForExercise("bodyweight squats")
        val packUpper = WorkoutMediaGenerator.generateMediaForExercise("BODYWEIGHT SQUATS")
        val packMixed = WorkoutMediaGenerator.generateMediaForExercise("Bodyweight Squats")

        assertEquals("LOWER", packLower.categoryName)
        assertEquals("LOWER", packUpper.categoryName)
        assertEquals("LOWER", packMixed.categoryName)
    }

    // -----------------------------------------------------------------------
    //  Specific content validation for key exercises
    // -----------------------------------------------------------------------

    @Test
    fun `squat pack targets quadriceps and glutes`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Bodyweight Squats")
        val allMuscles = pack.procedureSteps.flatMap { it.targetMuscles }
        assertTrue("Squat should target quadriceps", allMuscles.any { it.contains("Quadriceps", ignoreCase = true) })
        assertTrue("Squat should target glutes", allMuscles.any { it.contains("Glutes", ignoreCase = true) })
    }

    @Test
    fun `push-up pack targets pectorals and triceps`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Push-ups")
        val allMuscles = pack.procedureSteps.flatMap { it.targetMuscles }
        assertTrue("Push-up should target pectorals", allMuscles.any { it.contains("Pectoral", ignoreCase = true) })
        assertTrue("Push-up should target triceps", allMuscles.any { it.contains("Triceps", ignoreCase = true) })
    }

    @Test
    fun `plank pack targets core stabilizers`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Plank Hold")
        val allMuscles = pack.procedureSteps.flatMap { it.targetMuscles }
        assertTrue("Plank should target core", allMuscles.any { it.contains("Core", ignoreCase = true) || it.contains("Abdominis", ignoreCase = true) })
    }

    @Test
    fun `default pack includes exercise name in instruction`() {
        val pack = WorkoutMediaGenerator.generateMediaForExercise("Unknown Exercise")
        val step2 = pack.procedureSteps.find { it.stepNumber == 2 }
        assertNotNull(step2)
        assertTrue("Default pack step 2 should mention exercise name",
            step2!!.instruction.contains("Unknown Exercise", ignoreCase = true))
    }
}
