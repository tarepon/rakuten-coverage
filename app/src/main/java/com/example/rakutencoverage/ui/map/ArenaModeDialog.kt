package com.example.rakutencoverage.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rakutencoverage.data.GamePhase
import com.example.rakutencoverage.data.Spot
import com.example.rakutencoverage.data.SpotType

data class ArenaModeInput(
    val spot: Spot,
    val seatLabel: String = "",
    val gamePhase: GamePhase = GamePhase.PRE_GAME
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInDialog(
    current: ArenaModeInput?,
    spotsByType: Map<SpotType, List<Spot>>,
    onConfirm: (ArenaModeInput) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(current?.spot?.type ?: SpotType.ARENA) }
    var selectedSpot by remember { mutableStateOf(current?.spot) }
    var seatLabel by remember { mutableStateOf(current?.seatLabel ?: "") }
    var selectedPhase by remember { mutableStateOf(current?.gamePhase ?: GamePhase.PRE_GAME) }
    var phaseExpanded by remember { mutableStateOf(false) }

    val spots = spotsByType[selectedType] ?: emptyList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎫 チェックイン") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 520.dp)
            ) {
                // スポット種別タブ
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SpotType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type; selectedSpot = null },
                            shape = SegmentedButtonDefaults.itemShape(index, SpotType.entries.size),
                            label = { Text("${type.icon} ${type.label}") }
                        )
                    }
                }

                // スポット一覧
                Text("スポットを選択", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(spots) { spot ->
                        val isSelected = selectedSpot?.id == spot.id
                        ListItem(
                            headlineContent = { Text(spot.name) },
                            trailingContent = {
                                if (isSelected) Text("✓", color = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.clickable { selectedSpot = spot }
                                .then(if (isSelected)
                                    Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                                else Modifier)
                        )
                        HorizontalDivider()
                    }
                    if (spots.isEmpty()) {
                        item {
                            Text(
                                "GeoJSONデータ準備中",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // アリーナ専用フィールド
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
                            modifier = Modifier.menuAnchor().fillMaxWidth()
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

                Text(
                    "位置情報はGPSの実測値を記録します。\nスタンプ判定はチェックイン選択を使用します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedSpot != null,
                onClick = {
                    selectedSpot?.let { spot ->
                        onConfirm(ArenaModeInput(spot, seatLabel, selectedPhase))
                    }
                }
            ) { Text("チェックイン") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

private fun Modifier.background(color: androidx.compose.ui.graphics.Color) =
    this.then(Modifier.wrapContentSize())
