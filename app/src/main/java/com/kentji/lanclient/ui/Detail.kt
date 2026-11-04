package com.kentji.lanclient.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
            Text("${leg.name.ifEmpty { leg.sym }}  ${leg.displayPeriod()}", style = MaterialTheme.typography.titleLarge)
            Text(
                "${leg.direction} · ${leg.rule} · ${leg.tier_name ?: ""} · ${leg.suggested_size ?: ""}u",
                style = MaterialTheme.typography.bodyLarge
            )
            Text("K线：${leg.bar_ts ?: ""} · 价格：${leg.px ?: ""}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
        }
        item {
            Text("虚拟挂单", style = MaterialTheme.typography.titleMedium)
            if (pending != null) {
                Text("${pending.direction} ${pending.target_u}u · ${pending.created_at}")
            } else {
                Text("该事件暂无虚拟挂单。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Text("AI 建议", style = MaterialTheme.typography.titleMedium)
            if (advice != null) {
                Text("建议：${advice.displayAction()}${if (advice.isBackfilled()) " · 事后补做" else ""}")
                Text(advice.parsedReason())
                advice.parsedConfidence()?.let { Text("置信度：$it") }
                Text("生成时间：${advice.created_at}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("该事件暂无 AI 建议。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ChartDetailScreen(vm: MainViewModel, code: String, name: String) {
    val chart by vm.chart.collectAsState()
    val chartError by vm.chartError.collectAsState()
    var period by remember { mutableStateOf("60m") }
    LaunchedEffect(code, period) { vm.loadChart(code, period) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("$name ($code)", style = MaterialTheme.typography.titleLarge)
        val periods = listOf(
            "1m" to "1分钟", "5m" to "5分钟", "15m" to "15分钟",
            "30m" to "30分钟", "60m" to "60分钟", "1d" to "日线"
        )
        // The selected period is scrolled into view, so the default 60分钟
        // is never cut off at the right edge on first open.
        val periodState = rememberLazyListState()
        LaunchedEffect(period) {
            val idx = periods.indexOfFirst { it.first == period }
            if (idx >= 0) periodState.scrollToItem(idx)
        }
        LazyRow(state = periodState) {
            items(periods) { (p, label) ->
                val selected = period == p
                Button(
                    onClick = { period = p },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        val data = chart
        if (chartError) {
            Text("图表加载失败，请检查服务器地址后重试。", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.loadChart(code, period) }) { Text("重新加载") }
        } else if (data == null) {
            Text("正在加载图表…")
        } else if (data.candles.isEmpty()) {
            Text("$code ${com.kentji.lanclient.data.periodLabel(period)} 暂无图表数据。")
        } else {
            if (!data.fresh) {
                Text("数据不是最新的（最后：${data.lastTs ?: ""}）。", color = MaterialTheme.colorScheme.error)
            }
            ChartView(data)
            Text(
                "左右拖动平移，双指缩放，点击 K 线看开高低收；三角为 S1 买卖点。",
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
            Text("成交记录", style = MaterialTheme.typography.titleMedium)
        }
        items(trades, key = { it.key() + it.fill_ts }) { t ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${t.side} ${t.qty} @ ${t.price}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "K线 ${t.bar_ts} · 成交 ${t.fill_ts} · 成交后仓位 ${t.position_after} · 成交后现金 ${t.cash_after}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
