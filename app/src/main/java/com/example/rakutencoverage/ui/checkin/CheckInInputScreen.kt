package com.example.rakutencoverage.ui.checkin

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.rakutencoverage.data.AppDatabase
import com.example.rakutencoverage.data.CheckInRecord
import com.example.rakutencoverage.data.GamePhase
import com.example.rakutencoverage.data.Spot
import com.example.rakutencoverage.data.SpotType
import com.example.rakutencoverage.data.nextPhase
import com.example.rakutencoverage.measurement.SpeedTester
import com.example.rakutencoverage.ui.map.ArenaModeInput
import com.example.rakutencoverage.ui.map.MapViewModel
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * チェックイン入力画面。スポット選択・写真・スピードテストを行い、確定でCheckInRecordを保存、
 * mapViewModel.setCheckIn() で従来通り計測ループへのタグ付けを開始する。
 *
 * @param initialSpotId   記録リストからのクイック追加で渡されるプリフィルスポットID。nullなら通常の選択UIを表示
 * @param initialSeatLabel 同じくクイック追加で渡される席プリフィル
 */
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInInputScreen(
    mapViewModel: MapViewModel,
    initialSpotId: String? = null,
    initialSeatLabel: String = "",
    onBack: () -> Unit,
    onCheckedIn: () -> Unit
) {
    val spotsByType by mapViewModel.spotsByType.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf(SpotType.ARENA) }
    var selectedSpot by remember { mutableStateOf<Spot?>(null) }
    var showSpotPicker by remember { mutableStateOf(initialSpotId == null) }
    var seatLabel by remember { mutableStateOf(initialSeatLabel) }
    var selectedPhase by remember { mutableStateOf(GamePhase.PRE_GAME) }
    var phaseExpanded by remember { mutableStateOf(false) }

    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var cameraTargetUri by remember { mutableStateOf<Uri?>(null) }

    var speedResult by remember { mutableStateOf<SpeedTester.Result?>(null) }
    var speedTesting by remember { mutableStateOf(false) }
    var speedProgress by remember { mutableStateOf("") }

    var submitting by remember { mutableStateOf(false) }

    // 4-1: spotId指定ありの場合、該当Spotをロード完了次第選択済みにする
    LaunchedEffect(initialSpotId, spotsByType) {
        val spotId = initialSpotId ?: return@LaunchedEffect
        if (selectedSpot != null) return@LaunchedEffect
        val spot = spotsByType.values.flatten().firstOrNull { it.id == spotId } ?: return@LaunchedEffect
        selectedSpot = spot
        selectedType = spot.type
    }

    // 4-2: spotId指定ありの場合、そのスポットの「今日」の最新記録の次フェーズを初期値にする
    LaunchedEffect(initialSpotId) {
        val spotId = initialSpotId ?: return@LaunchedEffect
        val todayRecords = withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).checkInDao().getBySpot(spotId)
        }
        val today = LocalDate.now()
        // getBySpot は timestamp DESC のため、今日の記録のうち先頭に来たものが最新
        val latestToday = todayRecords.firstOrNull { record ->
            runCatching {
                Instant.parse(record.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() == today
            }.getOrDefault(false)
        }
        selectedPhase = latestToday?.gamePhase
            ?.let { runCatching { GamePhase.valueOf(it) }.getOrNull() }
            ?.let { nextPhase(it) }
            ?: GamePhase.PRE_GAME
    }

    // 4-3: spotId指定なしの場合のみ現在地を取得し、最寄りスポット自動選択・距離表示に使う
    var deviceLocation by remember { mutableStateOf<Location?>(null) }
    LaunchedEffect(Unit) {
        if (initialSpotId == null) {
            deviceLocation = runCatching {
                LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
            }.getOrNull()
        }
    }

    val allSpots = remember(spotsByType) { spotsByType.values.flatten() }
    val spotDistances: Map<String, Float> = remember(deviceLocation, allSpots) {
        val loc = deviceLocation
        if (loc == null || allSpots.isEmpty()) {
            emptyMap()
        } else {
            allSpots.associate { spot ->
                val results = FloatArray(1)
                Location.distanceBetween(loc.latitude, loc.longitude, spot.latitude, spot.longitude, results)
                spot.id to results[0]
            }
        }
    }

    var nearestAutoApplied by remember { mutableStateOf(false) }
    LaunchedEffect(spotDistances) {
        if (initialSpotId != null || nearestAutoApplied || spotDistances.isEmpty()) return@LaunchedEffect
        nearestAutoApplied = true
        val nearest = spotDistances.entries.minByOrNull { it.value } ?: return@LaunchedEffect
        if (nearest.value <= 2000f) {
            val spot = allSpots.firstOrNull { it.id == nearest.key } ?: return@LaunchedEffect
            selectedType = spot.type
            selectedSpot = spot
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) pendingPhotoUri = cameraTargetUri
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pendingPhotoUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎫 新規チェックイン") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←", fontSize = 20.sp) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // スポット選択: プリフィル済みなら選択済みカード、それ以外(または「変更」後)は通常の選択UI
            val currentSelected = selectedSpot
            if (initialSpotId != null && !showSpotPicker && currentSelected != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${currentSelected.type.icon} ${currentSelected.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { showSpotPicker = true }) { Text("変更") }
                    }
                }
            } else {
                SpotPickerSection(
                    spotsByType = spotsByType,
                    selectedType = selectedType,
                    onTypeChange = { selectedType = it; selectedSpot = null },
                    selectedSpot = selectedSpot,
                    onSpotSelected = { spot ->
                        selectedSpot = spot
                        showSpotPicker = false
                    },
                    spotDistances = spotDistances
                )
            }

            if (selectedType == SpotType.ARENA) {
                OutlinedTextField(
                    value = seatLabel,
                    onValueChange = { seatLabel = it },
                    label = { Text("自席（例: A-12列-5番）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = phaseExpanded,
                    onExpandedChange = { phaseExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedPhase.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("試合フェーズ") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(phaseExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = phaseExpanded,
                        onDismissRequest = { phaseExpanded = false }
                    ) {
                        GamePhase.entries.forEach { phase ->
                            DropdownMenuItem(
                                text = { Text(phase.label) },
                                onClick = { selectedPhase = phase; phaseExpanded = false }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // 写真
            Column {
                Text("写真（任意）", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val photosDir = File(context.cacheDir, "checkin_photos").apply { mkdirs() }
                        val tmpFile = File(photosDir, "tmp_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tmpFile)
                        cameraTargetUri = uri
                        cameraLauncher.launch(uri)
                    }) { Text("📷 撮影") }

                    OutlinedButton(onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) { Text("🖼️ ギャラリー") }

                    pendingPhotoUri?.let { uri ->
                        CheckInPhotoPreview(uri = uri, sizeDp = 56.dp)
                    }
                }
            }

            HorizontalDivider()

            // スピードテスト
            Column {
                Text("回線速度チェック（任意）", style = MaterialTheme.typography.labelMedium)
                Text(
                    "約12MBの通信が発生します",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (speedTesting) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(speedProgress, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    OutlinedButton(onClick = {
                        speedTesting = true
                        speedResult = null
                        scope.launch {
                            val result = SpeedTester.run { progress -> speedProgress = progress }
                            speedResult = result
                            speedTesting = false
                        }
                    }) { Text("⚡ スピードテスト実行") }
                }
                speedResult?.let { r ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "↓${r.downloadMbps?.let { "%.1f".format(it) } ?: "-"} Mbps　" +
                            "↑${r.uploadMbps?.let { "%.1f".format(it) } ?: "-"} Mbps　" +
                            "遅延 ${r.latencyMs?.let { "${it}ms" } ?: "-"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    r.error?.let {
                        Text(
                            "⚠️ $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                enabled = selectedSpot != null && !submitting,
                onClick = {
                    val spot = selectedSpot ?: return@Button
                    submitting = true
                    scope.launch {
                        val photoPath = pendingPhotoUri?.let { uri -> copyPhotoToFilesDir(context, uri) }
                        val location = runCatching {
                            LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
                        }.getOrNull()

                        val isArena = spot.type == SpotType.ARENA
                        val record = CheckInRecord(
                            spotId = spot.id,
                            spotType = spot.type.name,
                            spotName = spot.name,
                            latitude = location?.latitude,
                            longitude = location?.longitude,
                            timestamp = Instant.now().toString(),
                            seatLabel = if (isArena) seatLabel.ifBlank { null } else null,
                            gamePhase = if (isArena) selectedPhase.name else null,
                            photoPath = photoPath,
                            downloadMbps = speedResult?.downloadMbps,
                            uploadMbps = speedResult?.uploadMbps,
                            latencyMs = speedResult?.latencyMs
                        )
                        withContext(Dispatchers.IO) {
                            AppDatabase.getInstance(context).checkInDao().insert(record)
                        }
                        mapViewModel.setCheckIn(ArenaModeInput(spot, seatLabel, selectedPhase))
                        submitting = false
                        onCheckedIn()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("🎫 チェックインする", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * スポット選択UI。種別・検索・都道府県/距離/ディビジョンの絞り込みと、
 * 該当件数・LazyColumnによるスポット一覧を表示する。
 * 全件(道の駅1,145件等)をComposeしないよう、常にLazyColumnで描画する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpotPickerSection(
    spotsByType: Map<SpotType, List<Spot>>,
    selectedType: SpotType,
    onTypeChange: (SpotType) -> Unit,
    selectedSpot: Spot?,
    onSpotSelected: (Spot) -> Unit,
    spotDistances: Map<String, Float>
) {
    var query by remember { mutableStateOf("") }
    var selectedPref by remember { mutableStateOf<String?>(null) }
    var selectedDistanceKm by remember { mutableStateOf<Int?>(null) }
    var selectedDivision by remember { mutableStateOf<String?>(null) }
    var prefExpanded by remember { mutableStateOf(false) }

    // 種別切替時は、別種別にしか存在しない可能性がある絞り込み条件をリセットする
    LaunchedEffect(selectedType) {
        selectedPref = null
        selectedDivision = null
    }

    val spots = spotsByType[selectedType] ?: emptyList()
    val availablePrefs = remember(spots) {
        SpotFilter.PREF_ORDER.filter { pref -> spots.any { it.pref == pref } }
    }

    val filteredSpots = remember(spots, query, selectedPref, selectedDistanceKm, selectedDivision, spotDistances) {
        SpotFilter.filter(
            spots = spots,
            query = query,
            pref = selectedPref,
            maxDistanceM = selectedDistanceKm?.let { it * 1000f },
            division = selectedDivision,
            distances = spotDistances
        )
    }

    Column {
        // スポット種別
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SpotType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = selectedType == type,
                    onClick = { onTypeChange(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, SpotType.entries.size),
                    label = { Text("${type.icon} ${type.label}") }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("🔍 検索（スポット名・都道府県・市区町村・クラブ名）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = prefExpanded,
                onExpandedChange = { prefExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedPref ?: "すべて",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("都道府県") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(prefExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).width(160.dp)
                )
                ExposedDropdownMenu(
                    expanded = prefExpanded,
                    onDismissRequest = { prefExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("すべて") },
                        onClick = { selectedPref = null; prefExpanded = false }
                    )
                    availablePrefs.forEach { pref ->
                        DropdownMenuItem(
                            text = { Text(pref) },
                            onClick = { selectedPref = pref; prefExpanded = false }
                        )
                    }
                }
            }

            if (spotDistances.isNotEmpty()) {
                FilterChip(
                    selected = selectedDistanceKm == 10,
                    onClick = { selectedDistanceKm = if (selectedDistanceKm == 10) null else 10 },
                    label = { Text("10km以内") }
                )
                FilterChip(
                    selected = selectedDistanceKm == 50,
                    onClick = { selectedDistanceKm = if (selectedDistanceKm == 50) null else 50 },
                    label = { Text("50km以内") }
                )
            }

            if (selectedType == SpotType.ARENA) {
                FilterChip(
                    selected = selectedDivision == "PREMIER",
                    onClick = { selectedDivision = if (selectedDivision == "PREMIER") null else "PREMIER" },
                    label = { Text("PREMIER") }
                )
                FilterChip(
                    selected = selectedDivision == "ONE",
                    onClick = { selectedDivision = if (selectedDivision == "ONE") null else "ONE" },
                    label = { Text("ONE") }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "該当 ${filteredSpots.size} 件",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))

        if (filteredSpots.isEmpty()) {
            Text(
                if (spots.isEmpty()) "GeoJSONデータ準備中" else "該当するスポットがありません",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                items(filteredSpots, key = { it.id }) { spot ->
                    val isSelected = selectedSpot?.id == spot.id
                    val distance = spotDistances[spot.id]
                    ListItem(
                        headlineContent = { Text(spot.name) },
                        supportingContent = {
                            Text(
                                if (spot.type == SpotType.ARENA) "${spot.pref}・${spot.club}"
                                else "${spot.pref} ${spot.city}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isSelected) Text("✓", color = MaterialTheme.colorScheme.primary)
                                distance?.let {
                                    Text(
                                        formatDistance(it),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                              else Color.Transparent
                        ),
                        modifier = Modifier.clickable { onSpotSelected(spot) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/** 距離表示。1000m未満は「120m」、以上は「1.2km」の表記にする */
private fun formatDistance(meters: Float): String =
    if (meters < 1000f) "${meters.roundToInt()}m" else "%.1fkm".format(meters / 1000f)

/** 選択済みの写真(カメラ一時ファイル or ギャラリーのUri)を filesDir/checkin_photos/{ISO時刻}.jpg へコピーする */
private suspend fun copyPhotoToFilesDir(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val photosDir = File(context.filesDir, "checkin_photos").apply { mkdirs() }
        val safeTimestamp = Instant.now().toString().replace(":", "-")
        val fileName = "${safeTimestamp}_${(1000..9999).random()}.jpg"
        val destFile = File(photosDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        "checkin_photos/$fileName"
    }.getOrNull()
}
