package com.example.rakutencoverage.ui.checkin

import kotlin.math.roundToInt

/**
 * チェックインの不正防止ジオフェンス判定(純粋ロジック)。
 * 確定ボタン押下時に取得した「新鮮な現在地」とスポットの距離で判定する。
 * 半径は一律1km(オーナー確定 2026-07-11)。根拠: 最大級施設(レイクタウン・幕張新都心)でも
 * 中心点から端まで約500m+屋内GPS誤差200m+収録座標誤差300mを吸収できる安全側の値。
 * 位置が取得できない場合もブロックする(不正防止の趣旨を優先)。
 */
object CheckInGeofence {

    /** 判定半径(m)。最寄りスポット自動選択の閾値もこれに合わせる */
    const val RADIUS_M = 1000f

    sealed interface Verdict {
        data object Ok : Verdict
        data class TooFar(val distanceM: Float) : Verdict
        data object NoLocation : Verdict
    }

    /** distanceM=null は位置不取得を意味する */
    fun judge(distanceM: Float?): Verdict = when {
        distanceM == null -> Verdict.NoLocation
        distanceM > RADIUS_M -> Verdict.TooFar(distanceM)
        else -> Verdict.Ok
    }

    /** 距離表示(ピッカーの距離表示と同形式): 1000m未満は"850m"、以上は"1.2km" */
    fun formatDistance(m: Float): String =
        if (m < 1000f) "${m.roundToInt()}m" else "%.1fkm".format(m / 1000f)
}
