package com.fitlens.app.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.fitlens.app.pose.PoseAnalysisFrame
import com.fitlens.app.pose.PoseLandmarkManager
import com.fitlens.app.vitals.PresageFrameAnalyzer

/**
 * DualStreamAnalyzer dispatches single CameraX ImageProxy frames simultaneously to:
 * 1. MediaPipe Pose Landmarker (biomechanical skeletal tracking)
 * 2. Presage SmartSpectra SDK (useCustomInput rPPG vitals extraction)
 *
 * Ordering contract: MediaPipe runs FIRST on each ImageProxy (using duplicated read-only
 * ByteBuffers) because [PresageFrameAnalyzer.analyze] closes the proxy in its finally block.
 *
 * [enablePoseAnalysis] and [isFrontCamera] can be toggled dynamically at runtime so a
 * single shared pipeline serves both vitals-only screens and full workout screens.
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

    override fun analyze(imageProxy: ImageProxy) {
        if (enablePoseAnalysis) {
            try {
                val analysisFrame = poseManager.analyzeFrame(
                    imageProxy = imageProxy,
                    isFrontCamera = isFrontCamera
                )
                onPoseFrame?.invoke(analysisFrame)
            } catch (e: Exception) {
                Log.w(TAG, "MediaPipe frame analysis failed: ${e.message}")
            }
        }

        try {
            presageAnalyzer.analyze(imageProxy)
        } catch (e: Exception) {
            Log.w(TAG, "Presage frame dispatch failed: ${e.message}")
            runCatching { imageProxy.close() }
        }
    }
}
