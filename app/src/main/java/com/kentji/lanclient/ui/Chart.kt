package com.kentji.lanclient.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kentji.lanclient.data.Candle
import com.kentji.lanclient.data.ChartData
import com.kentji.lanclient.data.MarkPoint
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.absolute
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberCandlestickCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.candlestickSeries
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.core.cartesian.layer.CandlestickCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CandlestickCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker

// Candle body width and bar spacing, in dp. The price and volume charts
// use the same values so their shared scroll/zoom state maps to the same
// x positions in both panes.
private const val BODY_WIDTH_DP = 8f
private const val BAR_SPACING_DP = 4f

// Fixed vertical-axis width, in dp, on both charts. The price chart puts
// its price axis on the right and a blank spacer of the same width on the
// left; the volume chart mirrors that, so both plot areas line up exactly.
private const val AXIS_WIDTH_DP = 56f

/**
 * Stock chart on Vico (compose-m3 2.1.4): candlestick pane + volume pane,
 * sharing one scroll/zoom state so dragging and pinching move both panes
 * together. Tapping a candle shows an OHLC marker; S1 buy/sell points are
 * drawn by a custom [Decoration] using Vico's own layer coordinates, so
 * the triangles stay glued to their candles while scrolling and zooming.
 */
@Composable
fun ChartView(data: ChartData, modifier: Modifier = Modifier) {
    val candles = data.candles
    if (candles.isEmpty()) return

    // Start on the newest bars, showing about 60 at first; pinch in to 20.
    val scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End)
    val zoomState = rememberVicoZoomState(
        zoomEnabled = true,
        initialZoom = Zoom.x(60.0),
        minZoom = Zoom.Content,
        maxZoom = Zoom.x(20.0),
    )

    val priceProducer = remember { CartesianChartModelProducer() }
    val volumeProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        priceProducer.runTransaction {
            candlestickSeries(
                opening = candles.map { it.open.toDouble() },
                closing = candles.map { it.close.toDouble() },
                low = candles.map { it.low.toDouble() },
                high = candles.map { it.high.toDouble() },
            )
        }
        volumeProducer.runTransaction {
            columnSeries {
                series(candles.map { if (it.close >= it.open) it.volume.toDouble() else 0.0 })
                series(candles.map { if (it.close >= it.open) 0.0 else it.volume.toDouble() })
            }
        }
    }

    val priceFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "%.2f".format(value) }
    }
    val volumeFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            when {
                value >= 1e8 -> "%.1f亿".format(value / 1e8)
                value >= 10000 -> "%.0f万".format(value / 10000)
                else -> "%.0f".format(value)
            }
        }
    }
    val timeFormatter = remember(candles, data.period) {
        CartesianValueFormatter { _, value, _ ->
            val idx = value.toInt()
            if (idx in candles.indices) formatTimeLabel(candles[idx].ts, data.period) else ""
        }
    }
    val ohlcFormatter = remember {
        object : DefaultCartesianMarker.ValueFormatter {
            override fun format(
                context: CartesianDrawingContext,
                targets: List<CartesianMarker.Target>,
            ): CharSequence {
                val target = targets.firstOrNull() as? CandlestickCartesianLayerMarkerTarget
                    ?: return ""
                val e = target.entry
                return "开 %.2f 高 %.2f 低 %.2f 收 %.2f".format(
                    e.opening, e.high, e.low, e.closing
                )
            }
        }
    }
    // Vico's auto range always floors at 0 for all-positive values, which
    // squashes the candles into a thin strip at the top. Pin the price
    // range to the data instead, with a small pad above and below.
    val priceRange = remember(data) {
        val lo = candles.minOf { it.low.toDouble() }
        val hi = candles.maxOf { it.high.toDouble() }
        val pad = ((hi - lo) * 0.05).takeIf { it > 0.0 } ?: 0.01
        CartesianLayerRangeProvider.fixed(minY = lo - pad, maxY = hi + pad)
    }
    val decorations = remember(data) { listOf(S1Decoration(data.marks, candles)) }

    val upBody = rememberLineComponent(fill = fill(UpColor), thickness = BODY_WIDTH_DP.dp)
    val upWick = rememberLineComponent(fill = fill(UpColor), thickness = 1.5.dp)
    val downBody = rememberLineComponent(fill = fill(DownColor), thickness = BODY_WIDTH_DP.dp)
    val downWick = rememberLineComponent(fill = fill(DownColor), thickness = 1.5.dp)

    Column(modifier.fillMaxWidth()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberCandlestickCartesianLayer(
                    candleProvider = CandlestickCartesianLayer.CandleProvider.absolute(
                        bullish = CandlestickCartesianLayer.Candle(upBody, upWick, upWick),
                        neutral = CandlestickCartesianLayer.Candle(upBody, upWick, upWick),
                        bearish = CandlestickCartesianLayer.Candle(downBody, downWick, downWick),
                    ),
                    candleSpacing = BAR_SPACING_DP.dp,
                    rangeProvider = priceRange,
                ),
                // Blank left spacer so the price plot starts where the
                // volume plot starts (the volume axis sits on the left).
                startAxis = VerticalAxis.rememberStart(
                    line = null,
                    label = null,
                    tick = null,
                    guideline = null,
                    size = BaseAxis.Size.Fixed(AXIS_WIDTH_DP),
                ),
                endAxis = VerticalAxis.rememberEnd(
                    label = rememberAxisLabelComponent(
                        color = Color(0xFFB0B0B0), textSize = 11.sp
                    ),
                    valueFormatter = priceFormatter,
                    guideline = rememberAxisGuidelineComponent(),
                    size = BaseAxis.Size.Fixed(AXIS_WIDTH_DP),
                ),
                marker = rememberDefaultCartesianMarker(
                    label = rememberTextComponent(color = Color.White, textSize = 12.sp),
                    valueFormatter = ohlcFormatter,
                    labelPosition = DefaultCartesianMarker.LabelPosition.Top,
                    guideline = rememberAxisGuidelineComponent(),
                ),
                decorations = decorations,
            ),
            modelProducer = priceProducer,
            scrollState = scrollState,
            zoomState = zoomState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        listOf(
                            rememberLineComponent(
                                fill = fill(UpColor), thickness = BODY_WIDTH_DP.dp
                            ),
                            rememberLineComponent(
                                fill = fill(DownColor), thickness = BODY_WIDTH_DP.dp
                            ),
                        )
                    ),
                    columnCollectionSpacing = BAR_SPACING_DP.dp,
                    // Two series (up bars, down bars) with a zero in the
                    // other series' slots; stacked, they read as one bar
                    // per x in the right color.
                    mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = rememberAxisLabelComponent(
                        color = Color(0xFFB0B0B0), textSize = 11.sp
                    ),
                    valueFormatter = volumeFormatter,
                    guideline = rememberAxisGuidelineComponent(),
                    size = BaseAxis.Size.Fixed(AXIS_WIDTH_DP),
                ),
                // Blank right spacer mirroring the price axis, so the
                // volume plot ends where the price plot ends.
                endAxis = VerticalAxis.rememberEnd(
                    line = null,
                    label = null,
                    tick = null,
                    guideline = null,
                    size = BaseAxis.Size.Fixed(AXIS_WIDTH_DP),
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = rememberAxisLabelComponent(
                        color = Color(0xFFB0B0B0), textSize = 11.sp
                    ),
                    valueFormatter = timeFormatter,
                    itemPlacer = HorizontalAxis.ItemPlacer.aligned(
                        // Fixed spacing in bars, not a fraction of the
                        // whole series: the visible window only holds
                        // ~60 bars, so a total/5 spacing left the window
                        // with a single label.
                        spacing = { 12 }
                    ),
                ),
            ),
            modelProducer = volumeProducer,
            scrollState = scrollState,
            zoomState = zoomState,
            modifier = Modifier.fillMaxWidth().weight(0.3f),
        )
    }
}

/**
 * S1 buy/sell triangles, drawn over the Vico price pane. The x/y mapping
 * is copied from Vico's own candlestick layer (same start padding, scroll,
 * zoom and spacing), so each triangle tracks its candle exactly.
 */
private class S1Decoration(
    marks: List<MarkPoint>,
    private val candles: List<Candle>,
) : Decoration {
    private val indexed: List<Pair<Int, MarkPoint>> = marks.mapNotNull { mark ->
        candles.indexOfFirst { it.ts == mark.ts }
            .takeIf { it >= 0 }
            ?.let { it to mark }
    }
    private val upPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
        color = UpColor.toArgb()
    }
    private val downPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
        color = DownColor.toArgb()
    }

    override fun drawOverLayers(context: CartesianDrawingContext) {
        if (indexed.isEmpty()) return
        with(context) {
            val yRange = ranges.getYRange(null)
            val dir = layoutDirectionMultiplier.toFloat()
            val start = if (isLtr) layerBounds.left else layerBounds.right
            val halfW = (BODY_WIDTH_DP / 2f).pixels * zoom
            val drawingStart = start + (layerDimensions.startPadding - halfW) * dir - scroll
            val halfBase = 6f.pixels
            val triHeight = 8f.pixels
            val gap = 3f.pixels
            val path = android.graphics.Path()
            for ((idx, mark) in indexed) {
                val mult = ((idx.toDouble() - ranges.minX) / ranges.xStep).toFloat()
                val cx = drawingStart + dir * layerDimensions.xSpacing * mult + halfW
                if (cx <= layerBounds.left - 1f || cx >= layerBounds.right + 1f) continue
                val c = candles[idx]
                val isSell = mark.kind == "sell"
                val price = if (isSell) c.high else c.low
                val cy = layerBounds.bottom -
                    ((price - yRange.minY) / yRange.length).toFloat() * layerBounds.height()
                path.reset()
                if (isSell) {
                    // Triangle above the high, pointing down at the candle.
                    val apexY = cy - gap
                    path.moveTo(cx - halfBase, apexY - triHeight)
                    path.lineTo(cx + halfBase, apexY - triHeight)
                    path.lineTo(cx, apexY)
                    path.close()
                    canvas.drawPath(path, downPaint)
                } else {
                    // Triangle below the low, pointing up at the candle.
                    val apexY = cy + gap
                    path.moveTo(cx - halfBase, apexY + triHeight)
                    path.lineTo(cx + halfBase, apexY + triHeight)
                    path.lineTo(cx, apexY)
                    path.close()
                    canvas.drawPath(path, upPaint)
                }
            }
        }
    }
}

private fun formatTimeLabel(ts: String, period: String, full: Boolean = false): String {
    if (ts.length < 10) return ts
    if (full) return if (ts.length >= 16) ts.substring(0, 16).replace('T', ' ') else ts
    if (period == "1d") return ts.substring(0, 10)
    return if (ts.length >= 16) ts.substring(5, 16).replace('T', ' ') else ts
}

@Composable
fun EquityLine(points: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
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
