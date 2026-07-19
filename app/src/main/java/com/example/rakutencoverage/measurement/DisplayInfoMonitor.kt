package com.example.rakutencoverage.measurement

import android.content.Context
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * TelephonyDisplayInfo (ステータスバーの 5G アイコンと同じ情報源) を購読し、
 * NSA 5G 接続状態を保持するプロセス寿命のシングルトン。
 *
 * 背景: 5G NSA では端末によって allCellInfo に NR セカンダリセルが一切現れず、
 * セル走査だけではステータスバーが 5G/5G+ でも LTE と判定されてしまう。
 * TelephonyDisplayInfo.overrideNetworkType はまさにステータスバー表示用の
 * ネットワーク抽象で、これを併用することで表示と判定を一致させる。
 *
 * 権限: API 31+ では DisplayInfoListener の購読に READ_PHONE_STATE は不要
 * (TelephonyRegistry の compat change REQUIRE_READ_PHONE_STATE_PERMISSION_FOR_DISPLAY_INFO
 *  により targetSdk 31+ のアプリは権限要求が撤廃されている)。
 * API 30 以前は READ_PHONE_STATE が必要なため購読せず、従来のセル走査のみで判定する。
 */
object DisplayInfoMonitor {

    /** 5G判定の実機診断用ログタグ。取得方法: adb logcat -s SignalDiag */
    private const val TAG = "SignalDiag"

    @Volatile private var overrideType: Int = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
    @Volatile private var started = false

    /** registerTelephonyCallback は弱参照保持の可能性があるため GC 防止に強参照を保持する */
    private var callback: Any? = null

    /** NSA 5G 接続中 (ステータスバーが 5G/5G+ を表示する状態) なら true */
    val isNrConnected: Boolean
        get() = overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED

    /**
     * 購読を開始する (多重呼び出し安全・API 31 未満では何もしない)。
     * 登録直後に現在状態のコールバックが非同期で届くため、登録した同じ呼び出し内の
     * collect() ではまだ反映されないことがある (計測ループの次回以降は反映される)。
     * NetworkInfoCollector のコンストラクタからも呼び、初回計測までのラグを最小化する。
     */
    fun ensureStarted(context: Context) {
        if (started || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        synchronized(this) {
            if (started) return
            registerCallback(context.applicationContext)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerCallback(appContext: Context) {
        val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
            override fun onDisplayInfoChanged(displayInfo: TelephonyDisplayInfo) {
                overrideType = displayInfo.overrideNetworkType
                Log.d(TAG, "DisplayInfo変化: overrideNetworkType=${displayInfo.overrideNetworkType} " +
                    "(0=なし 1=LTE_CA 2=LTE_ADV_PRO 3=NR_NSA 4=NR_NSA_MMWAVE 5=NR_ADVANCED) " +
                    "isNrConnected=$isNrConnected")
            }
        }
        try {
            tm.registerTelephonyCallback(appContext.mainExecutor, cb)
            callback = cb
            started = true
            Log.i(TAG, "DisplayInfo購読開始 (targetSdk31+のためREAD_PHONE_STATE不要)")
        } catch (e: SecurityException) {
            // 想定外に権限を要求される端末では以後も成功しないため再試行しない。
            // isNrConnected は false のまま = 従来のセル走査のみの判定で継続
            started = true
            Log.w(TAG, "DisplayInfo購読がSecurityExceptionで拒否。セル走査のみで判定継続", e)
        } catch (e: IllegalStateException) {
            // Telephony サービス未起動など一時的な失敗。次回の ensureStarted で再試行
            Log.w(TAG, "DisplayInfo購読が一時失敗。次回計測時に再試行", e)
        }
    }
}
