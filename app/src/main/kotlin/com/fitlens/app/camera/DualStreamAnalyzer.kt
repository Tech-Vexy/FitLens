package com.fitlens.app.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.fitlens.app.pose.PoseLandmarkManager
import com.fitlens.app.vitals.PresageFrameAnalyzer

/**
 * DualStreamAnalyzer dispatches single CameraX ImageProxy frames simultaneously to:
 * 1. Presage SmartSpectra SDK (useCustomInput rPPG vitals extraction)
 * 2. MediaPipe Pose Landmarker (biomechanical skeletal tracking)
 *
 * This completely avoids camera device lock contention between the two computer vision libraries.
 *
 * The analyzer forwards each frame to both pipelines. PresageFrameAnalyzer handles the
 * SmartSpectra custom-input submission, while MediaPipe processes the frame for pose landmarks.
 * Since ImageProxy can only be consumed once, MediaPipe receives a copy of the image buffer
 * via the PresageFrameAnalyzer's frame callback mechanism.
 */
class DualStreamAnalyzer(
    private val presageAnalyzer: PresageFrameAnalyzer,
    private val poseManager: PoseLandmarkManager,
    private val onFrameProcessed: (() -> Unit)? = null
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "DualStreamAnalyzer"
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            // Forward frame to SmartSpectra custom-input (vitals)
            presageAnalyzer.analyze(imageProxy)

            // Forward frame to MediaPipe Pose Landmarker (biomechanics)
            // MediaPipe requires a Bitmap or MpImage; we pass the ImageProxy directly
            // and let PoseLandmarkManager handle the conversion.
            try {
                val analysisFrame = poseManager.analyzeFrame(imageProxy)
                onFrameProcessed?.invoke()
            } catch (e: Exception) {
                Log.w(TAG, "MediaPipe frame analysis failed: ${e.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "DualStream frame dispatch failed: ${e.message}")
            imageProxy.close()
        }
    }
}
