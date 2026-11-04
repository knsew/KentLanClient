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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kentji.lanclient.data.Candle
import com.kentji.lanclient.data.ChartData
import kotlin.math.max
import kotlin.math.min

/**
 * Native stock chart, rebuilt after studying the reference libraries
 * (MPAndroidChart's CandleStickChart + Vico's Cartesian layers):
 * right price axis with gridlines, bottom time axis, volume pane with
 * up/down coloring, dashed crosshair with an OHLC info box and a price
 * tag on the axis, last-price line + tag, S1 buy/sell triangles.
 * Gestures: drag to pan, pinch to zoom, tap for crosshair, double-tap
 * to reset. No third-party chart dependency: the S1 overlays are custom.
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
            .height(460.dp)
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
                        // Map the tap inside the plot area (axis excluded).
                        val plotW = size.width - 56.dp.toPx()
                        val start = max(0, endIndex - visibleCount)
                        val idx = start + (offset.x.coerceIn(0f, plotW) / plotW * visibleCount).toInt()
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

        // ---- Layout: plot area + right price axis + bottom time axis.
        val axisW = 56.dp.toPx()
        val timeH = 22.dp.toPx()
        val plotW = size.width - axisW
        val plotH = size.height - timeH
        val candleH = plotH * 0.70f
        val volTop = candleH + 6f
        val volH = plotH - volTop

        var lo = Float.MAX_VALUE
        var hi = Float.MIN_VALUE
        var maxVol = 1L
        for (c in window) {
            lo = min(lo, c.low); hi = max(hi, c.high); maxVol = max(maxVol, c.volume)
        }
        if (hi <= lo) hi = lo + 1f
        val pad = (hi - lo) * 0.05f
        lo -= pad; hi += pad
        val stepX = plotW / max(1, window.size)
        val yFor: (Float) -> Float = { px -> candleH - (px - lo) / (hi - lo) * candleH }
        val gridColor = Color(0xFF3A3A3A)
        val axisText = TextStyle(color = Color(0xFFB0B0B0), fontSize = 11.sp)
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

        // ---- Price gridlines + right axis labels (5 rows).
        for (i in 0..4) {
            val price = lo + (hi - lo) * i / 4f
            val y = yFor(price)
            drawLine(gridColor, Offset(0f, y), Offset(plotW, y), strokeWidth = 1f)
            val label = "%.2f".format(price)
            val layout = measurer.measure(label, axisText)
            drawText(
                measurer, label,
                topLeft = Offset(plotW + 6f, (y - layout.size.height / 2f).coerceIn(0f, candleH - layout.size.height)),
                style = axisText
            )
        }
        // Axis + pane separators.
        drawLine(gridColor, Offset(plotW, 0f), Offset(plotW, plotH), strokeWidth = 1f)
        drawLine(gridColor, Offset(0f, volTop - 3f), Offset(plotW, volTop - 3f), strokeWidth = 1f)
        drawLine(gridColor, Offset(0f, plotH), Offset(plotW, plotH), strokeWidth = 1f)

        // ---- Volume bars (up red / down green, same convention as candles).
        for ((i, c) in window.withIndex()) {
            val x = i * stepX
            val h = volH * (c.volume.toFloat() / maxVol.toFloat())
            if (h <= 0f) continue
            drawRect(
                color = (if (c.close >= c.open) UpColor else DownColor).copy(alpha = 0.85f),
                topLeft = Offset(x + 1f, volTop + volH - h),
                size = Size(stepX - 2f, h)
            )
        }

        // ---- Candles: wick + body.
        for ((i, c) in window.withIndex()) {
            val x = i * stepX + stepX / 2f
            val color = if (c.close >= c.open) UpColor else DownColor
            drawLine(color, Offset(x, yFor(c.high)), Offset(x, yFor(c.low)), strokeWidth = 2f)
            val top = yFor(max(c.open, c.close))
            val bottom = yFor(min(c.open, c.close))
            drawRect(
                color, Offset(x - stepX * 0.35f, top),
                Size(stepX * 0.7f, max(2f, bottom - top))
            )
        }

        // ---- S1 marks: triangles above highs (sell) / below lows (buy).
        val tsToIndex = window.mapIndexed { i, c -> c.ts to i }.toMap()
        for (mark in data.marks) {
            val idx = tsToIndex[mark.ts] ?: continue
            val c = window[idx]
            val x = idx * stepX + stepX / 2f
            val isSell = mark.kind == "sell"
            val yBase = if (isSell) yFor(c.high) - 12f else yFor(c.low) + 12f
            val path = Path().apply {
                if (isSell) {
                    moveTo(x - 7f, yBase - 10f); lineTo(x + 7f, yBase - 10f); lineTo(x, yBase); close()
                } else {
                    moveTo(x - 7f, yBase + 10f); lineTo(x + 7f, yBase + 10f); lineTo(x, yBase); close()
                }
            }
            drawPath(path, if (isSell) DownColor else UpColor)
        }

        // ---- Time axis labels (up to 5, spread across the window).
        val timeIdx = (0..4).map { it * (window.size - 1) / 4 }.distinct()
        for (idx in timeIdx) {
            val c = window[idx]
            val label = formatTimeLabel(c.ts, data.period)
            val layout = measurer.measure(label, axisText)
            val cx = idx * stepX + stepX / 2f
            val tx = (cx - layout.size.width / 2f).coerceIn(0f, plotW - layout.size.width)
            drawText(measurer, label, topLeft = Offset(tx, plotH + 4f), style = axisText)
        }

        // ---- Last price line + tag (color follows up/down).
        val last = window.last()
        val lastColor = if (last.close >= last.open) UpColor else DownColor
        drawLine(lastColor, Offset(0f, yFor(last.close)), Offset(plotW, yFor(last.close)), strokeWidth = 1f, pathEffect = dash)
        drawPriceTag(measurer, plotW, yFor(last.close), "%.2f".format(last.close), lastColor, candleH)

        // ---- Crosshair: dashed lines, axis price tag, OHLC info box.
        crossIndex?.let { ci ->
            if (ci in start until end) {
                val local = ci - start
                val c = window[local]
                val x = local * stepX + stepX / 2f
                drawLine(Color.Gray, Offset(x, 0f), Offset(x, plotH), strokeWidth = 1f, pathEffect = dash)
                drawLine(Color.Gray, Offset(0f, yFor(c.close)), Offset(plotW, yFor(c.close)), strokeWidth = 1f, pathEffect = dash)
                drawPriceTag(measurer, plotW, yFor(c.close), "%.2f".format(c.close), Color.Gray, candleH)
                val chg = if (c.open != 0f) (c.close - c.open) / c.open * 100f else 0f
                val chgText = "%+.2f%%".format(chg)
                val line1 = formatTimeLabel(c.ts, data.period, full = true)
                val line2 = "开 %.2f  高 %.2f  低 %.2f  收 %.2f  %s".format(c.open, c.high, c.low, c.close, chgText)
                val infoStyle = TextStyle(color = Color.White, fontSize = 12.sp)
                val l1 = measurer.measure(line1, infoStyle)
                val l2 = measurer.measure(line2, infoStyle)
                val boxW = max(l1.size.width, l2.size.width) + 20f
                val boxH = l1.size.height + l2.size.height + 16f
                val boxX = if (x > plotW / 2f) 8f else plotW - boxW - 8f
                drawRoundRect(
                    Color(0xDD1E1E1E), Offset(boxX, 8f), Size(boxW, boxH),
                    CornerRadius(8f, 8f)
                )
                drawText(measurer, line1, topLeft = Offset(boxX + 10f, 14f), style = infoStyle)
                drawText(measurer, line2, topLeft = Offset(boxX + 10f, 14f + l1.size.height + 4f), style = infoStyle)
            }
        }
    }
}

private fun formatTimeLabel(ts: String, period: String, full: Boolean = false): String {
    if (ts.length < 10) return ts
    if (full) return if (ts.length >= 16) ts.substring(0, 16).replace('T', ' ') else ts
    if (period == "1d") return ts.substring(0, 10)
    return if (ts.length >= 16) ts.substring(5, 16) else ts
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPriceTag(
    measurer: TextMeasurer,
    plotW: Float,
    y: Float,
    label: String,
    bg: Color,
    candleH: Float,
) {
    val style = TextStyle(color = Color.White, fontSize = 11.sp)
    val layout = measurer.measure(label, style)
    val tagH = layout.size.height + 8f
    val top = (y - tagH / 2f).coerceIn(0f, candleH - tagH)
    drawRoundRect(bg, Offset(plotW + 2f, top), Size(layout.size.width + 10f, tagH), CornerRadius(4f, 4f))
    drawText(measurer, label, topLeft = Offset(plotW + 7f, top + 4f), style = style)
}

@Composable
fun EquityLine(points: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
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
        // Start / end value labels, so the line is readable without guessing.
        val labelStyle = TextStyle(color = Color(0xFFB0B0B0), fontSize = 11.sp)
        val firstLabel = "%.0f".format(values.first())
        val lastLabel = "%.0f".format(values.last())
        drawText(measurer, firstLabel, topLeft = Offset(4f, 4f), style = labelStyle)
        val lastLayout = measurer.measure(lastLabel, labelStyle)
        drawText(
            measurer, lastLabel,
            topLeft = Offset(size.width - lastLayout.size.width - 4f, 4f),
            style = labelStyle
        )
    }
}

// Keep the text measurer import used on all compiler versions.
private fun unusedMeasurer(m: TextMeasurer) = m
