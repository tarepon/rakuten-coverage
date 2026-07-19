package com.example.rakutencoverage.data

import android.content.Context

/**
 * アプリ全体設定の永続化。PartnerStore と同じ SharedPreferences("game_prefs") を使う。
 */
object SettingsStore {
    private const val PREF = "game_prefs"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

    /** 常に画面ON(スリープさせない)設定。既定はOFF */
    fun keepScreenOn(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_SCREEN_ON, false)

    fun setKeepScreenOn(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_KEEP_SCREEN_ON, enabled)
            .apply()
    }

    /** 起動時の「計測は楽天回線のみ」注釈を「次回から表示しない」にしたか。既定は表示する */
    fun rakutenOnlyNoticeDismissed(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_RAKUTEN_NOTICE_DISMISSED, false)

    fun setRakutenOnlyNoticeDismissed(context: Context, dismissed: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_RAKUTEN_NOTICE_DISMISSED, dismissed)
            .apply()
    }

    private const val KEY_RAKUTEN_NOTICE_DISMISSED = "rakuten_only_notice_dismissed"
}
