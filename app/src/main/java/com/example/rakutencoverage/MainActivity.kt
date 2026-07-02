package com.example.rakutencoverage

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.rakutencoverage.ui.collection.CollectionScreen
import com.example.rakutencoverage.ui.export.ExportScreen
import com.example.rakutencoverage.ui.history.HistoryScreen
import com.example.rakutencoverage.ui.map.MapScreen
import com.example.rakutencoverage.ui.map.MapViewModel
import com.example.rakutencoverage.ui.stamp.StampScreen
import com.example.rakutencoverage.ui.theme.RakutenCoverageTheme
import com.example.rakutencoverage.widget.EXTRA_AUTO_MEASURE

class MainActivity : ComponentActivity() {

    private val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        // maxSdkVersion=28 のため API 29+ では不要（ダイアログを出さない）
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            add(Manifest.permission.READ_PHONE_STATE)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    private val autoMeasure = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        permissionLauncher.launch(requiredPermissions)
        autoMeasure.value = intent.getBooleanExtra(EXTRA_AUTO_MEASURE, false)
        setContent { RakutenCoverageTheme { RakutenCoverageApp(autoMeasure) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // referrer が自アプリ（ウィジェット PendingIntent）の場合のみ AUTO_MEASURE を処理する
        val fromSelf = referrer?.host == packageName
        if (fromSelf && intent.getBooleanExtra(EXTRA_AUTO_MEASURE, false)) {
            autoMeasure.value = true
        }
    }
}

private data class NavItem(val route: String, val label: String, val icon: String)

private val navItems = listOf(
    NavItem("map",        "マップ",       "🗺️"),
    NavItem("history",    "履歴",         "📋"),
    NavItem("export",     "エクスポート", "📤"),
    NavItem("stamp",      "スタンプ",     "🏅"),
    NavItem("collection", "図鑑",         "📖"),
)

@Composable
fun RakutenCoverageApp(autoMeasure: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(false)) {
    val navController = rememberNavController()
    val backstackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backstackEntry?.destination?.route
    val mapViewModel: MapViewModel = viewModel()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ウィジェットの「起動して計測」から起動された場合、マップに遷移して計測開始
    LaunchedEffect(Unit) {
        snapshotFlow { autoMeasure.value }
            .filter { it }
            .collect {
                navController.navigate("map") {
                    popUpTo(navController.graph.startDestinationId) { saveState = false }
                    launchSingleTop = true
                }
                mapViewModel.startMeasurementIfStopped()
                autoMeasure.value = false
            }
    }

    fun navigate(route: String) = navController.navigate(route) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    // 縦横共通: Scaffold + BottomBar
    // 横画面のレイアウト差異はMapScreen側の左パネルで吸収
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                // 横画面は高さを48dpに圧縮、システムinsetも除去
                modifier = if (isLandscape) Modifier.height(48.dp) else Modifier,
                windowInsets = if (isLandscape) WindowInsets(0) else NavigationBarDefaults.windowInsets
            ) {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = { navigate(item.route) },
                        icon = {
                            if (isLandscape) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(item.icon, fontSize = 22.sp)
                                    Text(item.label, fontSize = 11.sp)
                                }
                            } else {
                                Text(item.icon, fontSize = 20.sp)
                            }
                        },
                        label = if (isLandscape) null else ({ Text(item.label) }),
                        alwaysShowLabel = !isLandscape
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "map",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("map")        { MapScreen(mapViewModel) }
            composable("history")    { HistoryScreen(mapViewModel) }
            composable("export")     { ExportScreen(mapViewModel) }
            composable("stamp")      { StampScreen() }
            composable("collection") { CollectionScreen() }
        }
    }
}
