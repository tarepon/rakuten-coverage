package com.example.rakutencoverage.ui.collection

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rakutencoverage.data.monster.Combatant
import com.example.rakutencoverage.data.monster.battleMaxHp
import com.example.rakutencoverage.data.monster.battleMoves

/**
 * トレーニングバトル画面(denpamon-go の BattleScreen 移植)。
 * ドラクエ風: 黒地 + 白枠ウィンドウ。全画面オーバーレイで表示する。
 * 縦画面: 対戦者→ログ→技ボタンの縦積み。
 * 横画面: 高さが足りず技ボタンが画面外に押し出されるため、
 * 左ペイン(対戦者+ログ)・右ペイン(技ボタン)の2ペイン構成に切り替える。
 */
@Composable
fun TrainingBattleScreen(b: CollectionViewModel.TrainingBattleUi, vm: CollectionViewModel) {
    val logState = rememberLazyListState()
    LaunchedEffect(b.log.size) {
        if (b.log.isNotEmpty()) logState.animateScrollToItem(b.log.size - 1)
    }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val backgroundModifier = Modifier
        .fillMaxSize()
        .background(
            Brush.verticalGradient(
                listOf(Color(0xFF000814), Color(0xFF001030), Color(0xFF000814))
            )
        )
        // 背後の図鑑へのタッチを遮断
        .clickable(enabled = false) {}
        .padding(16.dp)

    if (isLandscape) {
        Row(backgroundModifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1.3f).fillMaxHeight()) {
                CombatantRow(b.foe, mine = false, compact = true)
                Spacer(Modifier.height(6.dp))
                CombatantRow(b.me, mine = true, compact = true)
                BattleLogWindow(
                    log = b.log,
                    state = logState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)
                )
            }
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                BattleActions(b, vm)
            }
        }
    } else {
        Column(backgroundModifier) {
            Spacer(Modifier.height(24.dp))
            CombatantRow(b.foe, mine = false)
            Spacer(Modifier.height(8.dp))
            CombatantRow(b.me, mine = true)
            BattleLogWindow(
                log = b.log,
                state = logState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp)
            )
            BattleActions(b, vm)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** バトルログ(メッセージウィンドウ)。白枠+黒地のDQ風ウィンドウ */
@Composable
private fun BattleLogWindow(log: List<String>, state: LazyListState, modifier: Modifier = Modifier) {
    LazyColumn(
        state = state,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xEE000814))
            .border(3.dp, Color.White, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        items(log) { line ->
            Text(
                "▶ $line", color = Color.White, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }
    }
}

/** 技ボタン(2×2)+やめる、または決着後のとじるボタン */
@Composable
private fun BattleActions(b: CollectionViewModel.TrainingBattleUi, vm: CollectionViewModel) {
    if (b.result != null) {
        Button(
            onClick = { vm.closeBattle() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                if (b.result == "win") "🎉 とじる" else "とじる",
                fontSize = 17.sp, fontWeight = FontWeight.Bold
            )
        }
    } else {
        val moves = b.me.monster.battleMoves()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                moves.take(2).forEach { move ->
                    MoveButton(move.name, move.power, enabled = !b.busy, modifier = Modifier.weight(1f)) {
                        vm.playerMove(move)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                moves.drop(2).forEach { move ->
                    MoveButton(move.name, move.power, enabled = !b.busy, modifier = Modifier.weight(1f)) {
                        vm.playerMove(move)
                    }
                }
                OutlinedButton(
                    onClick = { vm.closeBattle() },
                    enabled = !b.busy,
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("やめる", color = Color.White) }
            }
        }
    }
}

@Composable
private fun MoveButton(
    name: String, power: Int,
    enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("威力$power", fontSize = 10.sp)
        }
    }
}

/**
 * 対戦者1体分の表示行(絵文字+名前/Lv/HPバー)。
 * compact=true は横画面用で、絵文字と余白を縮めて縦方向の占有を抑える。
 */
@Composable
private fun CombatantRow(c: Combatant, mine: Boolean, compact: Boolean = false) {
    val emojiSize = if (compact) 38.sp else 56.sp
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!mine) Text(c.monster.emoji, fontSize = emojiSize)
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xEE000814))
                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                .padding(if (compact) 7.dp else 10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    c.monster.name, color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
                Text("Lv.${c.monster.level}", color = Color(0xFF9A9AB0), fontSize = 12.sp)
            }
            Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
            val maxHp = c.monster.battleMaxHp
            val pct = (c.curHp.coerceAtLeast(0).toFloat() / maxHp).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { pct },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = when {
                    pct < 0.25f -> Color(0xFFFF5B5B)
                    pct < 0.55f -> Color(0xFFFFCC33)
                    else -> Color(0xFF3ECF6A)
                },
                trackColor = Color(0xFF333333)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "${c.curHp.coerceAtLeast(0)} / $maxHp", color = Color(0xFF9A9AB0),
                fontSize = 10.sp, modifier = Modifier.align(Alignment.End)
            )
        }
        if (mine) Text(c.monster.emoji, fontSize = emojiSize)
    }
}
