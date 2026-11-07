package com.fitlens.app.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.fitlens.app.pose.LandmarkPoint
import com.fitlens.app.pose.PoseLandmarkManager

data class SkeletonBone(val startIndex: Int, val endIndex: Int)

val POSE_CONNECTIONS = listOf(
    // Torso
    SkeletonBone(PoseLandmarkManager.LEFT_SHOULDER, PoseLandmarkManager.RIGHT_SHOULDER),
    SkeletonBone(PoseLandmarkManager.LEFT_SHOULDER, PoseLandmarkManager.LEFT_HIP),
    SkeletonBone(PoseLandmarkManager.RIGHT_SHOULDER, PoseLandmarkManager.RIGHT_HIP),
    SkeletonBone(PoseLandmarkManager.LEFT_HIP, PoseLandmarkManager.RIGHT_HIP),
    // Left Arm
    SkeletonBone(PoseLandmarkManager.LEFT_SHOULDER, PoseLandmarkManager.LEFT_ELBOW),
    SkeletonBone(PoseLandmarkManager.LEFT_ELBOW, PoseLandmarkManager.LEFT_WRIST),
    // Right Arm
    SkeletonBone(PoseLandmarkManager.RIGHT_SHOULDER, PoseLandmarkManager.RIGHT_ELBOW),
    SkeletonBone(PoseLandmarkManager.RIGHT_ELBOW, PoseLandmarkManager.RIGHT_WRIST),
    // Left Leg
    SkeletonBone(PoseLandmarkManager.LEFT_HIP, PoseLandmarkManager.LEFT_KNEE),
    SkeletonBone(PoseLandmarkManager.LEFT_KNEE, PoseLandmarkManager.LEFT_ANKLE),
    // Right Leg
    SkeletonBone(PoseLandmarkManager.RIGHT_HIP, PoseLandmarkManager.RIGHT_KNEE),
    SkeletonBone(PoseLandmarkManager.RIGHT_KNEE, PoseLandmarkManager.RIGHT_ANKLE)
)

@Composable
fun SkeletonOverlay(
    landmarks: Map<Int, LandmarkPoint>,
    hasActiveDeviation: Boolean,
    modifier: Modifier = Modifier
) {
    if (landmarks.isEmpty()) return

    val boneColor = if (hasActiveDeviation) Color(0xFFFF5252) else Color(0xFF00E676)
    val jointColor = Color.White

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 1. Draw Bones (connecting lines)
        for (bone in POSE_CONNECTIONS) {
            val start = landmarks[bone.startIndex]
            val end = landmarks[bone.endIndex]
            if (start != null && end != null && start.visibility > 0.5f && end.visibility > 0.5f) {
                drawLine(
                    color = boneColor,
                    start = Offset(start.x * canvasWidth, start.y * canvasHeight),
                    end = Offset(end.x * canvasWidth, end.y * canvasHeight),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }
        }

        // 2. Draw Joints
        for ((_, point) in landmarks) {
            if (point.visibility > 0.5f) {
                drawCircle(
                    color = jointColor,
                    radius = 8f,
                    center = Offset(point.x * canvasWidth, point.y * canvasHeight)
                )
                drawCircle(
                    color = boneColor,
                    radius = 4f,
                    center = Offset(point.x * canvasWidth, point.y * canvasHeight)
                )
            }
        }
    }
}
