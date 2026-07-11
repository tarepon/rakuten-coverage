package com.example.rakutencoverage.util

import org.osmdroid.util.GeoPoint

/** 囲って保存(ラッソエクスポート)機能向けの幾何ユーティリティ。 */
object GeoUtils {

    /**
     * レイキャスティング法(奇偶判定, PNPOLY)による点のポリゴン内判定。
     * 凸・凹どちらのポリゴンにも対応する。境界線上ちょうどの扱いは未定義(奇偶判定の性質上)。
     *
     * @param polygon 頂点列。3点未満は常に false を返す
     */
    fun pointInPolygon(lat: Double, lng: Double, polygon: List<GeoPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].longitude
            val yi = polygon[i].latitude
            val xj = polygon[j].longitude
            val yj = polygon[j].latitude
            val intersects = ((yi > lat) != (yj > lat)) &&
                (lng < (xj - xi) * (lat - yi) / (yj - yi) + xi)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }
}
