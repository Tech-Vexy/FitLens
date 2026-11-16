package com.fitlens.app.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.fitlens.app.pose.PoseAnalysisFrame
import com.fitlens.app.pose.PoseLandmarkManager
import com.fitlens.app.vitals.PresageFrameAnalyzer
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DualStreamAnalyzer dispatches CameraX ImageProxy frames concurrently to:
 * 1. Presage SmartSpectra SDK ([PresageFrameAnalyzer]) at full camera FPS.
 * 2. Gemini Live API ([onVideoFrameForAi]) as throttled, low-res upright JPEG frames when live.
 * 3. MediaPipe PoseLandmarker ([poseLandmarkManager]) as throttled upright bitmap frames
 *    when [poseAnalysisEnabled] is set (biomechanics coach / rep counting).
 *
 * [PresageFrameAnalyzer.analyze] closes [imageProxy] in its finally block.
 */
class DualStreamAnalyzer(
    private val presageAnalyzer: PresageFrameAnalyzer,
    @Volatile var enableLiveVideoFrames: Boolean = false,
    @Volatile var isFrontCamera: Boolean = true,
    private val onVideoFrameForAi: ((ByteArray) -> Unit)? = null,
    private val poseLandmarkManager: PoseLandmarkManager? = null,
    private val onPoseFrame: ((PoseAnalysisFrame) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "DualStreamAnalyzer"
        private const val AI_FRAME_INTERVAL_MS = 2000L
        private const val AI_FRAME_MAX_DIM = 320
        private const val POSE_FRAME_INTERVAL_MS = 100L
    }

    @Volatile var poseAnalysisEnabled: Boolean = false

    private val aiFrameExecutor = Executors.newSingleThreadExecutor()
    private val poseFrameExecutor = Executors.newSingleThreadExecutor()
    private val isAiFrameBusy = AtomicBoolean(false)
    private val isPoseFrameBusy = AtomicBoolean(false)
    @Volatile private var lastAiFrameAtMs = 0L
    @Volatile private var lastPoseFrameAtMs = 0L

    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()

        val poseManager = poseLandmarkManager
        val poseCallback = onPoseFrame
        if (poseAnalysisEnabled &&
            poseManager != null &&
            poseCallback != null &&
            now - lastPoseFrameAtMs >= POSE_FRAME_INTERVAL_MS &&
            isPoseFrameBusy.compareAndSet(false, true)
        ) {
            lastPoseFrameAtMs = now
            val poseBitmap = runCatching {
                poseManager.extractUprightBitmap(imageProxy, isFrontCamera)
            }.getOrNull()

            if (poseBitmap != null) {
                runCatching {
                    poseFrameExecutor.execute {
                        try {
                            val frame = poseManager.analyzeBitmap(poseBitmap)
                            poseCallback.invoke(frame)
                        } catch (e: Exception) {
                            Log.v(TAG, "Pose analysis skipped: ${e.message}")
                        } finally {
                            poseBitmap.recycle()
                            isPoseFrameBusy.set(false)
                        }
                    }
                }.onFailure {
                    poseBitmap.recycle()
                    isPoseFrameBusy.set(false)
                }
            } else {
                isPoseFrameBusy.set(false)
            }
        }

        val aiCallback = onVideoFrameForAi
        if (enableLiveVideoFrames &&
            aiCallback != null &&
            now - lastAiFrameAtMs >= AI_FRAME_INTERVAL_MS &&
            isAiFrameBusy.compareAndSet(false, true)
        ) {
            lastAiFrameAtMs = now
            val aiBitmap = runCatching {
                extractUprightBitmap(imageProxy, isFrontCamera)
            }.getOrNull()

            if (aiBitmap != null) {
                runCatching {
                    aiFrameExecutor.execute {
                        try {
                            val jpegBytes = compressForLiveAi(aiBitmap)
                            if (jpegBytes.isNotEmpty()) {
                                aiCallback.invoke(jpegBytes)
                            }
                        } catch (e: Exception) {
                            Log.v(TAG, "AI frame compression skipped: ${e.message}")
                        } finally {
                            aiBitmap.recycle()
                            isAiFrameBusy.set(false)
                        }
                    }
                }.onFailure {
                    aiBitmap.recycle()
                    isAiFrameBusy.set(false)
                }
            } else {
                isAiFrameBusy.set(false)
            }
        }

        try {
            presageAnalyzer.analyze(imageProxy)
        } catch (e: Exception) {
            Log.w(TAG, "Presage frame dispatch failed: ${e.message}")
            runCatching { imageProxy.close() }
        }
    }

    private fun extractUprightBitmap(imageProxy: ImageProxy, mirrorHorizontally: Boolean): Bitmap {
        val raw = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
        if (rotation == 0f && !mirrorHorizontally) return raw

        val matrix = Matrix().apply {
            if (rotation != 0f) postRotate(rotation)
            if (mirrorHorizontally) postScale(-1f, 1f)
        }
        return try {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        } finally {
            raw.recycle()
        }
    }

    private fun compressForLiveAi(source: Bitmap): ByteArray {
        val maxDim = maxOf(source.width, source.height).coerceAtLeast(1)
        val scale = if (maxDim > AI_FRAME_MAX_DIM) AI_FRAME_MAX_DIM.toFloat() / maxDim else 1f
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            source
        }
        return try {
            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
                out.toByteArray()
            }
        } finally {
            if (scaled !== source) scaled.recycle()
        }
    }

    fun shutdown() {
        runCatching { aiFrameExecutor.shutdown() }
        runCatching { poseFrameExecutor.shutdown() }
    }
}
