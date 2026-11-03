package com.kentji.lanclient

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kentji.lanclient.data.Leg
import com.kentji.lanclient.data.LENIENT_JSON
import com.kentji.lanclient.notify.Notifier
import com.kentji.lanclient.notify.SignalService
import com.kentji.lanclient.ui.ChartDetailScreen
import com.kentji.lanclient.ui.LanClientTheme
import com.kentji.lanclient.ui.MainViewModel
import com.kentji.lanclient.ui.MarketScreen
import com.kentji.lanclient.ui.PaperDetailScreen
import com.kentji.lanclient.ui.PaperScreen
import com.kentji.lanclient.ui.SettingsScreen
import com.kentji.lanclient.ui.SignalDetailScreen
import com.kentji.lanclient.ui.SignalsScreen
import com.kentji.lanclient.ui.WatchScreen
import kotlinx.serialization.encodeToString
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.createChannels(this)
        SignalService.scheduleFallback(this)
        if (vm.store.serviceEnabled()) {
            startForegroundService(Intent(this, SignalService::class.java))
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            val themeMode by vm.themeMode.collectAsState()
            LanClientTheme(themeMode) {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val current = backStack?.destination?.route ?: "signals"
                // Deep link from a notification: route extra.
                LaunchedEffect(Unit) {
                    intent.getStringExtra("route")?.let { route ->
                        if (route in listOf("signals", "market", "paper", "watch", "settings")) {
                            nav.navigate(route)
                        }
                    }
                    vm.refreshAll()
                }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            for ((route, label, icon) in listOf(
                                Triple("signals", "Signals", Icons.Filled.Notifications),
                                Triple("market", "Market", Icons.Filled.Search),
                                Triple("paper", "Paper", Icons.Filled.Favorite),
                                Triple("watch", "Watch", Icons.Filled.Home),
                                Triple("settings", "Settings", Icons.Filled.Settings)
                            )) {
                                NavigationBarItem(
                                    selected = current == route,
                                    onClick = { nav.navigate(route) },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                ) { inner ->
                    NavHost(nav, startDestination = "signals", Modifier.padding(inner)) {
                        composable("signals") {
                            SignalsScreen(vm) { leg ->
                                val encoded = URLEncoder.encode(LENIENT_JSON.encodeToString(leg), "UTF-8")
                                nav.navigate("signal_detail/$encoded")
                            }
                        }
                        composable(
                            "signal_detail/{leg}",
                            arguments = listOf(navArgument("leg") { type = NavType.StringType })
                        ) { entry ->
                            val raw = URLDecoder.decode(entry.arguments?.getString("leg") ?: "", "UTF-8")
                            val leg = runCatching { LENIENT_JSON.decodeFromString(Leg.serializer(), raw) }.getOrNull()
                            if (leg != null) SignalDetailScreen(vm, leg)
                        }
                        composable("market") {
                            MarketScreen(vm) { code, _ ->
                                nav.navigate("chart/$code")
                            }
                        }
                        composable(
                            "chart/{code}",
                            arguments = listOf(navArgument("code") { type = NavType.StringType })
                        ) { entry ->
                            val code = entry.arguments?.getString("code") ?: ""
                            val name = vm.stocks.value.firstOrNull { it.code == code }?.name ?: code
                            ChartDetailScreen(vm, code, name)
                        }
                        composable("paper") {
                            PaperScreen(vm) { symbol -> nav.navigate("paper_detail/$symbol") }
                        }
                        composable(
                            "paper_detail/{symbol}",
                            arguments = listOf(navArgument("symbol") { type = NavType.StringType })
                        ) { entry ->
                            PaperDetailScreen(vm, entry.arguments?.getString("symbol") ?: "")
                        }
                        composable("watch") { WatchScreen(vm) }
                        composable("settings") {
                            SettingsScreen(vm) {
                                com.kentji.lanclient.data.ApiClient(vm.store.baseUrl).ping()
                            }
                        }
                    }
                }
            }
        }
    }
}
