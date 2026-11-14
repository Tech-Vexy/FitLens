package com.fitlens.app.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.fitlens.app.pose.PoseAnalysisFrame
import com.fitlens.app.pose.PoseLandmarkManager
import com.fitlens.app.vitals.PresageFrameAnalyzer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DualStreamAnalyzer dispatches single CameraX ImageProxy frames concurrently to:
 * 1. MediaPipe Pose Landmarker (biomechanical skeletal tracking, offloaded to a dedicated executor)
 * 2. Presage SmartSpectra SDK (useCustomInput rPPG vitals extraction at full camera FPS)
 *
 * Non-blocking contract: MediaPipe inference never stalls [PresageFrameAnalyzer.analyze].
 * If MediaPipe is still processing a previous frame, intermediate pose frames are skipped
 * while SmartSpectra still receives every camera frame at >=25-30 fps.
 *
 * [PresageFrameAnalyzer.analyze] closes [imageProxy] in its finally block.
 */
class DualStreamAnalyzer(
    private val presageAnalyzer: PresageFrameAnalyzer,
    private val poseManager: PoseLandmarkManager,
    @Volatile var enablePoseAnalysis: Boolean = true,
    @Volatile var isFrontCamera: Boolean = true,
    private val onPoseFrame: ((PoseAnalysisFrame) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "DualStreamAnalyzer"
    }

    private val poseExecutor = Executors.newSingleThreadExecutor()
    private val isPoseBusy = AtomicBoolean(false)

    override fun analyze(imageProxy: ImageProxy) {
        if (enablePoseAnalysis && isPoseBusy.compareAndSet(false, true)) {
            val bitmap = try {
                poseManager.extractUprightBitmap(
                    imageProxy = imageProxy,
                    isFrontCamera = isFrontCamera
                )
            } catch (e: Exception) {
                Log.w(TAG, "MediaPipe bitmap extraction failed: ${e.message}")
                null
            }

            if (bitmap != null) {
                try {
                    poseExecutor.execute {
                        try {
                            val analysisFrame = poseManager.analyzeBitmap(bitmap)
                            onPoseFrame?.invoke(analysisFrame)
                        } catch (e: Exception) {
                            Log.w(TAG, "MediaPipe frame analysis failed: ${e.message}")
                        } finally {
                            bitmap.recycle()
                            isPoseBusy.set(false)
                        }
                    }
                } catch (e: Exception) {
                    bitmap.recycle()
                    isPoseBusy.set(false)
                }
            } else {
                isPoseBusy.set(false)
            }
        }

        try {
            presageAnalyzer.analyze(imageProxy)
        } catch (e: Exception) {
            Log.w(TAG, "Presage frame dispatch failed: ${e.message}")
            runCatching { imageProxy.close() }
        }
    }

    fun shutdown() {
        runCatching { poseExecutor.shutdown() }
    }
}
