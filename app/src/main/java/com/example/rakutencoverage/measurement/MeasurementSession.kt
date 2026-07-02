package com.example.rakutencoverage.measurement

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.example.rakutencoverage.data.Measurement
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import java.time.Instant

/**
 * 1回分の計測（GPS + 電波 + RTT）を実行して Room DB に保存するクラス。
 * ViewModel から呼び出される。アリーナチェックイン情報はオプションで付加できる。
 */
class MeasurementSession(context: Context) {

    private val fusedLocation  = LocationServices.getFusedLocationProviderClient(context)
    private val networkCollector = NetworkInfoCollector(context)

    /**
     * GPS・電波・RTT を収集して Measurement を生成して返す。DB への挿入は行わない。
     * 重複チェックや挿入タイミングの制御は呼び出し元 (ViewModel) で行う。
     *
     * @param arenaId    チェックイン中のスポット ID (通常計測時は null)
     * @param arenaName  チェックイン中のスポット名 (通常計測時は null)
     * @param seatLabel  アリーナの座席情報 (例: "A-12列-5番"、未入力は null)
     * @param gamePhase  試合フェーズ名 (GamePhase.name、通常計測時は null)
     * @return 収集した Measurement。GPS 取得失敗時は null
     */
    @SuppressLint("MissingPermission")
    suspend fun collect(
        arenaId: String?   = null,
        arenaName: String? = null,
        seatLabel: String? = null,
        gamePhase: String? = null
    ): Measurement? {
        val cancelToken = CancellationTokenSource()
        val location: Location = try {
            fusedLocation.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancelToken.token
            ).await() ?: return null
        } finally {
            cancelToken.cancel()
        }

        val network = networkCollector.collect()
        val rtt     = if (network.isValidMeasurement) PingMeasurer.measureRttMs() else -1

        return Measurement(
            latitude    = location.latitude,
            longitude   = location.longitude,
            timestamp   = Instant.now().toString(),
            networkType = network.networkType,
            band        = network.band,
            rssi        = network.rssi,
            rttMs       = rtt,
            carrier     = network.carrier,
            arenaId     = arenaId,
            arenaName   = arenaName,
            seatLabel   = seatLabel,
            gamePhase   = gamePhase,
            cellId      = network.cellId
        )
    }

    fun release() {}
}
