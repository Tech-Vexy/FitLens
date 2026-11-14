package com.fitlens.app.camera

import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

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
    val previewViewHolder = remember { arrayOfNulls<PreviewView>(1) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Keep DualStreamAnalyzer's mirroring state synchronized with the active camera lens
    LaunchedEffect(useFrontCamera, currentAnalyzer) {
        (currentAnalyzer as? DualStreamAnalyzer)?.isFrontCamera = useFrontCamera
    }

    // Rebind CameraX whenever the user flips between front and rear camera
    LaunchedEffect(useFrontCamera) {
        val previewView = previewViewHolder[0] ?: return@LaunchedEffect
        hasCameraError = false
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, currentAnalyzer)
                    }
                val cameraSelector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                cameraProviderHolder[0] = cameraProvider
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
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
                .background(Color(0xFF161B26)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Camera unavailable on this device/emulator.\nLive Simulation Controls are ready below.",
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
    } else {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewViewHolder[0] = this
                }
            },
            modifier = modifier.fillMaxSize()
        )
    }
}
