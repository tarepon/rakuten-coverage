package com.example.rakutencoverage.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.roundToLong

/**
 * 約11m グリッドのセルごとに1体のモンスターを管理するコレクション記録。
 * セルキーは緯度・経度を小数点4桁に丸めた "lat,lng" 文字列 (≒ H3 レベル10 相当)。
 * 同一セルでより強い SignalLevel を計測したら上書き（進化）する。
 */
@Entity(tableName = "collection_records")
data class CollectionRecord(
    @PrimaryKey val h3Index: String,       // セルID: "lat4,lng4" 形式
    val signalLevel: String,               // SignalLevel.name
    val latitude: Double,                  // 捕獲時のGPS緯度
    val longitude: Double,                 // 捕獲時のGPS経度
    val capturedAt: String,                // ISO8601
    @ColumnInfo(defaultValue = "5") val level: Int = 1,  // トレーニングで成長するレベル
    @ColumnInfo(defaultValue = "0") val xp: Int = 0      // 次のレベルまでの経験値
)

/** SignalLevel のレア度順序（小さいほど強い・レア）。AIRPLANE_MODE/NO_SIMはコレクション対象外 */
val SignalLevel.rarityRank: Int get() = when (this) {
    SignalLevel.PLATINUM_5G     -> 0
    SignalLevel.FIVE_G          -> 1
    SignalLevel.PLATINUM        -> 2
    SignalLevel.LTE             -> 3
    SignalLevel.WEAK            -> 4
    SignalLevel.NO_SIGNAL       -> 5
    SignalLevel.AIRPLANE_MODE   -> 99
    SignalLevel.NO_SIM          -> 99
}

val SignalLevel.isCollectable: Boolean get() =
    this != SignalLevel.AIRPLANE_MODE && this != SignalLevel.NO_SIM

/**
 * 緯度・経度を小数点4桁 (≈ 11m) に丸め、"lat,lng" 形式のセルキーを返す。
 * H3 ネイティブライブラリ不要で Android 上で動作する。
 */
fun latLngToH3Index(latitude: Double, longitude: Double): String {
    val lat = (latitude  * 10000).roundToLong() / 10000.0
    val lng = (longitude * 10000).roundToLong() / 10000.0
    return "$lat,$lng"
}

/**
 * セルキー "lat,lng" から代表点の緯度・経度を返す。
 */
fun h3IndexToLatLng(h3Index: String): Pair<Double, Double> {
    val (lat, lng) = h3Index.split(",").map { it.toDouble() }
    return Pair(lat, lng)
}

/**
 * 信号レベルに応じてセルIDを返す。
 * 通常: 小数4桁 ≈ 11m グリッド（latLngToH3Index と同じ）
 * NO_SIGNAL: 0.002度 ≈ 222m グリッド（圏外モンスターはレア）
 */
fun latLngToCellId(latitude: Double, longitude: Double, signalLevel: SignalLevel): String {
    return if (signalLevel == SignalLevel.NO_SIGNAL) {
        val la = (latitude  / 0.002).roundToLong() * 0.002
        val ln = (longitude / 0.002).roundToLong() * 0.002
        "nosig:$la,$ln"
    } else {
        latLngToH3Index(latitude, longitude)
    }
}
