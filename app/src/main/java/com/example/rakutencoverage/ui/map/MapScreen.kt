package com.example.rakutencoverage.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.os.Build
import android.provider.Settings
import kotlin.math.pow
import kotlin.math.hypot
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.rakutencoverage.data.CollectionRecord
import com.example.rakutencoverage.data.Measurement
import com.example.rakutencoverage.data.monster.Monster
import com.example.rakutencoverage.data.rarityRank
import com.example.rakutencoverage.data.latLngToH3Index
import com.example.rakutencoverage.util.DataExporter
import com.example.rakutencoverage.util.GeoUtils
import kotlinx.coroutines.tasks.await
import com.example.rakutencoverage.data.SignalLevel
import com.example.rakutencoverage.data.SpotType
import com.example.rakutencoverage.ui.character.CharacterState
import com.example.rakutencoverage.ui.theme.GoAccent
import com.example.rakutencoverage.ui.theme.GoBlue
import com.example.rakutencoverage.ui.theme.GoDanger
import com.example.rakutencoverage.ui.theme.GoDarkNavy
import com.example.rakutencoverage.ui.theme.GoWhite
import com.example.rakutencoverage.ui.theme.PanelBackgroundAlpha
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration as OsmConfiguration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Overlay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(vm: MapViewModel = viewModel(), onNavigateToCheckIn: () -> Unit = {}) {
    val measurements by vm.measurements.collectAsState()
    val collectionRecords by vm.collectionRecords.collectAsState()
    val character by vm.character.collectAsState()
    val isRunning by vm.isRunning.collectAsState()
    val last by vm.lastMeasurement.collectAsState()
    val isFollowing by vm.isFollowing.collectAsState()
    val checkIn by vm.checkIn.collectAsState()
    val spotsByType by vm.spotsByType.collectAsState()
    val signalCounts by vm.signalCounts.collectAsState()
    val capture by vm.capture.collectAsState()
    val capturedMonster by vm.capturedMonster.collectAsState()
    val showCoverageArea by vm.showCoverageArea.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    OsmConfiguration.getInstance().userAgentValue = context.packageName
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val fusedLocation = remember { LocationServices.getFusedLocationProviderClient(context) }

    // ────────────────────────────────────────
    // バックグラウンド計測の通知許可(時計横のステータスアイコン表示に必須)。
    // API33+はランタイム許可が必要で、起動時1回のリクエストだけだと拒否・見逃しで
    // 二度と聞かれず「アイコンが出ない」状態になりうるため、計測開始のたびに再チェックする。
    // ────────────────────────────────────────
    var showNotificationRationale by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (!granted) showNotificationRationale = true }

    LaunchedEffect(isRunning) {
        if (isRunning && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // API33未満はランタイム許可の概念がなく、設定アプリでの手動許可のみが道
                showNotificationRationale = true
            }
        }
    }

    if (showNotificationRationale) {
        AlertDialog(
            onDismissRequest = { showNotificationRationale = false },
            icon = { Text("🔕", style = MaterialTheme.typography.headlineMedium) },
            title = { Text("通知が許可されていません") },
            text = {
                Text("計測中は時計横にアイコンで電波状況を表示しますが、通知が無効だと表示されません。設定から通知を許可してください。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationRationale = false
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }) { Text("設定を開く") }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationRationale = false }) { Text("後で") }
            }
        )
    }

    // ────────────────────────────────────────
    // 囲って保存(ラッソエクスポート)の状態
    // ────────────────────────────────────────
    var lassoEnabled by remember { mutableStateOf(false) }
    var lassoPoints by remember { mutableStateOf<List<GeoPoint>?>(null) }
    var lassoMatches by remember { mutableStateOf<List<Measurement>>(emptyList()) }
    var showLassoConfirmDialog by remember { mutableStateOf(false) }
    var lassoFileNameInput by remember { mutableStateOf("") }
    var lassoMessage by remember { mutableStateOf<String?>(null) }

    val lassoOverlay = remember { LassoOverlay(onComplete = { points -> lassoPoints = points }) }

    val lassoSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            lassoEnabled = false
        } else {
            scope.launch {
                lassoMessage = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(DataExporter.toGeoJsonString(lassoMatches).toByteArray(Charsets.UTF_8))
                        } ?: error("ファイルを開けませんでした")
                    }
                    "✅ ${lassoMatches.size}件を保存しました"
                }.getOrElse { "❌ 保存に失敗: ${it.message}" }
                lassoEnabled = false
            }
        }
    }

    // enabled のON/OFFに合わせてオーバーレイの内部状態を同期し、OFF時は軌跡を消して再描画する
    LaunchedEffect(lassoEnabled) {
        lassoOverlay.active = lassoEnabled
        if (!lassoEnabled) {
            lassoOverlay.clear()
            mapViewRef.value?.invalidate()
        }
    }

    // ラッソ確定(3点以上でACTION_UP)時: 範囲内件数を数え、0件ならメッセージのみ、1件以上なら確認ダイアログ
    LaunchedEffect(lassoPoints) {
        val points = lassoPoints ?: return@LaunchedEffect
        val matches = measurements.filter { GeoUtils.pointInPolygon(it.latitude, it.longitude, points) }
        lassoPoints = null
        if (matches.isEmpty()) {
            lassoMessage = "範囲内に計測データがありません"
            lassoEnabled = false
        } else {
            lassoMatches = matches
            lassoFileNameInput = "area_export_${lassoFileNameFormatter.format(LocalDateTime.now())}"
            showLassoConfirmDialog = true
        }
    }

    // 一定時間で結果メッセージを自動的に消す(簡易トースト代わり)
    LaunchedEffect(lassoMessage) {
        if (lassoMessage != null) {
            delay(3000L)
            lassoMessage = null
        }
    }

    val lassoStatusText = if (lassoEnabled && !showLassoConfirmDialog) {
        "✂️ 指で囲むと範囲内の計測をエクスポートできます"
    } else {
        lassoMessage
    }

    // 初回表示時のみ最終既知位置へ移動（GPSキャッシュから即取得）。
    // 画面復帰時(savedCameraあり)は factory で前回位置を同期復元済みのため再センタリングしない —
    // 非同期取得後に setCenter すると、戻るたびに地図がスクロールして見えてしまう。
    LaunchedEffect(Unit) {
        if (vm.savedCamera != null) return@LaunchedEffect
        try {
            val loc = fusedLocation.lastLocation.await()
            if (loc != null) {
                mapViewRef.value?.controller?.setCenter(GeoPoint(loc.latitude, loc.longitude))
            }
        } catch (_: Exception) {}
    }

    // 画面を離れるときにカメラ位置を保存し、復帰時に MapView 生成と同時に復元できるようにする
    DisposableEffect(Unit) {
        onDispose {
            mapViewRef.value?.let { map ->
                vm.saveCamera(map.mapCenter.latitude, map.mapCenter.longitude, map.zoomLevelDouble)
                map.onDetach()
            }
            mapViewRef.value = null
        }
    }

    DisposableEffect(isFollowing) {
        if (!isFollowing) return@DisposableEffect onDispose {}
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                mapViewRef.value?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
            }
        }
        fusedLocation.requestLocationUpdates(request, callback, context.mainLooper)
        onDispose { fusedLocation.removeLocationUpdates(callback) }
    }

    if (isLandscape) {
        LandscapeMapLayout(
            vm = vm,
            measurements = measurements,
            collectionRecords = collectionRecords,
            mapViewRef = mapViewRef,
            character = character,
            isRunning = isRunning,
            last = last,
            isFollowing = isFollowing,
            checkIn = checkIn,
            signalCounts = signalCounts,
            capture = capture,
            capturedMonster = capturedMonster,
            showCoverageArea = showCoverageArea,
            onCheckInClick = onNavigateToCheckIn,
            lassoEnabled = lassoEnabled,
            lassoStatusText = lassoStatusText,
            onLassoToggle = { lassoEnabled = !lassoEnabled },
            lassoOverlay = lassoOverlay
        )
    } else {
        PortraitMapLayout(
            vm = vm,
            measurements = measurements,
            collectionRecords = collectionRecords,
            mapViewRef = mapViewRef,
            character = character,
            isRunning = isRunning,
            last = last,
            isFollowing = isFollowing,
            checkIn = checkIn,
            signalCounts = signalCounts,
            capture = capture,
            capturedMonster = capturedMonster,
            showCoverageArea = showCoverageArea,
            onCheckInClick = onNavigateToCheckIn,
            lassoEnabled = lassoEnabled,
            lassoStatusText = lassoStatusText,
            onLassoToggle = { lassoEnabled = !lassoEnabled },
            lassoOverlay = lassoOverlay
        )
    }

    if (showLassoConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLassoConfirmDialog = false; lassoEnabled = false },
            icon = { Text("✂️", style = MaterialTheme.typography.headlineMedium) },
            title = { Text("範囲内の計測をGeoJSONで保存") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("範囲内の計測 ${lassoMatches.size} 件をGeoJSONで保存します。")
                    OutlinedTextField(
                        value = lassoFileNameInput,
                        onValueChange = { lassoFileNameInput = it },
                        label = { Text("ファイル名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "⚠️ 位置情報を含むファイルです。共有先にご注意ください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showLassoConfirmDialog = false
                    val name = lassoFileNameInput.ifBlank { "area_export" }
                    lassoSaveLauncher.launch("$name.geojson")
                }) { Text("保存先を選ぶ") }
            },
            dismissButton = {
                TextButton(onClick = { showLassoConfirmDialog = false; lassoEnabled = false }) { Text("キャンセル") }
            }
        )
    }
}

private val lassoFileNameFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

// ────────────────────────────────────────────
// 縦画面レイアウト
// ────────────────────────────────────────────
@Composable
private fun PortraitMapLayout(
    vm: MapViewModel,
    measurements: List<Measurement>,
    collectionRecords: List<CollectionRecord>,
    mapViewRef: MutableState<MapView?>,
    character: com.example.rakutencoverage.ui.character.CharacterState,
    isRunning: Boolean,
    last: Measurement?,
    isFollowing: Boolean,
    checkIn: com.example.rakutencoverage.ui.map.ArenaModeInput?,
    signalCounts: MapViewModel.SignalCounts,
    capture: MapViewModel.CaptureUi?,
    capturedMonster: Monster?,
    showCoverageArea: Boolean,
    onCheckInClick: () -> Unit,
    lassoEnabled: Boolean,
    lassoStatusText: String?,
    onLassoToggle: () -> Unit,
    lassoOverlay: LassoOverlay
) {
    Box(modifier = Modifier.fillMaxSize()) {
        OsmMapView(measurements, collectionRecords, showCoverageArea, mapViewRef, vm.savedCamera, vm::stopFollowing, lassoOverlay)

        capturedMonster?.let {
            CapturedMonsterCard(
                monster = it,
                onDismiss = { vm.dismissCapturedMonster() },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        TrainerBadge(
            state = character,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp, start = 12.dp)
        )

        if (signalCounts.total > 0) {
            SignalCountPill(
                counts = signalCounts,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
            )
        }

        CheckInBanner(
            checkIn = checkIn,
            topPadding = 84.dp,
            onDismiss = { vm.setCheckIn(null) },
            onClick = onCheckInClick,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(0.92f)
        )

        lassoStatusText?.let {
            LassoStatusBanner(
                text = it,
                topPadding = if (checkIn != null) 150.dp else 84.dp,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(0.92f)
            )
        }

        last?.let {
            StatusPill(
                m = it,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp)
            )
        }

        BottomHud(
            vm = vm,
            isRunning = isRunning,
            isFollowing = isFollowing,
            lassoEnabled = lassoEnabled,
            onLassoToggle = onLassoToggle,
            onFollowClick = {
                if (isFollowing) vm.stopFollowing()
                else { mapViewRef.value?.controller?.setZoom(15.0); vm.startFollowing() }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        )

        // 捕獲ミニゲーム(全画面オーバーレイ、最前面)
        capture?.let { CaptureMinigame(it, vm) }
    }
}

// ────────────────────────────────────────────
// 横画面レイアウト
// ────────────────────────────────────────────
@Composable
private fun LandscapeMapLayout(
    vm: MapViewModel,
    measurements: List<Measurement>,
    collectionRecords: List<CollectionRecord>,
    mapViewRef: MutableState<MapView?>,
    character: com.example.rakutencoverage.ui.character.CharacterState,
    isRunning: Boolean,
    last: Measurement?,
    isFollowing: Boolean,
    checkIn: com.example.rakutencoverage.ui.map.ArenaModeInput?,
    signalCounts: MapViewModel.SignalCounts,
    capture: MapViewModel.CaptureUi?,
    capturedMonster: Monster?,
    showCoverageArea: Boolean,
    onCheckInClick: () -> Unit,
    lassoEnabled: Boolean,
    lassoStatusText: String?,
    onLassoToggle: () -> Unit,
    lassoOverlay: LassoOverlay
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    // 左パネルを画面幅の28%に（最低160dp、最大240dp）
    val leftPanelWidth = (screenWidthDp * 0.28f).coerceIn(160f, 240f).dp

    Box(modifier = Modifier.fillMaxSize()) {
        OsmMapView(measurements, collectionRecords, showCoverageArea, mapViewRef, vm.savedCamera, vm::stopFollowing, lassoOverlay)

        capturedMonster?.let {
            CapturedMonsterCard(
                monster = it,
                onDismiss = { vm.dismissCapturedMonster() },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 左上: トレーナーバッジ
        TrainerBadge(
            state = character,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp)
        )

        // 右上: 集計ピル
        if (signalCounts.total > 0) {
            SignalCountPill(
                counts = signalCounts,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
            )
        }

        lassoStatusText?.let {
            LassoStatusBanner(
                text = it,
                topPadding = 8.dp,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(0.6f)
            )
        }

        // 左パネル: チェックイン情報（バッジの下）
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 68.dp)
                .width(leftPanelWidth),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // チェックイン情報（横画面）。CheckInBanner(縦画面)と同様タップでチェックイン画面へ遷移する
            checkIn?.let { ci ->
                Surface(
                    onClick = onCheckInClick,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🎫 ${ci.spot.type.icon} ${ci.spot.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (ci.spot.type == SpotType.ARENA) {
                                Text("${ci.gamePhase.label} | ${ci.seatLabel.ifBlank { "席未入力" }}", fontSize = 9.sp)
                            }
                        }
                        TextButton(onClick = { vm.setCheckIn(null) }, contentPadding = PaddingValues(4.dp)) {
                            Text("解除", fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // 最新計測ステータス（下部中央。HUDが右端に移動したため中央下に配置）
        last?.let {
            StatusPill(
                m = it,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }

        // 右端HUD（縦一列・カメラアプリ式。ラベル非表示）
        BottomHud(
            vm = vm,
            isRunning = isRunning,
            isFollowing = isFollowing,
            lassoEnabled = lassoEnabled,
            onLassoToggle = onLassoToggle,
            onFollowClick = {
                if (isFollowing) vm.stopFollowing()
                else { mapViewRef.value?.controller?.setZoom(15.0); vm.startFollowing() }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 8.dp),
            vertical = true
        )

        // 捕獲ミニゲーム(全画面オーバーレイ、最前面)
        capture?.let { CaptureMinigame(it, vm) }
    }
}

// ────────────────────────────────────────────
// 共通パーツ
// ────────────────────────────────────────────

@Composable
private fun OsmMapView(
    measurements: List<Measurement>,
    collectionRecords: List<CollectionRecord>,
    showCoverageArea: Boolean,
    mapViewRef: MutableState<MapView?>,
    initialCamera: MapViewModel.CameraState?,
    onUserTouch: () -> Unit,
    lassoOverlay: LassoOverlay
) {
    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                // 画面復帰時は前回のカメラ位置を生成と同時に復元する。
                // 非同期の位置取得を待ってから移動すると、戻るたびに地図がスクロールして見えるため
                if (initialCamera != null) {
                    controller.setZoom(initialCamera.zoom)
                    controller.setCenter(GeoPoint(initialCamera.latitude, initialCamera.longitude))
                } else {
                    controller.setZoom(15.0)
                }
                overlays.add(TouchInterceptOverlay(onUserTouch))
                // 囲って保存(ラッソ)描画。enabled=true の間だけタッチを消費する
                overlays.add(lassoOverlay)
                // OSMタイル利用時のライセンス上必須の帰属表示（© OpenStreetMap contributors）
                overlays.add(CopyrightOverlay(ctx))
            }.also { mapViewRef.value = it }
        },
        update = { mapView ->
            mapView.overlays.removeAll {
                it is MeasurementOverlay || it is CollectionOverlay || it is CoverageAreaOverlay
            }
            // エリア（面）表示はドットより下に敷く
            if (showCoverageArea) {
                mapView.overlays.add(CoverageAreaOverlay(measurements))
            }
            mapView.overlays.add(MeasurementOverlay(measurements))
            mapView.overlays.add(CollectionOverlay(collectionRecords))
            mapView.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}

/** チェックイン中バナー。タップでチェックイン画面(記録タブ)へ遷移する */
@Composable
private fun CheckInBanner(
    checkIn: ArenaModeInput?,
    topPadding: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (checkIn == null) return
    Surface(
        onClick = onClick,
        modifier = modifier.padding(top = topPadding),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("🎫 ${checkIn.spot.type.icon} ${checkIn.spot.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (checkIn.spot.type == SpotType.ARENA) {
                    Text("${checkIn.gamePhase.label}  |  席: ${checkIn.seatLabel.ifBlank { "未入力" }}", style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = onDismiss) { Text("解除") }
        }
    }
}

/** 囲って保存(ラッソ)モードの案内 or 結果メッセージを表示するバナー。CheckInBannerと同スタイル */
@Composable
private fun LassoStatusBanner(
    text: String,
    topPadding: Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(top = topPadding),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

/**
 * 下部HUD: 中央に大きなメインボタン（マッピング開始/停止、実行中はパルス）、
 * 右に現在位置ボタン、左に「⋯」その他設定ボタン（間隔・自動捕獲・エリア表示をシートに集約。
 * チェックインはボトムナビ/バナーに移設したためシートには含まない）、
 * その隣に「✂️」囲って保存トグルボタン。
 * vertical=true の場合は横一列(Row)ではなく縦一列(Column)で並べ、ラベルを非表示にする
 * （横画面・右端縦一列レイアウト用。縦画面では常に vertical=false）。
 */
@Composable
private fun BottomHud(
    vm: MapViewModel,
    isRunning: Boolean,
    isFollowing: Boolean,
    lassoEnabled: Boolean,
    onLassoToggle: () -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false
) {
    val buttons: @Composable () -> Unit = {
        CircleIconButton(
            emoji = "✂️",
            label = "囲んで保存",
            size = 52.dp,
            active = lassoEnabled,
            onClick = onLassoToggle,
            labelAtStart = vertical
        )

        MainMeasureButton(
            isRunning = isRunning,
            onClick = { vm.toggleMeasurement() },
            labelAtStart = vertical,
            boxSize = if (vertical) 80.dp else 88.dp
        )

        CircleIconButton(
            emoji = "🎯",
            label = "現在地",
            size = 52.dp,
            active = isFollowing,
            onClick = onFollowClick,
            labelAtStart = vertical
        )
    }

    if (vertical) {
        // 縦一列時はボタンを右端に揃え、ラベルは各ボタンの左横に出す
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { buttons() }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) { buttons() }
    }

}

/**
 * 中央下の大きな円形メインボタン。マッピング実行中は外周にパルスするリングを表示する。
 * labelAtStart=true でラベルを左横に(横画面の縦一列HUD用)、boxSize で外箱サイズを調整可能（円自体は常に72dp）。
 */
@Composable
private fun MainMeasureButton(
    isRunning: Boolean,
    onClick: () -> Unit,
    labelAtStart: Boolean = false,
    boxSize: Dp = 88.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val circle: @Composable () -> Unit = {
        Box(modifier = Modifier.size(boxSize), contentAlignment = Alignment.Center) {
            if (isRunning) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(GoBlue.copy(alpha = pulseAlpha))
                )
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (isRunning) GoDanger else GoBlue)
                    .border(3.dp, GoWhite.copy(alpha = 0.9f), CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isRunning) "⏹" else "▶", fontSize = 28.sp, color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
    val label = if (isRunning) "計測停止" else "計測開始"

    if (labelAtStart) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HudLabel(label)
            circle()
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            circle()
            HudLabel(label)
        }
    }
}

/**
 * 汎用の円形アイコンボタン。active=true でハイライト表示。
 * labelAtStart=false: ラベルをボタンの下に(縦画面の横一列HUD用)
 * labelAtStart=true : ラベルをボタンの左横に(横画面の縦一列HUD用。列の高さを増やさない)
 */
@Composable
private fun CircleIconButton(
    emoji: String,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    active: Boolean,
    onClick: () -> Unit,
    labelAtStart: Boolean = false
) {
    val circle: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (active) GoBlue else GoDarkNavy.copy(alpha = PanelBackgroundAlpha))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 20.sp)
        }
    }

    if (labelAtStart) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HudLabel(label)
            circle()
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            circle()
            HudLabel(label)
        }
    }
}

/** HUD用の小さな半透明ラベルピル。ボタン等の下に添えて操作内容を示す。 */
@Composable
private fun HudLabel(text: String) {
    Surface(
        color = GoDarkNavy.copy(alpha = PanelBackgroundAlpha),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            fontSize = 10.sp,
            color = GoWhite,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/**
 * トレーナーバッジ: 左上に浮かぶ円形バッジ。現在の SignalLevel の絵文字とリング色を表示する。
 * タップでキャラのメッセージ・回線情報を展開表示（AnimatedVisibility）。
 */
@Composable
private fun TrainerBadge(state: CharacterState, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val ringColor = badgeRingColor(state.level)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(GoDarkNavy.copy(alpha = PanelBackgroundAlpha))
                .border(3.dp, ringColor, CircleShape)
                .clickable { expanded = !expanded },
            contentAlignment = Alignment.Center
        ) {
            Text(state.emoji, fontSize = 26.sp)
        }
        if (!expanded) HudLabel("電波状況")
        AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Surface(
                modifier = Modifier.padding(top = 8.dp).widthIn(max = 240.dp),
                color = GoDarkNavy.copy(alpha = PanelBackgroundAlpha),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.message, color = GoWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    state.level?.let {
                        Text(it.shortLabel(), color = GoWhite.copy(alpha = 0.75f), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun badgeRingColor(level: SignalLevel?): androidx.compose.ui.graphics.Color = when (level) {
    SignalLevel.PLATINUM_5G     -> androidx.compose.ui.graphics.Color(0xFFFFD700)
    SignalLevel.FIVE_G          -> androidx.compose.ui.graphics.Color(0xFF1E88E5)
    SignalLevel.PLATINUM        -> androidx.compose.ui.graphics.Color(0xFFAB47BC)
    SignalLevel.LTE             -> androidx.compose.ui.graphics.Color(0xFF43A047)
    SignalLevel.WEAK            -> androidx.compose.ui.graphics.Color(0xFF5D4037)
    SignalLevel.NO_SIGNAL       -> androidx.compose.ui.graphics.Color(0xFF212121)
    SignalLevel.AIRPLANE_MODE   -> androidx.compose.ui.graphics.Color(0xFF1565C0)
    SignalLevel.NO_SIM          -> androidx.compose.ui.graphics.Color(0xFFB71C1C)
    null                        -> androidx.compose.ui.graphics.Color(0xFF616161)
}

/**
 * 右上に浮かぶピル型カウンター。主要3種のみ常時表示し、タップで全カテゴリのボトムシートを開く。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignalCountPill(counts: MapViewModel.SignalCounts, modifier: Modifier = Modifier) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Surface(
        modifier = modifier.clickable { showSheet = true },
        color = GoDarkNavy.copy(alpha = PanelBackgroundAlpha),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚡${counts.fiveG}", color = GoWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("📶${counts.lte}", color = GoWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            SignalCountDetailSheet(counts)
        }
    }
}

@Composable
private fun SignalCountDetailSheet(counts: MapViewModel.SignalCounts) {
    val items = listOf(
        Triple("PtLTE", counts.platinum,  androidx.compose.ui.graphics.Color(0xFFAB47BC)),
        Triple("5G",    counts.fiveG,     androidx.compose.ui.graphics.Color(0xFF1E88E5)),
        Triple("LTE",   counts.lte,       androidx.compose.ui.graphics.Color(0xFF43A047)),
        Triple("Roam",  counts.kddi,      androidx.compose.ui.graphics.Color(0xFFFF7043)),
        Triple("圏外",   counts.noSignal,  androidx.compose.ui.graphics.Color(0xFF757575)),
    )
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            "📊 エリア計測の累計",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        items.chunked(3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                row.forEach { (label, count, color) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(count.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
                        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            "合計 ${counts.total} 件",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.End).padding(top = 4.dp, bottom = 24.dp)
        )
    }
}

/**
 * 捕獲成功カード。中央にポップアップし、GET!テキストがバウンスしながら登場、
 * レア度の星を1個ずつ遅延表示する演出付き。
 */
@Composable
private fun CapturedMonsterCard(
    monster: Monster,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rarityColor = badgeRingColor(monster.signalLevel)
    val totalStars = monster.signalLevel.starCount()
    var starsShown by remember(monster) { mutableStateOf(0) }
    val getScale = remember(monster) { Animatable(0f) }

    LaunchedEffect(monster) {
        getScale.animateTo(1f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
        starsShown = 0
        repeat(totalStars) {
            delay(160)
            starsShown++
        }
    }

    Box(
        modifier = modifier
            .size(240.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(rarityColor.copy(alpha = 0.45f), androidx.compose.ui.graphics.Color.Transparent)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onDismiss,
            color = GoDarkNavy.copy(alpha = PanelBackgroundAlpha),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "⭐ GET!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoAccent,
                    modifier = Modifier.scale(getScale.value)
                )
                Text(monster.emoji, fontSize = 64.sp)
                Text(monster.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoWhite)
                Text(
                    "★".repeat(starsShown) + "☆".repeat(totalStars - starsShown),
                    fontSize = 14.sp,
                    color = GoAccent
                )
                Text(
                    "HP ${monster.hp}  ATK ${monster.attack}  DEF ${monster.defense}",
                    fontSize = 11.sp,
                    color = GoWhite.copy(alpha = 0.75f)
                )
                if (monster.moves.isNotEmpty()) {
                    Text(
                        monster.moves.joinToString(" / "),
                        fontSize = 11.sp,
                        color = GoWhite.copy(alpha = 0.75f)
                    )
                }
                Text("タップで閉じる", fontSize = 10.sp, color = GoWhite.copy(alpha = 0.5f))
            }
        }
    }
}

/**
 * 捕獲ミニゲーム(denpamon-go 移植)。全画面オーバーレイ。
 * 金色リングが縮んでいき、点線ターゲット(scale 0.5)に重なった瞬間に
 * 投げるほど捕獲率ボーナスが大きい。
 */
@Composable
private fun CaptureMinigame(e: MapViewModel.CaptureUi, vm: MapViewModel) {
    val rarityColor = badgeRingColor(e.monster.signalLevel)

    // リングアニメーション: scale 1.0 → 0.22 を1.4秒ループ
    val transition = rememberInfiniteTransition(label = "captureRing")
    val ringScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringScale"
    )

    // モンスターの上下ゆらゆら
    val bob by transition.animateFloat(
        initialValue = 0f, targetValue = -14f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(GoDarkNavy.copy(alpha = 0.97f), androidx.compose.ui.graphics.Color(0xFF101522))
                )
            )
            // 背後のマップへのタッチを遮断
            .clickable(enabled = false) {}
    ) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Text(
                if (e.phase == MapViewModel.CapturePhase.CAUGHT) "つかまえた!"
                else "やせいの ${e.monster.name}が あらわれた!",
                color = GoWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Lv.${e.monster.level} / ${e.monster.signalLevel.shortLabel()} / ★${e.monster.signalLevel.starCount()}",
                color = GoWhite.copy(alpha = 0.6f),
                fontSize = 12.sp
            )

            // ステージ
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // 点線ターゲットリング(固定 scale 0.5)
                Box(
                    Modifier
                        .size(220.dp)
                        .scale(0.5f)
                        .border(2.dp, GoWhite.copy(alpha = 0.35f), CircleShape)
                )
                // 収縮リング
                if (e.phase == MapViewModel.CapturePhase.READY || e.phase == MapViewModel.CapturePhase.MISS) {
                    Box(
                        Modifier
                            .size(220.dp)
                            .scale(ringScale)
                            .border(4.dp, GoAccent, CircleShape)
                    )
                }
                // モンスター
                if (e.phase != MapViewModel.CapturePhase.CAUGHT && e.phase != MapViewModel.CapturePhase.FLED) {
                    Text(
                        e.monster.emoji,
                        fontSize = 88.sp,
                        modifier = Modifier.offset(y = bob.dp)
                    )
                } else if (e.phase == MapViewModel.CapturePhase.CAUGHT) {
                    Text("⚪", fontSize = 72.sp)
                }
                // 結果メッセージ
                e.message?.let {
                    Text(
                        it,
                        color = rarityColor.takeIf { e.phase == MapViewModel.CapturePhase.CAUGHT } ?: GoWhite,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    )
                }
            }

            // 操作ボタン
            when (e.phase) {
                MapViewModel.CapturePhase.CAUGHT, MapViewModel.CapturePhase.FLED -> {
                    Button(
                        onClick = { vm.dismissCapture() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("とじる", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                }
                else -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { vm.dismissCapture() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            enabled = e.phase != MapViewModel.CapturePhase.THROWING
                        ) { Text("にげる", color = GoWhite) }
                        Button(
                            onClick = {
                                // タイミング判定: 点線リング(0.5)に近いほど 1.0
                                val accuracy = (1f - kotlin.math.abs(ringScale - 0.5f) / 0.5f).coerceIn(0f, 1f)
                                vm.throwBall(accuracy)
                            },
                            modifier = Modifier.weight(2f).height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            enabled = e.phase == MapViewModel.CapturePhase.READY
                        ) {
                            Text("⚪ 投げる!", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun SignalLevel.starCount(): Int = when (this) {
    SignalLevel.PLATINUM_5G     -> 4
    SignalLevel.FIVE_G          -> 3
    SignalLevel.PLATINUM        -> 3
    SignalLevel.LTE             -> 1
    SignalLevel.WEAK            -> 2
    SignalLevel.NO_SIGNAL       -> 3
    else                        -> 1
}

/**
 * 最新計測ステータスの細いピル表示。1行のみ常時表示し、タップで詳細ボトムシートを開く。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusPill(
    m: Measurement,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.clickable { showSheet = true },
        color = GoDarkNavy.copy(alpha = PanelBackgroundAlpha),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            "📡 ${m.networkType}${m.band?.let { " • $it" } ?: ""} • ${m.rssi?.let { "${it}dBm" } ?: "RTT ${m.rttMs}ms"}",
            fontSize = 12.sp,
            color = GoWhite,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("📡 計測ステータス詳細", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (m.isArenaMode) {
                    Text(
                        "🎫 ${m.arenaName}" +
                            (if (m.gamePhase != null) "  |  ${m.gamePhase}" else "") +
                            (if (!m.seatLabel.isNullOrBlank()) "  |  席: ${m.seatLabel}" else ""),
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Text("${m.networkType}${m.band?.let { "  |  $it" } ?: ""}  |  RTT: ${m.rttMs}ms",
                    fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text("キャリア: ${m.carrier ?: "不明"}  |  RSSI: ${m.rssi?.let { "${it}dBm" } ?: "不明"}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun SignalLevel.shortLabel() = when (this) {
    SignalLevel.PLATINUM_5G   -> "🏆 プラチナ5G"
    SignalLevel.FIVE_G        -> "⚡ 5G"
    SignalLevel.PLATINUM      -> "💎 プラチナBand28"
    SignalLevel.LTE           -> "📶 LTE"
    SignalLevel.WEAK          -> "👹 パートナー回線"
    SignalLevel.NO_SIGNAL     -> "🚫 圏外"
    SignalLevel.AIRPLANE_MODE -> "✈️ 機内モード"
    SignalLevel.NO_SIM        -> "📵 SIMなし"
}

private class TouchInterceptOverlay(private val onUserTouch: () -> Unit) : Overlay() {
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float, mapView: MapView): Boolean { onUserTouch(); return false }
    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float, mapView: MapView): Boolean { onUserTouch(); return false }
    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) = Unit
}

/** パルス周期（ミリ秒）。最新地点のリングがこの周期で拡大・フェードアウトする。 */
private const val PULSE_PERIOD_MS = 1400L

/**
 * 自分の実測データだけで作る「面」表示（カバレッジエリア風オーバーレイ）。
 * 楽天モバイル公式のエリアマップとは無関係の自作コンテンツ — 公式データの取得・転載は行わない。
 * 実測点を約11mグリッド（latLngToH3Index）で集約し、グリッドごとに最良の信号レベルの色で
 * 実距離換算の円を塗ることで、複数の計測点が「面」として繋がって見えるようにする。
 * 機内モード・SIMなしは端末状態であり回線の受信状況を表さないため対象外。
 */
private class CoverageAreaOverlay(measurements: List<Measurement>) : Overlay() {
    private val cells: List<Pair<Measurement, SignalLevel>> = measurements
        .filter { it.signalLevel != SignalLevel.AIRPLANE_MODE && it.signalLevel != SignalLevel.NO_SIM }
        .groupBy { latLngToH3Index(it.latitude, it.longitude) }
        .values
        .map { inCell ->
            val best = inCell.minByOrNull { it.signalLevel.rarityRank } ?: inCell.first()
            best to best.signalLevel
        }
        // 弱い信号を先に描き、強い信号を後から上書き表示する
        .sortedByDescending { (_, level) -> level.rarityRank }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** グリッド1マス分の実距離半径(m)。周辺のグリッドと重なって「面」に見える程度の大きさ */
    private val radiusMeters = 70.0

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        cells.forEach { (m, level) ->
            val center = Point()
            projection.toPixels(GeoPoint(m.latitude, m.longitude), center)
            val edge = Point()
            projection.toPixels(GeoPoint(m.latitude + radiusMeters / 111_320.0, m.longitude), edge)
            val radiusPx = hypot((edge.x - center.x).toDouble(), (edge.y - center.y).toDouble()).toFloat()

            paint.color = level.toColor()
            paint.alpha = 70
            canvas.drawCircle(center.x.toFloat(), center.y.toFloat(), radiusPx, paint)
        }
    }
}

private class MeasurementOverlay(measurements: List<Measurement>) : Overlay() {
    private val sorted = measurements.sortedByDescending { it.signalLevel.rarityRank }
    private val latest = measurements.maxByOrNull { it.timestamp }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        val radius = zoomRadius(mapView.zoomLevelDouble, base = 18f, min = 3f, max = 48f)
        sorted.forEach { m ->
            val color = m.signalLevel.toColor()
            val pt = Point()
            projection.toPixels(GeoPoint(m.latitude, m.longitude), pt)

            glowPaint.color = color
            glowPaint.alpha = 60
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), radius * 1.6f, glowPaint)

            paint.color = color
            paint.alpha = 180
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), radius, paint)
        }

        latest?.let { m ->
            val pt = Point()
            projection.toPixels(GeoPoint(m.latitude, m.longitude), pt)
            val phase = (System.currentTimeMillis() % PULSE_PERIOD_MS) / PULSE_PERIOD_MS.toFloat()
            pulsePaint.color = m.signalLevel.toColor()
            pulsePaint.alpha = ((1f - phase) * 200).toInt().coerceIn(0, 255)
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), radius * (1f + phase * 0.8f), pulsePaint)
            mapView.postInvalidate()
        }
    }
}

private class CollectionOverlay(records: List<CollectionRecord>) : Overlay() {
    private val sorted = records.mapNotNull { r ->
        val level = runCatching { SignalLevel.valueOf(r.signalLevel) }.getOrNull() ?: return@mapNotNull null
        Pair(r, level)
    }.sortedByDescending { (_, level) -> level.rarityRank }
    private val latest = records.maxByOrNull { it.capturedAt }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        val radius = zoomRadius(mapView.zoomLevelDouble, base = 28f, min = 5f, max = 72f)
        sorted.forEach { (record, level) ->
            val color = level.toColor()
            val pt = Point()
            projection.toPixels(GeoPoint(record.latitude, record.longitude), pt)

            glowPaint.color = color
            glowPaint.alpha = 70
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), radius * 1.6f, glowPaint)

            paint.color = color
            paint.alpha = 220
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), radius, paint)
        }

        latest?.let { record ->
            val level = runCatching { SignalLevel.valueOf(record.signalLevel) }.getOrNull() ?: return@let
            val pt = Point()
            projection.toPixels(GeoPoint(record.latitude, record.longitude), pt)
            val phase = (System.currentTimeMillis() % PULSE_PERIOD_MS) / PULSE_PERIOD_MS.toFloat()
            pulsePaint.color = level.toColor()
            pulsePaint.alpha = ((1f - phase) * 200).toInt().coerceIn(0, 255)
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), radius * (1f + phase * 0.8f), pulsePaint)
            mapView.postInvalidate()
        }
    }
}

/** ズームレベルに応じたドット半径を返す。基準ズーム15、2倍ずつスケール */
private fun zoomRadius(zoom: Double, base: Float, min: Float, max: Float): Float =
    (base * 2.0.pow(zoom - 15.0)).toFloat().coerceIn(min, max)

private fun SignalLevel.toColor(): Int = when (this) {
    SignalLevel.PLATINUM_5G   -> Color.parseColor("#FFD700")
    SignalLevel.FIVE_G        -> Color.parseColor("#1E88E5")
    SignalLevel.PLATINUM      -> Color.parseColor("#AB47BC")
    SignalLevel.LTE           -> Color.parseColor("#43A047")
    SignalLevel.WEAK          -> Color.parseColor("#5D4037")
    SignalLevel.NO_SIGNAL     -> Color.parseColor("#212121")
    SignalLevel.AIRPLANE_MODE -> Color.parseColor("#1565C0")
    SignalLevel.NO_SIM        -> Color.parseColor("#B71C1C")
}
