package com.fitlens.app.camera

import android.content.Context
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    // Holds the bound ProcessCameraProvider so it can be unbound on dispose.
    val cameraProviderHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            // Release the camera; otherwise it stays bound after leaving the screen.
            cameraProviderHolder[0]?.let { runCatching { it.unbindAll() } }
            cameraExecutor.shutdown()
        }
    }

    if (hasCameraError) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Camera Unavailable or Running in Emulator.\nSimulation HUD Active.",
                color = Color.LightGray
            )
        }
    } else {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
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
                        Log.w("CameraPreviewView", "Camera binding deferred or unavailable: ${e.message}")
                        hasCameraError = true
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = modifier.fillMaxSize()
        )
    }
}
