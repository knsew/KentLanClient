package com.kentji.lanclient.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kentji.lanclient.data.ApiClient
import com.kentji.lanclient.data.ChartData
import com.kentji.lanclient.data.EquityPoint
import com.kentji.lanclient.data.Leg
import com.kentji.lanclient.data.LlmAdvice
import com.kentji.lanclient.data.ObsResult
import com.kentji.lanclient.data.PaperAccount
import com.kentji.lanclient.data.PaperPending
import com.kentji.lanclient.data.PaperTrade
import com.kentji.lanclient.data.PreviewItem
import com.kentji.lanclient.data.SignalsResult
import com.kentji.lanclient.data.StockItem
import com.kentji.lanclient.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    private var api = ApiClient(store.baseUrl)

    private val _baseUrl = MutableStateFlow(store.baseUrl)
    val baseUrl: StateFlow<String> = _baseUrl
    private val _themeMode = MutableStateFlow(store.themeMode)
    val themeMode: StateFlow<String> = _themeMode
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline
    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version

    private val _stocks = MutableStateFlow<List<StockItem>>(emptyList())
    val stocks: StateFlow<List<StockItem>> = _stocks
    private val _signals = MutableStateFlow<SignalsResult?>(null)
    val signals: StateFlow<SignalsResult?> = _signals
    private val _obs = MutableStateFlow(ObsResult(emptyList(), emptyList()))
    val obs: StateFlow<ObsResult> = _obs
    private val _paper = MutableStateFlow<List<PaperAccount>>(emptyList())
    val paper: StateFlow<List<PaperAccount>> = _paper
    private val _pending = MutableStateFlow<List<PaperPending>>(emptyList())
    val pending: StateFlow<List<PaperPending>> = _pending
    private val _advice = MutableStateFlow<List<LlmAdvice>>(emptyList())
    val advice: StateFlow<List<LlmAdvice>> = _advice
    private val _trades = MutableStateFlow<List<PaperTrade>>(emptyList())
    val trades: StateFlow<List<PaperTrade>> = _trades
    private val _equity = MutableStateFlow<List<EquityPoint>>(emptyList())
    val equity: StateFlow<List<EquityPoint>> = _equity
    private val _chart = MutableStateFlow<ChartData?>(null)
    val chart: StateFlow<ChartData?> = _chart

    fun setBaseUrl(url: String) {
        store.baseUrl = url
        _baseUrl.value = url
        api = ApiClient(url)
    }

    fun setThemeMode(mode: String) {
        store.themeMode = mode
        _themeMode.value = mode
    }

    fun refreshAll() {
        viewModelScope.launch {
            _version.value = api.version()
            _stocks.value = api.stocks()
            val sig = api.signals()
            _signals.value = sig
            _offline.value = sig == null
            _obs.value = api.obsSignals()
            _paper.value = api.paperSummary()
            _pending.value = api.paperPending()
            _advice.value = api.llmAdvice(100)
        }
    }

    fun loadTrades(symbol: String) {
        viewModelScope.launch {
            _trades.value = api.paperTrades(symbol)
            _equity.value = api.paperEquity(symbol)
        }
    }

    fun loadChart(code: String, period: String) {
        viewModelScope.launch {
            _chart.value = null
            _chart.value = api.chart(code, period, 300)
        }
    }

    fun adviceFor(leg: Leg): LlmAdvice? = _advice.value.firstOrNull {
        it.symbol == leg.sym && it.bar_ts == leg.bar_ts && it.rule == leg.rule
    }

    fun pendingFor(leg: Leg): PaperPending? = _pending.value.firstOrNull {
        it.symbol == leg.sym && it.signal_bar_ts == leg.bar_ts
    }

    fun previewItems(): List<PreviewItem> = _signals.value?.preview.orEmpty()
    fun alertLegs(): List<Leg> = _signals.value?.alertFired.orEmpty()
}
