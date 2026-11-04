package com.kentji.lanclient.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.kentji.lanclient.data.PaneMark
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.absolute
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberCandlestickCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
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
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.core.cartesian.layer.CandlestickCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CandlestickCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker

// Candle body width and bar spacing, in dp. Every pane uses the same
// 12dp pitch (8 body + 4 spacing, or 12 point spacing for line layers)
// so the shared scroll/zoom state maps to the same x positions in all
// panes.
private const val BODY_WIDTH_DP = 8f
private const val BAR_SPACING_DP = 4f
private const val LINE_PITCH_DP = 12f

// Fixed vertical-axis width, in dp, on both sides of every pane, so all
// plot areas line up exactly.
private const val AXIS_WIDTH_DP = 56f

private val AXIS_LABEL_COLOR = Color(0xFFB0B0B0)
private val PANE_TITLE_COLOR = Color(0xFF8B98AD)

/**
 * Stock chart on Vico (compose-m3 2.1.4), web parity: candlestick pane +
 * volume pane + 量能/短摆/S1/observation sub-panels, all sharing one
 * scroll/zoom state so dragging and pinching move every pane together.
 * The column scrolls vertically; the time axis sits on the last pane.
 * Tapping a candle shows an OHLC marker; S1 buy/sell points are drawn by
 * a custom [Decoration] using Vico's own layer coordinates, so the
 * triangles stay glued to their candles while scrolling and zooming.
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

    val showVol = data.volRatio.any { it != null }
    val showSwing = data.swingZhan.any { it != null }
    val showS1 = data.s1PosLines.values.any { it.any { v -> v != null } } ||
        data.s1PaneMarks.isNotEmpty()
    val showObs = data.obsPanels.isNotEmpty()
    val lastPane = when {
        showObs -> "obs"
        showS1 -> "s1"
        showSwing -> "swing"
        showVol -> "vol"
        else -> "volume"
    }

    val priceProducer = remember { CartesianChartModelProducer() }
    val volumeProducer = remember { CartesianChartModelProducer() }
    val volProducer = remember { CartesianChartModelProducer() }
    val swingProducer = remember { CartesianChartModelProducer() }
    val s1Producer = remember { CartesianChartModelProducer() }
    val obsProducers = remember(data.obsPanels.size) {
        List(data.obsPanels.size) { CartesianChartModelProducer() }
    }
    val volPairs = remember(data) { filterPairs(data.volRatio) }
    val swingPairs = remember(data) { filterPairs(data.swingZhan) }
    val s1Pairs = remember(data) {
        data.s1PosLines.mapValues { (_, v) -> filterPairs(v) }
    }
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
        if (showVol) {
            volProducer.runTransaction {
                columnSeries { series(volPairs.first, volPairs.second) }
                lineSeries {
                    if (data.volLines.isEmpty()) {
                        // Invisible carrier so the line layer always has a
                        // series; 0 keeps the auto range unaffected.
                        series(List(candles.size) { 0.0 })
                    } else {
                        for (line in data.volLines) {
                            series(List(candles.size) { line.value })
                        }
                    }
                }
            }
        }
        if (showSwing) {
            swingProducer.runTransaction {
                lineSeries {
                    series(swingPairs.first, swingPairs.second)
                    for (line in data.swingLines) {
                        series(List(candles.size) { line.value })
                    }
                }
            }
        }
        if (showS1) {
            s1Producer.runTransaction {
                lineSeries {
                    if (s1Pairs.isEmpty()) {
                        // Cross-period marks only: invisible carrier line
                        // so the pane still has an x range for the marks.
                        series(List(candles.size) { 50.0 })
                    } else {
                        for ((_, p) in s1Pairs) series(p.first, p.second)
                    }
                    for (line in data.s1Lines) {
                        series(List(candles.size) { line.value })
                    }
                }
            }
        }
        data.obsPanels.forEachIndexed { i, _ ->
            obsProducers[i].runTransaction {
                lineSeries { series(List(candles.size) { 50.0 }) }
            }
        }
    }

    val priceFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "%.2f".format(value) }
    }
    val intFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "%.0f".format(value) }
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
    val axisLabel = rememberAxisLabelComponent(color = AXIS_LABEL_COLOR, textSize = 11.sp)
    val guideline = rememberAxisGuidelineComponent()
    val blankEnd = VerticalAxis.rememberEnd(
        line = null, label = null, tick = null, guideline = null,
        size = BaseAxis.Size.Fixed(AXIS_WIDTH_DP),
    )
    // Time axis only on the last pane; every other pane has none.
    val bottomAxis = HorizontalAxis.rememberBottom(
        label = axisLabel,
        valueFormatter = timeFormatter,
        itemPlacer = HorizontalAxis.ItemPlacer.aligned(
            // Fixed spacing in bars, not a fraction of the whole series:
            // the visible window only holds ~60 bars, so a total/5
            // spacing left the window with a single label.
            spacing = { 12 }
        ),
    )
    // Line styles, built per data load (line layers need one Line per
    // series, in the same order as the series fed to the producer).
    val volLines = remember(data) {
        if (data.volLines.isEmpty()) {
            listOf(transparentLine())
        } else {
            data.volLines.map { dashedLine(parseHexColor(it.colorHex, PANE_TITLE_COLOR)) }
        }
    }
    val swingLines = remember(data) {
        val list = mutableListOf(
            LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(fill(Color(0xFFC678DD)))
            )
        )
        for (t in data.swingLines) {
            list.add(dashedLine(parseHexColor(t.colorHex, PANE_TITLE_COLOR)))
        }
        list
    }
    val s1Lines = remember(data) {
        val list = mutableListOf<LineCartesianLayer.Line>()
        for ((np, _) in s1Pairs) {
            list.add(
                LineCartesianLayer.Line(
                    fill = LineCartesianLayer.LineFill.single(
                        fill(posColorFor(np))
                    )
                )
            )
        }
        for (t in data.s1Lines) {
            list.add(dashedLine(parseHexColor(t.colorHex, PANE_TITLE_COLOR)))
        }
        if (list.isEmpty()) list.add(transparentLine())
        list
    }
    val obsLine = remember { transparentLine() }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
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
                // sub-panel plots start (their axes sit on the left).
                startAxis = VerticalAxis.rememberStart(
                    line = null,
                    label = null,
                    tick = null,
                    guideline = null,
                    size = BaseAxis.Size.Fixed(AXIS_WIDTH_DP),
                ),
                endAxis = VerticalAxis.rememberEnd(
                    label = axisLabel,
                    valueFormatter = priceFormatter,
                    guideline = guideline,
                    size = BaseAxis.Size.Fixed(AXIS_WIDTH_DP),
                ),
                marker = rememberDefaultCartesianMarker(
                    label = rememberTextComponent(color = Color.White, textSize = 12.sp),
                    valueFormatter = ohlcFormatter,
                    labelPosition = DefaultCartesianMarker.LabelPosition.Top,
                    guideline = guideline,
                ),
                decorations = decorations,
            ),
            modelProducer = priceProducer,
            scrollState = scrollState,
            zoomState = zoomState,
            modifier = Modifier.fillMaxWidth().height(320.dp),
        )
        PaneLabel("成交量")
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
                    label = axisLabel,
                    valueFormatter = intFormatter,
                    guideline = guideline,
                    size = BaseAxis.Size.Fixed(AXIS_WIDTH_DP),
                ),
                // Blank right spacer mirroring the price axis, so the
                // volume plot ends where the price plot ends.
                endAxis = blankEnd,
                bottomAxis = if (lastPane == "volume") bottomAxis else null,
            ),
            modelProducer = volumeProducer,
            scrollState = scrollState,
            zoomState = zoomState,
            modifier = Modifier.fillMaxWidth().height(110.dp),
        )
        if (showVol) {
            PaneLabel("量能 · 量比")
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(
                        columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                            listOf(
                                rememberLineComponent(
                                    fill = fill(Color(0xFF7B8AA0)),
                                    thickness = BODY_WIDTH_DP.dp
                                )
                            )
                        ),
                        columnCollectionSpacing = BAR_SPACING_DP.dp,
                        mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
                    ),
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(volLines),
                        pointSpacing = LINE_PITCH_DP.dp,
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        label = axisLabel,
                        valueFormatter = intFormatter,
                        guideline = guideline,
                        size = BaseAxis.Size.Fixed(AXIS_WIDTH_DP),
                    ),
                    endAxis = blankEnd,
                    bottomAxis = if (lastPane == "vol") bottomAxis else null,
                    decorations = remember(data) {
                        listOf(PaneDotDecoration(data.volMarks, candles))
                    },
                ),
                modelProducer = volProducer,
                scrollState = scrollState,
                zoomState = zoomState,
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
        }
        if (showSwing) {
            PaneLabel("短摆 · 战线")
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(swingLines),
                        pointSpacing = LINE_PITCH_DP.dp,
                        rangeProvider = CartesianLayerRangeProvider.fixed(
                            minY = 0.0, maxY = 100.0
                        ),
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        label = axisLabel,
                        valueFormatter = intFormatter,
                        guideline = guideline,
                        size = BaseAxis.Size.Fixed(AXIS_WIDTH_DP),
                    ),
                    endAxis = blankEnd,
                    bottomAxis = if (lastPane == "swing") bottomAxis else null,
                    decorations = remember(data) {
                        listOf(PaneDotDecoration(data.swingMarks, candles))
                    },
                ),
                modelProducer = swingProducer,
                scrollState = scrollState,
                zoomState = zoomState,
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
        }
        if (showS1) {
            PaneLabel("S1策略 · pos线")
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(s1Lines),
                        pointSpacing = LINE_PITCH_DP.dp,
                        rangeProvider = CartesianLayerRangeProvider.fixed(
                            minY = 0.0, maxY = 100.0
                        ),
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        label = axisLabel,
                        valueFormatter = intFormatter,
                        guideline = guideline,
                        size = BaseAxis.Size.Fixed(AXIS_WIDTH_DP),
                    ),
                    endAxis = blankEnd,
                    bottomAxis = if (lastPane == "s1") bottomAxis else null,
                    decorations = remember(data) {
                        listOf(S1PaneDecoration(data.s1PaneMarks, candles))
                    },
                ),
                modelProducer = s1Producer,
                scrollState = scrollState,
                zoomState = zoomState,
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
        }
        if (showObs) {
            data.obsPanels.forEachIndexed { i, panel ->
                PaneLabel(panel.title)
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(
                                listOf(obsLine)
                            ),
                            pointSpacing = LINE_PITCH_DP.dp,
                            rangeProvider = CartesianLayerRangeProvider.fixed(
                                minY = 0.0, maxY = 100.0
                            ),
                        ),
                        startAxis = VerticalAxis.rememberStart(
                            label = axisLabel,
                            valueFormatter = intFormatter,
                            guideline = guideline,
                            size = BaseAxis.Size.Fixed(AXIS_WIDTH_DP),
                        ),
                        endAxis = blankEnd,
                        bottomAxis = if (lastPane == "obs" && i == data.obsPanels.lastIndex) {
                            bottomAxis
                        } else {
                            null
                        },
                        decorations = remember(data) {
                            listOf(PaneDotDecoration(panel.marks, candles))
                        },
                    ),
                    modelProducer = obsProducers[i],
                    scrollState = scrollState,
                    zoomState = zoomState,
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                )
            }
        }
    }
}

@Composable
private fun PaneLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = PANE_TITLE_COLOR,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/** Drops nulls but keeps each value's candle index, so lines don't break. */
private fun filterPairs(values: List<Double?>): Pair<List<Int>, List<Double>> {
    val xs = mutableListOf<Int>()
    val ys = mutableListOf<Double>()
    values.forEachIndexed { i, v ->
        if (v != null) {
            xs.add(i)
            ys.add(v)
        }
    }
    return xs to ys
}

/** Web pos-line colors: Np 60 yellow, Np 20 cyan. */
private fun posColorFor(np: String): Color = when (np) {
    "60" -> Color(0xFFFFD54F)
    "20" -> Color(0xFF4DD0E1)
    else -> Color(0xFFFFD54F)
}

private fun transparentLine(): LineCartesianLayer.Line =
    LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(fill(Color.Transparent))
    )

private fun dashedLine(color: Color): LineCartesianLayer.Line =
    LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(fill(color)),
        stroke = LineCartesianLayer.LineStroke.Dashed(thicknessDp = 1f),
    )

private fun parseHexColor(hex: String?, fallback: Color): Color {
    if (hex == null) return fallback
    val h = hex.removePrefix("#")
    if (h.length != 6) return fallback
    return try {
        Color(0xFF000000L or h.toLong(16))
    } catch (_: Exception) {
        fallback
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

/**
 * S1 marks on the S1 pane itself: triangles pinned to the mark's pos
 * value (0-100 scale), buy below pointing up, sell above pointing down.
 * Kind comes from the label (卖/sell), same rule as the parser.
 */
private class S1PaneDecoration(
    marks: List<PaneMark>,
    private val candles: List<Candle>,
) : Decoration {
    private val indexed: List<Triple<Int, PaneMark, Boolean>> = marks.mapNotNull { mark ->
        val value = mark.value ?: return@mapNotNull null
        val idx = candles.indexOfFirst { it.ts == mark.ts }
        if (idx < 0) return@mapNotNull null
        val lower = mark.label.lowercase()
        val isSell = lower.contains("卖") || lower.contains("sell")
        Triple(idx, mark, isSell)
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
            for ((idx, mark, isSell) in indexed) {
                val value = mark.value ?: continue
                val mult = ((idx.toDouble() - ranges.minX) / ranges.xStep).toFloat()
                val cx = drawingStart + dir * layerDimensions.xSpacing * mult + halfW
                if (cx <= layerBounds.left - 1f || cx >= layerBounds.right + 1f) continue
                val cy = layerBounds.bottom -
                    ((value - yRange.minY) / yRange.length).toFloat() * layerBounds.height()
                path.reset()
                if (isSell) {
                    val apexY = cy - gap
                    path.moveTo(cx - halfBase, apexY - triHeight)
                    path.lineTo(cx + halfBase, apexY - triHeight)
                    path.lineTo(cx, apexY)
                    path.close()
                    canvas.drawPath(path, downPaint)
                } else {
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

/** Mark points on a sub-panel: small dots in the web color at (ts, value). */
private class PaneDotDecoration(
    marks: List<PaneMark>,
    private val candles: List<Candle>,
) : Decoration {
    private val indexed: List<Triple<Int, PaneMark, android.graphics.Paint>> =
        marks.mapNotNull { mark ->
            val value = mark.value ?: return@mapNotNull null
            val idx = candles.indexOfFirst { it.ts == mark.ts }
            if (idx < 0) return@mapNotNull null
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.FILL
                color = parseHexColor(mark.colorHex, PANE_TITLE_COLOR).toArgb()
            }
            Triple(idx, mark, paint)
        }

    override fun drawOverLayers(context: CartesianDrawingContext) {
        if (indexed.isEmpty()) return
        with(context) {
            val yRange = ranges.getYRange(null)
            val dir = layoutDirectionMultiplier.toFloat()
            val start = if (isLtr) layerBounds.left else layerBounds.right
            val halfW = (BODY_WIDTH_DP / 2f).pixels * zoom
            val drawingStart = start + (layerDimensions.startPadding - halfW) * dir - scroll
            val radius = 3.5f.pixels
            for ((idx, mark, paint) in indexed) {
                val value = mark.value ?: continue
                val mult = ((idx.toDouble() - ranges.minX) / ranges.xStep).toFloat()
                val cx = drawingStart + dir * layerDimensions.xSpacing * mult + halfW
                if (cx <= layerBounds.left - 1f || cx >= layerBounds.right + 1f) continue
                val cy = layerBounds.bottom -
                    ((value - yRange.minY) / yRange.length).toFloat() * layerBounds.height()
                canvas.drawCircle(cx, cy, radius, paint)
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
