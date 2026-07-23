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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rakutencoverage.data.monster.Combatant
import com.example.rakutencoverage.data.monster.battleMaxHp
import com.example.rakutencoverage.data.monster.battleMoves

/** 自分側カードの枠色。相手(白枠)と一目で区別できるようにする */
private val MineBorderColor = Color(0xFF4FC3F7)

/**
 * トレーニングバトル画面(denpamon-go の BattleScreen 移植)。
 * ドラクエ風: 黒地 + 白枠ウィンドウ。全画面オーバーレイで表示する。
 *
 * バトルログ(メッセージウィンドウ)が常に十分な高さを確保できるよう、
 * 固定要素は縦方向に最小限しか占有しない構成にする:
 * - 縦画面: ステータスカード2枚を縦積み → ログ(残り全高) → 技ボタン2段
 * - 横画面: ステータスカード2枚を左右並列(約70dp) → ログ(残り全高) → 全ボタンを1行
 *   (旧デザインはカード縦積みが高さを食い、ログが押し潰されて見えなくなっていた)
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
        Column(backgroundModifier) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CombatantCard(b.foe, mine = false, emojiSize = 20.sp, modifier = Modifier.weight(1f))
                CombatantCard(b.me, mine = true, emojiSize = 20.sp, modifier = Modifier.weight(1f))
            }
            BattleLogWindow(
                log = b.log,
                state = logState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)
            )
            BattleActions(b, vm, singleRow = true, buttonHeight = 52.dp)
        }
    } else {
        Column(backgroundModifier) {
            Spacer(Modifier.height(16.dp))
            CombatantCard(b.foe, mine = false, emojiSize = 34.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            CombatantCard(b.me, mine = true, emojiSize = 34.sp, modifier = Modifier.fillMaxWidth())
            BattleLogWindow(
                log = b.log,
                state = logState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp)
            )
            BattleActions(b, vm, singleRow = false, buttonHeight = 60.dp)
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * 対戦者1体分のステータスカード。
 * 絵文字・名前・Lvを1行に収め、HPバーと「あいて/じぶん」ラベルを添える。
 * 横画面では2枚を左右に並べられるよう、縦方向の占有を最小限にしている。
 */
@Composable
private fun CombatantCard(
    c: Combatant,
    mine: Boolean,
    emojiSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (mine) MineBorderColor else Color.White
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xEE000814))
            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(c.monster.emoji, fontSize = emojiSize)
            Text(
                c.monster.name, color = Color.White,
                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text("Lv.${c.monster.level}", color = Color(0xFF9A9AB0), fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (mine) "じぶん" else "あいて",
                color = borderColor, fontSize = 10.sp, fontWeight = FontWeight.Bold
            )
            Text(
                "${c.curHp.coerceAtLeast(0)} / $maxHp",
                color = Color(0xFF9A9AB0), fontSize = 10.sp
            )
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

/**
 * 操作ボタン群。決着後は「とじる」のみ。
 * singleRow=true(横画面): 全技+やめるを1行に並べ、縦方向の占有を最小にする。
 * singleRow=false(縦画面): 従来どおり2段組み。
 * 技は基本技1+固有技0〜2の最大3つのため、1行でも4ボタンまでしか並ばない。
 */
@Composable
private fun BattleActions(
    b: CollectionViewModel.TrainingBattleUi,
    vm: CollectionViewModel,
    singleRow: Boolean,
    buttonHeight: Dp
) {
    if (b.result != null) {
        Button(
            onClick = { vm.closeBattle() },
            modifier = Modifier.fillMaxWidth().height(buttonHeight),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                if (b.result == "win") "🎉 とじる" else "とじる",
                fontSize = 17.sp, fontWeight = FontWeight.Bold
            )
        }
        return
    }

    val moves = b.me.monster.battleMoves()
    if (singleRow) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            moves.forEach { move ->
                MoveButton(
                    move.name, move.power, enabled = !b.busy,
                    height = buttonHeight, modifier = Modifier.weight(1f)
                ) { vm.playerMove(move) }
            }
            OutlinedButton(
                onClick = { vm.closeBattle() },
                enabled = !b.busy,
                modifier = Modifier.weight(0.7f).height(buttonHeight),
                shape = RoundedCornerShape(12.dp)
            ) { Text("やめる", color = Color.White) }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                moves.take(2).forEach { move ->
                    MoveButton(
                        move.name, move.power, enabled = !b.busy,
                        height = buttonHeight, modifier = Modifier.weight(1f)
                    ) { vm.playerMove(move) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                moves.drop(2).forEach { move ->
                    MoveButton(
                        move.name, move.power, enabled = !b.busy,
                        height = buttonHeight, modifier = Modifier.weight(1f)
                    ) { vm.playerMove(move) }
                }
                OutlinedButton(
                    onClick = { vm.closeBattle() },
                    enabled = !b.busy,
                    modifier = Modifier.weight(1f).height(buttonHeight),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("やめる", color = Color.White) }
            }
        }
    }
}

@Composable
private fun MoveButton(
    name: String, power: Int,
    enabled: Boolean, height: Dp,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("威力$power", fontSize = 10.sp)
        }
    }
}
