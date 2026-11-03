package com.kentji.lanclient.notify

import android.content.Context
import com.kentji.lanclient.data.ApiClient
import com.kentji.lanclient.data.Store

/**
 * Polling + dedup logic shared by the foreground service and the WorkManager
 * fallback. Rules (decided with the user):
 * - Alerts (events) are authoritative; legs alone can go stale after cooling.
 * - First sync seeds today's keys without notifying.
 * - Signal + paper pending for the same event merge into one notification.
 * - LLM follow: no second push. LLM skip/halve/double: second push.
 * - LLM backfilled: never push. Preview: never push.
 * - Paper filled: one push per trade key. Pending disappearing with no trade: silent.
 * - Offline / unreachable: system-low only, never a fake signal.
 */
object SignalChecker {
    suspend fun check(context: Context) {
        val store = Store(context)
        val api = ApiClient(store.baseUrl)
        val notified = store.notifiedKeys()

        val signals = api.signals()
        if (signals == null) {
            if (store.notifyEnabled(Notifier.CH_SYSTEM)) {
                Notifier.push(
                    context, Notifier.CH_SYSTEM, 9001,
                    "Server unreachable", "Local server is offline or unreachable. No signal will be shown.",
                    "settings"
                )
            }
            return
        }

        // Seed on first run: remember today's alert keys, notify nothing.
        val alertKeys = signals.alertFired.map { it.eventKey() }.toSet()
        val seenSignal = notified["signal"] ?: mutableSetOf()
        if (seenSignal.isEmpty() && alertKeys.isNotEmpty()) {
            store.seedIfEmpty("signal", alertKeys)
            return
        }

        val pending = if (store.notifyEnabled(Notifier.CH_PAPER)) api.paperPending() else emptyList()
        val pendingByKey = pending.associateBy { it.key() }

        for (leg in signals.alertFired) {
            val key = leg.eventKey()
            if (key in seenSignal) continue
            if (!store.notifyEnabled(Notifier.CH_SIGNAL)) continue
            val pend = pendingByKey.entries.firstOrNull { (_, p) ->
                p.symbol == leg.sym && p.signal_bar_ts == leg.bar_ts
            }?.value
            val text = buildString {
                append("${leg.name} ${leg.period} ${leg.direction}")
                if (leg.tier_name != null) append(" ${leg.tier_name}")
                if (leg.suggested_size != null) append(" ${leg.suggested_size}u")
                if (leg.px != null) append(" @ ${leg.px}")
                if (pend != null) append(" · Paper order pending ${pend.target_u}u")
            }
            Notifier.push(
                context, Notifier.CH_SIGNAL, key.hashCode(),
                "Signal: ${leg.name} ${leg.period}",
                text, "signals"
            )
            seenSignal.add(key)
        }
        notified["signal"] = seenSignal

        // LLM advice: only deviations push, and only when not backfilled.
        if (store.notifyEnabled(Notifier.CH_AI)) {
            val seenAi = notified["ai"] ?: mutableSetOf()
            val advice = api.llmAdvice(50)
            for (ad in advice) {
                if (ad.isBackfilled() || !ad.isDeviation()) continue
                val key = ad.key()
                if (key in seenAi) continue
                Notifier.push(
                    context, Notifier.CH_AI, key.hashCode(),
                    "AI deviation: ${ad.symbol} ${ad.period}",
                    "${ad.action}: ${ad.parsedReason()}", "signals"
                )
                seenAi.add(key)
            }
            notified["ai"] = seenAi
        }

        // Paper fills.
        if (store.notifyEnabled(Notifier.CH_PAPER)) {
            val seenPaper = notified["paper"] ?: mutableSetOf()
            val symbols = pending.map { it.symbol }.toSet() +
                signals.alertFired.map { it.sym }.toSet()
            for (sym in symbols) {
                for (trade in api.paperTrades(sym)) {
                    val key = trade.key()
                    if (key in seenPaper) continue
                    val sideText = if (trade.side == "buy") "Buy" else "Sell"
                    Notifier.push(
                        context, Notifier.CH_PAPER, key.hashCode(),
                        "Paper filled: $sym",
                        "$sideText ${trade.qty} @ ${trade.price}, position ${trade.position_after}, cash ${trade.cash_after}",
                        "paper"
                    )
                    seenPaper.add(key)
                }
            }
            notified["paper"] = seenPaper
        }

        // Watch pool: notify only for currently-fired legs, deduped by event key.
        if (store.notifyEnabled(Notifier.CH_WATCH)) {
            val seenWatch = notified["watch"] ?: mutableSetOf()
            val obs = api.obsSignals()
            for (leg in obs.dailyMr + obs.strategyH) {
                if (!leg.signal) continue
                val key = leg.eventKey()
                if (key in seenWatch) continue
                Notifier.push(
                    context, Notifier.CH_WATCH, key.hashCode(),
                    "Watch: ${leg.name} ${leg.period}",
                    "${leg.direction} ${leg.rule}", "watch"
                )
                seenWatch.add(key)
            }
            notified["watch"] = seenWatch
        }

        store.saveNotifiedKeys(notified)
    }
}
