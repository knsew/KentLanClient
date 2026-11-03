package com.kentji.lanclient.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

const val DEFAULT_BASE_URL = "http://10.1.1.7:5888/"

/** User settings (SharedPreferences) + notification dedup state (a small JSON file). */
class Store(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("lanclient", Context.MODE_PRIVATE)
    private val notifiedFile: File = File(context.filesDir, "notified.json")
    private val lock = Any()

    var baseUrl: String
        get() = prefs.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString("base_url", value).apply()

    var themeMode: String
        get() = prefs.getString("theme_mode", "system") ?: "system"
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    fun notifyEnabled(channel: String, default: Boolean = true): Boolean =
        prefs.getBoolean("notify_$channel", default)

    fun setNotifyEnabled(channel: String, enabled: Boolean) =
        prefs.edit().putBoolean("notify_$channel", enabled).apply()

    fun serviceEnabled(): Boolean = prefs.getBoolean("service_enabled", true)
    fun setServiceEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("service_enabled", enabled).apply()

    /** All notified keys, grouped by type. Persisted so a restart never re-pushes. */
    fun notifiedKeys(): MutableMap<String, MutableSet<String>> {
        synchronized(lock) {
            if (!notifiedFile.exists()) return mutableMapOf()
            return try {
                val root = LENIENT_JSON.parseToJsonElement(notifiedFile.readText()).jsonObject
                val out = mutableMapOf<String, MutableSet<String>>()
                for ((k, v) in root) {
                    out[k] = v.jsonArray.map { it.jsonPrimitive.content }.toMutableSet()
                }
                out
            } catch (_: Exception) {
                mutableMapOf()
            }
        }
    }

    fun saveNotifiedKeys(map: Map<String, Set<String>>) {
        synchronized(lock) {
            try {
                val obj = buildJsonObject {
                    for ((k, v) in map) {
                        put(k, buildJsonArray { for (s in v.sorted()) add(s) })
                    }
                }
                notifiedFile.writeText(obj.toString())
            } catch (_: Exception) {
                // Dedup persistence is best-effort; in-memory state still guards this run.
            }
        }
    }

    /** Seed today's keys without notifying (first-install behaviour). */
    fun seedIfEmpty(type: String, keys: Set<String>) {
        val map = notifiedKeys()
        if (map[type].isNullOrEmpty() && keys.isNotEmpty()) {
            map[type] = keys.toMutableSet()
            saveNotifiedKeys(map)
        }
    }
}
