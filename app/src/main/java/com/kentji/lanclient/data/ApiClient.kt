package com.kentji.lanclient.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SignalsResult(
    val legs: List<Leg>,
    val alertFired: List<Leg>,
    val preview: List<PreviewItem>,
    val checkedAt: String?,
)

data class PreviewItem(
    val sym: String,
    val name: String,
    val period: String,
    val direction: String,
    val fired: Boolean,
    val text: String,
)

data class ObsResult(
    val dailyMr: List<Leg>,
    val strategyH: List<Leg>,
)

/**
 * Thin HTTP client for the local-network server. No auth headers: the
 * deployment is LAN-only by design. Every call is best-effort and returns
 * null/empty on failure so the UI can show an offline state instead of
 * crashing.
 */
class ApiClient(baseUrl: String) {
    private val base = baseUrl.trimEnd('/')
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend fun get(path: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$base$path").get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun ping(): Boolean {
        val body = get("/api/ping") ?: return false
        return try {
            LENIENT_JSON.parseToJsonElement(body).jsonObject.boolVal("ok") == true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun version(): String? {
        val body = get("/api/version") ?: return null
        return try {
            LENIENT_JSON.parseToJsonElement(body).jsonObject.str("version")
        } catch (_: Exception) {
            null
        }
    }

    suspend fun stocks(): List<StockItem> {
        val body = get("/api/stocks") ?: return emptyList()
        return try {
            LENIENT_JSON.decodeFromString(ListSerializer(StockItem.serializer()), body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun signals(): SignalsResult? {
        val body = get("/api/signals") ?: return null
        return try {
            val root = LENIENT_JSON.parseToJsonElement(body).jsonObject
            val legs = root["legs"]?.jsonArray?.mapNotNull { el ->
                runCatching { LENIENT_JSON.decodeFromJsonElement(Leg.serializer(), el) }.getOrNull()
            }.orEmpty()
            val alertFired = root["alerts"]?.jsonArray?.flatMap { group ->
                val fired = group.jsonObject["fired"]?.jsonArray.orEmpty()
                fired.mapNotNull { el ->
                    runCatching { LENIENT_JSON.decodeFromJsonElement(Leg.serializer(), el) }.getOrNull()
                }
            }.orEmpty()
            val preview = root["preview"]?.jsonArray?.mapNotNull { el ->
                val o = el.jsonObject
                PreviewItem(
                    sym = o.str("sym") ?: "",
                    name = o.str("name") ?: "",
                    period = o.str("period") ?: "",
                    direction = o.str("direction") ?: "",
                    fired = o.boolVal("fired") ?: false,
                    text = o.str("text") ?: "",
                )
            }.orEmpty()
            SignalsResult(legs, alertFired, preview, root.str("checked_at"))
        } catch (_: Exception) {
            null
        }
    }

    suspend fun obsSignals(): ObsResult {
        val body = get("/api/obs_signals") ?: return ObsResult(emptyList(), emptyList())
        return try {
            val root = LENIENT_JSON.parseToJsonElement(body).jsonObject
            fun parse(key: String): List<Leg> = root[key]?.jsonArray?.mapNotNull { el ->
                runCatching { LENIENT_JSON.decodeFromJsonElement(Leg.serializer(), el) }.getOrNull()
            }.orEmpty()
            ObsResult(parse("daily_mr"), parse("strategy_h"))
        } catch (_: Exception) {
            ObsResult(emptyList(), emptyList())
        }
    }

    suspend fun paperSummary(): List<PaperAccount> {
        val body = get("/api/paper/summary") ?: return emptyList()
        return try {
            LENIENT_JSON.decodeFromString(ListSerializer(PaperAccount.serializer()), body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun paperPending(): List<PaperPending> {
        val body = get("/api/paper/pending") ?: return emptyList()
        return try {
            LENIENT_JSON.decodeFromString(ListSerializer(PaperPending.serializer()), body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun paperTrades(symbol: String): List<PaperTrade> {
        val body = get("/api/paper/trades?symbol=$symbol") ?: return emptyList()
        return try {
            LENIENT_JSON.decodeFromString(ListSerializer(PaperTrade.serializer()), body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun paperEquity(symbol: String): List<EquityPoint> {
        val body = get("/api/paper/equity?symbol=$symbol") ?: return emptyList()
        return try {
            LENIENT_JSON.decodeFromString(ListSerializer(EquityPoint.serializer()), body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun llmAdvice(limit: Int = 100): List<LlmAdvice> {
        val body = get("/api/llm/advice?limit=$limit") ?: return emptyList()
        return try {
            LENIENT_JSON.decodeFromString(ListSerializer(LlmAdvice.serializer()), body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun llmReport(date: String): Pair<String?, Boolean> {
        val body = get("/api/llm/report?date=$date") ?: return Pair(null, false)
        return try {
            val root = LENIENT_JSON.parseToJsonElement(body).jsonObject
            Pair(root.str("md"), root.boolVal("backfilled") ?: false)
        } catch (_: Exception) {
            Pair(null, false)
        }
    }

    suspend fun dataStatusTrading(): Boolean? {
        val body = get("/api/data_status") ?: return null
        return try {
            LENIENT_JSON.parseToJsonElement(body).jsonObject.boolVal("trading")
        } catch (_: Exception) {
            null
        }
    }

    /** Chart: candles + volume + S1 marks, parsed tolerantly from /api/chart. */
    suspend fun chart(code: String, period: String, limit: Int = 300): ChartData? {
        val body = get("/api/chart?code=$code&period=$period&limit=$limit") ?: return null
        return try {
            val root = LENIENT_JSON.parseToJsonElement(body).jsonObject
            val klines = root["klines"]?.jsonObject
            val rows: JsonArray = klines?.get("data")?.jsonArray ?: JsonArray(emptyList())
            val candles = rows.mapNotNull { row ->
                val a = row.jsonArray
                if (a.size < 6) return@mapNotNull null
                Candle(
                    ts = a[0].jsonPrimitive.contentOrNull ?: "",
                    open = a[1].jsonPrimitive.floatOrNull ?: 0f,
                    close = a[2].jsonPrimitive.floatOrNull ?: 0f,
                    low = a[3].jsonPrimitive.floatOrNull ?: 0f,
                    high = a[4].jsonPrimitive.floatOrNull ?: 0f,
                    // Server sends volume as a float (e.g. 54261200.0);
                    // longOrNull fails on the ".0" and silently zeroed every bar.
                    volume = (a[5].jsonPrimitive.doubleOrNull ?: 0.0).toLong(),
                )
            }
            val indicators = root["indicators"]?.jsonObject
            val s1 = indicators?.get("s1")?.jsonObject
            val marks = mutableListOf<MarkPoint>()
            fun collectMarks(obj: JsonElement?, prefix: String) {
                val arr = obj?.jsonObject?.get("mark_points")?.jsonArray ?: return
                for (el in arr) {
                    val o = el.jsonObject
                    val ts = o.str("ts") ?: continue
                    val label = o.str("label") ?: o.str("text") ?: prefix
                    val lower = label.lowercase()
                    val kind = when {
                        lower.contains("卖") || lower.contains("sell") -> "sell"
                        else -> "buy"
                    }
                    marks.add(MarkPoint(ts, label, kind))
                }
            }
            collectMarks(s1, "S1")
            // Main-chart triangles are S1 only; volume/swing marks move
            // to their own sub-panels below.
            fun doubleList(el: JsonElement?): List<Double?> {
                val arr = el?.jsonArray ?: return emptyList()
                return arr.map { it.jsonPrimitive.doubleOrNull }
            }
            fun thresholdLines(obj: JsonObject?): List<ThresholdLine> {
                val arr = obj?.get("mark_lines")?.jsonArray ?: return emptyList()
                return arr.mapNotNull { e ->
                    val o = e.jsonObject
                    val v = o["value"]?.jsonPrimitive?.doubleOrNull
                        ?: return@mapNotNull null
                    ThresholdLine(v, o.str("color"))
                }
            }
            fun paneMarks(obj: JsonObject?): List<PaneMark> {
                val arr = obj?.get("mark_points")?.jsonArray ?: return emptyList()
                return arr.mapNotNull { e ->
                    val o = e.jsonObject
                    val ts = o.str("ts") ?: return@mapNotNull null
                    PaneMark(
                        ts,
                        o["value"]?.jsonPrimitive?.doubleOrNull,
                        o.str("label") ?: o.str("text") ?: "",
                        o.str("color"),
                    )
                }
            }
            val volume = indicators?.get("volume")?.jsonObject
            val swing = indicators?.get("swing")?.jsonObject
            val s1PosLines = mutableMapOf<String, List<Double?>>()
            val posObj = s1?.get("pos_lines")
            if (posObj is JsonObject) {
                for ((np, arr) in posObj) {
                    if (arr !is JsonNull) s1PosLines[np] = doubleList(arr)
                }
            }
            // Observation panels only for the strategy panels this symbol
            // enables (same /api/subcharts list the web page uses).
            val enabled = subcharts(code)
            val obsPanels = mutableListOf<ObsPanel>()
            if ("daily_mr" in enabled) {
                val o = indicators?.get("daily_mr")?.jsonObject
                val m = paneMarks(o)
                if (o != null && m.isNotEmpty()) {
                    obsPanels.add(ObsPanel("daily_mr", "日线MR★观察池", "#4488ff", m))
                }
            }
            if ("strategy_h" in enabled) {
                val o = indicators?.get("strategy_h")?.jsonObject
                val m = paneMarks(o)
                if (o != null && m.isNotEmpty()) {
                    obsPanels.add(ObsPanel("strategy_h", "超跌H★观察池", "#ffaa00", m))
                }
            }
            // S1 pos line: best effort. Server sends pos_lines as a dict
            // keyed by leg (e.g. {"20": [...]}), not a list; tolerate both.
            val pos = mutableListOf<Float?>()
            val posEl = s1?.get("pos_lines")
            val posValues: JsonArray? = when {
                posEl == null || posEl is JsonNull -> null
                posEl is JsonArray -> posEl.firstOrNull()?.jsonObject?.let {
                    it["values"]?.takeIf { v -> v !is JsonNull }?.jsonArray
                        ?: it["data"]?.takeIf { v -> v !is JsonNull }?.jsonArray
                }
                posEl is JsonObject -> posEl.values.firstOrNull()
                    ?.takeIf { v -> v !is JsonNull }?.jsonArray
                else -> null
            }
            if (posValues != null) {
                for (v in posValues) pos.add(v.jsonPrimitive.floatOrNull)
            }
            val fresh = root["freshness"]?.jsonObject?.boolVal("fresh") ?: false
            ChartData(
                code = klines?.str("code") ?: code,
                name = klines?.str("name") ?: code,
                period = klines?.str("period") ?: period,
                candles = candles,
                s1Pos = pos,
                marks = marks,
                fresh = fresh,
                lastTs = candles.lastOrNull()?.ts,
                volRatio = doubleList(volume?.get("values")),
                volLines = thresholdLines(volume),
                volMarks = paneMarks(volume),
                swingZhan = doubleList(swing?.get("zhan")),
                swingLines = thresholdLines(swing),
                swingMarks = paneMarks(swing),
                s1PosLines = s1PosLines,
                s1Lines = thresholdLines(s1),
                s1PaneMarks = paneMarks(s1),
                obsPanels = obsPanels,
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Strategy sub-panels enabled for a symbol (web parity, default s1). */
    suspend fun subcharts(code: String): List<String> {
        return try {
            val body = get("/api/subcharts?code=$code") ?: return listOf("s1")
            val arr = LENIENT_JSON.parseToJsonElement(body).jsonObject["subcharts"]?.jsonArray
            val list = arr?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            if (list.isEmpty()) listOf("s1") else list
        } catch (_: Exception) {
            listOf("s1")
        }
    }
}
