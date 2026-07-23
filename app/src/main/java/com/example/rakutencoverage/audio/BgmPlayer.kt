package com.example.rakutencoverage.audio

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.RawRes
import com.example.rakutencoverage.R
import com.example.rakutencoverage.data.SettingsStore

/**
 * シーン別BGMのループ再生管理。
 *
 * - 各画面(シーン)が request() で曲を要求し、同じ曲なら再生を継続、違えば切り替える
 * - Activityのフォアグラウンド状態と設定(SettingsStore.bgmEnabled)を掛け合わせて実際の再生可否を決める。
 *   バックグラウンドでは必ず停止する(計測サービスは裏で動き続けるが、BGMは画面がある時だけ)
 * - 全メソッドはメインスレッドから呼ぶこと(Compose LaunchedEffect / Activityライフサイクル想定)
 *
 * 音源(res/raw/bgm_*.ogg)は scripts/generate_bgm.py で合成した本アプリのオリジナル曲。
 * 外部の楽曲素材は使用していない。
 */
object BgmPlayer {

    enum class Track(@RawRes val resId: Int) {
        OPENING(R.raw.bgm_opening),  // オープニング(物語スクロール)
        TITLE(R.raw.bgm_title),      // タイトル画面
        MAP(R.raw.bgm_map),          // アプリ本体(マップ・通常時)
        BATTLE(R.raw.bgm_battle),    // 捕獲ミニゲーム・特訓バトル
    }

    private const val BGM_VOLUME = 0.5f

    private var player: MediaPlayer? = null
    private var current: Track? = null
    private var requested: Track? = null
    private var enabled = true
    private var inForeground = false

    /** アプリ起動時に永続化済みのON/OFF設定を読み込む */
    fun init(context: Context) {
        enabled = SettingsStore.bgmEnabled(context)
    }

    /** シーンがBGMを要求する。同じ曲を要求された場合は再生を継続する */
    fun request(context: Context, track: Track) {
        requested = track
        sync(context)
    }

    fun isEnabled(): Boolean = enabled

    /** 設定画面のトグル。永続化も行う */
    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        SettingsStore.setBgmEnabled(context, value)
        sync(context)
    }

    fun onForeground(context: Context) {
        inForeground = true
        sync(context)
    }

    fun onBackground() {
        inForeground = false
        release()
    }

    private fun sync(context: Context) {
        val track = requested
        if (!enabled || !inForeground || track == null) {
            release()
            return
        }
        if (track == current && player != null) return
        release()
        player = MediaPlayer.create(context.applicationContext, track.resId)?.apply {
            isLooping = true
            setVolume(BGM_VOLUME, BGM_VOLUME)
            start()
        }
        current = if (player != null) track else null
    }

    private fun release() {
        player?.release()
        player = null
        current = null
    }
}
