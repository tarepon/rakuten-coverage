package com.example.rakutencoverage.data

import android.content.Context

/**
 * パートナーモンスター(トレーニングで戦う相棒)の永続化。
 * denpamon-go の Wallet.partnerKey に相当。SharedPreferences で保持する。
 */
object PartnerStore {
    private const val PREF = "game_prefs"
    private const val KEY_PARTNER = "partner_cell_id"

    fun get(context: Context): String? =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_PARTNER, null)

    fun set(context: Context, cellId: String?) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .apply { if (cellId == null) remove(KEY_PARTNER) else putString(KEY_PARTNER, cellId) }
            .apply()
    }
}
