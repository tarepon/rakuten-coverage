package com.example.rakutencoverage.measurement

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.*

/**
 * 端末の通信状態を一括取得するデータクラス。
 * @property networkType 通信種別文字列: "5G" / "LTE" / "3G" / "2G" / "NO_SERVICE" / "AIRPLANE_MODE" / "NO_SIM"
 * @property band バンド名 (例: "Band 28", "n77")。取得不可なら null
 * @property rssi 電波強度 dBm。取得不可なら null
 * @property carrier キャリア名 (例: "Rakuten")。取得不可・圏外なら null
 * @property isValidMeasurement false = 機内モード / SIMなし → スタンプ付与対象外
 */
data class NetworkSnapshot(
    val networkType: String,
    val band: String?,
    val rssi: Int?,
    val carrier: String?,
    val isValidMeasurement: Boolean = true,
    val cellId: String? = null
)

/**
 * TelephonyManager を使って端末の通信状態を収集するクラス。
 * 機内モード・SIM不在を優先的に検出し、それ以外はバンド・RSSI・キャリアを取得する。
 */
class NetworkInfoCollector(private val context: Context) {

    private val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    /**
     * 端末の現在の通信状態を収集して NetworkSnapshot として返す。
     * 優先順: 機内モード → SIMなし → 圏外 → 通常の電波
     * @return NetworkSnapshot (isValidMeasurement=false なら計測データとしては無効)
     */
    @SuppressLint("MissingPermission")
    fun collect(): NetworkSnapshot {
        if (isAirplaneMode()) {
            return NetworkSnapshot("AIRPLANE_MODE", null, null, null, isValidMeasurement = false)
        }
        if (isNoSim()) {
            return NetworkSnapshot("NO_SIM", null, null, null, isValidMeasurement = false)
        }
        val networkType = resolveNetworkType()
        if (networkType == "NO_SERVICE") {
            return NetworkSnapshot("NO_SERVICE", null, null, null, isValidMeasurement = true)
        }
        val carrier = tm.networkOperatorName.takeIf { it.isNotBlank() }
        val (band, rssi, cellId) = resolveBandRssiAndCellId()
        return NetworkSnapshot(networkType, band, rssi, carrier, isValidMeasurement = true, cellId = cellId)
    }

    /** Settings.Global.AIRPLANE_MODE_ON が 1 なら機内モード */
    private fun isAirplaneMode(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    /** SIM 状態が ABSENT / UNKNOWN なら SIM 不在とみなす */
    private fun isNoSim(): Boolean =
        tm.simState == TelephonyManager.SIM_STATE_ABSENT ||
        tm.simState == TelephonyManager.SIM_STATE_UNKNOWN

    /**
     * ネットワーク種別を取得する。
     * API 29+ では READ_PHONE_STATE 不要の allCellInfo からセル種別を判定する。
     * API 26-28 では dataNetworkType を使用（READ_PHONE_STATE が必要、maxSdkVersion=28 で宣言済み）。
     * SecurityException は念のためキャッチし NO_SERVICE を返す。
     */
    @SuppressLint("MissingPermission")
    private fun resolveNetworkType(): String {
        // API 29+ は allCellInfo からセル種別を導出（READ_PHONE_STATE 不要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try {
                when (tm.allCellInfo.firstOrNull()) {
                    is CellInfoNr    -> "5G"
                    is CellInfoLte   -> "LTE"
                    is CellInfoWcdma -> "3G"
                    is CellInfoGsm   -> "2G"
                    else             -> "NO_SERVICE"
                }
            } catch (e: SecurityException) {
                "NO_SERVICE"
            }
        }
        // API 26-28: dataNetworkType（READ_PHONE_STATE が必要）
        return try {
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR    -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE   -> "LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_UMTS  -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS  -> "2G"
                else                                -> "NO_SERVICE"
            }
        } catch (e: SecurityException) {
            "NO_SERVICE"
        }
    }

    /**
     * allCellInfo から最初のセル情報を取得し、バンド名と RSSI を返す。
     * Android Q (API 29) 未満はバンド取得不可のため (null, null) を返す。
     * 5G (NR) と LTE のみ対応。それ以外は (null, null)。
     * @return Pair(バンド名, RSSI dBm)
     */
    @SuppressLint("MissingPermission")
    private fun resolveBandRssiAndCellId(): Triple<String?, Int?, String?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Triple(null, null, null)

        val info = tm.allCellInfo.firstOrNull() ?: return Triple(null, null, null)
        return when (info) {
            is CellInfoNr -> {
                val identity = info.cellIdentity as CellIdentityNr
                val signal   = info.cellSignalStrength as CellSignalStrengthNr
                val bandName = identity.bands.firstOrNull()?.let { bandNumberToName(it, isNr = true) }
                val cellId   = identity.nci.takeIf { it != Long.MAX_VALUE }?.toString()
                Triple(bandName, signal.dbm, cellId)
            }
            is CellInfoLte -> {
                val identity = info.cellIdentity as CellIdentityLte
                val signal   = info.cellSignalStrength as CellSignalStrengthLte
                val bandName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    identity.bands.firstOrNull()?.let { bandNumberToName(it, isNr = false) }
                else null
                val cellId = identity.ci.takeIf { it != Int.MAX_VALUE }?.toString()
                Triple(bandName, signal.dbm, cellId)
            }
            else -> Triple(null, null, null)
        }
    }

    /**
     * バンド番号を文字列に変換する。
     * @param bandNumber 整数バンド番号 (例: 28, 77)
     * @param isNr true = 5G NR → "n28" 形式、false = LTE → "Band 28" 形式
     */
    private fun bandNumberToName(bandNumber: Int, isNr: Boolean): String =
        if (isNr) "n$bandNumber" else "Band $bandNumber"
}
