package com.fitlens.app.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.nio.ByteBuffer
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
    val formScore: Float,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0
)

/**
 * PoseLandmarkManager processes skeletal landmarks from MediaPipe PoseLandmarker
 * in RunningMode.VIDEO with upright rotation/mirror normalization, EMA landmark
 * smoothing, joint angle calculation, rep counting, and biomechanical form checks.
 */
class PoseLandmarkManager(
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "PoseLandmarkManager"
        private const val MAX_ANALYSIS_DIM = 480
        private const val DEFAULT_SMOOTHING_ALPHA = 0.65f

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

        /**
         * Maps an output bitmap coordinate (ox, oy) in the upright (and optionally
         * horizontally mirrored) output space back to raw camera sensor coordinates (sx, sy).
         *
         * @param rotationDegrees Clockwise sensor-to-display rotation (0, 90, 180, or 270).
         * @param mirrorHorizontally True for front-facing camera previews so skeletal landmarks
         *                           align with the mirrored PreviewView.
         */
        fun mapOutputToSensorCoords(
            ox: Int,
            oy: Int,
            outW: Int,
            outH: Int,
            stride: Int,
            srcW: Int,
            srcH: Int,
            rotationDegrees: Int,
            mirrorHorizontally: Boolean
        ): Pair<Int, Int> {
            val normalizedRot = ((rotationDegrees % 360) + 360) % 360
            val rotatedW = if (normalizedRot == 90 || normalizedRot == 270) srcH else srcW
            val rotatedH = if (normalizedRot == 90 || normalizedRot == 270) srcW else srcH

            val unmirroredOx = if (mirrorHorizontally) (outW - 1 - ox) else ox
            val rx = (unmirroredOx * stride).coerceIn(0, rotatedW - 1)
            val ry = (oy * stride).coerceIn(0, rotatedH - 1)

            return when (normalizedRot) {
                90 -> Pair(ry.coerceIn(0, srcW - 1), (srcH - 1 - rx).coerceIn(0, srcH - 1))
                180 -> Pair((srcW - 1 - rx).coerceIn(0, srcW - 1), (srcH - 1 - ry).coerceIn(0, srcH - 1))
                270 -> Pair((srcW - 1 - ry).coerceIn(0, srcW - 1), rx.coerceIn(0, srcH - 1))
                else -> Pair(rx.coerceIn(0, srcW - 1), ry.coerceIn(0, srcH - 1))
            }
        }

        /**
         * Returns true when [exercise] is an isometric timed hold (e.g. Plank) rather than
         * a dynamic concentric/eccentric repetition movement.
         */
        fun isIsometricExercise(exercise: String): Boolean {
            return exercise.trim().equals("plank", ignoreCase = true)
        }
    }

    private var activeExercise: String = "Squat"
    private var repCount: Int = 0
    private var currentPhase: RepPhase = RepPhase.START
    private var minAngleObserved: Double = 180.0
    private var totalDeviationsCount: Int = 0
    private var previousCriticalJoints: Set<String> = emptySet()

    // Temporal EMA state for camera video landmark smoothing
    private val smoothedLandmarkState = mutableMapOf<Int, LandmarkPoint>()
    private var lastVideoTimestampMs: Long = 0L

    // MediaPipe PoseLandmarker instance (lazy-initialized on first frame)
    @Volatile private var mpLandmarker: PoseLandmarker? = null

    // Reused scratch buffers across frames to avoid GC churn on the camera thread
    private var scratchY: ByteArray? = null
    private var scratchU: ByteArray? = null
    private var scratchV: ByteArray? = null
    private var scratchPixels: IntArray? = null
    private var scratchBitmap: Bitmap? = null
    private var scratchBitmapW = 0
    private var scratchBitmapH = 0

    private fun ensureLandmarker(): PoseLandmarker? {
        return mpLandmarker ?: runCatching {
            context?.let { ctx ->
                val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath("pose_landmarker.task")
                            .build()
                    )
                    .setRunningMode(RunningMode.VIDEO)
                    .setMinPoseDetectionConfidence(0.5f)
                    .setMinPosePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setOutputSegmentationMasks(false)
                    .build()
                PoseLandmarker.createFromOptions(ctx, options).also {
                    mpLandmarker = it
                    Log.d(TAG, "MediaPipe PoseLandmarker initialized in VIDEO mode")
                }
            }
        }.getOrNull()
    }

    /**
     * Analyzes a CameraX ImageProxy frame via MediaPipe PoseLandmarker in VIDEO mode.
     * Normalizes sensor rotation and front-camera mirroring into an upright color Bitmap,
     * runs temporal pose tracking, smooths landmarks, and computes biomechanical metrics.
     *
     * The caller (DualStreamAnalyzer) remains responsible for closing [imageProxy]
     * after SmartSpectra consumes it.
     */
    @Synchronized
    fun analyzeFrame(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean = true
    ): PoseAnalysisFrame {
        val landmarker = ensureLandmarker()
            ?: return analyzeLandmarks(emptyMap())

        if (imageProxy.format != ImageFormat.YUV_420_888) {
            Log.w(TAG, "Unsupported image format: ${imageProxy.format}")
            return analyzeLandmarks(emptyMap())
        }

        return runCatching {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toUprightColorBitmap(
                rotationDegrees = rotationDegrees,
                mirrorHorizontally = isFrontCamera
            )
            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestampMs = maxOf(lastVideoTimestampMs + 1L, SystemClock.uptimeMillis())
            lastVideoTimestampMs = timestampMs

            val mpResult = landmarker.detectForVideo(mpImage, timestampMs)
            mpImage.close()

            val rawLandmarks = landmarksFromMediaPipe(mpResult)
            val smoothed = smoothLandmarks(rawLandmarks, DEFAULT_SMOOTHING_ALPHA)
            analyzeLandmarks(
                landmarks = smoothed,
                frameWidth = bitmap.width,
                frameHeight = bitmap.height
            )
        }.getOrElse { e ->
            Log.w(TAG, "MediaPipe frame analysis failed: ${e.message}")
            analyzeLandmarks(emptyMap())
        }
    }

    /**
     * Applies Exponential Moving Average (EMA) temporal smoothing to 3D landmarks
     * to eliminate single-frame jitter while maintaining crisp responsiveness.
     */
    @Synchronized
    fun smoothLandmarks(
        raw: Map<Int, LandmarkPoint>,
        alpha: Float = DEFAULT_SMOOTHING_ALPHA
    ): Map<Int, LandmarkPoint> {
        if (raw.isEmpty()) {
            smoothedLandmarkState.clear()
            return emptyMap()
        }
        val clampedAlpha = alpha.coerceIn(0.05f, 1.0f)
        val invAlpha = 1.0f - clampedAlpha
        val result = HashMap<Int, LandmarkPoint>(raw.size)

        for ((idx, current) in raw) {
            val prev = smoothedLandmarkState[idx]
            val blended = if (prev != null && current.visibility >= 0.35f) {
                LandmarkPoint(
                    x = (clampedAlpha * current.x + invAlpha * prev.x).coerceIn(0f, 1f),
                    y = (clampedAlpha * current.y + invAlpha * prev.y).coerceIn(0f, 1f),
                    z = clampedAlpha * current.z + invAlpha * prev.z,
                    visibility = current.visibility
                )
            } else {
                current
            }
            smoothedLandmarkState[idx] = blended
            result[idx] = blended
        }
        return result
    }

    private fun copyPlaneBuffer(buffer: ByteBuffer, targetHolder: ByteArray?): Pair<ByteArray, Int> {
        val dup = buffer.duplicate()
        dup.rewind()
        val length = dup.remaining()
        val array = if (targetHolder == null || targetHolder.size < length) {
            ByteArray(length)
        } else {
            targetHolder
        }
        dup.get(array, 0, length)
        return Pair(array, length)
    }

    /**
     * Converts a YUV_420_888 ImageProxy to a downscaled, upright, optionally mirrored
     * ARGB_8888 color Bitmap without altering the underlying ImageProxy ByteBuffers.
     */
    private fun ImageProxy.toUprightColorBitmap(
        rotationDegrees: Int,
        mirrorHorizontally: Boolean
    ): Bitmap {
        val srcW = width
        val srcH = height
        val normalizedRot = ((rotationDegrees % 360) + 360) % 360
        val rotatedW = if (normalizedRot == 90 || normalizedRot == 270) srcH else srcW
        val rotatedH = if (normalizedRot == 90 || normalizedRot == 270) srcW else srcH

        val stride = maxOf(1, maxOf(rotatedW, rotatedH) / MAX_ANALYSIS_DIM)
        val outW = maxOf(1, rotatedW / stride)
        val outH = maxOf(1, rotatedH / stride)

        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val (yBytes, yLen) = copyPlaneBuffer(yPlane.buffer, scratchY)
        val (uBytes, uLen) = copyPlaneBuffer(uPlane.buffer, scratchU)
        val (vBytes, vLen) = copyPlaneBuffer(vPlane.buffer, scratchV)
        scratchY = yBytes
        scratchU = uBytes
        scratchV = vBytes

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        var pixels = scratchPixels
        if (pixels == null || pixels.size < outW * outH) {
            pixels = IntArray(outW * outH)
            scratchPixels = pixels
        }
        val px = pixels

        for (oy in 0 until outH) {
            val rowOffset = oy * outW
            for (ox in 0 until outW) {
                val unmirroredOx = if (mirrorHorizontally) (outW - 1 - ox) else ox
                val rx = (unmirroredOx * stride).coerceIn(0, rotatedW - 1)
                val ry = (oy * stride).coerceIn(0, rotatedH - 1)

                val sx: Int
                val sy: Int
                when (normalizedRot) {
                    90 -> {
                        sx = ry
                        sy = srcH - 1 - rx
                    }
                    180 -> {
                        sx = srcW - 1 - rx
                        sy = srcH - 1 - ry
                    }
                    270 -> {
                        sx = srcW - 1 - ry
                        sy = rx
                    }
                    else -> {
                        sx = rx
                        sy = ry
                    }
                }

                val yIdx = (sy * yRowStride + sx * yPixelStride).coerceIn(0, maxOf(0, yLen - 1))
                val uvX = sx shr 1
                val uvY = sy shr 1
                val uIdx = (uvY * uRowStride + uvX * uPixelStride).coerceIn(0, maxOf(0, uLen - 1))
                val vIdx = (uvY * vRowStride + uvX * vPixelStride).coerceIn(0, maxOf(0, vLen - 1))

                val yVal = yBytes[yIdx].toInt() and 0xFF
                val uVal = (uBytes[uIdx].toInt() and 0xFF) - 128
                val vVal = (vBytes[vIdx].toInt() and 0xFF) - 128

                val r = (yVal + ((1436 * vVal) shr 10)).coerceIn(0, 255)
                val g = (yVal - ((352 * uVal + 731 * vVal) shr 10)).coerceIn(0, 255)
                val b = (yVal + ((1814 * uVal) shr 10)).coerceIn(0, 255)

                px[rowOffset + ox] = -0x1000000 or (r shl 16) or (g shl 8) or b
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
        smoothedLandmarkState.clear()
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

    @Synchronized
    fun close() {
        runCatching { mpLandmarker?.close() }
        mpLandmarker = null
        smoothedLandmarkState.clear()
    }

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
    @Synchronized
    fun analyzeLandmarks(
        landmarks: Map<Int, LandmarkPoint>,
        frameWidth: Int = 0,
        frameHeight: Int = 0
    ): PoseAnalysisFrame {
        val deviations = mutableListOf<FormDeviation>()
        var primaryAngle = 180.0

        val normalizedExercise = activeExercise.lowercase()
        when {
            normalizedExercise.contains("squat") -> {
                primaryAngle = analyzeSquat(landmarks, deviations)
            }
            normalizedExercise.contains("push-up") || normalizedExercise.contains("pushup") || normalizedExercise.contains("press") -> {
                primaryAngle = analyzePushUp(landmarks, deviations)
            }
            normalizedExercise.contains("lunge") || normalizedExercise.contains("deadlift") -> {
                primaryAngle = analyzeLunge(landmarks, deviations)
            }
            normalizedExercise.contains("plank") || normalizedExercise.contains("climber") -> {
                primaryAngle = analyzePlank(landmarks, deviations)
            }
            else -> {
                val hip = landmarks[LEFT_HIP] ?: landmarks[RIGHT_HIP]
                val knee = landmarks[LEFT_KNEE] ?: landmarks[RIGHT_KNEE]
                val ankle = landmarks[LEFT_ANKLE] ?: landmarks[RIGHT_ANKLE]
                if (hip != null && knee != null && ankle != null) {
                    primaryAngle = calculateAngle(hip, knee, ankle)
                    updateRepStateMachine(
                        angle = primaryAngle,
                        inflectionThreshold = 105.0,
                        completionThreshold = 160.0
                    )
                }
            }
        }

        val criticalJoints = deviations
            .filter { it.severity == DeviationSeverity.CRITICAL }
            .map { it.jointName }
            .toSet()
        totalDeviationsCount += criticalJoints.count { it !in previousCriticalJoints }
        previousCriticalJoints = criticalJoints

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
            formScore = formScore,
            frameWidth = frameWidth,
            frameHeight = frameHeight
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

        val kneeAngle = calculateAngle(hip, knee, ankle)

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

        val workingKneeAngle = minOf(leftKneeAngle, rightKneeAngle)

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

        // Signed bend check alongside interior angle so both sagging and piking are caught
        val bodyAngle = calculateAngle(shoulder, hip, ankle)
        val midY = (shoulder.y + ankle.y) / 2f

        if (bodyAngle < 155.0) {
            if (hip.y >= midY) {
                deviations.add(
                    FormDeviation(
                        jointName = "Hips",
                        message = "Hips sagging. Squeeze glutes and brace abdominals.",
                        severity = DeviationSeverity.CRITICAL
                    )
                )
            } else {
                deviations.add(
                    FormDeviation(
                        jointName = "Hips",
                        message = "Hips piked too high. Lower hips into a straight plank line.",
                        severity = DeviationSeverity.WARNING
                    )
                )
            }
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
