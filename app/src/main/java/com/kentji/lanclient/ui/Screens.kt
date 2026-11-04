package com.kentji.lanclient.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kentji.lanclient.data.Leg
import com.kentji.lanclient.notify.Notifier

@Composable
fun LegRow(leg: Leg, extra: String = "", onClick: (() -> Unit)? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                buildAnnotatedString {
                    append("${leg.name.ifEmpty { leg.sym }}  ${leg.displayPeriod()}  ")
                    val dirColor = when (leg.direction) {
                        "加仓" -> UpColor
                        "减仓" -> DownColor
                        else -> null
                    }
                    if (dirColor != null) {
                        withStyle(SpanStyle(color = dirColor)) { append(leg.direction) }
                    } else {
                        append(leg.direction)
                    }
                },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                buildString {
                    append(leg.rule)
                    if (leg.tier_name != null) append(" · ${leg.tier_name}")
                    if (leg.suggested_size != null) append(" · ${leg.suggested_size}u")
                    if (leg.px != null) append(" · ${leg.px}")
                    if (leg.bar_ts != null) append(" · ${leg.bar_ts}")
                    if (extra.isNotEmpty()) append(" · $extra")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SignalsScreen(vm: MainViewModel, onLeg: (Leg) -> Unit) {
    val signals by vm.signals.collectAsState()
    val advice by vm.advice.collectAsState()
    val offline by vm.offline.collectAsState()
    var onlyDeviation by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        if (offline) {
            item {
                Text(
                    "服务器离线：显示上次已知数据，信号不按实时展示。",
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("今日已触发信号", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                FilterChip(
                    selected = onlyDeviation,
                    onClick = { onlyDeviation = !onlyDeviation },
                    label = { Text("仅看 AI 偏离") }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        val fired = vm.alertLegs()
        val shown = if (onlyDeviation) {
            fired.filter { leg ->
                val ad = advice.firstOrNull {
                    it.symbol == leg.sym && it.bar_ts == leg.bar_ts && it.rule == leg.rule
                }
                ad?.isDeviation() == true
            }
        } else fired
        if (shown.isEmpty()) {
            item { Text("今日暂无已触发信号。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(shown, key = { it.eventKey() }) { leg ->
            val ad = vm.adviceFor(leg)
            val extra = when {
                ad == null -> ""
                ad.isBackfilled() -> "AI ${ad.displayAction()} · 事后补做"
                ad.isDeviation() -> "AI ${ad.displayAction()} · 偏离"
                else -> "AI ${ad.displayAction()}"
            }
            LegRow(leg, extra) { onLeg(leg) }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Text("接近触发（尚未触发）", style = MaterialTheme.typography.titleLarge)
            Text(
                "接近触发的意思是：如果当前 K 线按现价收盘，就会触发。这不是信号。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
        items(vm.previewItems(), key = { "${it.sym}${it.period}${it.direction}" }) { p ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${p.name.ifEmpty { p.sym }}  ${com.kentji.lanclient.data.periodLabel(p.period)}  ${p.direction}", style = MaterialTheme.typography.titleMedium)
                    Text(p.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Text("当前信号腿", style = MaterialTheme.typography.titleLarge)
        }
        items(signals?.legs.orEmpty(), key = { it.eventKey() }) { leg ->
            LegRow(leg, if (leg.signal) "已触发" else if (leg.cooled) "冷却中" else "") { onLeg(leg) }
        }
    }
}

@Composable
fun MarketScreen(vm: MainViewModel, onStock: (String, String) -> Unit) {
    val stocks by vm.stocks.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        if (stocks.isEmpty()) {
            item { Text("暂无数据。请到设置页刷新，或检查服务器地址。") }
        }
        items(stocks, key = { it.code }) { s ->
            val up = (s.chg_pct ?: 0.0) >= 0
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onStock(s.code, s.name) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${s.name} (${s.code})", style = MaterialTheme.typography.titleMedium)
                        Text(s.quote_date ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            s.last_px?.toString() ?: "-",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            if (s.chg_pct != null) "${if (up) "▲" else "▼"} ${s.chg_pct}%" else "-",
                            color = if (up) UpColor else DownColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PaperScreen(vm: MainViewModel, onSymbol: (String) -> Unit) {
    val paper by vm.paper.collectAsState()
    val pending by vm.pending.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("虚拟账户", style = MaterialTheme.typography.titleLarge) }
        items(paper, key = { it.symbol }) { a ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSymbol(a.symbol) }) {
                Column(Modifier.padding(12.dp)) {
                    Text("${a.name} (${a.symbol})", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "持仓 ${a.qty} · 现金 ${a.cash} · 总资产 ${a.total_equity} · 收益 ${a.return_pct}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (a.valuation_stale) Text("估值已过期", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Text("挂单中", style = MaterialTheme.typography.titleLarge)
        }
        if (pending.isEmpty()) {
            item { Text("暂无挂单。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(pending, key = { it.key() }) { p ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${p.symbol}  ${p.direction}  ${p.target_u}u", style = MaterialTheme.typography.titleMedium)
                    Text("${p.rule} · ${p.signal_bar_ts} · ${p.created_at}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WatchScreen(vm: MainViewModel) {
    val obs by vm.obs.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("日线均值回归", style = MaterialTheme.typography.titleLarge) }
        items(obs.dailyMr, key = { it.eventKey() }) { LegRow(it, if (it.signal) "已触发" else "") }
        item {
            Spacer(Modifier.height(16.dp))
            Text("策略 H", style = MaterialTheme.typography.titleLarge)
        }
        items(obs.strategyH, key = { it.eventKey() }) { LegRow(it, if (it.signal) "已触发" else "") }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, onTest: suspend () -> Boolean) {
    val baseUrl by vm.baseUrl.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val store = vm.store
    var urlText by remember(baseUrl) { mutableStateOf(baseUrl) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var notifySignal by remember { mutableStateOf(store.notifyEnabled(Notifier.CH_SIGNAL)) }
    var notifyPaper by remember { mutableStateOf(store.notifyEnabled(Notifier.CH_PAPER)) }
    var notifyAi by remember { mutableStateOf(store.notifyEnabled(Notifier.CH_AI)) }
    var notifyWatch by remember { mutableStateOf(store.notifyEnabled(Notifier.CH_WATCH)) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("服务器地址", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = urlText, onValueChange = { urlText = it },
                label = { Text("服务器地址") }, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.setBaseUrl(urlText); vm.refreshAll() }) { Text("保存") }
                Button(onClick = {
                    testResult = "测试中…"
                    vm.setBaseUrl(urlText)
                    scope.launch {
                        testResult = if (onTest()) "连接成功" else "连接失败：服务器无法连接"
                    }
                }) { Text("测试连接") }
            }
            testResult?.let { Text(it) }
            Text(
                "默认：http://10.1.1.7:5888/。服务器地址变了就在这里改。" +
                    "后台检查要准时，需要允许忽略电池优化；如果没给权限，检查会退回 15 分钟一次。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Text("通知", style = MaterialTheme.typography.titleLarge)
            NotificationToggle("S1 信号", notifySignal) { notifySignal = it; store.setNotifyEnabled(Notifier.CH_SIGNAL, it) }
            NotificationToggle("虚拟成交", notifyPaper) { notifyPaper = it; store.setNotifyEnabled(Notifier.CH_PAPER, it) }
            NotificationToggle("AI 偏离建议", notifyAi) { notifyAi = it; store.setNotifyEnabled(Notifier.CH_AI, it) }
            NotificationToggle("观察池", notifyWatch) { notifyWatch = it; store.setNotifyEnabled(Notifier.CH_WATCH, it) }
        }
        item {
            Text("主题", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.setThemeMode("system") }) { Text("跟随系统") }
                Button(onClick = { vm.setThemeMode("light") }) { Text("浅色") }
                Button(onClick = { vm.setThemeMode("dark") }) { Text("深色") }
            }
            Text(
                "当前：" + when (themeMode) {
                    "light" -> "浅色"
                    "dark" -> "深色"
                    else -> "跟随系统"
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun NotificationToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
