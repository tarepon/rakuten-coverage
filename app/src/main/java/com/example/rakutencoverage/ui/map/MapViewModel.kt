package com.example.rakutencoverage.ui.map

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rakutencoverage.data.AppDatabase
import com.example.rakutencoverage.data.CollectionRecord
import com.example.rakutencoverage.data.Measurement
import com.example.rakutencoverage.data.SignalLevel
import com.example.rakutencoverage.data.Spot
import com.example.rakutencoverage.data.SpotRepository
import com.example.rakutencoverage.data.SpotType
import com.example.rakutencoverage.data.isCollectable
import com.example.rakutencoverage.data.latLngToCellId
import com.example.rakutencoverage.data.monster.Monster
import com.example.rakutencoverage.data.monster.MonsterGenerator
import com.example.rakutencoverage.data.PartnerStore
import com.example.rakutencoverage.data.monster.baseCatchRate
import kotlin.random.Random
import com.example.rakutencoverage.measurement.MeasurementController
import com.example.rakutencoverage.measurement.MeasurementService
import com.example.rakutencoverage.measurement.NetworkInfoCollector
import com.example.rakutencoverage.ui.character.CharacterState
import com.example.rakutencoverage.ui.character.idleCharacterState
import com.example.rakutencoverage.ui.character.toCharacterState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * GPS 計測インターバルの選択肢。
 * 5s 以下は GPS が常時 ON になりバッテリー消費が大きいため設けていない。
 * 表示更新（キャラ・回線種別）は displayJob が 3s 周期で別途行う。
 */
enum class MeasureInterval(val seconds: Int, val label: String) {
    FAST(5, "5s"),
    NORMAL(10, "10s"),
    ECO(20, "20s")
}

/**
 * マップ画面の状態管理 ViewModel。
 * - GPS 計測ループそのものは MeasurementService (フォアグラウンドサービス) が担い、
 *   VM/Activity が破棄されてもバックグラウンドで計測を継続する。
 * - 最新計測の購読 (observeLatestMeasurement): MeasurementController.latestMeasurement を購読し、
 *   キャラ表示更新・モンスター発見演出(前面 UI のみ)を行う
 * - 表示更新ループ (displayJob): 3s ごとに電波チェックのみ実行し、キャラ表示を更新
 * - スタンプ付与・DB保存・重複排除: MeasurementService 側で完結(measurement/MeasurementService.kt 参照)
 */
class MapViewModel(app: Application) : AndroidViewModel(app) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (app.getSystemService(VibratorManager::class.java) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val db               = AppDatabase.getInstance(app)
    private val measurementDao   = db.measurementDao()
    private val stampDao         = db.stampDao()
    private val collectionDao    = db.collectionDao()
    private val spotRepo         = SpotRepository(app)
    private val networkCollector = NetworkInfoCollector(app)

    /** DB 全件をリアルタイム監視する Flow (UI でマップドット描画に使用) */
    val measurements: StateFlow<List<Measurement>> = measurementDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 信号レベル別・KDDI 別・圏外の件数集計 */
    data class SignalCounts(
        val fiveG: Int     = 0,
        val platinum: Int  = 0,
        val lte: Int       = 0,
        val kddi: Int      = 0,   // au ローミング (Band 18 / Band 26)
        val noSignal: Int  = 0,   // 圏外 (機内モード・SIMなしは含まない)
        val total: Int     = 0
    )

    /**
     * measurements の変化を監視して信号カウントを自動再計算する。
     * Flow.map で同期的に集計するため、中間の MutableStateFlow は不要。
     */
    val signalCounts: StateFlow<SignalCounts> = measurementDao.observeAll()
        .map { list ->
            SignalCounts(
                fiveG  = list.count { it.signalLevel == SignalLevel.FIVE_G },
                platinum   = list.count { it.signalLevel == SignalLevel.PLATINUM },
                lte        = list.count { it.signalLevel == SignalLevel.LTE },
                kddi       = list.count {
                    it.band?.contains("18") == true || it.band?.contains("26") == true
                },
                noSignal   = list.count {
                    it.signalLevel == SignalLevel.NO_SIGNAL
                        || (it.rttMs < 0
                            && it.signalLevel != SignalLevel.AIRPLANE_MODE
                            && it.signalLevel != SignalLevel.NO_SIM)
                },
                total = list.size
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SignalCounts())

    val stamps = stampDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val collectionRecords: StateFlow<List<CollectionRecord>> = collectionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 捕獲済みセルIDのHashSet — contains() が O(1)。
     * UI からは購読されず ViewModel 内部の重複判定にのみ使うため、
     * WhileSubscribed だと購読者不在で上流の DB 監視が始まらず常に空集合のままになる。
     * Eagerly にして ViewModel 生存中は常に最新の捕獲済みセルを反映させる。
     */
    private val capturedCellIds: StateFlow<Set<String>> = collectionDao.observeAllH3Indexes()
        .map { it.toHashSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** 捕獲ミニゲームの進行フェーズ(denpamon-go 移植) */
    enum class CapturePhase { READY, THROWING, MISS, FLED, CAUGHT }

    /** 発見〜捕獲ミニゲームの状態 */
    data class CaptureUi(
        val monster: Monster,
        val phase: CapturePhase = CapturePhase.READY,
        val message: String? = null
    )

    private val _capture = MutableStateFlow<CaptureUi?>(null)
    val capture: StateFlow<CaptureUi?> = _capture

    private val _capturedMonster = MutableStateFlow<Monster?>(null)
    val capturedMonster: StateFlow<Monster?> = _capturedMonster

    /** バックグラウンド計測サービスと共有する状態(Controller)をそのまま公開する */
    val autoCapture: StateFlow<Boolean> = MeasurementController.autoCapture

    fun setAutoCapture(enabled: Boolean) { MeasurementController.autoCapture.value = enabled }

    /**
     * 自分の計測データから作る「面」表示（カバレッジエリア風オーバーレイ）の表示切り替え。
     * 楽天モバイル公式のエリアマップとは無関係 — あくまで自分が実測した範囲を可視化するもの。
     */
    private val _showCoverageArea = MutableStateFlow(true)
    val showCoverageArea: StateFlow<Boolean> = _showCoverageArea

    fun setShowCoverageArea(enabled: Boolean) { _showCoverageArea.value = enabled }

    private var currentCellId: String? = null

    /** このセッション中に逃げられた/見送ったセル(再遭遇させない) */
    private val dismissedCellIds = mutableSetOf<String>()

    /**
     * observeLatest() の最初の1件を処理済みか。
     * VM 再生成直後(アプリ再起動・画面回転後の再購読等)に届く「現在の最新計測」は、
     * バックグラウンド滞在中に既に一度見た(あるいは見送った)可能性があるデータなので、
     * それをその場で「新規発見」として扱わないための同期専用フラグ。
     */
    private var hasSyncedInitialMeasurement = false

    private val _character       = MutableStateFlow(idleCharacterState())
    val character: StateFlow<CharacterState> = _character

    /** 実際の起動/停止は MeasurementService が管理する。VM は購読のみ */
    val isRunning: StateFlow<Boolean> = MeasurementController.isRunning

    private val _lastMeasurement = MutableStateFlow<Measurement?>(null)
    val lastMeasurement: StateFlow<Measurement?> = _lastMeasurement

    private val _isFollowing     = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing

    /** バックグラウンド計測サービスと共有する状態(Controller)をそのまま公開する */
    val checkIn: StateFlow<ArenaModeInput?> = MeasurementController.checkIn

    private val _selectedInterval = MutableStateFlow(MeasureInterval.FAST)
    val selectedInterval: StateFlow<MeasureInterval> = _selectedInterval

    private val _spotsByType     = MutableStateFlow<Map<SpotType, List<Spot>>>(emptyMap())
    val spotsByType: StateFlow<Map<SpotType, List<Spot>>> = _spotsByType

    private var displayJob: Job? = null

    init {
        viewModelScope.launch {
            _spotsByType.value = SpotType.entries.associateWith { spotRepo.loadSpots(it) }
        }
        startDisplayUpdates()
        observeLatestMeasurement()
    }

    /**
     * MeasurementService がこのプロセスで実行した最新計測を購読する。
     * GPS 計測ループそのもの(収集・重複排除・DB保存・スタンプ判定)はサービス側に移り、
     * VM はここで結果を受け取ってキャラ表示更新とモンスター発見演出(前面 UI のみ)を行う。
     * DB の最新行 (旧 observeLatest) ではなくメモリ上の MeasurementController を購読する —
     * バックアップ復元・インポートで挿入された過去レコードを「現在の電波状態」として
     * 表示しないため(実測するまで StatusPill は表示されない)。
     */
    private fun observeLatestMeasurement() {
        viewModelScope.launch {
            MeasurementController.latestMeasurement.collect { result ->
                if (result != null) onNewMeasurement(result)
            }
        }
    }

    /**
     * GPS を使わずに電波種別だけを 3s ごとチェックし、キャラ表示を更新する。
     * バッテリーへの影響は軽微 (TelephonyManager API のみ)。
     * バックグラウンド計測ループ (MeasurementService) とは独立して常時動作する。
     */
    private fun startDisplayUpdates() {
        displayJob?.cancel()
        displayJob = viewModelScope.launch {
            while (true) {
                val snap  = networkCollector.collect()
                val level = when (snap.networkType) {
                    "AIRPLANE_MODE"          -> SignalLevel.AIRPLANE_MODE
                    "NO_SIM"                 -> SignalLevel.NO_SIM
                    "NO_SERVICE", "UNKNOWN"  -> SignalLevel.NO_SIGNAL
                    "5G"                     -> SignalLevel.FIVE_G
                    "LTE"                    -> SignalLevel.LTE
                    else                     -> SignalLevel.WEAK
                }
                if (level != _character.value.level) {
                    _character.value = level.toCharacterState()
                }
                delay(3000L)
            }
        }
    }

    /** マップの現在地追従を開始する */
    fun startFollowing() { _isFollowing.value = true }

    /** マップの現在地追従を停止する */
    fun stopFollowing()  { _isFollowing.value = false }

    /** チェックイン情報を更新する。null を渡すとチェックアウト */
    fun setCheckIn(input: ArenaModeInput?) { MeasurementController.checkIn.value = input }

    /**
     * 計測インターバルを変更する。
     * サービス側のループは再起動しない — 次の delay() で新しい値を自動的に読む。
     * 再起動すると旧ループの最終計測と新ループの先頭計測が重複するため。
     * @param interval 新しいインターバル設定
     */
    fun setInterval(interval: MeasureInterval) {
        _selectedInterval.value = interval
        MeasurementController.intervalMs.value = interval.seconds * 1000L
    }

    /** 計測中なら停止、停止中なら開始するトグル関数 */
    fun toggleMeasurement() {
        if (MeasurementController.isRunning.value) stopMeasurement() else startMeasurement()
    }

    fun startMeasurementIfStopped() {
        if (!MeasurementController.isRunning.value) startMeasurement()
    }

    /**
     * バックグラウンド計測サービスの起動を要求する。
     * 実際に isRunning が true になるのはサービスが startForeground() に成功してから
     * (MeasurementController.isRunning を購読しているため反映は非同期)。
     */
    private fun startMeasurement() {
        MeasurementService.start(getApplication())
    }

    /**
     * バックグラウンド計測サービスの停止を要求する。
     * onCleared() からは呼ばない — FGS 化の目的である「VM/Activity が破棄されても計測を続ける」を
     * 満たすため、明示的な停止操作(トグルボタン等)からのみ呼ばれる。
     */
    fun stopMeasurement() {
        MeasurementService.stop(getApplication())
    }

    /**
     * MeasurementService が保存した最新計測の到着ごとに呼ばれる。
     * DB への計測保存・重複排除・スタンプ判定は MeasurementService 側で完結しているため、
     * ここではキャラ表示の更新とモンスター発見判定(前面 UI のみの演出)のみを行う。
     */
    private fun onNewMeasurement(result: Measurement) {
        _lastMeasurement.value = result
        if (result.signalLevel != _character.value.level) {
            _character.value = result.signalLevel.toCharacterState()
        }

        // 基地局IDを優先。NO_SIGNALは基地局がないのでGPSグリッド（222m）で代替
        val cellId: String = when {
            result.cellId != null                          -> result.cellId
            result.signalLevel == SignalLevel.NO_SIGNAL    -> latLngToCellId(result.latitude, result.longitude, result.signalLevel)
            else                                           -> return
        }

        if (!hasSyncedInitialMeasurement) {
            // VM再生成直後に届く「現在の最新計測」は、バックグラウンド滞在中に既に見た/見送った
            // 可能性があるため、ここでは currentCellId の同期のみ行い発見演出はしない
            hasSyncedInitialMeasurement = true
            currentCellId = cellId
            return
        }

        if (cellId == currentCellId) return
        currentCellId = cellId

        // ミニゲーム進行中は新しい遭遇で上書きしない(denpamon-go 踏襲)
        if (_capture.value != null) return

        if (result.signalLevel.isCollectable &&
            cellId !in capturedCellIds.value &&
            cellId !in dismissedCellIds
        ) {
            val monster = MonsterGenerator.generate(cellId, 1, result.signalLevel)
            if (MeasurementController.autoCapture.value) {
                // コレクションDBへの登録は MeasurementService が既に行っている。ここではカード表示のみ
                showAutoCaptureCard(monster)
            } else {
                _capturedMonster.value = null
                _capture.value = CaptureUi(monster)
                vibrateShort()
            }
        }
    }

    private fun vibrateShort() {
        // ト（200ms）・トン（800ms）を5サイクル、サイクル間1750ms
        // [off, on, off, on, off(gap)] × 5
        val pattern = longArrayOf(
            0, 200, 200, 800,
            1750, 200, 200, 800,
            1750, 200, 200, 800
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    /** 捕獲結果カードをタップで即閉じる */
    fun dismissCapturedMonster() { _capturedMonster.value = null }

    /**
     * 自動捕獲モード：発見通知を省略しカードを4秒後に自動的に消す。
     * コレクションDBへの登録・パートナー設定は MeasurementService が(裏で)既に行っているため、
     * ここでは行わない(二重登録防止)。
     */
    private fun showAutoCaptureCard(monster: Monster) {
        viewModelScope.launch {
            _capturedMonster.value = monster
            delay(4000L)
            if (_capturedMonster.value == monster) _capturedMonster.value = null
        }
    }

    /** 最初の1匹を自動でパートナーにする(denpamon-go 踏襲) */
    private fun ensurePartner(cellId: String) {
        val app = getApplication<Application>()
        if (PartnerStore.get(app) == null) PartnerStore.set(app, cellId)
    }

    /* ---------- 捕獲ミニゲーム(denpamon-go 移植) ---------- */

    /**
     * ボールを投げる。timingAccuracy は 0.0(最悪)〜1.0(ジャスト)。
     * UI側の収縮リング判定から渡される。
     * 捕獲率 = レア度基本率 + タイミングボーナス(最大+25%)、上限95%。
     */
    fun throwBall(timingAccuracy: Float) {
        val e = _capture.value ?: return
        if (e.phase == CapturePhase.THROWING || e.phase == CapturePhase.CAUGHT) return
        vibrator.cancel()  // 発見通知の振動を止める

        _capture.value = e.copy(phase = CapturePhase.THROWING, message = null)

        viewModelScope.launch {
            delay(650)
            val cur = _capture.value ?: return@launch
            val prob = (baseCatchRate(cur.monster.signalLevel) +
                    timingAccuracy * 0.25).coerceAtMost(0.95)
            if (Random.nextDouble() < prob) {
                registerCatch(cur.monster)
                _capture.value = cur.copy(
                    phase = CapturePhase.CAUGHT,
                    message = "🎉 ${cur.monster.name} を捕まえた!"
                )
            } else if (Random.nextDouble() < 0.25) {
                dismissedCellIds.add(cur.monster.cellId)
                _capture.value = cur.copy(
                    phase = CapturePhase.FLED,
                    message = "💨 ${cur.monster.name} は逃げてしまった…"
                )
            } else {
                _capture.value = cur.copy(
                    phase = CapturePhase.MISS,
                    message = "ああっ! 出てきてしまった!"
                )
                delay(900)
                val c = _capture.value
                if (c != null && c.phase == CapturePhase.MISS) {
                    _capture.value = c.copy(phase = CapturePhase.READY, message = null)
                }
            }
        }
    }

    /** ミニゲームを閉じる(にげる/結果確認後) */
    fun dismissCapture() {
        vibrator.cancel()
        _capture.value?.let {
            if (it.phase == CapturePhase.READY || it.phase == CapturePhase.MISS) {
                dismissedCellIds.add(it.monster.cellId)
            }
        }
        _capture.value = null
    }

    private fun registerCatch(monster: Monster) {
        val loc = _lastMeasurement.value
        viewModelScope.launch {
            collectionDao.upsert(
                CollectionRecord(
                    h3Index     = monster.cellId,
                    signalLevel = monster.signalLevel.name,
                    latitude    = loc?.latitude ?: 0.0,
                    longitude   = loc?.longitude ?: 0.0,
                    capturedAt  = Instant.now().toString(),
                    level       = monster.level
                )
            )
            ensurePartner(monster.cellId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // MeasurementService は停止しない — VM/Activity が破棄されてもバックグラウンド計測を継続するため
        displayJob?.cancel()
    }
}
