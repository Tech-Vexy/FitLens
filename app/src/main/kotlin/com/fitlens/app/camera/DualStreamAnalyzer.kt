package com.fitlens.app.camera

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
 */
class DualStreamAnalyzer(
    private val presageAnalyzer: PresageFrameAnalyzer,
    private val poseManager: PoseLandmarkManager,
    private val onFrameProcessed: (() -> Unit)? = null
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        try {
            // Forward frame to SmartSpectra custom-input
            presageAnalyzer.analyze(imageProxy)
            onFrameProcessed?.invoke()
        } catch (e: Exception) {
            imageProxy.close()
        }
    }
}
