package com.kentji.lanclient.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kentji.lanclient.data.Candle
import com.kentji.lanclient.data.ChartData
import kotlin.math.max
import kotlin.math.min

/**
 * Native multi-pane chart: candles on top, volume below, S1 marks as
 * triangles on the candle pane. One shared time axis. Gestures: drag to
 * pan, pinch to zoom, tap for a crosshair, double-tap to reset.
 */
@Composable
fun ChartView(data: ChartData, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    var visibleCount by remember(data) { mutableStateOf(120) }
    var endIndex by remember(data) { mutableStateOf(data.candles.size) }
    var crossIndex by remember(data) { mutableStateOf<Int?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp)
            .pointerInput(data) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f) {
                        visibleCount = (visibleCount / zoom).toInt().coerceIn(20, data.candles.size.coerceAtLeast(20))
                    }
                    if (pan.x != 0f) {
                        val step = (-pan.x / 8f).toInt()
                        endIndex = (endIndex + step).coerceIn(visibleCount, data.candles.size)
                    }
                }
            }
            .pointerInput(data) {
                detectTapGestures(
                    onTap = { offset ->
                        val start = max(0, endIndex - visibleCount)
                        val w = size.width
                        val idx = start + (offset.x / w * visibleCount).toInt()
                        crossIndex = idx.coerceIn(0, data.candles.lastIndex)
                    },
                    onDoubleTap = {
                        visibleCount = 120
                        endIndex = data.candles.size
                        crossIndex = null
                    }
                )
            }
    ) {
        val candles = data.candles
        if (candles.isEmpty()) return@Canvas
        val start = max(0, endIndex - visibleCount)
        val end = min(candles.size, endIndex)
        val window = candles.subList(start, end)
        if (window.isEmpty()) return@Canvas
        val candleH = size.height * 0.68f
        val volTop = candleH + 8f
        val volH = size.height - volTop
        var lo = Float.MAX_VALUE
        var hi = Float.MIN_VALUE
        var maxVol = 1L
        for (c in window) {
            lo = min(lo, c.low); hi = max(hi, c.high); maxVol = max(maxVol, c.volume)
        }
        if (hi <= lo) hi = lo + 1f
        val stepX = size.width / max(1, window.size)
        val yFor: (Float) -> Float = { px -> candleH - (px - lo) / (hi - lo) * candleH }
        // Volume bars.
        for ((i, c) in window.withIndex()) {
            val x = i * stepX
            val h = volH * (c.volume.toFloat() / maxVol.toFloat())
            drawRect(
                color = if (c.close >= c.open) UpColor else DownColor,
                topLeft = Offset(x + 1f, volTop + volH - h),
                size = androidx.compose.ui.geometry.Size(stepX - 2f, h)
            )
        }
        // Candles.
        for ((i, c) in window.withIndex()) {
            val x = i * stepX + stepX / 2f
            val color = if (c.close >= c.open) UpColor else DownColor
            drawLine(color, Offset(x, yFor(c.high)), Offset(x, yFor(c.low)), strokeWidth = 2f)
            val top = yFor(max(c.open, c.close))
            val bottom = yFor(min(c.open, c.close))
            drawRect(
                color, Offset(x - stepX * 0.35f, top),
                androidx.compose.ui.geometry.Size(stepX * 0.7f, max(2f, bottom - top))
            )
        }
        // S1 marks: triangles at the candle that matches the mark timestamp.
        val tsToIndex = window.mapIndexed { i, c -> c.ts to i }.toMap()
        for (mark in data.marks) {
            val idx = tsToIndex[mark.ts] ?: continue
            val c = window[idx]
            val x = idx * stepX + stepX / 2f
            val isSell = mark.kind == "sell"
            val yBase = if (isSell) yFor(c.high) - 14f else yFor(c.low) + 14f
            val path = Path().apply {
                if (isSell) {
                    moveTo(x - 7f, yBase - 10f); lineTo(x + 7f, yBase - 10f); lineTo(x, yBase); close()
                } else {
                    moveTo(x - 7f, yBase + 10f); lineTo(x + 7f, yBase + 10f); lineTo(x, yBase); close()
                }
            }
            drawPath(path, if (isSell) DownColor else UpColor)
        }
        // Crosshair + readout.
        crossIndex?.let { ci ->
            if (ci in start until end) {
                val local = ci - start
                val c = window[local]
                val x = local * stepX + stepX / 2f
                drawLine(Color.Gray, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                drawLine(Color.Gray, Offset(0f, yFor(c.close)), Offset(size.width, yFor(c.close)), strokeWidth = 1f)
                drawText(
                    measurer, "${c.ts}  ${c.close}",
                    topLeft = Offset(8f, 8f)
                )
            }
        }
        // Last price line.
        val last = window.last()
        drawLine(
            UpColor, Offset(0f, yFor(last.close)), Offset(size.width, yFor(last.close)),
            strokeWidth = 1f
        )
    }
}

@Composable
fun EquityLine(points: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        if (points.size < 2) return@Canvas
        val values = points.map { it.second }
        val lo = values.minOrNull() ?: 0.0
        val hi = values.maxOrNull() ?: 1.0
        val span = if (hi - lo == 0.0) 1.0 else hi - lo
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = size.width * i / (points.size - 1)
            val y = size.height - ((p.second - lo) / span * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, UpColor, style = Stroke(width = 3f))
    }
}

// Keep the text measurer import used on all compiler versions.
private fun unusedMeasurer(m: TextMeasurer) = m
