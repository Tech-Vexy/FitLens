package com.fitlens.app.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.fitlens.app.pose.PoseAnalysisFrame
import com.fitlens.app.pose.PoseLandmarkManager
import com.fitlens.app.vitals.PresageFrameAnalyzer

/**
 * DualStreamAnalyzer dispatches single CameraX ImageProxy frames simultaneously to:
 * 1. Presage SmartSpectra SDK (useCustomInput rPPG vitals extraction)
 * 2. MediaPipe Pose Landmarker (biomechanical skeletal tracking)
 *
 * This completely avoids camera device lock contention between the two computer vision libraries.
 *
 * Ordering contract: MediaPipe runs FIRST on each ImageProxy because
 * [PresageFrameAnalyzer.analyze] always closes the proxy when it returns (required by the
 * SmartSpectra SDK contract). The resulting [PoseAnalysisFrame] is delivered to
 * [onPoseFrame] so the workout pipeline can consume rep counts, form deviations, and landmarks.
 */
class DualStreamAnalyzer(
    private val presageAnalyzer: PresageFrameAnalyzer,
    private val poseManager: PoseLandmarkManager,
    private val enablePoseAnalysis: Boolean = true,
    private val onPoseFrame: ((PoseAnalysisFrame) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "DualStreamAnalyzer"
    }

    override fun analyze(imageProxy: ImageProxy) {
        // 1. MediaPipe Pose Landmarker (biomechanics) — must run before the proxy is closed.
        if (enablePoseAnalysis) {
            try {
                val analysisFrame = poseManager.analyzeFrame(imageProxy)
                onPoseFrame?.invoke(analysisFrame)
            } catch (e: Exception) {
                Log.w(TAG, "MediaPipe frame analysis failed: ${e.message}")
            }
        }

        // 2. SmartSpectra custom-input (vitals) — closes imageProxy in its finally block.
        try {
            presageAnalyzer.analyze(imageProxy)
        } catch (e: Exception) {
            Log.w(TAG, "Presage frame dispatch failed: ${e.message}")
            runCatching { imageProxy.close() }
        }
    }
}
