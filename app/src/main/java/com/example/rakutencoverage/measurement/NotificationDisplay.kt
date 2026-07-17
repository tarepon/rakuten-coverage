package com.example.rakutencoverage.measurement

import com.example.rakutencoverage.data.Measurement
import com.example.rakutencoverage.data.SignalLevel

/**
 * 計測ステータス通知のスモールアイコンを3種に分類するカテゴリ。
 * 実際のアイコン drawable への対応は MeasurementService 側 (Android 依存) で行う。
 */
enum class StatusIconCategory { PLATINUM, NORMAL, BAD }

/**
 * SignalLevel から通知アイコンのカテゴリを判定する純粋関数。
 * - PLATINUM: ミリ波 / プラチナ5G / プラチナ(Band28) — 楽天回線として最良の状態
 * - NORMAL  : 5G(非プラチナ) / LTE — 通常の楽天回線
 * - BAD     : 弱電界 / 圏外 / 機内モード / SIMなし — 楽天回線として有効に使えない状態
 */
fun statusIconCategory(level: SignalLevel): StatusIconCategory = when (level) {
    SignalLevel.PLATINUM_5G,
    SignalLevel.PLATINUM      -> StatusIconCategory.PLATINUM

    SignalLevel.FIVE_G,
    SignalLevel.LTE           -> StatusIconCategory.NORMAL

    SignalLevel.WEAK,
    SignalLevel.NO_SIGNAL,
    SignalLevel.AIRPLANE_MODE,
    SignalLevel.NO_SIM        -> StatusIconCategory.BAD
}

/** 通知本文用の日本語表示名 (widget/MeasurementWidget.kt の shortLabel() と表記を揃えている) */
fun signalLevelDisplayName(level: SignalLevel): String = when (level) {
    SignalLevel.PLATINUM_5G     -> "プラチナ5G"
    SignalLevel.FIVE_G          -> "5G"
    SignalLevel.PLATINUM        -> "プラチナBand28"
    SignalLevel.LTE             -> "LTE"
    SignalLevel.WEAK            -> "弱電界"
    SignalLevel.NO_SIGNAL       -> "圏外"
    SignalLevel.AIRPLANE_MODE   -> "機内モード"
    SignalLevel.NO_SIM          -> "SIMなし"
}

/**
 * 通知本文 "{band} {rssi}dBm・{レベル表示名}" を組み立てる。
 * band/rssi が無い（圏外・機内モード・SIMなし等）場合はレベル表示名のみを返す。
 */
fun formatNotificationText(measurement: Measurement?): String {
    if (measurement == null) return "計測待ち…"
    val level = measurement.signalLevel
    val band  = measurement.band
    val rssi  = measurement.rssi
    return if (band != null && rssi != null) {
        "$band ${rssi}dBm・${signalLevelDisplayName(level)}"
    } else {
        signalLevelDisplayName(level)
    }
}

/** 通知タイトル "📡 電波計測中(今日{N}件)" を組み立てる */
fun formatNotificationTitle(todayCount: Int): String = "📡 電波計測中(今日${todayCount}件)"
