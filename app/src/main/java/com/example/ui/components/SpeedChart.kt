package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun SpeedChart(
    downloadSpeedBps: Long,
    uploadSpeedBps: Long,
    modifier: Modifier = Modifier
) {
    val downloadHistory = remember { mutableStateListOf<Float>() }
    val uploadHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(downloadSpeedBps, uploadSpeedBps) {
        val maxPoints = 28
        val downKb = (downloadSpeedBps / 1024f)
        val upKb = (uploadSpeedBps / 1024f)

        if (downloadHistory.size >= maxPoints) downloadHistory.removeAt(0)
        downloadHistory.add(downKb)

        if (uploadHistory.size >= maxPoints) uploadHistory.removeAt(0)
        uploadHistory.add(upKb)
    }

    val maxVal = remember(downloadHistory.toList(), uploadHistory.toList()) {
        val peakDown = downloadHistory.maxOrNull() ?: 100f
        val peakUp = uploadHistory.maxOrNull() ?: 50f
        maxOf(peakDown, peakUp, 80f)
    }

    val currentDownKb = downloadHistory.lastOrNull() ?: 0f
    val currentUpKb = uploadHistory.lastOrNull() ?: 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column {
            // Recharts-style Header Bar with Legend and Live Stream Values
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "REAL-TIME TRAFFIC (STREAM)",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )

                // Download Legend Indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(CyanPrimary))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "DL: ${formatKb(currentDownKb)}",
                        color = CyanPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Upload Legend Indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(ElectricBlue))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "UL: ${formatKb(currentUpKb)}",
                        color = ElectricBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Canvas Area Chart with Monotone Cubic Bezier Curves
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (downloadHistory.size < 2) return@Canvas

                    val w = size.width
                    val h = size.height

                    val stepX = w / (downloadHistory.size - 1).coerceAtLeast(1)

                    // Cartesian Grid lines
                    for (i in 1..3) {
                        val y = h * (i / 4f)
                        drawLine(
                            color = Color.White.copy(alpha = 0.04f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Build Smooth Monotone Paths for Download (Cyan)
                    val downPath = Path()
                    val downFill = Path()

                    val pointsDown = downloadHistory.mapIndexed { index, value ->
                        val x = index * stepX
                        val normalized = (value / maxVal).coerceIn(0f, 1f)
                        val y = h - (normalized * (h - 6f))
                        Offset(x, y)
                    }

                    buildMonotoneCubicPath(pointsDown, downPath, downFill, h, w)

                    // Draw Download Gradient Fill
                    drawPath(
                        path = downFill,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                CyanPrimary.copy(alpha = 0.35f),
                                CyanPrimary.copy(alpha = 0.02f),
                                Color.Transparent
                            )
                        )
                    )

                    // Draw Download Curve Stroke
                    drawPath(
                        path = downPath,
                        color = CyanPrimary,
                        style = Stroke(width = 2.2.dp.toPx())
                    )

                    // Build Smooth Monotone Paths for Upload (Electric Blue)
                    val upPath = Path()
                    val upFill = Path()

                    val pointsUp = uploadHistory.mapIndexed { index, value ->
                        val x = index * stepX
                        val normalized = (value / maxVal).coerceIn(0f, 1f)
                        val y = h - (normalized * (h - 6f))
                        Offset(x, y)
                    }

                    buildMonotoneCubicPath(pointsUp, upPath, upFill, h, w)

                    // Draw Upload Gradient Fill
                    drawPath(
                        path = upFill,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ElectricBlue.copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        )
                    )

                    // Draw Upload Curve Stroke
                    drawPath(
                        path = upPath,
                        color = ElectricBlue,
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                }
            }
        }
    }
}

private fun buildMonotoneCubicPath(
    points: List<Offset>,
    linePath: Path,
    fillPath: Path,
    canvasHeight: Float,
    canvasWidth: Float
) {
    if (points.isEmpty()) return

    linePath.moveTo(points.first().x, points.first().y)
    fillPath.moveTo(points.first().x, canvasHeight)
    fillPath.lineTo(points.first().x, points.first().y)

    for (i in 0 until points.size - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]

        val controlX1 = p0.x + (p1.x - p0.x) / 2
        val controlY1 = p0.y
        val controlX2 = p0.x + (p1.x - p0.x) / 2
        val controlY2 = p1.y

        linePath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
        fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
    }

    fillPath.lineTo(points.last().x, canvasHeight)
    fillPath.close()
}

private fun formatKb(kb: Float): String {
    return if (kb >= 1024f) {
        String.format(Locale.US, "%.1f MB/s", kb / 1024f)
    } else {
        String.format(Locale.US, "%.0f KB/s", kb)
    }
}
