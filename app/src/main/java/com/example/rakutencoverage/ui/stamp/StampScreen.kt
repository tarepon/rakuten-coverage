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

@Composable
fun StampScreen(vm: MapViewModel = viewModel()) {
    val stamps by vm.stamps.collectAsState()
    val spotsByType by vm.spotsByType.collectAsState()

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

        SpotType.entries.forEach { type ->
            val spots = spotsByType[type] ?: emptyList()
            val achievedIds = stamps.filter { it.spotType == type.name }.map { it.spotId }.toSet()

            item {
                StampCategorySection(
                    type = type,
                    spots = spots.map { it.id to it.name },
                    achievedIds = achievedIds,
                    stamps = stamps
                )
            }
        }
    }
}

@Composable
private fun StampCategorySection(
    type: SpotType,
    spots: List<Pair<String, String>>,
    achievedIds: Set<String>,
    stamps: List<StampRecord>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ヘッダー
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(type.icon, fontSize = 24.sp)
                Column {
                    Text(type.label, style = MaterialTheme.typography.titleSmall)
                    if (spots.isNotEmpty()) {
                        Text(
                            "${achievedIds.size} / ${spots.size} 達成",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (spots.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "GeoJSONファイル追加で有効になります",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (spots.isEmpty()) 0f else achievedIds.size.toFloat() / spots.size },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // スポット一覧
            spots.forEach { (id, name) ->
                val achieved = id in achievedIds
                val stamp = stamps.firstOrNull { it.spotId == id }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (achieved) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (achieved) "✓" else "",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                    Column {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (achieved) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        stamp?.let {
                            Text(
                                it.achievedAt.take(10),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
