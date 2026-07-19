package com.example.rakutencoverage.measurement

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.rakutencoverage.MainActivity
import com.example.rakutencoverage.R
import com.example.rakutencoverage.data.AppDatabase
import com.example.rakutencoverage.data.CollectionRecord
import com.example.rakutencoverage.data.Measurement
import com.example.rakutencoverage.data.PartnerStore
import com.example.rakutencoverage.data.SignalLevel
import com.example.rakutencoverage.data.StampRecord
import com.example.rakutencoverage.data.isCollectable
import com.example.rakutencoverage.data.latLngToCellId
import com.example.rakutencoverage.data.monster.MonsterGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs
import android.location.Location

/**
 * バックグラウンドでも計測ループを継続させるためのフォアグラウンドサービス。
 * 計測ロジック自体は MeasurementSession に委譲し、このクラスは
 * ループ制御・重複排除・チェックイン/スタンプ判定・自動捕獲(裏でのDB登録)・通知更新を担う。
 *
 * MapViewModel はこのサービスの START/STOP を Intent で指示するだけで、
 * 計測結果は MeasurementController.latestMeasurement (メモリ上のFlow) 経由で前面UIへ渡す。
 */
class MeasurementService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    private val session by lazy { MeasurementSession(applicationContext) }
    private val db by lazy { AppDatabase.getInstance(applicationContext) }
    private val measurementDao by lazy { db.measurementDao() }
    private val stampDao by lazy { db.stampDao() }
    private val collectionDao by lazy { db.collectionDao() }

    /** 直前の保存済み計測(重複判定用)。サービスの生存期間中のみ保持すればよい */
    private var lastMeasurement: Measurement? = null

    /** 自動捕獲の遭遇判定用(前面 UI の discoveredCellId とは別に独立して追跡する) */
    private var currentCellId: String? = null

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMeasuring()
            ACTION_STOP  -> stopMeasuring()
        }
        return START_STICKY
    }

    private fun startMeasuring() {
        if (loopJob != null) return // 既に起動中なら二重起動しない

        createChannelIfNeeded()
        val initialNotification = buildNotification(todayCount = 0, latest = null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, initialNotification)
        }

        MeasurementController.isRunning.value = true

        loopJob = scope.launch {
            while (true) {
                ensureActive()
                runCatching { runSingleMeasurement() }
                ensureActive()
                delay(MeasurementController.intervalMs.value)
            }
        }
    }

    private fun stopMeasuring() {
        loopJob?.cancel()
        loopJob = null
        MeasurementController.isRunning.value = false
        // minSdk=26 は STOP_FOREGROUND_REMOVE (API 24+) が常に使える
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * 1回の計測を実行し、重複でなければ DB に保存して通知を更新する。
     * MapViewModel.runSingleMeasurement() (旧実装) をサービス側へ移植したもの。
     */
    private suspend fun runSingleMeasurement() {
        val ci = MeasurementController.checkIn.value
        val result = session.collect(
            arenaId   = ci?.spot?.id,
            arenaName = ci?.spot?.name,
            seatLabel = ci?.seatLabel,
            gamePhase = ci?.gamePhase?.name
        ) ?: return

        val last = lastMeasurement
        if (last != null && isDuplicate(result, last)) return

        // 機内モード・SIMなしは前面表示のみ更新し、計測データとしてはDBに保存しない
        if (result.signalLevel == SignalLevel.AIRPLANE_MODE || result.signalLevel == SignalLevel.NO_SIM) {
            lastMeasurement = result
            MeasurementController.latestMeasurement.value = result
            return
        }

        measurementDao.insert(result)
        lastMeasurement = result
        MeasurementController.latestMeasurement.value = result

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

        maybeAutoCapture(result)
        updateNotification(result)
    }

    /**
     * 自動捕獲がONのときだけ、裏で(UI演出なしに)新しいセルをコレクションへ登録する。
     * OFFのときは何もしない — 発見バナー・ミニゲームは前面の MapViewModel が担当する。
     */
    private suspend fun maybeAutoCapture(result: Measurement) {
        if (!MeasurementController.autoCapture.value) return

        val cellId: String = when {
            result.cellId != null                        -> result.cellId
            result.signalLevel == SignalLevel.NO_SIGNAL   -> latLngToCellId(result.latitude, result.longitude, result.signalLevel)
            else                                           -> return
        }
        if (cellId == currentCellId) return
        currentCellId = cellId

        if (!result.signalLevel.isCollectable) return
        if (collectionDao.findByH3Index(cellId) != null) return // 既に捕獲済み

        val monster = MonsterGenerator.generate(cellId, 1, result.signalLevel)
        collectionDao.upsert(
            CollectionRecord(
                h3Index     = monster.cellId,
                signalLevel = monster.signalLevel.name,
                latitude    = result.latitude,
                longitude   = result.longitude,
                capturedAt  = Instant.now().toString(),
                level       = monster.level
            )
        )
        if (PartnerStore.get(applicationContext) == null) {
            PartnerStore.set(applicationContext, monster.cellId)
        }
    }

    private suspend fun updateNotification(result: Measurement) {
        val todayPrefix = Instant.now().atOffset(ZoneOffset.UTC).toLocalDate().toString()
        val todayCount = measurementDao.countByUtcDatePrefix(todayPrefix)
        notificationManager.notify(NOTIF_ID, buildNotification(todayCount, result))
    }

    private fun buildNotification(todayCount: Int, latest: Measurement?): android.app.Notification {
        val category = statusIconCategory(latest?.signalLevel ?: SignalLevel.NO_SIGNAL)
        val iconRes = when (category) {
            StatusIconCategory.PLATINUM -> R.drawable.ic_stat_signal_platinum
            StatusIconCategory.NORMAL   -> R.drawable.ic_stat_signal_normal
            StatusIconCategory.BAD      -> R.drawable.ic_stat_signal_bad
        }

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MeasurementService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(formatNotificationTitle(todayCount))
            .setContentText(formatNotificationText(latest))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .addAction(0, "計測停止", stopIntent)

        // Android 12+ はFGS通知の初回表示を最大10秒遅延・短命サービスとして抑制することがある。
        // 計測は長時間継続するサービスのため、即時表示を明示して抑制を回避する。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // 旧チャンネル(IMPORTANCE_LOW)は「サイレント」区分に分類され、実機検証で
        // ステータスバーのアイコンが表示されないことが判明した(通知シェードには出るが
        // 時計横のアイコンだけ省略される、Android新UIの仕様)。
        // NotificationChannelは作成後に重要度を変更できないため、既存インストール分にも
        // 反映されるよう新IDへ切り替える。旧チャンネルは掃除のため削除しておく。
        notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        val channel = NotificationChannel(CHANNEL_ID, "計測ステータス", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "電波計測の実行中ステータスを表示します"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 2つの計測が「実質同じ」かを判定する。MapViewModel.isDuplicate() (旧実装) と同一ロジック。
     * 同じ場所 (10m 以内) + 同じ networkType + RSSI が ±5 dBm 以内 → 重複とみなす。
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

    override fun onDestroy() {
        super.onDestroy()
        loopJob?.cancel()
        scope.cancel()
        MeasurementController.isRunning.value = false
    }

    companion object {
        const val ACTION_START = "com.example.rakutencoverage.measurement.action.START"
        const val ACTION_STOP  = "com.example.rakutencoverage.measurement.action.STOP"

        private const val NOTIF_ID   = 1001
        // IMPORTANCE_LOWで作成していた旧チャンネル。掃除のためcreateChannelIfNeeded()で削除する
        private const val LEGACY_CHANNEL_ID = "measurement_status"
        private const val CHANNEL_ID = "measurement_status_v2"

        fun start(context: Context) {
            val intent = Intent(context, MeasurementService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MeasurementService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
