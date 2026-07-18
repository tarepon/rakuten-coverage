package com.example.rakutencoverage.ui.settings

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rakutencoverage.data.AppDatabase
import com.example.rakutencoverage.data.SettingsStore
import com.example.rakutencoverage.ui.map.MapViewModel
import com.example.rakutencoverage.util.BackupManager
import com.example.rakutencoverage.util.DataExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: MapViewModel = viewModel()) {
    val measurements by vm.measurements.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showBackupNotice by remember { mutableStateOf(false) }

    // バックアップ保存先の選択（SAF）。選択後にJSONを書き込む
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            resultMessage = runCatching {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getInstance(context)
                    val json = BackupManager.toJson(
                        BackupManager.BackupData(
                            measurements = db.measurementDao().getAll(),
                            collections  = db.collectionDao().getAll(),
                            stamps       = db.stampDao().getAll(),
                            checkins     = db.checkInDao().getAll()
                        )
                    )
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("ファイルを開けませんでした")
                }
                "✅ バックアップを保存しました"
            }.getOrElse { "❌ 保存に失敗: ${it.message}" }
            busy = false
        }
    }

    // バックアップファイルの選択（SAF）。読み込んでマージインポートする
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            resultMessage = runCatching {
                withContext(Dispatchers.IO) {
                    val json = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error("ファイルを読めませんでした")
                    val data = BackupManager.fromJson(json)

                    val db = AppDatabase.getInstance(context)
                    // 計測: 既存タイムスタンプと重複しないものだけ追加
                    val existing = db.measurementDao().getAllTimestamps().toHashSet()
                    val newMeasurements = data.measurements.filter { it.timestamp !in existing }
                    if (newMeasurements.isNotEmpty()) db.measurementDao().insertAll(newMeasurements)
                    // 図鑑・スタンプ: 主キー重複は既存を優先
                    db.collectionDao().insertAllIgnore(data.collections)
                    db.stampDao().insertAllIgnore(data.stamps)
                    // チェックイン記録: 既存タイムスタンプと重複しないものだけ追加(計測と同じ方式)
                    val existingCheckins = db.checkInDao().getAllTimestamps().toHashSet()
                    val newCheckins = data.checkins.filter { it.timestamp !in existingCheckins }
                    if (newCheckins.isNotEmpty()) db.checkInDao().insertAll(newCheckins)

                    "✅ インポート完了: 計測${newMeasurements.size}件を追加" +
                        "（図鑑${data.collections.size}件・スタンプ${data.stamps.size}件・" +
                        "チェックイン${newCheckins.size}件をマージ）"
                }
            }.getOrElse { "❌ インポートに失敗: ${it.message}" }
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("⚙️ 設定", style = MaterialTheme.typography.headlineSmall)

        // ---------- アプリ設定 ----------
        Text(
            "アプリ設定",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        var keepScreenOn by remember { mutableStateOf(SettingsStore.keepScreenOn(context)) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("📱 常に画面をONにする", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "計測中にスリープさせない(電池消費は増えます)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = keepScreenOn,
                onCheckedChange = { enabled ->
                    keepScreenOn = enabled
                    SettingsStore.setKeepScreenOn(context, enabled)
                    (context as? Activity)?.window?.let { window ->
                        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ---------- データ管理 ----------
        Text(
            "データ管理",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Text("計測件数: ${measurements.size} 件", style = MaterialTheme.typography.bodyMedium)

        // 位置情報を含むデータであることの常設注意
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "⚠️ 保存・エクスポートするファイルには位置情報の履歴が含まれます。共有先や保存先にご注意ください。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }

        Button(
            enabled = !busy,
            onClick = { showBackupNotice = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("💾 バックアップを保存 (JSON)") }

        Button(
            enabled = !busy,
            onClick = { importLauncher.launch(arrayOf("application/json")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("📥 バックアップから復元") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        OutlinedButton(
            enabled = measurements.isNotEmpty() && !busy,
            onClick = {
                scope.launch {
                    busy = true
                    val file = withContext(Dispatchers.IO) { DataExporter.toCsv(measurements, context) }
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.provider", file
                    )
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "CSV で共有"
                    ))
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("CSV でエクスポート") }

        OutlinedButton(
            enabled = measurements.isNotEmpty() && !busy,
            onClick = {
                scope.launch {
                    busy = true
                    val file = withContext(Dispatchers.IO) { DataExporter.toGeoJson(measurements, context) }
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.provider", file
                    )
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "GeoJSON で共有"
                    ))
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("GeoJSON でエクスポート") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        OutlinedButton(
            enabled = !busy,
            onClick = { showClearDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        ) { Text("🗑 全データを削除") }

        if (busy) CircularProgressIndicator()
        resultMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }

    if (showBackupNotice) {
        AlertDialog(
            onDismissRequest = { showBackupNotice = false },
            icon = { Text("🔒", style = MaterialTheme.typography.headlineMedium) },
            title = { Text("位置情報を含むファイルです") },
            text = {
                Text(
                    "このバックアップには、いつどこで計測したかの位置履歴が含まれます。" +
                        "保存先（クラウド等）や共有相手の取り扱いにご注意ください。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackupNotice = false
                    backupLauncher.launch("coverage_backup_${LocalDate.now()}.json")
                }) { Text("保存先を選ぶ") }
            },
            dismissButton = {
                TextButton(onClick = { showBackupNotice = false }) { Text("キャンセル") }
            }
        )
    }

    if (showClearDialog) {
        ClearDataDialog(
            measurementCount = measurements.size,
            onDismiss = { showClearDialog = false },
            onConfirm = {
                showClearDialog = false
                scope.launch {
                    busy = true
                    resultMessage = runCatching {
                        withContext(Dispatchers.IO) {
                            val db = AppDatabase.getInstance(context)
                            db.measurementDao().deleteAll()
                            db.collectionDao().deleteAll()
                            db.stampDao().deleteAll()
                            // チェックイン写真(filesDir)はDB削除だけでは残ってしまうため、
                            // レコードを消す前にベストエフォートで実ファイルも削除する
                            db.checkInDao().getAll().forEach { record ->
                                record.photoPath?.let { path ->
                                    runCatching { File(context.filesDir, path).delete() }
                                }
                            }
                            db.checkInDao().deleteAll()
                        }
                        "✅ 全データを削除しました"
                    }.getOrElse { "❌ 削除に失敗: ${it.message}" }
                    busy = false
                }
            }
        )
    }
}

/**
 * 全データ削除の確認ダイアログ。
 * 誤操作防止として、チェックボックスにチェックを入れないと削除ボタンを押せない。
 */
@Composable
private fun ClearDataDialog(
    measurementCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var acknowledged by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("⚠️", style = MaterialTheme.typography.headlineMedium) },
        title = { Text("全データを削除しますか？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "計測データ・図鑑・スタンプがすべて消去されます。" +
                        "この操作は取り消せません。（計測${measurementCount}件）",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "先にバックアップの保存をおすすめします。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { acknowledged = !acknowledged }
                ) {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                    Text("バックアップ不要／削除を理解しました", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = acknowledged,
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("削除する") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}
