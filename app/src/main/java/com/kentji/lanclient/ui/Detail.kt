package com.kentji.lanclient.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kentji.lanclient.data.Leg

@Composable
fun SignalDetailScreen(vm: MainViewModel, leg: Leg) {
    val advice = vm.adviceFor(leg)
    val pending = vm.pendingFor(leg)
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("${leg.name.ifEmpty { leg.sym }}  ${leg.period}", style = MaterialTheme.typography.titleLarge)
            Text(
                "${leg.direction} · ${leg.rule} · ${leg.tier_name ?: ""} · ${leg.suggested_size ?: ""}u",
                style = MaterialTheme.typography.bodyLarge
            )
            Text("Bar: ${leg.bar_ts ?: ""} · Price: ${leg.px ?: ""}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
        }
        item {
            Text("Paper order", style = MaterialTheme.typography.titleMedium)
            if (pending != null) {
                Text("${pending.direction} ${pending.target_u}u · ${pending.created_at}")
            } else {
                Text("No pending order for this event.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Text("AI advice", style = MaterialTheme.typography.titleMedium)
            if (advice != null) {
                Text("Action: ${advice.action}${if (advice.isBackfilled()) " · 事后补做" else ""}")
                Text(advice.parsedReason())
                advice.parsedConfidence()?.let { Text("Confidence: $it") }
                Text("Created: ${advice.created_at}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("No AI advice yet for this event.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ChartDetailScreen(vm: MainViewModel, code: String, name: String) {
    val chart by vm.chart.collectAsState()
    var period by remember { mutableStateOf("60m") }
    LaunchedEffect(code, period) { vm.loadChart(code, period) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("$name ($code)", style = MaterialTheme.typography.titleLarge)
        Row {
            for (p in listOf("15m", "30m", "60m", "1d", "1m", "5m")) {
                Button(
                    onClick = { period = p },
                    modifier = Modifier.padding(end = 4.dp)
                ) { Text(p) }
            }
        }
        Spacer(Modifier.height(8.dp))
        val data = chart
        if (data == null) {
            Text("Loading chart...")
        } else if (data.candles.isEmpty()) {
            Text("No chart data for $code $period.")
        } else {
            if (!data.fresh) {
                Text("Data is not fresh (last: ${data.lastTs ?: ""}).", color = MaterialTheme.colorScheme.error)
            }
            ChartView(data)
            Text(
                "Drag to pan, pinch to zoom, tap for crosshair, double-tap to reset. " +
                    "Triangles mark S1 buy/sell points.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PaperDetailScreen(vm: MainViewModel, symbol: String) {
    val trades by vm.trades.collectAsState()
    val equity by vm.equity.collectAsState()
    LaunchedEffect(symbol) { vm.loadTrades(symbol) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(symbol, style = MaterialTheme.typography.titleLarge)
            if (equity.size >= 2) {
                EquityLine(equity.map { it.date to it.equity })
            }
            Spacer(Modifier.height(12.dp))
            Text("Trades", style = MaterialTheme.typography.titleMedium)
        }
        items(trades, key = { it.key() + it.fill_ts }) { t ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${t.side} ${t.qty} @ ${t.price}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Bar ${t.bar_ts} · Fill ${t.fill_ts} · Position ${t.position_after} · Cash ${t.cash_after}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
