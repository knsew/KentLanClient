package com.kentji.lanclient.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val LENIENT_JSON = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

fun JsonObject.str(key: String): String? =
    get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" }

fun JsonObject.dbl(key: String): Double? = get(key)?.jsonPrimitive?.doubleOrNull
fun JsonObject.intVal(key: String): Int? = get(key)?.jsonPrimitive?.intOrNull
fun JsonObject.boolVal(key: String): Boolean? =
    get(key)?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

/** Display name for a server period code (server stores codes like "30m"). */
fun periodLabel(period: String): String = when (period) {
    "1m" -> "1分钟"
    "5m" -> "5分钟"
    "15m" -> "15分钟"
    "30m" -> "30分钟"
    "60m" -> "60分钟"
    "1d" -> "日线"
    else -> period
}

/** One leg / fired event. Field names follow the server API; parsing is tolerant. */
@Serializable
data class Leg(
    val sym: String = "",
    val name: String = "",
    val period: String = "",
    val direction: String = "",
    val rule: String = "",
    val Np: Int = 0,
    val Nv: Int = 0,
    val H: Int = 0,
    val tier: Int? = null,
    val tier_name: String? = null,
    val suggested_size: Int? = null,
    val signal: Boolean = false,
    val cooled: Boolean = false,
    val bar_ts: String? = null,
    val px: Double? = null,
    val leg_id: String? = null,
    val strategy: String? = null,
    val checked_at: String? = null,
) {
    /** Stable identity for notification dedup. */
    fun eventKey(): String = "${leg_id ?: ""}|${bar_ts ?: ""}|$sym|$period|$direction|$rule"
    fun displayDirection(): String = direction
    fun displayPeriod(): String = periodLabel(period)
}

@Serializable
data class StockItem(
    val code: String = "",
    val name: String = "",
    val market: String = "",
    val last_px: Double? = null,
    val chg_pct: Double? = null,
    val quote_date: String? = null,
)

@Serializable
data class PaperAccount(
    val symbol: String = "",
    val name: String = "",
    val initial_cash: Double = 0.0,
    val qty: Long = 0,
    val avg_cost: Double = 0.0,
    val last_px: Double? = null,
    val market_value: Double? = null,
    val cash: Double = 0.0,
    val total_equity: Double? = null,
    val unrealized_pnl: Double? = null,
    val realized_pnl: Double = 0.0,
    val return_pct: Double? = null,
    val valuation_stale: Boolean = false,
)

@Serializable
data class PaperPending(
    val symbol: String = "",
    val direction: String = "",
    val target_u: Int = 0,
    val signal_bar_ts: String = "",
    val rule: String = "",
    val tier_name: String? = null,
    val created_at: String = "",
) {
    fun key(): String = "$symbol|$signal_bar_ts|$rule|$direction"
}

@Serializable
data class PaperTrade(
    val symbol: String = "",
    val side: String = "",
    val bar_ts: String = "",
    val fill_ts: String = "",
    val price: Double = 0.0,
    val qty: Long = 0,
    val amount: Double = 0.0,
    val fee: Double = 0.0,
    val rule: String = "",
    val tier_name: String? = null,
    val target_u: Int? = null,
    val position_after: Long? = null,
    val cash_after: Double? = null,
) {
    fun key(): String = "$symbol|$bar_ts|$side|$rule"
}

@Serializable
data class EquityPoint(
    val date: String = "",
    val equity: Double = 0.0,
    val return_pct: Double = 0.0,
)

/** LLM advice. The server returns `advice` as a JSON-encoded string. */
@Serializable
data class LlmAdvice(
    val bar_ts: String = "",
    val symbol: String = "",
    val period: String = "",
    val direction: String = "",
    val rule: String = "",
    val tier_name: String? = null,
    val suggested_size: Int? = null,
    val action: String = "",
    val advice: String = "",
    val backfilled: Int = 0,
    val created_at: String = "",
) {
    fun key(): String = "$symbol|$bar_ts|$rule|$created_at"
    fun isBackfilled(): Boolean = backfilled != 0
    fun isDeviation(): Boolean = action in setOf("skip", "halve", "double")

    /** Display name for the advice action code (server stores English codes). */
    fun displayAction(): String = when (action) {
        "follow" -> "跟随"
        "skip" -> "跳过"
        "halve" -> "减半"
        "double" -> "加倍"
        else -> action
    }

    /** Parsed reason/confidence from the inner advice JSON, best effort. */
    fun parsedReason(): String {
        return try {
            val inner = LENIENT_JSON.parseToJsonElement(advice).jsonObject
            inner.str("reason") ?: advice
        } catch (_: Exception) {
            advice
        }
    }

    fun parsedConfidence(): Double? {
        return try {
            LENIENT_JSON.parseToJsonElement(advice).jsonObject.dbl("confidence")
        } catch (_: Exception) {
            null
        }
    }
}

data class Candle(
    val ts: String,
    val open: Float,
    val close: Float,
    val low: Float,
    val high: Float,
    val volume: Long,
)

data class MarkPoint(
    val ts: String,
    val label: String,
    val kind: String, // "buy" or "sell" best effort
)

data class ChartData(
    val code: String,
    val name: String,
    val period: String,
    val candles: List<Candle>,
    val s1Pos: List<Float?>,
    val marks: List<MarkPoint>,
    val fresh: Boolean,
    val lastTs: String?,
    // Sub-panels, parsed from /api/chart indicators (web parity).
    val volRatio: List<Double?> = emptyList(),
    val volLines: List<ThresholdLine> = emptyList(),
    val volMarks: List<PaneMark> = emptyList(),
    val swingZhan: List<Double?> = emptyList(),
    val swingLines: List<ThresholdLine> = emptyList(),
    val swingMarks: List<PaneMark> = emptyList(),
    val s1PosLines: Map<String, List<Double?>> = emptyMap(),
    val s1Lines: List<ThresholdLine> = emptyList(),
    val s1PaneMarks: List<PaneMark> = emptyList(),
    val obsPanels: List<ObsPanel> = emptyList(),
)

/** A horizontal threshold line on a sub-panel (value + web color). */
data class ThresholdLine(
    val value: Double,
    val colorHex: String?,
)

/** A mark point on a sub-panel (ts + value + web label/color). */
data class PaneMark(
    val ts: String,
    val value: Double?,
    val label: String,
    val colorHex: String?,
)

/** An observation-pool panel (daily MR / oversold H) carried by mark points. */
data class ObsPanel(
    val key: String,
    val title: String,
    val colorHex: String?,
    val marks: List<PaneMark>,
)
