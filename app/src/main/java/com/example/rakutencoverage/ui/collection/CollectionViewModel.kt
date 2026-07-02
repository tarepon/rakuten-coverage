package com.example.rakutencoverage.ui.collection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rakutencoverage.data.AppDatabase
import com.example.rakutencoverage.data.CollectionRecord
import com.example.rakutencoverage.data.SignalLevel
import com.example.rakutencoverage.data.monster.MonsterGenerator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
            level to Pair(MonsterGenerator.generate(record.h3Index, 1, level), record.capturedAt)
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
}
