package com.example.rakutencoverage.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * 「囲って保存」機能: 指でなぞって範囲を囲むラッソ描画オーバーレイ。
 * [enabled] が true の間だけタッチイベントを消費し(マップのパン/ズームを止める)、
 * ACTION_UP で3点以上あれば [onComplete] を呼ぶ。
 * 描画は [clear] を呼ぶまで残る(ACTION_UP 後、確定ダイアログが閉じるまで表示を残す仕様)。
 */
class LassoOverlay(
    private val onComplete: (List<GeoPoint>) -> Unit
) : Overlay() {

    /**
     * true の間だけラッソ描画モード。false 時はタッチイベントを素通しする。
     * 基底クラス Overlay が setEnabled(boolean) を持ちJVMシグネチャが衝突するため enabled という名前は使えない。
     */
    var active: Boolean = false

    private val points = mutableListOf<GeoPoint>()

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#AB47BC")
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#AB47BC")
        alpha = 48
    }

    /** 軌跡を消去する。呼び出し側で mapView.invalidate() を呼ぶこと */
    fun clear() {
        points.clear()
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        if (!active) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                points.clear()
                points.add(mapView.projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint)
                mapView.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                points.add(mapView.projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint)
                mapView.invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (points.size >= 3) {
                    onComplete(points.toList())
                }
                mapView.invalidate()
                return true
            }
        }
        return false
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        if (points.size < 2) return
        val projection = mapView.projection
        val path = Path()
        val pt = Point()
        points.forEachIndexed { index, geoPoint ->
            projection.toPixels(geoPoint, pt)
            if (index == 0) path.moveTo(pt.x.toFloat(), pt.y.toFloat())
            else path.lineTo(pt.x.toFloat(), pt.y.toFloat())
        }
        if (points.size >= 3) {
            path.close()
            canvas.drawPath(path, fillPaint)
        }
        canvas.drawPath(path, strokePaint)
    }
}
