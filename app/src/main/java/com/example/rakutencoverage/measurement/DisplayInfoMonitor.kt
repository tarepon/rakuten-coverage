package com.example.rakutencoverage.measurement

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * 5G判定の実機診断用ログタグ (NetworkInfoCollector と共通)。取得方法: adb logcat -s SignalDiag
 * 2クラスのログを1つのタグで一括取得するため、定義はここ1箇所に集約する。
 */
internal const val DIAG_TAG = "SignalDiag"

/**
 * TelephonyDisplayInfo (ステータスバーの 5G アイコンと同じ情報源) を購読し、
 * NSA 5G 接続状態を保持するプロセス寿命のシングルトン。
 *
 * 背景: 5G NSA では端末によってセル一覧に NR セカンダリセルが一切現れず、
 * セル走査だけではステータスバーが 5G/5G+ でも LTE と判定されてしまう。
 * TelephonyDisplayInfo.overrideNetworkType はまさにステータスバー表示用の
 * ネットワーク抽象で、これを併用することで表示と判定を一致させる。
 *
 * DUAL SIM対応: 渡された TelephonyManager (楽天SIM専用インスタンスが望ましい) の
 * サブスクリプションを購読する。他社データSIMのDisplayInfoを読むと楽天LTEを
 * 誤って5G昇格させるため、subId が変わったら購読先を張り替える。
 *
 * 権限: API 31+ では DisplayInfoListener の購読に READ_PHONE_STATE は不要
 * (TelephonyRegistry の compat change REQUIRE_READ_PHONE_STATE_PERMISSION_FOR_DISPLAY_INFO
 *  により targetSdk 31+ のアプリは権限要求が撤廃されている)。
 * API 30 以前は READ_PHONE_STATE が必要なため購読せず、従来のセル走査のみで判定する。
 */
object DisplayInfoMonitor {

    @Volatile private var overrideType: Int = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
    @Volatile private var permanentlyFailed = false

    /** 現在購読中のサブスクリプションID。未購読なら null */
    private var registeredSubId: Int? = null

    /** 購読に使った TelephonyManager (解除に必要)。実型は TelephonyManager */
    private var registeredTm: TelephonyManager? = null

    /**
     * ロック外の高速パス用: 現在購読中の TelephonyManager 参照。
     * ensureStarted は計測ごとに(メインスレッドのVMとサービスの両方から)呼ばれるため、
     * 定常状態(同じTMで購読済み)ではロックを取らずに抜けられるようにする。
     * cachedRakutenTm がキャッシュされる限り呼び出し側の targetTm は同一インスタンスになる。
     */
    @Volatile private var fastPathTm: TelephonyManager? = null

    /** 登録済みコールバック (GC防止の強参照兼解除用)。実型は TelephonyCallback (API 31+) */
    private var callback: Any? = null

    /** NSA 5G 接続中 (ステータスバーが 5G/5G+ を表示する状態) なら true */
    val isNrConnected: Boolean
        get() = overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED

    /**
     * targetTm のサブスクリプションに対する購読を保証する (多重呼び出し安全・API 31 未満では何もしない)。
     * 既に同じ subId を購読中なら何もしない。subId が変わっていたら (SIM入れ替え・楽天SIM検出等)
     * 旧購読を解除して張り替える。
     * 登録直後に現在状態のコールバックが非同期で届くため、登録した同じ呼び出し内の
     * collect() ではまだ反映されないことがある (計測ループの次回以降は反映される)。
     */
    fun ensureStarted(context: Context, targetTm: TelephonyManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || permanentlyFailed) return
        if (targetTm === fastPathTm) return  // 定常状態: 同じTMで購読済み(ロック不要)
        synchronized(this) {
            ensureStartedLocked(context.applicationContext, targetTm)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun ensureStartedLocked(appContext: Context, targetTm: TelephonyManager) {
        val subId = runCatching { targetTm.subscriptionId }
            .getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        if (callback != null && subId == registeredSubId) {
            // 同じsubIdの別TMインスタンス(キャッシュ再生成等)。購読は有効なので参照だけ更新
            fastPathTm = targetTm
            return
        }

        // 購読先の張り替え: 旧購読を解除し、判定値も一旦リセット (旧SIMの状態を引きずらない)
        (callback as? TelephonyCallback)?.let { old ->
            runCatching { registeredTm?.unregisterTelephonyCallback(old) }
            callback = null
            registeredTm = null
            registeredSubId = null
            fastPathTm = null
            overrideType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
        }

        val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
            override fun onDisplayInfoChanged(displayInfo: TelephonyDisplayInfo) {
                overrideType = displayInfo.overrideNetworkType
                Log.d(DIAG_TAG, "DisplayInfo変化: overrideNetworkType=${displayInfo.overrideNetworkType} " +
                    "(0=なし 1=LTE_CA 2=LTE_ADV_PRO 3=NR_NSA 4=NR_NSA_MMWAVE 5=NR_ADVANCED) " +
                    "isNrConnected=$isNrConnected")
            }
        }
        try {
            targetTm.registerTelephonyCallback(appContext.mainExecutor, cb)
            callback = cb
            registeredTm = targetTm
            registeredSubId = subId
            fastPathTm = targetTm
            Log.i(DIAG_TAG, "DisplayInfo購読開始 subId=$subId (targetSdk31+のためREAD_PHONE_STATE不要)")
        } catch (e: SecurityException) {
            // 想定外に権限を要求される端末では以後も成功しないため再試行しない。
            // isNrConnected は false のまま = 従来のセル走査のみの判定で継続
            permanentlyFailed = true
            Log.w(DIAG_TAG, "DisplayInfo購読がSecurityExceptionで拒否。セル走査のみで判定継続", e)
        } catch (e: IllegalStateException) {
            // Telephony サービス未起動など一時的な失敗。次回の ensureStarted で再試行
            Log.w(DIAG_TAG, "DisplayInfo購読が一時失敗。次回計測時に再試行", e)
        }
    }
}
