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
import com.example.rakutencoverage.data.StampRecord
import com.example.rakutencoverage.data.isCollectable
import com.example.rakutencoverage.data.latLngToCellId
import com.example.rakutencoverage.data.monster.Monster
import com.example.rakutencoverage.data.monster.MonsterGenerator
import com.example.rakutencoverage.data.PartnerStore
import com.example.rakutencoverage.data.monster.baseCatchRate
import kotlin.random.Random
import com.example.rakutencoverage.measurement.MeasurementSession
import com.example.rakutencoverage.measurement.NetworkInfoCollector
import com.example.rakutencoverage.ui.character.CharacterState
import com.example.rakutencoverage.ui.character.idleCharacterState
import com.example.rakutencoverage.ui.character.toCharacterState
import android.location.Location
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
 * - GPS 計測ループ (measureJob): 選択インターバルごとに GPS + 電波 + RTT を DB 保存
 * - 表示更新ループ (displayJob): 3s ごとに電波チェックのみ実行し、キャラ表示を更新
 * - スタンプ付与: チェックイン中かつ有効な計測時のみ stampDao に記録
 */
class MapViewModel(app: Application) : AndroidViewModel(app) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (app.getSystemService(VibratorManager::class.java) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val session          = MeasurementSession(app)
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
        val mmWave: Int    = 0,   // MILLIMETER_WAVE (n257)
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
                mmWave = list.count { it.signalLevel == SignalLevel.MILLIMETER_WAVE },
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

    private val _autoCapture = MutableStateFlow(false)
    val autoCapture: StateFlow<Boolean> = _autoCapture

    fun setAutoCapture(enabled: Boolean) { _autoCapture.value = enabled }

    private var currentCellId: String? = null

    /** このセッション中に逃げられた/見送ったセル(再遭遇させない) */
    private val dismissedCellIds = mutableSetOf<String>()

    private val _character       = MutableStateFlow(idleCharacterState())
    val character: StateFlow<CharacterState> = _character

    private val _isRunning       = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _lastMeasurement = MutableStateFlow<Measurement?>(null)
    val lastMeasurement: StateFlow<Measurement?> = _lastMeasurement

    private val _isFollowing     = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing

    private val _checkIn         = MutableStateFlow<ArenaModeInput?>(null)
    val checkIn: StateFlow<ArenaModeInput?> = _checkIn

    private val _selectedInterval = MutableStateFlow(MeasureInterval.FAST)
    val selectedInterval: StateFlow<MeasureInterval> = _selectedInterval

    private val _spotsByType     = MutableStateFlow<Map<SpotType, List<Spot>>>(emptyMap())
    val spotsByType: StateFlow<Map<SpotType, List<Spot>>> = _spotsByType

    private var measureJob: Job? = null
    private var displayJob: Job? = null

    init {
        viewModelScope.launch {
            _spotsByType.value = SpotType.entries.associateWith { spotRepo.loadSpots(it) }
        }
        startDisplayUpdates()
    }

    /**
     * GPS を使わずに電波種別だけを 3s ごとチェックし、キャラ表示を更新する。
     * バッテリーへの影響は軽微 (TelephonyManager API のみ)。
     * GPS 計測ループ (measureJob) とは独立して常時動作する。
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
    fun setCheckIn(input: ArenaModeInput?) { _checkIn.value = input }

    /**
     * 計測インターバルを変更する。
     * ループは再起動しない — 次の delay() で新しい値を自動的に読む。
     * 再起動すると旧ループの最終計測と新ループの先頭計測が重複するため。
     * @param interval 新しいインターバル設定
     */
    fun setInterval(interval: MeasureInterval) {
        _selectedInterval.value = interval
    }

    /** 計測中なら停止、停止中なら開始するトグル関数 */
    fun toggleMeasurement() {
        if (_isRunning.value) stopMeasurement() else startMeasurement()
    }

    fun startMeasurementIfStopped() {
        if (!_isRunning.value) startMeasurement()
    }

    /**
     * GPS 計測ループを開始する。
     * 選択インターバルごとに runSingleMeasurement() を繰り返す。
     * 既存ループがあればキャンセルしてから再起動する (インターバル変更時も同じ経路)。
     */
    private fun startMeasurement() {
        measureJob?.cancel()
        _isRunning.value = true
        measureJob = viewModelScope.launch {
            while (true) {
                runSingleMeasurement()
                delay(_selectedInterval.value.seconds * 1000L)
            }
        }
    }

    /** GPS 計測ループを停止する */
    fun stopMeasurement() {
        measureJob?.cancel()
        measureJob        = null
        _isRunning.value  = false
    }

    /**
     * 1回の計測を実行し、重複でなければ DB に保存して UI ステートを更新する。
     *
     * 処理フロー:
     * 1. MeasurementSession.collect() で GPS + 電波 + RTT を収集 (DB 保存なし)
     * 2. 直前の計測と比較: 同じ場所 (15m 以内) + 同じ networkType + 同じ RSSI → スキップ
     * 3. 重複でなければ ensureActive() で停止チェック後、DB に保存
     * 4. キャラ状態更新・スタンプ付与
     */
    private suspend fun runSingleMeasurement() {
        val ci     = _checkIn.value
        val result = session.collect(
            arenaId   = ci?.spot?.id,
            arenaName = ci?.spot?.name,
            seatLabel = ci?.seatLabel,
            gamePhase = ci?.gamePhase?.name
        ) ?: return

        val last = _lastMeasurement.value
        if (last != null && isDuplicate(result, last)) return

        currentCoroutineContext().ensureActive()
        measurementDao.insert(result)
        _lastMeasurement.value = result

        if (result.signalLevel != _character.value.level) {
            _character.value = result.signalLevel.toCharacterState()
        }
        val isValidForStamp = result.signalLevel != SignalLevel.AIRPLANE_MODE &&
                              result.signalLevel != SignalLevel.NO_SIM
        if (isValidForStamp) ci?.spot?.let { spot ->
            stampDao.achieve(
                StampRecord(
                    spotId     = spot.id,
                    spotType   = spot.type.name,
                    spotName   = spot.name,
                    achievedAt = Instant.now().toString()
                )
            )
        }

        // 基地局IDを優先。NO_SIGNALは基地局がないのでGPSグリッド（222m）で代替
        val cellId: String = when {
            result.cellId != null                          -> result.cellId
            result.signalLevel == SignalLevel.NO_SIGNAL    -> latLngToCellId(result.latitude, result.longitude, result.signalLevel)
            else                                           -> return
        }
        if (cellId != currentCellId) {
            currentCellId = cellId
            // ミニゲーム進行中は新しい遭遇で上書きしない(denpamon-go 踏襲)
            if (_capture.value != null) return
            if (result.signalLevel.isCollectable &&
                cellId !in capturedCellIds.value &&
                cellId !in dismissedCellIds
            ) {
                val monster = MonsterGenerator.generate(cellId, 1, result.signalLevel)
                if (_autoCapture.value) {
                    autoCapture(monster, result)
                } else {
                    _capturedMonster.value = null
                    _capture.value = CaptureUi(monster)
                    vibrateShort()
                }
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

    /** 自動捕獲モード：発見通知を省略し即座にDB保存・4秒後に通知消去 */
    private fun autoCapture(monster: Monster, loc: Measurement) {
        viewModelScope.launch {
            collectionDao.upsert(
                CollectionRecord(
                    h3Index     = monster.cellId,
                    signalLevel = monster.signalLevel.name,
                    latitude    = loc.latitude,
                    longitude   = loc.longitude,
                    capturedAt  = Instant.now().toString(),
                    level       = monster.level
                )
            )
            ensurePartner(monster.cellId)
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

    /**
     * 2つの計測が「実質同じ」かを判定する。
     * 同じ場所 (10m 以内) + 同じ networkType + RSSI が ±5 dBm 以内 → 重複とみなす。
     * RSSI は常時数 dBm 揺れるため完全一致ではなく許容幅を持たせる。
     * @return true なら重複 → 保存スキップ
     */
    private fun isDuplicate(a: Measurement, b: Measurement): Boolean {
        if (a.networkType != b.networkType) return false
        val rssiDiff = when {
            a.rssi == null && b.rssi == null -> 0
            a.rssi == null || b.rssi == null -> Int.MAX_VALUE
            else                             -> abs(a.rssi - b.rssi)
        }
        if (rssiDiff > 5) return false
        val results = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, results)
        return results[0] < 10f
    }

    override fun onCleared() {
        super.onCleared()
        stopMeasurement()
        displayJob?.cancel()
        session.release()
    }
}
