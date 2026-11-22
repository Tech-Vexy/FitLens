package com.fitlens.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fitlens.app.ui.FitLensPalette
import java.util.concurrent.Executors

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraPreviewView(
    analyzer: ImageAnalysis.Analyzer,
    modifier: Modifier = Modifier,
    useFrontCamera: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraError by remember { mutableStateOf(false) }
    val currentAnalyzer by rememberUpdatedState(analyzer)
    val cameraProviderHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }

    // Create PreviewView eagerly in remember so LaunchedEffect never sees a null view
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Keep DualStreamAnalyzer's mirroring state synchronized with the active camera lens
    LaunchedEffect(useFrontCamera, currentAnalyzer) {
        (currentAnalyzer as? DualStreamAnalyzer)?.isFrontCamera = useFrontCamera
    }

    // Rebind CameraX whenever the user flips between front and rear camera
    LaunchedEffect(useFrontCamera) {
        hasCameraError = false
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val targetFpsRange = selectTargetFpsRange(context, useFrontCamera)
                val displayRotation = previewView.display?.rotation ?: Surface.ROTATION_0

                // SmartSpectra custom input (demo-app) uses 640x480 to keep frame
                // conversion lightweight while preserving enough resolution for rPPG.
                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val previewBuilder = Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setTargetRotation(displayRotation)
                targetFpsRange?.let { fps ->
                    Camera2Interop.Extender(previewBuilder)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps)
                }
                val preview = previewBuilder.build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // SmartSpectra custom input requires RGBA_8888 Bitmap frames (not YUV_420_888).
                // demo-app uses 640x480 to keep frame conversion lightweight while preserving
                // enough resolution for rPPG skin-pulse detection.
                val analysisBuilder = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setTargetRotation(displayRotation)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                targetFpsRange?.let { fps ->
                    Camera2Interop.Extender(analysisBuilder)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps)
                }
                val imageAnalysis = analysisBuilder.build().also {
                    it.setAnalyzer(cameraExecutor, currentAnalyzer)
                }

                val cameraSelector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                cameraProviderHolder[0] = cameraProvider
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                // Lock AE/AWB/Black-Level after auto-exposure converges (2.5s) so camera
                // gain shifts don't wash out rPPG skin pulse fluctuations.
                previewView.postDelayed({
                    runCatching {
                        val camera2Control = Camera2CameraControl.from(camera.cameraControl)
                        val optionsBuilder = CaptureRequestOptions.Builder()
                            .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                            .setCaptureRequestOption(CaptureRequest.BLACK_LEVEL_LOCK, true)
                        targetFpsRange?.let { fps ->
                            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps)
                        }
                        camera2Control.setCaptureRequestOptions(optionsBuilder.build())
                    }
                }, 2500L)
            } catch (e: Exception) {
                Log.w("CameraPreviewView", "Camera binding failed: ${e.message}")
                hasCameraError = true
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProviderHolder[0]?.let { runCatching { it.unbindAll() } }
            cameraExecutor.shutdown()
        }
    }

    if (hasCameraError) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(FitLensPalette.ElevatedAlt),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Camera unavailable on this device/emulator.\nLive Simulation Controls are ready below.",
                color = FitLensPalette.Zinc,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
    } else {
        AndroidView(
            factory = { previewView },
            modifier = modifier.fillMaxSize()
        )
    }
}

/**
 * Selects a camera AE target FPS range >= 25 fps (preferring [30, 30]) so SmartSpectra
 * receives a sustained frame rate without triggering SUSTAINED_LOW_FRAME_RATE.
 */
private fun selectTargetFpsRange(context: Context, useFrontCamera: Boolean): Range<Int>? {
    return runCatching {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return null
        val desiredFacing = if (useFrontCamera) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) == desiredFacing) {
                val ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    ?: continue
                val best = ranges
                    .filter { it.upper >= 30 && it.lower >= 25 }
                    .maxByOrNull { it.lower * 100 + it.upper }
                if (best != null) {
                    Log.d("CameraPreviewView", "Selected AE target FPS range: $best")
                    return best
                }
            }
        }
        null
    }.getOrNull()
}
