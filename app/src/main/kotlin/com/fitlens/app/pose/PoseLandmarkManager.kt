package com.fitlens.app.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
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
        private const val MAX_ANALYSIS_DIM = 640

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
    // Counts distinct critical-fault episodes (per joint), not per-frame occurrences,
    // so form accuracy doesn't decay to zero during sustained bad form.
    private var totalDeviationsCount: Int = 0
    private var previousCriticalJoints: Set<String> = emptySet()

    // MediaPipe PoseLandmarker instance (lazy-initialized on first frame)
    @Volatile private var mpLandmarker: PoseLandmarker? = null

    // Scratch buffers reused across frames. A full-res YUV_420_888 frame would
    // otherwise allocate a multi-MB IntArray + Bitmap on every camera frame.
    // analyzeFrame only runs on the single-threaded CameraX executor.
    private var scratchRow: ByteArray? = null
    private var scratchPixels: IntArray? = null
    private var scratchBitmap: Bitmap? = null
    private var scratchBitmapW = 0
    private var scratchBitmapH = 0

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
    @Synchronized
    fun analyzeFrame(imageProxy: ImageProxy): PoseAnalysisFrame {
        val landmarker = ensureLandmarker()
            ?: return analyzeLandmarks(emptyMap())

        // MediaPipe pose requires YUV_420_888; bail out cleanly for other formats.
        if (imageProxy.format != ImageFormat.YUV_420_888) {
            Log.w(TAG, "Unsupported image format: ${imageProxy.format}")
            return analyzeLandmarks(emptyMap())
        }

        return runCatching {
            // Convert ImageProxy to Bitmap, then to MediaPipe MPImage
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            val mpResult = landmarker.detect(mpImage)
            mpImage.close()
            // bitmap is a reused scratch buffer — do NOT recycle it here.

            val landmarks = landmarksFromMediaPipe(mpResult)
            analyzeLandmarks(landmarks)
        }.getOrElse { e ->
            Log.w(TAG, "MediaPipe frame analysis failed: ${e.message}")
            analyzeLandmarks(emptyMap())
        }
    }

    /**
     * Converts a CameraX ImageProxy (YUV_420_888) to a downscaled ARGB_8888 Bitmap
     * for MediaPipe, reading only the Y (luminance) plane as a grayscale approximation.
     *
     * The frame is downscaled to at most [MAX_ANALYSIS_DIM] pixels on the long edge:
     * MediaPipe internally resizes to its model input anyway, and pose landmarks are
     * normalized coordinates, so detection accuracy is unaffected while per-frame
     * conversion + inference cost drops substantially.
     *
     * The plane buffer is duplicated before reading so the original buffer position is
     * preserved for downstream consumers of the same ImageProxy (e.g. SmartSpectra rPPG),
     * and rowStride/pixelStride are honored so padded or subsampled planes decode correctly.
     *
     * Row/pixel/bitmap scratch buffers are reused across frames; the returned Bitmap
     * is overwritten on the next call and must not be retained or recycled by callers.
     */
    private fun ImageProxy.toBitmap(): Bitmap {
        val yPlane = planes[0]
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val w = width
        val h = height

        val stride = maxOf(1, maxOf(w, h) / MAX_ANALYSIS_DIM)
        val outW = (w + stride - 1) / stride
        val outH = (h + stride - 1) / stride

        val buffer = yPlane.buffer.duplicate()
        val rowBytes = w * pixelStride
        var row = scratchRow
        if (row == null || row.size < rowBytes) {
            row = ByteArray(rowBytes)
            scratchRow = row
        }
        var pixels = scratchPixels
        if (pixels == null || pixels.size < outW * outH) {
            pixels = IntArray(outW * outH)
            scratchPixels = pixels
        }
        val px = pixels
        val rw = row

        for (oy in 0 until outH) {
            val sy = oy * stride
            buffer.position(sy * rowStride)
            // Read exactly rowBytes — the scratch row may be larger than this
            // frame's row when reused across resolution changes.
            buffer.get(rw, 0, rowBytes)
            val rowOffset = oy * outW
            for (ox in 0 until outW) {
                val v = rw[ox * stride * pixelStride].toInt() and 0xFF
                px[rowOffset + ox] = -0x1000000 or (v shl 16) or (v shl 8) or v
            }
        }

        var bitmap = scratchBitmap
        if (bitmap == null || scratchBitmapW != outW || scratchBitmapH != outH || bitmap.isRecycled) {
            bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            scratchBitmap = bitmap
            scratchBitmapW = outW
            scratchBitmapH = outH
        }
        bitmap!!.setPixels(px, 0, outW, 0, 0, outW, outH)
        return bitmap
    }

    /**
     * Converts MediaPipe PoseLandmarkerResult to our LandmarkPoint map.
     * Only the highest-confidence (first) detected pose is used; merging multiple
     * detected poses would corrupt joint-angle calculations.
     */
    private fun landmarksFromMediaPipe(result: PoseLandmarkerResult): Map<Int, LandmarkPoint> {
        val map = mutableMapOf<Int, LandmarkPoint>()
        val pose = result.landmarks().firstOrNull() ?: return emptyMap()
        pose.forEachIndexed { landmarkIndex, landmark ->
            val x = landmark.x().coerceIn(0f, 1f)
            val y = landmark.y().coerceIn(0f, 1f)
            val z = landmark.z()
            val visibility = landmark.visibility().orElse(1.0f)
            map[landmarkIndex] = LandmarkPoint(x, y, z, visibility)
        }
        return map
    }

    @Synchronized
    fun setExercise(exercise: String) {
        activeExercise = exercise
        totalDeviationsCount = 0
        resetRepCounter()
    }

    @Synchronized
    fun resetRepCounter() {
        repCount = 0
        currentPhase = RepPhase.START
        minAngleObserved = 180.0
        previousCriticalJoints = emptySet()
    }

    @Synchronized
    fun getTotalDeviations(): Int = totalDeviationsCount

    @Synchronized
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
     * Synchronized because it is invoked from the CameraX analyzer thread and
     * (for simulation/testing) potentially the main thread.
     */
    @Synchronized
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

        // Count each critical joint fault once per episode (when it first appears),
        // not once per frame — otherwise sustained bad form inflates the count.
        val criticalJoints = deviations
            .filter { it.severity == DeviationSeverity.CRITICAL }
            .map { it.jointName }
            .toSet()
        totalDeviationsCount += criticalJoints.count { it !in previousCriticalJoints }
        previousCriticalJoints = criticalJoints

        // Form score reflects the current frame's deviations, not the cumulative total.
        val framePenalty = deviations.map {
            when (it.severity) {
                DeviationSeverity.CRITICAL -> 30f
                DeviationSeverity.WARNING -> 10f
                DeviationSeverity.INFO -> 3f
            }
        }.sum()
        val formScore = maxOf(0f, 100f - framePenalty)

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
