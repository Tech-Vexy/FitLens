package com.fitlens.app.vitals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * PulseWaveformView renders the real-time arterial pressure trace / photoplethysmogram (rPPG)
 * waveform computed by the Presage SmartSpectra SDK.
 */
@Composable
fun PulseWaveformView(
    waveformSamples: List<Float>,
    lineColor: Color = Color(0xFF00E676),
    modifier: Modifier = Modifier
) {
    if (waveformSamples.isEmpty()) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        val width = size.width
        val height = size.height
        val stepX = width / maxOf(1, waveformSamples.size - 1)

        val minVal = waveformSamples.minOrNull() ?: 0f
        val maxVal = waveformSamples.maxOrNull() ?: 1f
        val range = maxOf(0.001f, maxVal - minVal)

        val path = Path()
        waveformSamples.forEachIndexed { index, sample ->
            val normalizedY = 1f - ((sample - minVal) / range)
            val x = index * stepX
            val y = normalizedY * (height - 8f) + 4f

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // Draw glowing trace line
        drawPath(
            path = path,
            color = lineColor.copy(alpha = 0.3f),
            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
