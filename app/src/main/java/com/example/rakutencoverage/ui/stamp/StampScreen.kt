package com.example.rakutencoverage.ui.stamp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rakutencoverage.data.SpotType
import com.example.rakutencoverage.data.StampRecord
import com.example.rakutencoverage.ui.map.MapViewModel

/**
 * スタンプラリー表示。Scaffold等の外殻を持たない埋め込み用コンポーザブル。
 * チェックイン画面(CheckInScreen)の「スタンプ」タブから呼び出される。
 *
 * スポットマスタ(道の駅1,145件等)を全件描画せず、記録(StampRecord)ベースで表示する。
 * マスタから消えたスポットの達成記録も record 側に保存された名前でそのまま表示される。
 */
@Composable
fun StampContent(vm: MapViewModel = viewModel()) {
    val stamps by vm.stamps.collectAsState()
    val spotsByType by vm.spotsByType.collectAsState()

    val sortedStamps = remember(stamps) { stamps.sortedByDescending { it.achievedAt } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("スタンプラリー", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "合計 ${stamps.size} スタンプ達成",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SpotType.entries.forEach { type ->
                    val masterCount = spotsByType[type]?.size ?: 0
                    val achievedCount = stamps.count { it.spotType == type.name }
                    StampProgressCard(type = type, achievedCount = achievedCount, masterCount = masterCount)
                }
            }
        }

        if (sortedStamps.isEmpty()) {
            item {
                Text(
                    "まだスタンプがありません。チェックインして集めましょう！",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(sortedStamps, key = { it.spotId }) { stamp ->
                StampRecordRow(stamp)
            }
        }
    }
}

/** 種別ごとの進捗カード。分母はスポットマスタ件数、分子はStampRecordの達成数。 */
@Composable
private fun StampProgressCard(type: SpotType, achievedCount: Int, masterCount: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(type.icon, fontSize = 24.sp)
                Text(
                    "${type.label} $achievedCount/$masterCount",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    if (masterCount == 0) 0f else (achievedCount.toFloat() / masterCount).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 達成済みスタンプ1件の行。マスタに存在しないspotIdでも record の内容だけで表示できる。 */
@Composable
private fun StampRecordRow(stamp: StampRecord) {
    val icon = SpotType.entries.firstOrNull { it.name == stamp.spotType }?.icon ?: "🎫"
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Color.White, fontSize = 14.sp)
            }
            Column {
                Text(
                    "$icon ${stamp.spotName}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stamp.achievedAt.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
