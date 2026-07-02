package com.example.rakutencoverage.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

    var selectedLevel by remember { mutableStateOf<SignalLevel?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

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

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
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
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("💀 闇図鑑", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("圏外＝エリア拡張の伸び代を記録せよ", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
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
            }
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
                entries = monstersByLevel[selectedLevel] ?: emptyList()
            )
        }
    }
}

/**
 * 光図鑑・闇図鑑用の大型タイル。160dp角、カテゴリ色のグラデーション背景 + 大きな絵文字。
 * 横スクロールの LazyRow に並べて使用する。
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

    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = if (isCaptured)
                        listOf(baseColor.copy(alpha = 0.9f), baseColor.copy(alpha = 0.5f))
                    else
                        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
                )
            )
            .then(if (monsterCount > 0) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                level.displayName(),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isCaptured) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Text(level.toEmoji(), fontSize = 44.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.weight(1f))
            Text(
                level.rarity(),
                fontSize = 10.sp,
                color = if (isCaptured) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${monsterCount}匹" + if (monsterCount > 0) " ・ タップで一覧" else "",
                fontSize = 11.sp,
                fontWeight = if (monsterCount > 0) FontWeight.Bold else FontWeight.Normal,
                color = if (isCaptured) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonsterListSheet(level: SignalLevel, entries: List<Pair<Monster, String>>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            "${level.toEmoji()} ${level.displayName()} 一覧",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
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
                MonsterGridCell(monster, capturedAt)
            }
        }
    }
}

private val capturedAtFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
    .withZone(ZoneId.systemDefault())

/** 2列グリッド用のモンスターセル。絵文字大 + 名前 + 星 + 捕獲日時。 */
@Composable
private fun MonsterGridCell(monster: Monster, capturedAt: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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

private fun SignalLevel.displayName(): String = when (this) {
    SignalLevel.MILLIMETER_WAVE -> "5Gミリ波モンスター"
    SignalLevel.PLATINUM_5G     -> "プラチナ5Gモンスター"
    SignalLevel.FIVE_G          -> "5G Sub6モンスター"
    SignalLevel.PLATINUM        -> "プラチナモンスター"
    SignalLevel.LTE             -> "LTEモンスター"
    SignalLevel.WEAK            -> "おとぎの国の鬼"
    SignalLevel.NO_SIGNAL       -> "圏外の亡霊"
    SignalLevel.AIRPLANE_MODE   -> "機内モードの幽霊"
    SignalLevel.NO_SIM          -> "SIMなしの亡者"
}

private fun SignalLevel.rarity(): String = when (this) {
    SignalLevel.MILLIMETER_WAVE -> "★★★★★ 伝説"
    SignalLevel.PLATINUM_5G     -> "★★★★☆ レア"
    SignalLevel.FIVE_G          -> "★★★☆☆ アンコモン"
    SignalLevel.PLATINUM        -> "★★★☆☆ アンコモン"
    SignalLevel.LTE             -> "★☆☆☆☆ コモン"
    SignalLevel.WEAK            -> "★★☆☆☆ ローミング中"
    SignalLevel.NO_SIGNAL       -> "★★★☆☆ 闇レア"
    else                        -> ""
}

private fun SignalLevel.starCount(): Int = when (this) {
    SignalLevel.MILLIMETER_WAVE -> 5
    SignalLevel.PLATINUM_5G     -> 4
    SignalLevel.FIVE_G          -> 3
    SignalLevel.PLATINUM        -> 3
    SignalLevel.LTE             -> 1
    SignalLevel.WEAK            -> 2
    SignalLevel.NO_SIGNAL       -> 3
    else                        -> 1
}

private fun SignalLevel.toEmoji(): String = when (this) {
    SignalLevel.MILLIMETER_WAVE -> "👑"
    SignalLevel.PLATINUM_5G     -> "🤩"
    SignalLevel.FIVE_G          -> "😆"
    SignalLevel.PLATINUM        -> "😊"
    SignalLevel.LTE             -> "🙂"
    SignalLevel.WEAK            -> "👹"
    SignalLevel.NO_SIGNAL       -> "💀"
    else                        -> "？"
}

private fun SignalLevel.toArgb(): Long = when (this) {
    SignalLevel.MILLIMETER_WAVE -> 0xFFFF6F00L
    SignalLevel.PLATINUM_5G     -> 0xFFFFD700L
    SignalLevel.FIVE_G          -> 0xFF1E88E5L
    SignalLevel.PLATINUM        -> 0xFFAB47BCL
    SignalLevel.LTE             -> 0xFF43A047L
    SignalLevel.WEAK            -> 0xFF5D4037L
    SignalLevel.NO_SIGNAL       -> 0xFF212121L
    else                        -> 0xFF9E9E9EL
}
