package com.example.rakutencoverage.ui.checkin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * チェックイン記録の速度・レイテンシ表示を閾値で色分けする共通関数。
 * 数値そのものは変えず、視認性のための色分けのみを担当する。
 */

private val SpeedGreen = Color(0xFF43A047)
private val SpeedOrange = Color(0xFFFB8C00)
private val SpeedBrown = Color(0xFF5D4037)

/** ダウンロード速度の色。30Mbps以上=緑 / 5〜30Mbps=オレンジ / 5Mbps未満=茶 */
fun downloadColor(mbps: Double): Color = when {
    mbps >= 30.0 -> SpeedGreen
    mbps >= 5.0  -> SpeedOrange
    else         -> SpeedBrown
}

/** アップロード速度の色。10Mbps以上=緑 / 2〜10Mbps=オレンジ / 2Mbps未満=茶 */
fun uploadColor(mbps: Double): Color = when {
    mbps >= 10.0 -> SpeedGreen
    mbps >= 2.0  -> SpeedOrange
    else         -> SpeedBrown
}

/** レイテンシの色。60ms以下=緑 / 61〜150ms=オレンジ / 150ms超=茶 */
fun latencyColor(ms: Int): Color = when {
    ms <= 60  -> SpeedGreen
    ms <= 150 -> SpeedOrange
    else      -> SpeedBrown
}

/**
 * 「⚡︎ ↓45.2 ↑12.3Mbps・遅延38ms」形式の着色済み文字列を生成する。
 * 各値はそれぞれの色分け関数で色付けし、未計測(null)の項目は "-" を無色で表示する。
 * 記録行(CheckInRow)・詳細ダイアログ(CheckInDetailDialog)の双方で共用する。
 */
fun speedAnnotatedString(downloadMbps: Double?, uploadMbps: Double?, latencyMs: Int?): AnnotatedString =
    buildAnnotatedString {
        append("⚡︎ ↓")
        if (downloadMbps != null) {
            withStyle(SpanStyle(color = downloadColor(downloadMbps))) { append("%.1f".format(downloadMbps)) }
        } else {
            append("-")
        }
        append(" ↑")
        if (uploadMbps != null) {
            withStyle(SpanStyle(color = uploadColor(uploadMbps))) { append("%.1f".format(uploadMbps)) }
        } else {
            append("-")
        }
        append("Mbps・遅延")
        if (latencyMs != null) {
            withStyle(SpanStyle(color = latencyColor(latencyMs))) { append(latencyMs.toString()) }
        } else {
            append("-")
        }
        append("ms")
    }
