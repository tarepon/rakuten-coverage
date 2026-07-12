package com.example.rakutencoverage.measurement

import com.example.rakutencoverage.ui.map.ArenaModeInput
import com.example.rakutencoverage.ui.map.MeasureInterval
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * MapViewModel(前面 UI)と MeasurementService(バックグラウンド FGS)が共有する計測状態。
 * プロセス生存中はどちらの生死にも紐づかないシングルトンとして持つことで、
 * Activity/ViewModel が破棄されても Service 側の計測ループがインターバル・チェックイン・
 * 自動捕獲設定の最新値を読み続けられるようにする。
 *
 * 書き込み方向:
 * - isRunning   : Service が書く（開始/停止の実際の状態）。MapViewModel は購読のみ
 * - intervalMs  : MapViewModel が書く（インターバル選択 UI）。Service は次周期の delay() で読む
 * - checkIn     : MapViewModel が書く（チェックイン UI）。Service が計測タグ付け・スタンプ判定に使う
 * - autoCapture : MapViewModel が書く（設定 UI）。Service が裏での自動捕獲可否判定に使う
 */
object MeasurementController {
    val isRunning: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val intervalMs: MutableStateFlow<Long> = MutableStateFlow(MeasureInterval.FAST.seconds * 1000L)
    val checkIn: MutableStateFlow<ArenaModeInput?> = MutableStateFlow(null)
    val autoCapture: MutableStateFlow<Boolean> = MutableStateFlow(false)
}
