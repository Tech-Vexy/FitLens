package com.fitlens.app.ai.adk

import android.util.Log
import com.fitlens.app.ai.FatigueCheckResult
import com.fitlens.app.ai.MesocyclePlanResult
import com.fitlens.app.ai.RestAdjustmentResult
import com.fitlens.app.ai.SessionAnalysisResult
import com.fitlens.app.ai.WorkoutPlanResult
import java.lang.reflect.Method

/**
 * LocalEdgeEngine is the deterministic on-device inference backend for
 * [AdkAuthMode.ON_DEVICE_EDGE].
 *
 * It replaces the previous placeholder response with a real (network-free)
 * tool-routing layer that mirrors how an ADK agent dispatches to @Tool
 * functions:
 *
 *  1. Discovers every method annotated with [Tool] on the agent's tool objects.
 *  2. Scores each tool by keyword overlap between the camelCase method name
 *     (plus domain synonyms) and the normalized prompt.
 *  3. Extracts arguments from the prompt — numbers are matched to parameters
 *     by semantic keyword proximity, strings via domain keyword dictionaries.
 *  4. Invokes the best-scoring tool via reflection and formats the typed
 *     result into a coaching response.
 *
 * Everything runs locally with zero network access and zero model downloads,
 * which keeps sub-10ms safety-path latency for fatigue checks during a set.
 */
class LocalEdgeEngine {

    companion object {
        private const val TAG = "LocalEdgeEngine"

        /** Max char distance between a keyword hit and a number for them to pair. */
        private const val MAX_KEYWORD_DISTANCE = 48

        /** Generic tokens that carry no intent signal when matching tool names. */
        private val STOP_TOKENS = setOf(
            "generate", "calculate", "evaluate", "analyze", "request", "check",
            "get", "with", "from", "the", "for", "and", "result"
        )

        /** Domain synonyms appended to the normalized prompt before scoring. */
        private val SYNONYMS: Map<String, String> = mapOf(
            "recovery" to "rest",
            "fatigue" to "deviation form breakdown",
            "workout" to "plan session exercise",
            "routine" to "plan workout",
            "periodization" to "mesocycle week phase",
            "conditioning" to "endurance cardio",
            "soreness" to "recovery rest"
        )

        /**
         * Fallback parameter names keyed by method name, used only when the
         * Kotlin compiler did not emit Java parameter names
         * (i.e. -java-parameters is off). Mirrors FitnessTools signatures.
         */
        private val FALLBACK_PARAM_NAMES: Map<String, List<String>> = mapOf(
            "generateWorkoutPlan" to listOf("goal", "experience", "equipment", "durationMinutes"),
            "analyzeSession" to listOf("sessionDurationSeconds", "averageHeartRateBpm", "formDeviations"),
            "calculateAdaptiveRest" to listOf("baseRestSeconds", "currentHeartRateBpm", "restingHeartRateBpm"),
            "evaluateBiomechanicalFatigue" to listOf("consecutiveDeviations", "currentSetRepsCompleted", "targetReps"),
            "generateMesocyclePeriodization" to listOf("goal", "weeksCount", "daysPerWeek")
        )

        /** Defaults for Kotlin default parameters, which Java reflection can't see. */
        private val OPTIONAL_PARAM_DEFAULTS: Map<String, Any> = mapOf(
            "restingHeartRateBpm" to 70.0,
            "weeksCount" to 4,
            "daysPerWeek" to 3
        )

        /** Keywords used to locate the numeric argument for a parameter. */
        private val PARAM_KEYWORDS: Map<String, List<String>> = mapOf(
            "heartRate" to listOf("heart rate", "hr", "bpm", "pulse"),
            "currentHeartRateBpm" to listOf("heart rate", "hr", "bpm", "pulse"),
            "averageHeartRateBpm" to listOf("heart rate", "hr", "bpm", "pulse"),
            "restingHeartRateBpm" to listOf("resting heart rate", "resting", "rhr"),
            "sessionDurationSeconds" to listOf("duration", "seconds", "elapsed"),
            "durationMinutes" to listOf("duration", "minutes", "minute", "min"),
            "baseRestSeconds" to listOf("rest"),
            "currentSetRepsCompleted" to listOf("reps completed", "reps", "rep"),
            "targetReps" to listOf("target reps", "reps", "rep"),
            "consecutiveDeviations" to listOf("consecutive", "deviations", "deviation"),
            "formDeviations" to listOf("deviations", "deviation", "form"),
            "weeksCount" to listOf("weeks", "week"),
            "daysPerWeek" to listOf("days per week", "days", "day")
        )

        /** Name fragments worth using as keywords when no explicit entry exists. */
        private val GENERIC_KEYWORD_TOKENS = setOf(
            "heart", "rate", "bpm", "pulse", "rep", "reps", "set", "sets",
            "week", "weeks", "day", "days", "duration", "rest", "deviation",
            "deviations", "form", "second", "seconds", "minute", "minutes"
        )

        private val NUMBER_REGEX = Regex("""-?\d+(?:\.\d+)?""")
        private val CAMEL_BOUNDARY = Regex("(?<=[a-z])(?=[A-Z])")
        private val ARG_NAME = Regex("""arg\d+""")
    }

    private data class ParamSpec(
        val name: String,
        val javaType: Class<*>,
        val optionalDefault: Any?
    )

    private data class ToolCandidate(
        val method: Method,
        val declaringObject: Any,
        val params: List<ParamSpec>,
        val score: Int
    )

    /**
     * Routes [prompt] to the best-matching @Tool method on [toolObjects]
     * and returns a human-readable coaching response. Falls back to a
     * capability summary when no tool matches or invocation fails.
     */
    fun generate(toolObjects: List<Any>, prompt: String): String {
        val normalizedPrompt = prompt.lowercase()
        val expandedPrompt = expandWithSynonyms(normalizedPrompt)

        val candidates = discoverTools(toolObjects)
            .map { (method, owner, params) ->
                ToolCandidate(method, owner, params, scoreTool(method, expandedPrompt))
            }
            .filter { it.score >= 1 }
            .sortedByDescending { it.score }

        for (candidate in candidates) {
            val args = resolveArguments(candidate.params, prompt, normalizedPrompt)
                ?: continue
            val result = runCatching {
                candidate.method.invoke(candidate.declaringObject, *args)
            }.onFailure {
                Log.w(TAG, "Tool ${candidate.method.name} invocation failed: ${it.message}")
            }.getOrNull() ?: continue

            return formatResult(result)
        }

        return fallbackResponse()
    }

    // ------------------------------------------------------------------
    // Tool discovery & scoring
    // ------------------------------------------------------------------

    private fun discoverTools(toolObjects: List<Any>): List<Triple<Method, Any, List<ParamSpec>>> =
        toolObjects.flatMap { owner ->
            owner.javaClass.methods.mapNotNull { method ->
                if (method.getAnnotation(Tool::class.java) == null) return@mapNotNull null
                val params = method.parameters.mapIndexed { index, p ->
                    val name = resolveParamName(method, index)
                    ParamSpec(name, p.type, OPTIONAL_PARAM_DEFAULTS[name])
                }
                Triple(method, owner, params)
            }
        }.distinctBy { it.first }

    private fun resolveParamName(method: Method, index: Int): String {
        val javaName = method.parameters.getOrNull(index)?.name
        if (!javaName.isNullOrEmpty() && !ARG_NAME.matches(javaName)) return javaName
        return FALLBACK_PARAM_NAMES[method.name]?.getOrNull(index) ?: "param$index"
    }

    private fun expandWithSynonyms(normalizedPrompt: String): String = buildString {
        append(normalizedPrompt)
        SYNONYMS.forEach { (trigger, expansion) ->
            if (normalizedPrompt.contains(trigger)) append(' ').append(expansion)
        }
    }

    private fun scoreTool(method: Method, expandedPrompt: String): Int =
        method.name
            .split(CAMEL_BOUNDARY)
            .map { it.lowercase() }
            .filter { it.length >= 4 && it !in STOP_TOKENS }
            .count { token -> expandedPrompt.contains(token) }

    // ------------------------------------------------------------------
    // Argument extraction
    // ------------------------------------------------------------------

    /**
     * Resolves a value for every parameter, or returns null if a required
     * (non-optional) parameter cannot be extracted.
     */
    private fun resolveArguments(
        params: List<ParamSpec>,
        rawPrompt: String,
        normalizedPrompt: String
    ): Array<Any?>? {
        if (params.isEmpty()) return emptyArray()

        val numbers = NUMBER_REGEX.findAll(rawPrompt)
            .map { it.value.toDouble() to it.range.first }
            .toList()
        val usedIndices = mutableSetOf<Int>()
        val args = arrayOfNulls<Any>(params.size)

        params.forEachIndexed { index, param ->
            val value = if (param.javaType == String::class.java) {
                extractStringArg(param.name, rawPrompt)
            } else {
                extractNumberArg(param.name, normalizedPrompt, numbers, usedIndices)
            }
            when {
                value != null -> args[index] = coerceToType(value, param.javaType)
                param.optionalDefault != null -> args[index] = param.optionalDefault
                else -> return null
            }
        }
        return args
    }

    private fun coerceToType(value: Any, javaType: Class<*>): Any = when (javaType) {
        java.lang.Integer.TYPE, java.lang.Integer::class.java -> (value as Number).toInt()
        java.lang.Long.TYPE, java.lang.Long::class.java -> (value as Number).toLong()
        java.lang.Float.TYPE, java.lang.Float::class.java -> (value as Number).toFloat()
        java.lang.Double.TYPE, java.lang.Double::class.java -> (value as Number).toDouble()
        else -> value
    }

    /**
     * Picks the number closest to a semantic keyword occurrence in the prompt;
     * falls back to the first unused number, then to null.
     */
    private fun extractNumberArg(
        paramName: String,
        normalizedPrompt: String,
        numbers: List<Pair<Double, Int>>,
        usedIndices: MutableSet<Int>
    ): Double? {
        if (numbers.isEmpty()) return null
        val keywords = keywordsFor(paramName)

        var bestIndex = -1
        var bestDistance = Int.MAX_VALUE
        numbers.forEachIndexed { i, (_, position) ->
            if (i in usedIndices) return@forEachIndexed
            keywords.forEach { keyword ->
                var from = 0
                while (true) {
                    val kwIndex = normalizedPrompt.indexOf(keyword, from)
                    if (kwIndex < 0) break
                    val kwCenter = kwIndex + keyword.length / 2
                    val distance = kotlin.math.abs(position - kwCenter)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestIndex = i
                    }
                    from = kwIndex + keyword.length
                }
            }
        }

        val chosen = when {
            bestIndex >= 0 && bestDistance <= MAX_KEYWORD_DISTANCE -> bestIndex
            else -> numbers.indexOfFirstIndexed { i, _ -> i !in usedIndices }
        }
        if (chosen < 0) return null
        usedIndices += chosen
        return numbers[chosen].first
    }

    private fun List<Pair<Double, Int>>.indexOfFirstIndexed(predicate: (Int, Pair<Double, Int>) -> Boolean): Int =
        indices.firstOrNull { predicate(it, this[it]) } ?: -1

    private fun keywordsFor(paramName: String): List<String> =
        PARAM_KEYWORDS[paramName] ?: paramName
            .split(CAMEL_BOUNDARY)
            .map { it.lowercase() }
            .filter { it in GENERIC_KEYWORD_TOKENS }
            .ifEmpty { listOf(paramName.lowercase()) }

    private fun extractStringArg(paramName: String, prompt: String): String? = when {
        paramName.contains("goal", ignoreCase = true) -> extractGoal(prompt)
        paramName.contains("experience", ignoreCase = true) -> extractExperience(prompt)
        paramName.contains("equipment", ignoreCase = true) -> extractEquipment(prompt)
        paramName.contains("exercise", ignoreCase = true) -> extractExercise(prompt)
        else -> null
    }

    private fun extractGoal(prompt: String): String = when {
        prompt.contains("hypertrophy") || prompt.contains("muscle") || prompt.contains("size") -> "Hypertrophy"
        prompt.contains("strength") || prompt.contains("power") -> "Strength"
        prompt.contains("endurance") || prompt.contains("cardio") || prompt.contains("conditioning") -> "Endurance"
        prompt.contains("mobility") || prompt.contains("flexibility") -> "Mobility"
        prompt.contains("weight loss") || prompt.contains("fat loss") -> "Weight Loss"
        else -> "General Fitness"
    }

    private fun extractExperience(prompt: String): String = when {
        prompt.contains("advanced") || prompt.contains("expert") -> "Advanced"
        prompt.contains("intermediate") || prompt.contains("moderate") -> "Intermediate"
        else -> "Beginner"
    }

    private fun extractEquipment(prompt: String): String = when {
        prompt.contains("dumbbell") -> "Dumbbells"
        prompt.contains("barbell") -> "Barbell"
        prompt.contains("machine") -> "Machines"
        prompt.contains("band") -> "Resistance Bands"
        else -> "Bodyweight"
    }

    private fun extractExercise(prompt: String): String? {
        val match = Regex("""(?:for|of|exercise[:\s]+)([a-zA-Z][a-zA-Z\- ]{2,30})""")
            .find(prompt) ?: return null
        return match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
    }

    // ------------------------------------------------------------------
    // Response formatting
    // ------------------------------------------------------------------

    private fun formatResult(result: Any): String = when (result) {
        is WorkoutPlanResult -> buildString {
            append("Workout plan for ${result.goal} (~${result.targetDurationMinutes} min): ")
            append(result.targetExercises.joinToString(", ")).append(". ")
            append("Suggested rest: ${result.recommendedRestIntervalSeconds}s.")
        }
        is SessionAnalysisResult -> buildString {
            append(result.summary).append(' ')
            append("Exertion ${result.exertionScore}/10, form ${result.formAccuracyPercentage.toInt()}%. ")
            append(result.recommendation)
        }
        is RestAdjustmentResult -> buildString {
            append(result.reason).append(' ')
            append("Rest adjusted ${result.originalRestSeconds}s → ${result.adjustedRestSeconds}s.")
        }
        is FatigueCheckResult -> buildString {
            if (result.shouldHaltSet) append("Set halted — ") else append("Form check — ")
            append(result.correctiveAction)
        }
        is MesocyclePlanResult -> buildString {
            append("${result.totalWeeks}-week mesocycle (${result.goal}, ${result.daysPerWeek} days/week): ")
            append(result.phases.joinToString("; ") { "W${it.weekNumber} ${it.phaseName}" })
        }
        else -> result.toString()
    }

    private fun fallbackResponse(): String =
        "[On-Device Edge] Offline coach. I can generate workout plans, evaluate " +
            "sessions, adapt rest from heart rate, and check biomechanical fatigue " +
            "locally — e.g. \"generate a hypertrophy workout plan\", \"evaluate " +
            "biomechanical fatigue\", or \"calculate adaptive rest\"."
}
