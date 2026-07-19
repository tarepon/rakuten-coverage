package com.example.rakutencoverage.measurement

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.*
import androidx.annotation.RequiresApi

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

/** セル選択に使う簡易セル種別。allCellInfo の CellInfo サブクラスに対応する */
enum class CellType { NR, LTE, WCDMA, GSM, OTHER }

/**
 * セル選択の判断材料だけを抜き出した純粋データ。
 * Android フレームワーク非依存にすることで JVM 単体テストを可能にする。
 */
data class CellCandidate(
    val type: CellType,
    val isRegistered: Boolean,
    val hasSignal: Boolean
)

/**
 * allCellInfo のリストから計測に使うセルの index を選ぶ純粋関数。
 *
 * 背景: 5G NSA 構成 (LTE アンカー + NR セカンダリセル) では、在圏セル (isRegistered=true)
 * は LTE アンカー側で、NR セルは isRegistered=false のセカンダリとしてリスト後方に
 * 報告される端末が多い。従来の「リスト先頭のセルで判定」では、ステータスバーが
 * 5G/5G+ 表示でもアプリは LTE と誤判定していた (端末の並び順依存)。
 *
 * 優先順:
 *  1. 在圏の NR セル → 5G SA
 *  2. 有効な信号強度を持つ NR セル → 5G NSA (セカンダリセル在圏相当)
 *  3. 在圏のセル (LTE アンカー等)
 *  4. リスト先頭 (従来動作へのフォールバック)
 *
 * @return 選択したセルの index。リストが空なら null
 */
fun selectMeasurementCellIndex(cells: List<CellCandidate>): Int? {
    if (cells.isEmpty()) return null
    cells.indexOfFirst { it.type == CellType.NR && it.isRegistered }
        .takeIf { it >= 0 }?.let { return it }
    cells.indexOfFirst { it.type == CellType.NR && it.hasSignal }
        .takeIf { it >= 0 }?.let { return it }
    cells.indexOfFirst { it.isRegistered }
        .takeIf { it >= 0 }?.let { return it }
    return 0
}

/**
 * TelephonyManager を使って端末の通信状態を収集するクラス。
 * 機内モード・SIM不在を優先的に検出し、それ以外はバンド・RSSI・キャリアを取得する。
 *
 * 注意: ステータスバーの 5G アイコンは TelephonyDisplayInfo (overrideNetworkType) 由来で
 * OEM/キャリアのポリシー依存だが、その購読には READ_PHONE_STATE が必要 (本アプリは
 * API 29+ では非保持)。ここでは allCellInfo に現れる NR セルの検出で近似する。
 */
class NetworkInfoCollector(private val context: Context) {

    private val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    /**
     * 端末の現在の通信状態を収集して NetworkSnapshot として返す。
     * 優先順: 機内モード → SIMなし → 圏外 → 通常の電波
     * @return NetworkSnapshot (isValidMeasurement=false なら計測データとしては無効)
     */
    fun collect(): NetworkSnapshot {
        if (isAirplaneMode()) {
            return NetworkSnapshot("AIRPLANE_MODE", null, null, null, isValidMeasurement = false)
        }
        if (isNoSim()) {
            return NetworkSnapshot("NO_SIM", null, null, null, isValidMeasurement = false)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collectFromCellInfo()
        } else {
            collectLegacy()
        }
    }

    /** Settings.Global.AIRPLANE_MODE_ON が 1 なら機内モード */
    private fun isAirplaneMode(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    /** SIM 状態が ABSENT / UNKNOWN なら SIM 不在とみなす */
    private fun isNoSim(): Boolean =
        tm.simState == TelephonyManager.SIM_STATE_ABSENT ||
        tm.simState == TelephonyManager.SIM_STATE_UNKNOWN

    /**
     * API 29+: allCellInfo の全セルから selectMeasurementCellIndex で計測対象セルを選び、
     * 種別・バンド・RSSI・セルID を同一セルから導出する (READ_PHONE_STATE 不要)。
     * バンド名 (CellIdentityNr/Lte.bands) は API 30 (R) 以上でのみ取得可。API 29 では null。
     * SecurityException は念のためキャッチし NO_SERVICE を返す。
     */
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun collectFromCellInfo(): NetworkSnapshot {
        val cells = try {
            tm.allCellInfo.orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }
        val index = selectMeasurementCellIndex(cells.map { it.toCandidate() })
            ?: return NetworkSnapshot("NO_SERVICE", null, null, null)
        val carrier = tm.networkOperatorName.takeIf { it.isNotBlank() }
        return when (val info = cells[index]) {
            is CellInfoNr -> {
                val identity = info.cellIdentity as CellIdentityNr
                val signal   = info.cellSignalStrength as CellSignalStrengthNr
                val bandName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    identity.bands.firstOrNull()?.let { bandNumberToName(it, isNr = true) }
                else null
                val cellId   = identity.nci.takeIf { it != Long.MAX_VALUE }?.toString()
                NetworkSnapshot("5G", bandName, signal.dbm.takeIfAvailable(), carrier, cellId = cellId)
            }
            is CellInfoLte -> {
                val identity = info.cellIdentity
                val signal   = info.cellSignalStrength
                val bandName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    identity.bands.firstOrNull()?.let { bandNumberToName(it, isNr = false) }
                else null
                val cellId   = identity.ci.takeIf { it != Int.MAX_VALUE }?.toString()
                NetworkSnapshot("LTE", bandName, signal.dbm.takeIfAvailable(), carrier, cellId = cellId)
            }
            is CellInfoWcdma -> NetworkSnapshot("3G", null, null, carrier)
            is CellInfoGsm   -> NetworkSnapshot("2G", null, null, carrier)
            else             -> NetworkSnapshot("NO_SERVICE", null, null, null)
        }
    }

    /** CellInfo をセル選択用の純粋データに変換する */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun CellInfo.toCandidate(): CellCandidate {
        val type = when (this) {
            is CellInfoNr    -> CellType.NR
            is CellInfoLte   -> CellType.LTE
            is CellInfoWcdma -> CellType.WCDMA
            is CellInfoGsm   -> CellType.GSM
            else             -> CellType.OTHER
        }
        // hasSignal は NR の NSA 判定 (優先順 2) にのみ使うため NR/LTE 以外は false で足りる。
        // CellInfo.getCellSignalStrength() (基底クラス版) は API 30+ のためサブクラス経由で取得する
        val hasSignal = when (this) {
            is CellInfoNr  -> cellSignalStrength.dbm != CellInfo.UNAVAILABLE
            is CellInfoLte -> cellSignalStrength.dbm != CellInfo.UNAVAILABLE
            else           -> false
        }
        return CellCandidate(type, isRegistered, hasSignal)
    }

    /**
     * API 26-28: dataNetworkType で種別のみ判定 (READ_PHONE_STATE が必要、maxSdkVersion=28 で宣言済み)。
     * バンド・RSSI・セルIDは取得不可のため null。NSA 5G は LTE として報告される制約あり。
     * SecurityException は念のためキャッチし NO_SERVICE を返す。
     */
    @SuppressLint("MissingPermission")
    private fun collectLegacy(): NetworkSnapshot {
        val networkType = try {
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
        if (networkType == "NO_SERVICE") {
            return NetworkSnapshot("NO_SERVICE", null, null, null)
        }
        val carrier = tm.networkOperatorName.takeIf { it.isNotBlank() }
        return NetworkSnapshot(networkType, null, null, carrier)
    }

    /** CellSignalStrength 系の dbm は取得不可時 CellInfo.UNAVAILABLE (Int.MAX_VALUE) を返すため除外する */
    private fun Int.takeIfAvailable(): Int? = takeIf { this != CellInfo.UNAVAILABLE }

    /**
     * バンド番号を文字列に変換する。
     * @param bandNumber 整数バンド番号 (例: 28, 77)
     * @param isNr true = 5G NR → "n28" 形式、false = LTE → "Band 28" 形式
     */
    private fun bandNumberToName(bandNumber: Int, isNr: Boolean): String =
        if (isNr) "n$bandNumber" else "Band $bandNumber"
}
