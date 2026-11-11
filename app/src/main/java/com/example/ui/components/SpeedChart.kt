package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.ElectricBlue

@Composable
fun SpeedChart(
    downloadSpeedBps: Long,
    uploadSpeedBps: Long,
    modifier: Modifier = Modifier
) {
    val downloadHistory = remember { mutableStateListOf<Float>() }
    val uploadHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(downloadSpeedBps, uploadSpeedBps) {
        val maxPoints = 25
        val downKb = (downloadSpeedBps / 1024f)
        val upKb = (uploadSpeedBps / 1024f)

        if (downloadHistory.size >= maxPoints) downloadHistory.removeAt(0)
        downloadHistory.add(downKb)

        if (uploadHistory.size >= maxPoints) uploadHistory.removeAt(0)
        uploadHistory.add(upKb)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (downloadHistory.size < 2) return@Canvas

            val w = size.width
            val h = size.height

            val maxVal = (downloadHistory.maxOrNull() ?: 100f).coerceAtLeast(100f)
            val stepX = w / (downloadHistory.size - 1).coerceAtLeast(1)

            // Grid lines
            for (i in 1..3) {
                val y = h * (i / 4f)
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Download Path
            val downPath = Path()
            val fillPath = Path()

            downloadHistory.forEachIndexed { index, value ->
                val x = index * stepX
                val normalized = (value / maxVal).coerceIn(0f, 1f)
                val y = h - (normalized * (h - 8f))

                if (index == 0) {
                    downPath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    downPath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            fillPath.lineTo((downloadHistory.size - 1) * stepX, h)
            fillPath.close()

            // Draw filled gradient
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CyanPrimary.copy(alpha = 0.25f),
                        Color.Transparent
                    )
                )
            )

            // Draw Download stroke
            drawPath(
                path = downPath,
                color = CyanPrimary,
                style = Stroke(width = 2.dp.toPx())
            )

            // Upload Path
            val upPath = Path()
            uploadHistory.forEachIndexed { index, value ->
                val x = index * stepX
                val normalized = (value / maxVal).coerceIn(0f, 1f)
                val y = h - (normalized * (h - 8f))

                if (index == 0) {
                    upPath.moveTo(x, y)
                } else {
                    upPath.lineTo(x, y)
                }
            }

            drawPath(
                path = upPath,
                color = ElectricBlue,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}
