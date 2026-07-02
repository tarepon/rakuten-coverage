package com.example.rakutencoverage.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.rakutencoverage.MainActivity
import com.example.rakutencoverage.R
import com.example.rakutencoverage.data.AppDatabase
import com.example.rakutencoverage.data.SignalLevel
import com.example.rakutencoverage.data.resolveSignalLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val EXTRA_AUTO_MEASURE = "AUTO_MEASURE"

class MeasurementWidget : AppWidgetProvider() {

    private val scope = MainScope()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        scope.cancel()
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // ウィジェット本体タップ → アプリ通常起動
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val launchPi = PendingIntent.getActivity(
            context, widgetId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, launchPi)

        // 「起動して計測」ボタン → マップ画面 + 計測自動開始
        val measureIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_AUTO_MEASURE, true)
        }
        val measurePi = PendingIntent.getActivity(
            context, widgetId + 10000,
            measureIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_measure_button, measurePi)

        appWidgetManager.updateAppWidget(widgetId, views)

        // 最終計測結果を非同期で読んで表示（scope はインスタンス管理 → onDisabled でキャンセル）
        scope.launch {
            val last = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).measurementDao().getAll().firstOrNull()
            }
            val label = if (last != null) {
                val level = resolveSignalLevel(last.networkType, last.band)
                "${level.toEmoji()} ${level.shortLabel()}  |  ${last.rttMs}ms"
            } else {
                "📡 未計測"
            }
            views.setTextViewText(R.id.widget_signal_label, label)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    companion object {
        private fun SignalLevel.toEmoji() = when (this) {
            SignalLevel.MILLIMETER_WAVE -> "👑"
            SignalLevel.PLATINUM_5G     -> "🏆"
            SignalLevel.FIVE_G          -> "⚡"
            SignalLevel.PLATINUM        -> "💎"
            SignalLevel.LTE             -> "📶"
            SignalLevel.WEAK            -> "📉"
            SignalLevel.NO_SIGNAL       -> "🚫"
            SignalLevel.AIRPLANE_MODE   -> "✈️"
            SignalLevel.NO_SIM          -> "📵"
        }

        private fun SignalLevel.shortLabel() = when (this) {
            SignalLevel.MILLIMETER_WAVE -> "ミリ波5G"
            SignalLevel.PLATINUM_5G     -> "プラチナ5G"
            SignalLevel.FIVE_G          -> "5G"
            SignalLevel.PLATINUM        -> "プラチナBand28"
            SignalLevel.LTE             -> "LTE"
            SignalLevel.WEAK            -> "弱電界"
            SignalLevel.NO_SIGNAL       -> "圏外"
            SignalLevel.AIRPLANE_MODE   -> "機内モード"
            SignalLevel.NO_SIM          -> "SIMなし"
        }
    }
}
