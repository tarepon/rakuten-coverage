package com.example.rakutencoverage.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rakutencoverage.data.SignalLevel
import com.example.rakutencoverage.data.monster.Monster
import com.example.rakutencoverage.data.monster.category
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(vm: CollectionViewModel = viewModel()) {
    val capturedCount by vm.capturedCountByLevel.collectAsState()
    val noSignalQuests by vm.noSignalQuests.collectAsState()
    val monstersByLevel by vm.monstersByLevel.collectAsState()
    val battle by vm.battle.collectAsState()
    val partnerKey by vm.partnerKey.collectAsState()

    var selectedLevel by remember { mutableStateOf<SignalLevel?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // トレーニングバトル(全画面オーバーレイ)
    battle?.let { b ->
        TrainingBattleScreen(b, vm)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("⚡ 光図鑑", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("楽天電波のモンスターを集めよう", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }

        items(vm.lightLevels) { level ->
            MonsterTile(
                level = level,
                count = capturedCount[level] ?: 0,
                monsterCount = monstersByLevel[level]?.size ?: 0,
                onClick = {
                    selectedLevel = level
                    scope.launch { sheetState.show() }
                }
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("💀 闇図鑑", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("圏外＝エリア拡張の伸び代を捕獲せよ", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("圏外セルは約555m四方を1セルとして数えます(捕獲でカウント)", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }

        items(vm.darkLevels) { level ->
            MonsterTile(
                level = level,
                count = capturedCount[level] ?: 0,
                monsterCount = monstersByLevel[level]?.size ?: 0,
                onClick = {
                    selectedLevel = level
                    scope.launch { sheetState.show() }
                }
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("🎯 圏外クエスト", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("圏外を集めるチャレンジ", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }

        items(noSignalQuests) { quest ->
            QuestCard(quest)
        }
    }

    if (selectedLevel != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedLevel = null },
            sheetState = sheetState
        ) {
            MonsterListSheet(
                level = selectedLevel!!,
                entries = monstersByLevel[selectedLevel] ?: emptyList(),
                partnerKey = partnerKey,
                onTraining = { vm.startTraining(it) },
                onSetPartner = { vm.setPartner(it.cellId) }
            )
        }
    }
}

/**
 * 光図鑑・闇図鑑用のコンパクト行タイル。絵文字を左、名称＋レア度/匹数を右に横並びした2行構成。
 * LazyColumn に縦に並べて使用する。
 */
@Composable
private fun MonsterTile(
    level: SignalLevel,
    count: Int,
    monsterCount: Int,
    onClick: () -> Unit
) {
    val isCaptured = count > 0
    val baseColor = Color(level.toArgb())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    colors = if (isCaptured)
                        listOf(baseColor.copy(alpha = 0.9f), baseColor.copy(alpha = 0.5f))
                    else
                        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
                )
            )
            .then(if (monsterCount > 0) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(level.toEmoji(), fontSize = 30.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                level.displayName(),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isCaptured) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                level.rarity() + " ・ ${monsterCount}匹" + if (monsterCount > 0) " ・ タップで一覧" else "",
                fontSize = 11.sp,
                fontWeight = if (monsterCount > 0) FontWeight.Bold else FontWeight.Normal,
                color = if (isCaptured) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MonsterListSheet(
    level: SignalLevel,
    entries: List<Pair<Monster, String>>,
    partnerKey: String?,
    onTraining: (Monster) -> Unit,
    onSetPartner: (Monster) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            "${level.toEmoji()} ${level.displayName()} 一覧",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            "タップで 🥊 パートナーと特訓 ／ ⭐でパートナー変更",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.heightIn(max = 480.dp)
        ) {
            items(entries) { (monster, capturedAt) ->
                MonsterGridCell(
                    monster = monster,
                    capturedAt = capturedAt,
                    isPartner = monster.cellId == partnerKey,
                    onClick = { onTraining(monster) },
                    onSetPartner = { onSetPartner(monster) }
                )
            }
        }
    }
}

private val capturedAtFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
    .withZone(ZoneId.systemDefault())

/** 2列グリッド用のモンスターセル。タップで特訓、⭐ボタンでパートナー設定。 */
@Composable
private fun MonsterGridCell(
    monster: Monster,
    capturedAt: String,
    isPartner: Boolean,
    onClick: () -> Unit,
    onSetPartner: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        border = if (isPartner) androidx.compose.foundation.BorderStroke(
            2.dp, MaterialTheme.colorScheme.primary
        ) else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Lv.${monster.level}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    if (isPartner) "⭐" else "☆",
                    fontSize = 16.sp,
                    modifier = Modifier.clickable(onClick = onSetPartner)
                )
            }
            Text(monster.emoji, fontSize = 40.sp)
            Text(
                monster.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2
            )
            Text(
                "★".repeat(monster.signalLevel.starCount()),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            val dateStr = runCatching {
                capturedAtFormatter.format(Instant.parse(capturedAt))
            }.getOrDefault(capturedAt)
            Text(dateStr, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuestCard(quest: QuestEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (quest.achieved)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${if (quest.achieved) "✅" else "🎯"} ${quest.title}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "${quest.current} / ${quest.target}",
                    fontSize = 13.sp,
                    color = if (quest.achieved) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(quest.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!quest.achieved) {
                LinearProgressIndicator(
                    progress = { quest.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun SignalLevel.displayName(): String =
    category?.displayName ?: when (this) {
        SignalLevel.AIRPLANE_MODE -> "機内モードの幽霊"
        SignalLevel.NO_SIM        -> "SIMなしの亡者"
        else                      -> "" // category は AIRPLANE_MODE/NO_SIM 以外は非null
    }

private fun SignalLevel.rarity(): String = category?.rarityLabel ?: ""

private fun SignalLevel.starCount(): Int = category?.starCount ?: 1

private fun SignalLevel.toEmoji(): String = category?.emoji ?: "？"

private fun SignalLevel.toArgb(): Long = category?.argbColor ?: 0xFF9E9E9EL
