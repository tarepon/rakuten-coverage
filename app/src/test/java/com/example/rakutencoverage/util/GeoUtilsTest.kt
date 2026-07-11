package com.example.rakutencoverage.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint

/**
 * GeoUtils.pointInPolygon の単体テスト。
 * GeoPoint(double, double) は純粋なフィールド代入のみで Android ランタイム依存が無いため、
 * ローカルJVM単体テスト(Robolectric不使用)でも安全に生成できる。
 */
class GeoUtilsTest {

    /** lat 0-10, lng 0-10 の正方形(凸ポリゴン) */
    private fun square() = listOf(
        GeoPoint(0.0, 0.0),
        GeoPoint(0.0, 10.0),
        GeoPoint(10.0, 10.0),
        GeoPoint(10.0, 0.0)
    )

    @Test
    fun pointInsideConvexPolygonReturnsTrue() {
        assertTrue(GeoUtils.pointInPolygon(5.0, 5.0, square()))
    }

    @Test
    fun pointOutsideConvexPolygonReturnsFalse() {
        assertFalse(GeoUtils.pointInPolygon(20.0, 20.0, square()))
    }

    @Test
    fun pointOnOppositeCornerOutsideReturnsFalse() {
        assertFalse(GeoUtils.pointInPolygon(-1.0, -1.0, square()))
    }

    /**
     * lat[0,10] x lng[0,10] の正方形から、右辺中央(lat[4,6] x lng[6,10])を
     * 四角く切り欠いた単純多角形(自己交差なし)。
     * 頂点順: (0,0)→(10,0)→(10,10)→(6,10)→(6,6)→(4,6)→(4,10)→(0,10)→close
     */
    private fun notchedPolygon() = listOf(
        GeoPoint(0.0, 0.0),
        GeoPoint(10.0, 0.0),
        GeoPoint(10.0, 10.0),
        GeoPoint(6.0, 10.0),
        GeoPoint(6.0, 6.0),
        GeoPoint(4.0, 6.0),
        GeoPoint(4.0, 10.0),
        GeoPoint(0.0, 10.0)
    )

    @Test
    fun pointInsideConcavePolygonReturnsTrue() {
        // 切り欠きの影響を受けない左側(lng=3)は内側
        assertTrue(GeoUtils.pointInPolygon(5.0, 3.0, notchedPolygon()))
    }

    @Test
    fun pointInNotchOfConcavePolygonReturnsFalse() {
        // 切り欠き部分(lat[4,6] x lng[6,10])の内部はポリゴン外
        assertFalse(GeoUtils.pointInPolygon(5.0, 8.0, notchedPolygon()))
    }

    @Test
    fun fewerThanThreePointsReturnsFalse() {
        assertFalse(GeoUtils.pointInPolygon(1.0, 1.0, listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0))))
        assertFalse(GeoUtils.pointInPolygon(1.0, 1.0, emptyList()))
    }
}
