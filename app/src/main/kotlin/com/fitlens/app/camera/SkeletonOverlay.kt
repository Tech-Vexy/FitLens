package com.fitlens.app.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.fitlens.app.pose.FormDeviation
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

/**
 * Renders the 3D skeletal overlay aligned to PreviewView's ScaleType.FILL_CENTER crop,
 * highlighting specific joints involved in active biomechanical deviations.
 */
@Composable
fun SkeletonOverlay(
    landmarks: Map<Int, LandmarkPoint>,
    hasActiveDeviation: Boolean,
    modifier: Modifier = Modifier,
    frameWidth: Int = 0,
    frameHeight: Int = 0,
    activeDeviations: List<FormDeviation> = emptyList()
) {
    if (landmarks.isEmpty()) return

    val primaryBoneColor = if (hasActiveDeviation) Color(0xFFFF5252) else Color(0xFF00E676)
    val glowColor = primaryBoneColor.copy(alpha = 0.28f)
    val jointColor = Color.White
    val faultJointIndices = resolveFaultJointIndices(activeDeviations)

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Match PreviewView.ScaleType.FILL_CENTER aspect scaling + center crop
        val scaledW: Float
        val scaledH: Float
        val offsetX: Float
        val offsetY: Float

        if (frameWidth > 0 && frameHeight > 0 && canvasWidth > 0f && canvasHeight > 0f) {
            val scale = maxOf(canvasWidth / frameWidth.toFloat(), canvasHeight / frameHeight.toFloat())
            scaledW = frameWidth * scale
            scaledH = frameHeight * scale
            offsetX = (canvasWidth - scaledW) / 2f
            offsetY = (canvasHeight - scaledH) / 2f
        } else {
            scaledW = canvasWidth
            scaledH = canvasHeight
            offsetX = 0f
            offsetY = 0f
        }

        fun project(pt: LandmarkPoint): Offset =
            Offset(offsetX + pt.x * scaledW, offsetY + pt.y * scaledH)

        // 1. Draw Bones with soft neon glow
        for (bone in POSE_CONNECTIONS) {
            val start = landmarks[bone.startIndex]
            val end = landmarks[bone.endIndex]
            if (start != null && end != null && start.visibility > 0.45f && end.visibility > 0.45f) {
                val p1 = project(start)
                val p2 = project(end)
                val isBoneFaulted = bone.startIndex in faultJointIndices || bone.endIndex in faultJointIndices
                val lineColor = if (isBoneFaulted) Color(0xFFFF5252) else primaryBoneColor

                drawLine(
                    color = if (isBoneFaulted) Color(0xFFFF5252).copy(alpha = 0.32f) else glowColor,
                    start = p1,
                    end = p2,
                    strokeWidth = 14f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = lineColor,
                    start = p1,
                    end = p2,
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }
        }

        // 2. Draw Joints & Deviation Warning Rings
        for ((index, point) in landmarks) {
            if (point.visibility > 0.45f) {
                val center = project(point)
                val isFaulted = index in faultJointIndices
                if (isFaulted) {
                    drawCircle(
                        color = Color(0xFFFF5252).copy(alpha = 0.35f),
                        radius = 20f,
                        center = center
                    )
                }
                drawCircle(
                    color = jointColor,
                    radius = if (isFaulted) 10f else 8f,
                    center = center
                )
                drawCircle(
                    color = if (isFaulted) Color(0xFFFF5252) else primaryBoneColor,
                    radius = if (isFaulted) 6f else 4.5f,
                    center = center
                )
            }
        }
    }
}

private fun resolveFaultJointIndices(deviations: List<FormDeviation>): Set<Int> {
    if (deviations.isEmpty()) return emptySet()
    val indices = mutableSetOf<Int>()
    for (dev in deviations) {
        val name = dev.jointName.lowercase()
        when {
            name.contains("knee") -> {
                indices += PoseLandmarkManager.LEFT_KNEE
                indices += PoseLandmarkManager.RIGHT_KNEE
            }
            name.contains("hip") || name.contains("core") -> {
                indices += PoseLandmarkManager.LEFT_HIP
                indices += PoseLandmarkManager.RIGHT_HIP
            }
            name.contains("back") || name.contains("torso") -> {
                indices += PoseLandmarkManager.LEFT_SHOULDER
                indices += PoseLandmarkManager.RIGHT_SHOULDER
                indices += PoseLandmarkManager.LEFT_HIP
                indices += PoseLandmarkManager.RIGHT_HIP
            }
            name.contains("elbow") -> {
                indices += PoseLandmarkManager.LEFT_ELBOW
                indices += PoseLandmarkManager.RIGHT_ELBOW
            }
        }
    }
    return indices
}
