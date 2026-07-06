package com.example.rakutencoverage.ui.collection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rakutencoverage.data.AppDatabase
import com.example.rakutencoverage.data.CollectionRecord
import com.example.rakutencoverage.data.SignalLevel
import com.example.rakutencoverage.data.PartnerStore
import com.example.rakutencoverage.data.monster.BattleEngine
import com.example.rakutencoverage.data.monster.BattleMove
import com.example.rakutencoverage.data.monster.Combatant
import com.example.rakutencoverage.data.monster.Monster
import com.example.rakutencoverage.data.monster.MonsterGenerator
import com.example.rakutencoverage.data.monster.winXp
import com.example.rakutencoverage.data.monster.xpNeeded
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QuestEntry(
    val title: String,
    val description: String,
    val current: Int,
    val target: Int
) {
    val achieved: Boolean get() = current >= target
    val progress: Float get() = (current.toFloat() / target).coerceIn(0f, 1f)
}

class CollectionViewModel(app: Application) : AndroidViewModel(app) {

    private val db              = AppDatabase.getInstance(app)
    private val collectionDao   = db.collectionDao()

    /** 捕獲済み collection_records から SignalLevel → (Monster, capturedAt) 一覧を生成 */
    val monstersByLevel = collectionDao.observeAll().map { records ->
        records.mapNotNull { record ->
            val level = runCatching { SignalLevel.valueOf(record.signalLevel) }.getOrNull()
                ?: return@mapNotNull null
            val monster = MonsterGenerator.generate(record.h3Index, 1, level)
                .copy(level = record.level, xp = record.xp)  // 成長分はDBの値で上書き
            level to Pair(monster, record.capturedAt)
        }.groupBy({ it.first }, { it.second })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val records = collectionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 光図鑑用レベル一覧 */
    val lightLevels = listOf(
        SignalLevel.MILLIMETER_WAVE,
        SignalLevel.PLATINUM,
        SignalLevel.FIVE_G,
        SignalLevel.LTE
    )

    /** 闇図鑑用レベル一覧 */
    val darkLevels = listOf(
        SignalLevel.WEAK,
        SignalLevel.NO_SIGNAL
    )

    /** SignalLevelごとの捕獲数 */
    val capturedCountByLevel = collectionDao.observeAll()
        .map { list ->
            list.groupBy { record ->
                runCatching { SignalLevel.valueOf(record.signalLevel) }.getOrNull()
            }.mapValues { it.value.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 圏外（NO_SIGNAL）捕獲数 */
    val noSignalCount = collectionDao.observeNoSignalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 圏外クエスト一覧 */
    val noSignalQuests = noSignalCount.map { count ->
        listOf(
            QuestEntry("圏外ハンター入門", "圏外を初めて記録せよ", count, 1),
            QuestEntry("圏外5箇所制覇", "異なる5セルで圏外を記録せよ", count, 5),
            QuestEntry("圏外20箇所制覇", "異なる20セルで圏外を記録せよ", count, 20),
            QuestEntry("圏外50箇所制覇", "異なる50セルで圏外を記録せよ", count, 50),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /* ---------- パートナー ---------- */

    private val _partnerKey = MutableStateFlow(PartnerStore.get(app))
    val partnerKey: StateFlow<String?> = _partnerKey

    fun setPartner(cellId: String) {
        PartnerStore.set(getApplication(), cellId)
        _partnerKey.value = cellId
    }

    /** パートナーモンスター(未設定なら最初の1匹) */
    private fun partnerMonster(): Monster? {
        val all = monstersByLevel.value.values.flatten().map { it.first }
        return all.firstOrNull { it.cellId == _partnerKey.value } ?: all.firstOrNull()
    }

    /* ---------- トレーニングバトル(denpamon-go 移植) ---------- */

    /** トレーニングバトルの状態 */
    data class TrainingBattleUi(
        val me: Combatant,
        val foe: Combatant,
        val log: List<String>,
        val busy: Boolean = false,
        val result: String? = null   // "win" / "lose" / null(進行中)
    )

    private val _battle = MutableStateFlow<TrainingBattleUi?>(null)
    val battle: StateFlow<TrainingBattleUi?> = _battle

    /**
     * トレーニングバトルを開始する(denpamon-go 踏襲)。
     * パートナーが自分の戦士、タップしたモンスターが相手。
     * 相手のレベルは自分の±2に調整される(スパーリングなので公平に)。
     */
    fun startTraining(opponent: Monster) {
        val mine = partnerMonster() ?: return
        val foe = opponent.copy(
            level = (mine.level + kotlin.random.Random.nextInt(-2, 3)).coerceAtLeast(2)
        )
        _battle.value = TrainingBattleUi(
            me = Combatant(mine),
            foe = Combatant(foe),
            log = listOf(
                if (mine.cellId == foe.cellId) "分身の ${foe.name} とのトレーニング開始!"
                else "${foe.name} とのトレーニング開始!"
            )
        )
    }

    /** プレイヤーが技を選んで攻撃 → 敵の反撃までを1ターンとして進行する */
    fun playerMove(move: BattleMove) {
        val b = _battle.value ?: return
        if (b.busy || b.result != null) return
        _battle.value = b.copy(busy = true)

        viewModelScope.launch {
            var cur = _battle.value ?: return@launch

            // 自分の攻撃(HP はイミュータブル更新 — 再描画のため参照を変える)
            val myHit = BattleEngine.calcDamage(cur.me.monster, cur.foe.monster, move)
            cur = cur.copy(
                foe = cur.foe.hit(myHit.damage),
                log = cur.log + attackLine(cur.me, move.name, myHit.damage, myHit.multiplier)
            )
            _battle.value = cur
            delay(800)

            if (cur.foe.isFainted) {
                val xp = winXp(cur.foe.monster.level)
                var log = cur.log + "${cur.foe.monster.name} はたおれた!" +
                        "🎉 勝利! ${cur.me.monster.name} は $xp XP を獲得!"
                gainXp(cur.me.monster.cellId, xp)?.let { newLevel ->
                    log = log + "⬆️ ${cur.me.monster.name} は Lv.$newLevel に上がった!"
                }
                _battle.value = cur.copy(log = log, busy = false, result = "win")
                return@launch
            }

            // 敵の攻撃
            val foeMove = BattleEngine.pickEnemyMove(cur.foe.monster)
            val foeHit = BattleEngine.calcDamage(cur.foe.monster, cur.me.monster, foeMove)
            cur = cur.copy(
                me = cur.me.hit(foeHit.damage),
                log = cur.log + attackLine(cur.foe, foeMove.name, foeHit.damage, foeHit.multiplier)
            )
            _battle.value = cur
            delay(800)

            if (cur.me.isFainted) {
                _battle.value = cur.copy(
                    log = cur.log + "${cur.me.monster.name} はたおれた…" + "負けてしまった… また挑もう!",
                    busy = false, result = "lose"
                )
            } else {
                _battle.value = cur.copy(busy = false)
            }
        }
    }

    /**
     * XP を付与してレベルアップ処理を行い、DB に永続化する。
     * @return レベルが上がった場合は新しいレベル、変わらなければ null
     */
    private suspend fun gainXp(cellId: String, xp: Int): Int? {
        val record = collectionDao.findByH3Index(cellId) ?: return null
        var level = record.level
        var rest = record.xp + xp
        var ups = 0
        while (rest >= xpNeeded(level)) {
            rest -= xpNeeded(level)
            level++
            ups++
        }
        collectionDao.upsert(record.copy(level = level, xp = rest))
        return if (ups > 0) level else null
    }

    private fun attackLine(attacker: Combatant, moveName: String, damage: Int, multiplier: Double): String {
        val eff = when {
            multiplier > 1.3  -> " 効果はバツグンだ!"
            multiplier > 1.0  -> " ちからが暴走している!"
            multiplier < 1.0  -> " 効果はいまひとつ…"
            else              -> ""
        }
        return "${attacker.monster.name} の $moveName! $damage ダメージ$eff"
    }

    fun closeBattle() {
        val b = _battle.value ?: return
        if (b.busy) return
        _battle.value = null
    }
}
