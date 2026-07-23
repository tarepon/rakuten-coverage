package com.example.rakutencoverage

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.rakutencoverage.audio.BgmPlayer
import com.example.rakutencoverage.data.SettingsStore
import com.example.rakutencoverage.ui.checkin.CheckInInputScreen
import com.example.rakutencoverage.ui.checkin.CheckInScreen
import com.example.rakutencoverage.ui.collection.CollectionScreen
import com.example.rakutencoverage.ui.settings.SettingsScreen
import com.example.rakutencoverage.ui.history.HistoryScreen
import com.example.rakutencoverage.ui.map.MapScreen
import com.example.rakutencoverage.ui.map.MapViewModel
import com.example.rakutencoverage.ui.title.TitleFlow
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
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true && pendingAutoMeasure) {
            pendingAutoMeasure = false
            autoMeasure.value = true
        }
    }

    private val autoMeasure = mutableStateOf(false)
    private var pendingAutoMeasure = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BgmPlayer.init(this)
        if (SettingsStore.keepScreenOn(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val hasLocationPermission = hasLocationPermission()
        pendingAutoMeasure = intent.getBooleanExtra(EXTRA_AUTO_MEASURE, false)
        if (!hasLocationPermission) {
            showLocationDisclosure()
        } else if (pendingAutoMeasure) {
            pendingAutoMeasure = false
            autoMeasure.value = true
        }
        setContent { RakutenCoverageTheme { RakutenCoverageApp(autoMeasure) } }
    }

    override fun onStart() {
        super.onStart()
        BgmPlayer.onForeground(this)
    }

    override fun onStop() {
        super.onStop()
        // バックグラウンドではBGMを止める(計測サービスは影響なし)
        BgmPlayer.onBackground()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Play の位置情報ポリシーに沿い、OS 権限ダイアログの直前に用途を明示する。 */
    private fun showLocationDisclosure() {
        android.app.AlertDialog.Builder(this)
            .setTitle("位置情報を使用します")
            .setMessage(
                "電波の計測地点を地図に記録するため、正確な位置情報を使用します。" +
                    "計測開始後は、アプリを閉じている間も通知を表示して計測を続け、" +
                    "停止ボタンを押すと取得を終了します。記録は端末内に保存されます。"
            )
            .setPositiveButton("権限を確認") { _, _ -> permissionLauncher.launch(requiredPermissions) }
            .setNegativeButton("今はしない", null)
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // referrer が自アプリ（ウィジェット PendingIntent）の場合のみ AUTO_MEASURE を処理する
        val fromSelf = referrer?.host == packageName
        if (fromSelf && intent.getBooleanExtra(EXTRA_AUTO_MEASURE, false)) {
            if (hasLocationPermission()) {
                autoMeasure.value = true
            } else {
                pendingAutoMeasure = true
                showLocationDisclosure()
            }
        }
    }
}

private data class NavItem(val route: String, val label: String, val icon: String)

/** ナビ項目のアイコン。showBadge=true の間はBadgedBox+Badge(小ドット)で包んでチェックイン中を示す */
@Composable
private fun NavIcon(icon: String, fontSize: androidx.compose.ui.unit.TextUnit, showBadge: Boolean) {
    if (showBadge) {
        BadgedBox(badge = { Badge() }) {
            Text(icon, fontSize = fontSize)
        }
    } else {
        Text(icon, fontSize = fontSize)
    }
}

private val navItems = listOf(
    NavItem("map",        "マップ",       "🗺️"),
    NavItem("checkin",    "チェックイン", "🎫"),
    NavItem("history",    "履歴",         "📋"),
    NavItem("collection", "図鑑",         "📖"),
    NavItem("settings",   "設定",         "⚙️"),
)

@Composable
fun RakutenCoverageApp(autoMeasure: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(false)) {
    val navController = rememberNavController()
    val mapViewModel: MapViewModel = viewModel()

    // タイトル(オープニング演出)を通過済みか。回転では再表示しない
    var entered by rememberSaveable { mutableStateOf(false) }

    // ウィジェットの「起動して計測」から起動された場合、タイトルを飛ばしてマップに遷移し計測開始
    LaunchedEffect(Unit) {
        snapshotFlow { autoMeasure.value }
            .filter { it }
            .collect {
                entered = true
                // タイトル表示中はNavHost未構成(graph未設定)。startDestinationがmapなので遷移不要
                if (navController.currentDestination != null) {
                    navController.navigate("map") {
                        popUpTo(navController.graph.startDestinationId) { saveState = false }
                        launchSingleTop = true
                    }
                }
                mapViewModel.startMeasurementIfStopped()
                autoMeasure.value = false
            }
    }

    if (!entered) {
        TitleFlow(onEnter = { entered = true })
        return
    }

    // 本体BGM: 通常はマップ曲、捕獲ミニゲーム中は戦闘曲に切り替え
    val bgmContext = androidx.compose.ui.platform.LocalContext.current
    val captureUi by mapViewModel.capture.collectAsState()
    LaunchedEffect(captureUi != null) {
        BgmPlayer.request(
            bgmContext,
            if (captureUi != null) BgmPlayer.Track.BATTLE else BgmPlayer.Track.MAP
        )
    }

    // 起動時注釈: 計測対象は楽天回線のみ(DUAL SIM対応)。「次回から表示しない」で恒久スキップ可
    val noticeContext = androidx.compose.ui.platform.LocalContext.current
    var showRakutenNotice by remember {
        mutableStateOf(!SettingsStore.rakutenOnlyNoticeDismissed(noticeContext))
    }
    if (showRakutenNotice) {
        AlertDialog(
            onDismissRequest = { showRakutenNotice = false },
            icon = { Text("📡", style = MaterialTheme.typography.headlineMedium) },
            title = { Text("計測は楽天回線のみ") },
            text = {
                Text(
                    "このアプリは楽天モバイル回線(auパートナーローミング含む)だけを計測・記録します。\n\n" +
                        "・DUAL SIM端末では他社SIM(ドコモ等)の電波は記録されません\n" +
                        "・楽天SIMが入っていない場合や楽天回線が掴めない場所では「圏外」として記録されます"
                )
            },
            confirmButton = {
                TextButton(onClick = { showRakutenNotice = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    SettingsStore.setRakutenOnlyNoticeDismissed(noticeContext, true)
                    showRakutenNotice = false
                }) { Text("次回から表示しない") }
            }
        )
    }

    val backstackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backstackEntry?.destination?.route
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activeCheckIn by mapViewModel.checkIn.collectAsState()

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
                    val showBadge = item.route == "checkin" && activeCheckIn != null
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = { navigate(item.route) },
                        icon = {
                            if (isLandscape) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    NavIcon(item.icon, 22.sp, showBadge)
                                    Text(item.label, fontSize = 11.sp)
                                }
                            } else {
                                NavIcon(item.icon, 20.sp, showBadge)
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
            composable("map") {
                MapScreen(
                    mapViewModel,
                    onNavigateToCheckIn = { navController.navigate("checkin") }
                )
            }
            composable("history")    { HistoryScreen(mapViewModel) }
            composable("settings")   { SettingsScreen(mapViewModel) }
            composable("collection") { CollectionScreen() }
            // チェックインはボトムナビのトップレベル画面(記録/スタンプの2タブ)
            composable("checkin") {
                CheckInScreen(
                    mapViewModel = mapViewModel,
                    onNewCheckIn = { spotId, seatLabel ->
                        val spotParam = spotId?.let { "spotId=${Uri.encode(it)}" } ?: ""
                        val seatParam = seatLabel?.let { "seatLabel=${Uri.encode(it)}" } ?: ""
                        val query = listOf(spotParam, seatParam).filter { it.isNotEmpty() }.joinToString("&")
                        navController.navigate(if (query.isEmpty()) "checkin_input" else "checkin_input?$query")
                    }
                )
            }
            composable(
                route = "checkin_input?spotId={spotId}&seatLabel={seatLabel}",
                arguments = listOf(
                    navArgument("spotId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("seatLabel") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val spotId = backStackEntry.arguments?.getString("spotId").orEmpty()
                val seatLabel = backStackEntry.arguments?.getString("seatLabel").orEmpty()
                CheckInInputScreen(
                    mapViewModel = mapViewModel,
                    initialSpotId = spotId.ifBlank { null },
                    initialSeatLabel = seatLabel,
                    onBack = { navController.popBackStack() },
                    onCheckedIn = { navController.popBackStack() }
                )
            }
        }
    }
}
