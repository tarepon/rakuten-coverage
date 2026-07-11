package com.example.rakutencoverage.ui.checkin

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rakutencoverage.data.AppDatabase
import com.example.rakutencoverage.data.CheckInRecord
import com.example.rakutencoverage.data.GamePhase
import com.example.rakutencoverage.data.SpotType
import com.example.rakutencoverage.ui.map.ArenaModeInput
import com.example.rakutencoverage.ui.map.MapViewModel
import com.example.rakutencoverage.ui.stamp.StampContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val rowTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault())
private val detailTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault())
private val exportDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * チェックイン画面。「記録」「スタンプ」の2タブ構成(ボトムナビのトップレベル画面)。
 * アクティブなチェックイン(計測タグ付け)のバナーはタブの上に常時表示する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    mapViewModel: MapViewModel,
    checkInVm: CheckInViewModel = viewModel(),
    onNewCheckIn: (spotId: String?, seatLabel: String?) -> Unit
) {
    val records by checkInVm.records.collectAsState()
    val activeCheckIn by mapViewModel.checkIn.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var detailRecord by remember { mutableStateOf<CheckInRecord?>(null) }
    var selectedTab by remember { mutableStateOf(0) }

    // spotId でグループ化。records は timestamp DESC のため、最新の記録を含むグループが先頭に来る
    val grouped = remember(records) { records.groupBy { it.spotId } }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🎫 チェックイン") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            activeCheckIn?.let { ci ->
                ActiveCheckInBanner(checkIn = ci, onCheckout = { mapViewModel.setCheckIn(null) })
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("記録") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("スタンプ") })
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (selectedTab == 0) {
                    RecordsTab(
                        grouped = grouped,
                        onNewCheckIn = onNewCheckIn,
                        onDetail = { detailRecord = it },
                        snackbarHostState = snackbarHostState
                    )
                } else {
                    StampContent()
                }
            }
        }
    }

    detailRecord?.let { record ->
        CheckInDetailDialog(record = record, onDismiss = { detailRecord = null })
    }
}

/** タブの上に常時表示するアクティブチェックインバナー(旧CheckInScreen冒頭のものをここへ移動) */
@Composable
private fun ActiveCheckInBanner(checkIn: ArenaModeInput, onCheckout: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "🎫 チェックイン中: ${checkIn.spot.type.icon} ${checkIn.spot.name}",
                    fontWeight = FontWeight.Bold
                )
                if (checkIn.spot.type == SpotType.ARENA) {
                    Text(
                        "${checkIn.gamePhase.label}" +
                            (if (checkIn.seatLabel.isNotBlank()) "  |  席: ${checkIn.seatLabel}" else ""),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            TextButton(onClick = onCheckout) { Text("チェックアウト") }
        }
    }
}

/** 「記録」タブの中身。スポットごとにグループ化した記録リスト+クイック追加+サマリー+ミニチャート。 */
@Composable
private fun RecordsTab(
    grouped: Map<String, List<CheckInRecord>>,
    onNewCheckIn: (spotId: String?, seatLabel: String?) -> Unit,
    onDetail: (CheckInRecord) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val expandedCharts = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "new_checkin_button") {
            Button(
                onClick = { onNewCheckIn(null, null) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) { Text("➕ 新規チェックイン") }
        }

        if (grouped.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "まだチェックイン記録がありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        grouped.forEach { (spotId, groupRecords) ->
            val first = groupRecords.first()
            val icon = runCatching { SpotType.valueOf(first.spotType).icon }.getOrDefault("📍")
            val hasChartData = groupRecords.any { it.gamePhase != null && it.downloadMbps != null }
            val chartExpanded = expandedCharts[spotId] == true

            item(key = "header_$spotId") {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$icon ${first.spotName}（${groupRecords.size}件）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (hasChartData) {
                            IconButton(onClick = { expandedCharts[spotId] = !chartExpanded }) {
                                Text("📊")
                            }
                        }
                        IconButton(onClick = {
                            scope.launch {
                                exportSpotRecords(context, spotId, first.spotName, snackbarHostState)
                            }
                        }) { Text("📤") }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            spotSummaryLine(groupRecords),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = { onNewCheckIn(spotId, null) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text("追加", fontSize = 12.sp) }
                    }

                    if (hasChartData && chartExpanded) {
                        PhaseMiniChart(groupRecords)
                    }
                }
                HorizontalDivider()
            }

            items(groupRecords, key = { it.id }) { record ->
                CheckInRow(
                    record = record,
                    onClick = { onDetail(record) },
                    onQuickAdd = { onNewCheckIn(record.spotId, record.seatLabel) }
                )
            }
        }
    }
}

/** 📤 スポット別エクスポート。ファイル名は「{スポット名}_{yyyy-MM-dd}.json」(実行日基準・サニタイズ済み)。 */
private suspend fun exportSpotRecords(
    context: Context,
    spotId: String,
    spotName: String,
    snackbarHostState: SnackbarHostState
) {
    runCatching {
        val file = withContext(Dispatchers.IO) {
            val spotRecords = AppDatabase.getInstance(context).checkInDao().getBySpot(spotId)
            val json = checkInRecordsToJson(spotRecords)
            val dateStr = LocalDate.now().format(exportDateFormatter)
            val fileName = sanitizeFileName("${spotName}_$dateStr") + ".json"
            File(context.cacheDir, fileName).apply { writeText(json, Charsets.UTF_8) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "チェックイン記録を共有"
            )
        )
    }.onFailure { e ->
        snackbarHostState.showSnackbar("共有に失敗しました: ${e.message}")
    }
}

/** ファイル名に使えない文字( \ / : * ? " < > | )と改行を "_" に置換する */
private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")

/** スポット別サマリー行。「{N}回・平均↓{x}Mbps・遅延{y}ms」。スピードテスト記録が1件もなければ件数のみ */
private fun spotSummaryLine(records: List<CheckInRecord>): String {
    val downloads = records.mapNotNull { it.downloadMbps }
    if (downloads.isEmpty()) return "${records.size}回"
    val avgDownload = "%.1f".format(downloads.average())
    val latencies = records.mapNotNull { it.latencyMs }
    return if (latencies.isEmpty()) {
        "${records.size}回・平均↓${avgDownload}Mbps"
    } else {
        "${records.size}回・平均↓${avgDownload}Mbps・遅延${"%.0f".format(latencies.average())}ms"
    }
}

/** フェーズ別ミニチャート。GamePhase順に、データ(gamePhase+速度)のあるフェーズのみ横棒バーで表示する */
@Composable
private fun PhaseMiniChart(records: List<CheckInRecord>) {
    val byPhase = remember(records) {
        GamePhase.entries.mapNotNull { phase ->
            val phaseRecords = records.filter { it.gamePhase == phase.name && it.downloadMbps != null }
            if (phaseRecords.isEmpty()) return@mapNotNull null
            val avgDownload = phaseRecords.mapNotNull { it.downloadMbps }.average()
            val latencies = phaseRecords.mapNotNull { it.latencyMs }
            val avgLatency = if (latencies.isEmpty()) null else latencies.average()
            Triple(phase, avgDownload, avgLatency)
        }
    }
    if (byPhase.isEmpty()) return
    val maxDownload = byPhase.maxOf { it.second }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        byPhase.forEach { (phase, avgDownload, avgLatency) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(phase.label, fontSize = 11.sp, modifier = Modifier.width(56.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val fraction = if (maxDownload > 0) (avgDownload / maxDownload).toFloat().coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .clip(RoundedCornerShape(4.dp))
                            .background(downloadColor(avgDownload))
                    )
                }
                Text(
                    "↓${"%.1f".format(avgDownload)}Mbps" +
                        (avgLatency?.let { "・遅延${"%.0f".format(it)}ms" } ?: ""),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CheckInRow(record: CheckInRecord, onClick: () -> Unit, onQuickAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CheckInPhotoThumbnail(record.photoPath, 56.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                rowTimeFormatter.format(Instant.parse(record.timestamp)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                record.gamePhase?.let { phase ->
                    val label = runCatching { GamePhase.valueOf(phase).label }.getOrDefault(phase)
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                        Text(label, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                if (!record.seatLabel.isNullOrBlank()) {
                    Text(
                        "席: ${record.seatLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (record.downloadMbps != null || record.uploadMbps != null || record.latencyMs != null) {
                Text(
                    text = speedAnnotatedString(record.downloadMbps, record.uploadMbps, record.latencyMs),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (!record.memo.isNullOrBlank()) {
                Text(
                    "📝 ${record.memo}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onQuickAdd) { Text("＋") }
    }
}

@Composable
private fun CheckInDetailDialog(record: CheckInRecord, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(record.spotName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CheckInPhotoThumbnail(
                    record.photoPath,
                    200.dp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text("日時: ${detailTimeFormatter.format(Instant.parse(record.timestamp))}")
                record.gamePhase?.let {
                    Text("フェーズ: ${runCatching { GamePhase.valueOf(it).label }.getOrDefault(it)}")
                }
                if (!record.seatLabel.isNullOrBlank()) Text("席: ${record.seatLabel}")
                if (record.latitude != null && record.longitude != null) {
                    Text("位置: %.5f, %.5f".format(record.latitude, record.longitude))
                }
                if (record.downloadMbps != null || record.uploadMbps != null || record.latencyMs != null) {
                    Text(speedAnnotatedString(record.downloadMbps, record.uploadMbps, record.latencyMs))
                }
                if (!record.memo.isNullOrBlank()) Text("📝 ${record.memo}")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

/** 📤 共有エクスポート用のJSON整形。写真バイナリは含めずphotoPath(相対パス)のみ含める */
private fun checkInRecordsToJson(records: List<CheckInRecord>): String {
    val arr = JSONArray()
    records.forEach { r ->
        arr.put(JSONObject().apply {
            put("id", r.id)
            put("spotId", r.spotId)
            put("spotType", r.spotType)
            put("spotName", r.spotName)
            putOpt("latitude", r.latitude)
            putOpt("longitude", r.longitude)
            put("timestamp", r.timestamp)
            putOpt("seatLabel", r.seatLabel)
            putOpt("gamePhase", r.gamePhase)
            putOpt("photoPath", r.photoPath)
            putOpt("downloadMbps", r.downloadMbps)
            putOpt("uploadMbps", r.uploadMbps)
            putOpt("latencyMs", r.latencyMs)
            putOpt("memo", r.memo)
        })
    }
    return arr.toString(2)
}
