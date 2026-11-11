package com.fitlens.app.pose

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Normalized 3D landmark representation.
 */
data class LandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float = 1.0f
)

/**
 * Exercise rep state machine.
 */
enum class RepPhase {
    START,
    ECCENTRIC, // lowering / flexion
    INFLECTION, // lowest point
    CONCENTRIC, // rising / extension
    COMPLETED
}

/**
 * Biomechanical form deviation warning.
 */
data class FormDeviation(
    val jointName: String,
    val message: String,
    val severity: DeviationSeverity
)

enum class DeviationSeverity {
    INFO,
    WARNING,
    CRITICAL
}

/**
 * Pose analysis result emitted for each frame.
 */
data class PoseAnalysisFrame(
    val landmarks: Map<Int, LandmarkPoint>,
    val currentExercise: String,
    val currentRepCount: Int,
    val currentAngle: Double,
    val repPhase: RepPhase,
    val activeDeviations: List<FormDeviation>,
    val formScore: Float
)

/**
 * PoseLandmarkManager processes skeletal landmarks from MediaPipe PoseLandmarker
 * and computes joint angles, rep counts, and biomechanical form checks.
 */
class PoseLandmarkManager(
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "PoseLandmarkManager"

        // MediaPipe Pose landmark indices
        const val NOSE = 0
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_ELBOW = 13
        const val RIGHT_ELBOW = 14
        const val LEFT_WRIST = 15
        const val RIGHT_WRIST = 16
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        const val LEFT_KNEE = 25
        const val RIGHT_KNEE = 26
        const val LEFT_ANKLE = 27
        const val RIGHT_ANKLE = 28
    }

    private var activeExercise: String = "Squat"
    private var repCount: Int = 0
    private var currentPhase: RepPhase = RepPhase.START
    private var minAngleObserved: Double = 180.0
    private var totalDeviationsCount: Int = 0

    // MediaPipe PoseLandmarker instance (lazy-initialized on first frame)
    @Volatile private var mpLandmarker: PoseLandmarker? = null

    /**
     * Initializes the MediaPipe PoseLandmarker on the first call to [analyzeFrame].
     * Uses the bundled pose landmarker task binary resource.
     */
    private fun ensureLandmarker(): PoseLandmarker? {
        return mpLandmarker ?: runCatching {
            context?.let { ctx ->
                val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath("pose_landmarker.task")
                            .build()
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    .setMinPoseDetectionConfidence(0.5f)
                    .setMinPosePresenceConfidence(0.5f)
                    .setOutputSegmentationMasks(false)
                    .setOutputLandmarksWorldCoordinates(true)
                    .build()
                PoseLandmarker.createFromOptions(ctx, options).also {
                    mpLandmarker = it
                    Log.d(TAG, "MediaPipe PoseLandmarker initialized successfully")
                }
            }
        }.getOrNull()
    }

    /**
     * Analyzes a CameraX ImageProxy frame via MediaPipe PoseLandmarker.
     * Converts the YUV_420_888 ImageProxy to a Bitmap, runs pose detection,
     * and returns a [PoseAnalysisFrame] with the detected landmarks.
     *
     * The caller (DualStreamAnalyzer) is responsible for closing the ImageProxy
     * after this method returns.
     */
    fun analyzeFrame(imageProxy: ImageProxy): PoseAnalysisFrame {
        val landmarker = ensureLandmarker()
            ?: return analyzeLandmarks(emptyMap())

        return runCatching {
            // Convert ImageProxy to Bitmap, then to MediaPipe MPImage
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            val mpResult = landmarker.detect(mpImage)
            mpImage.close()
            bitmap.recycle()

            // Convert MediaPipe landmarks to our LandmarkPoint format
            val landmarks = landmarksFromMediaPipe(mpResult, imageProxy.width, imageProxy.height)
            analyzeLandmarks(landmarks)
        }.getOrElse { e ->
            Log.w(TAG, "MediaPipe frame analysis failed: ${e.message}")
            analyzeLandmarks(emptyMap())
        }
    }

    /**
     * Converts a CameraX ImageProxy (YUV_420_888) to an ARGB_8888 Bitmap for MediaPipe.
     * Extracts the Y (luminance) plane and creates a grayscale bitmap.
     */
    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        // For YUV_420_888, we create a grayscale approximation from Y plane
        val ySize = bytes.size
        val w = width
        val h = height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        for (i in 0 until ySize.coerceAtMost(w * h)) {
            val y = bytes[i].toInt() and 0xFF
            pixels[i] = -0x1000000 or (y shl 16) or (y shl 8) or y
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    /**
     * Converts MediaPipe PoseLandmarkerResult to our LandmarkPoint map.
     */
    private fun landmarksFromMediaPipe(
        result: PoseLandmarkerResult,
        imageWidth: Int,
        imageHeight: Int
    ): Map<Int, LandmarkPoint> {
        val map = mutableMapOf<Int, LandmarkPoint>()
        result.landmarks().forEachIndexed { categoryIndex, pose ->
            pose.forEachIndexed { landmarkIndex, landmark ->
                // Normalize coordinates to 0..1 range
                val x = landmark.x().coerceIn(0f, 1f)
                val y = landmark.y().coerceIn(0f, 1f)
                val z = landmark.z()
                val visibility = landmark.visibility().orElse(1.0f)
                map[landmarkIndex] = LandmarkPoint(x, y, z, visibility)
            }
        }
        return map
    }

    fun setExercise(exercise: String) {
        activeExercise = exercise
        resetRepCounter()
    }

    fun resetRepCounter() {
        repCount = 0
        currentPhase = RepPhase.START
        minAngleObserved = 180.0
        totalDeviationsCount = 0
    }

    fun getTotalDeviations(): Int = totalDeviationsCount
    fun getRepCount(): Int = repCount

    /**
     * Calculates the angle in degrees formed by three points (A -> B -> C) at vertex B.
     */
    fun calculateAngle(a: LandmarkPoint, b: LandmarkPoint, c: LandmarkPoint): Double {
        val radians = atan2(c.y - b.y, c.x - b.x) - atan2(a.y - b.y, a.x - b.x)
        var angle = abs(radians * 180.0 / Math.PI)
        if (angle > 180.0) {
            angle = 360.0 - angle
        }
        return angle
    }

    /**
     * Analyzes landmark map, detects reps, and checks exercise form.
     */
    fun analyzeLandmarks(landmarks: Map<Int, LandmarkPoint>): PoseAnalysisFrame {
        val deviations = mutableListOf<FormDeviation>()
        var primaryAngle = 180.0

        when (activeExercise.lowercase()) {
            "squat", "squats", "bodyweight squats", "barbell squat" -> {
                primaryAngle = analyzeSquat(landmarks, deviations)
            }
            "push-up", "push-ups" -> {
                primaryAngle = analyzePushUp(landmarks, deviations)
            }
            "lunge", "lunges" -> {
                primaryAngle = analyzeLunge(landmarks, deviations)
            }
            "plank" -> {
                primaryAngle = analyzePlank(landmarks, deviations)
            }
            else -> {
                // Default joint angle tracking (knee angle)
                val hip = landmarks[LEFT_HIP] ?: landmarks[RIGHT_HIP]
                val knee = landmarks[LEFT_KNEE] ?: landmarks[RIGHT_KNEE]
                val ankle = landmarks[LEFT_ANKLE] ?: landmarks[RIGHT_ANKLE]
                if (hip != null && knee != null && ankle != null) {
                    primaryAngle = calculateAngle(hip, knee, ankle)
                }
            }
        }

        if (deviations.isNotEmpty()) {
            totalDeviationsCount += deviations.count { it.severity == DeviationSeverity.CRITICAL }
        }

        val formScore = maxOf(0f, 100f - (totalDeviationsCount * 2.5f))

        return PoseAnalysisFrame(
            landmarks = landmarks,
            currentExercise = activeExercise,
            currentRepCount = repCount,
            currentAngle = primaryAngle,
            repPhase = currentPhase,
            activeDeviations = deviations,
            formScore = formScore
        )
    }

    private fun analyzeSquat(
        landmarks: Map<Int, LandmarkPoint>,
        deviations: MutableList<FormDeviation>
    ): Double {
        val hip = landmarks[LEFT_HIP] ?: landmarks[RIGHT_HIP]
        val knee = landmarks[LEFT_KNEE] ?: landmarks[RIGHT_KNEE]
        val ankle = landmarks[LEFT_ANKLE] ?: landmarks[RIGHT_ANKLE]
        val shoulder = landmarks[LEFT_SHOULDER] ?: landmarks[RIGHT_SHOULDER]

        if (hip == null || knee == null || ankle == null) {
            return 180.0
        }

        // Knee flexion angle: ~170-180 standing, ~80-90 at full squat depth
        val kneeAngle = calculateAngle(hip, knee, ankle)

        // Torso inclination angle
        if (shoulder != null) {
            val backAngle = calculateAngle(shoulder, hip, knee)
            if (backAngle < 60.0 && kneeAngle < 120.0) {
                deviations.add(
                    FormDeviation(
                        jointName = "Back",
                        message = "Excessive torso forward lean. Keep chest up.",
                        severity = DeviationSeverity.WARNING
                    )
                )
            }
        }

        // Knee valgus check (distance between knees vs distance between ankles)
        val lKnee = landmarks[LEFT_KNEE]
        val rKnee = landmarks[RIGHT_KNEE]
        val lAnkle = landmarks[LEFT_ANKLE]
        val rAnkle = landmarks[RIGHT_ANKLE]
        if (lKnee != null && rKnee != null && lAnkle != null && rAnkle != null) {
            val kneeDist = abs(lKnee.x - rKnee.x)
            val ankleDist = abs(lAnkle.x - rAnkle.x)
            if (kneeDist < ankleDist * 0.75f && kneeAngle < 120.0) {
                deviations.add(
                    FormDeviation(
                        jointName = "Knees",
                        message = "Knees caving inward (valgus). Push knees outward.",
                        severity = DeviationSeverity.CRITICAL
                    )
                )
            }
        }

        // Rep detection state machine
        updateRepStateMachine(
            angle = kneeAngle,
            inflectionThreshold = 95.0,
            completionThreshold = 160.0
        )

        return kneeAngle
    }

    private fun analyzePushUp(
        landmarks: Map<Int, LandmarkPoint>,
        deviations: MutableList<FormDeviation>
    ): Double {
        val shoulder = landmarks[LEFT_SHOULDER] ?: landmarks[RIGHT_SHOULDER]
        val elbow = landmarks[LEFT_ELBOW] ?: landmarks[RIGHT_ELBOW]
        val wrist = landmarks[LEFT_WRIST] ?: landmarks[RIGHT_WRIST]
        val hip = landmarks[LEFT_HIP] ?: landmarks[RIGHT_HIP]
        val ankle = landmarks[LEFT_ANKLE] ?: landmarks[RIGHT_ANKLE]

        if (shoulder == null || elbow == null || wrist == null) {
            return 180.0
        }

        val elbowAngle = calculateAngle(shoulder, elbow, wrist)

        // Body straightness check
        if (hip != null && ankle != null) {
            val bodyLineAngle = calculateAngle(shoulder, hip, ankle)
            if (bodyLineAngle < 155.0) {
                deviations.add(
                    FormDeviation(
                        jointName = "Core/Hips",
                        message = "Sagging hips detected. Engage core to maintain straight line.",
                        severity = DeviationSeverity.WARNING
                    )
                )
            }
        }

        // Rep detection: elbow < 90 is inflection, > 155 is extension
        updateRepStateMachine(
            angle = elbowAngle,
            inflectionThreshold = 90.0,
            completionThreshold = 155.0
        )

        return elbowAngle
    }

    private fun analyzeLunge(
        landmarks: Map<Int, LandmarkPoint>,
        deviations: MutableList<FormDeviation>
    ): Double {
        val lHip = landmarks[LEFT_HIP]
        val lKnee = landmarks[LEFT_KNEE]
        val lAnkle = landmarks[LEFT_ANKLE]
        val rHip = landmarks[RIGHT_HIP]
        val rKnee = landmarks[RIGHT_KNEE]
        val rAnkle = landmarks[RIGHT_ANKLE]

        val leftKneeAngle = if (lHip != null && lKnee != null && lAnkle != null) calculateAngle(lHip, lKnee, lAnkle) else 180.0
        val rightKneeAngle = if (rHip != null && rKnee != null && rAnkle != null) calculateAngle(rHip, rKnee, rAnkle) else 180.0

        // The working/leading leg has the sharper flexion (< 120)
        val workingKneeAngle = minOf(leftKneeAngle, rightKneeAngle)

        // Check if front knee goes past excessive flexion (< 75 deg, excessive forward drive)
        if (workingKneeAngle < 75.0) {
            deviations.add(
                FormDeviation(
                    jointName = "Front Knee",
                    message = "Front knee driving excessively forward. Step further forward.",
                    severity = DeviationSeverity.WARNING
                )
            )
        }

        updateRepStateMachine(
            angle = workingKneeAngle,
            inflectionThreshold = 95.0,
            completionThreshold = 160.0
        )

        return workingKneeAngle
    }

    private fun analyzePlank(
        landmarks: Map<Int, LandmarkPoint>,
        deviations: MutableList<FormDeviation>
    ): Double {
        val shoulder = landmarks[LEFT_SHOULDER] ?: landmarks[RIGHT_SHOULDER]
        val hip = landmarks[LEFT_HIP] ?: landmarks[RIGHT_HIP]
        val ankle = landmarks[LEFT_ANKLE] ?: landmarks[RIGHT_ANKLE]

        if (shoulder == null || hip == null || ankle == null) {
            return 180.0
        }

        val bodyAngle = calculateAngle(shoulder, hip, ankle)

        if (bodyAngle < 155.0) {
            deviations.add(
                FormDeviation(
                    jointName = "Hips",
                    message = "Hips sagging. Squeeze glutes and brace abdominals.",
                    severity = DeviationSeverity.CRITICAL
                )
            )
        } else if (bodyAngle > 205.0) {
            deviations.add(
                FormDeviation(
                    jointName = "Hips",
                    message = "Hips piked too high. Lower hips into a straight plank line.",
                    severity = DeviationSeverity.WARNING
                )
            )
        }

        return bodyAngle
    }

    private fun updateRepStateMachine(
        angle: Double,
        inflectionThreshold: Double,
        completionThreshold: Double
    ) {
        when (currentPhase) {
            RepPhase.START -> {
                if (angle < completionThreshold - 10.0) {
                    currentPhase = RepPhase.ECCENTRIC
                    minAngleObserved = angle
                }
            }
            RepPhase.ECCENTRIC -> {
                if (angle < minAngleObserved) {
                    minAngleObserved = angle
                }
                if (angle <= inflectionThreshold) {
                    currentPhase = RepPhase.INFLECTION
                } else if (angle > completionThreshold) {
                    // Aborted rep
                    currentPhase = RepPhase.START
                }
            }
            RepPhase.INFLECTION -> {
                if (angle > inflectionThreshold + 10.0) {
                    currentPhase = RepPhase.CONCENTRIC
                }
            }
            RepPhase.CONCENTRIC -> {
                if (angle >= completionThreshold) {
                    repCount++
                    currentPhase = RepPhase.COMPLETED
                    Log.d(TAG, "Rep completed! Total reps: $repCount (exercise: $activeExercise)")
                }
            }
            RepPhase.COMPLETED -> {
                currentPhase = RepPhase.START
                minAngleObserved = 180.0
            }
        }
    }
}
