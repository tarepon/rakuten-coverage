package com.example.rakutencoverage.measurement

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.*
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

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

/** 楽天モバイル自社網のPLMN (MCC 440 / MNC 11) か */
fun isRakutenPlmn(mcc: String?, mnc: String?): Boolean =
    mcc == "440" && mnc == "11"

/** 楽天のパートナーローミング(KDDI/au)のPLMNか。ドコモSIMはauに在圏しないため誤混入しない */
fun isPartnerRoamingPlmn(mcc: String?, mnc: String?): Boolean =
    mcc == "440" && mnc in setOf("50", "51", "53", "54")

/** セル選定用のPLMN情報(純関数テスト用の抽象) */
data class PlmnCell(val mcc: String?, val mnc: String?, val registered: Boolean)

/**
 * 計測対象セルのインデックスを選ぶ。優先順:
 * 在圏中の楽天 → 楽天(非在圏含む) → 在圏中のauローミング → auローミング(非在圏含む)。
 * DSDS端末はデータ通信SIM以外のセルを isRegistered=false で報告する機種があるため、
 * PLMNが楽天/auなら在圏フラグが立っていなくても採用する。該当なしは null(楽天として圏外)。
 */
fun selectCellIndexByPlmn(cells: List<PlmnCell>): Int? {
    val rakuten = cells.withIndex().filter { isRakutenPlmn(it.value.mcc, it.value.mnc) }
    val partner = cells.withIndex().filter { isPartnerRoamingPlmn(it.value.mcc, it.value.mnc) }
    return (rakuten.firstOrNull { it.value.registered } ?: rakuten.firstOrNull()
        ?: partner.firstOrNull { it.value.registered } ?: partner.firstOrNull())?.index
}

/**
 * NR-ARFCN からバンド名を逆引きする(楽天が使用するバンドのみ)。
 * CellIdentityNr.bands はAPI 30+専用のうえ、対応端末でもNSA接続時等に空を返す
 * 機種が多いため、周波数チャネル番号からのフォールバックとして使う。
 */
fun nrArfcnToBand(arfcn: Int): String? = when (arfcn) {
    in 620000..680000 -> "n77"   // 3.3-4.2GHz (Sub6)
    in 151600..160600 -> "n28"   // 700MHz (プラチナ)
    else              -> null
}

/**
 * EARFCN(LTE) からバンド名を逆引きする(楽天自社網＋auローミング帯のみ)。
 * ドコモ専用帯(Band 1/19/21等)は意図的に対象外(nullを返す)。
 */
fun earfcnToBand(earfcn: Int): String? = when (earfcn) {
    in 1200..1949 -> "Band 3"    // 1.7GHz (楽天メイン)
    in 5850..5999 -> "Band 18"   // 800MHz (auローミング)
    in 8690..9039 -> "Band 26"   // 800MHz (auローミング)
    in 9210..9659 -> "Band 28"   // 700MHz (楽天プラチナ)
    else          -> null
}

/**
 * TelephonyManager を使って端末の通信状態を収集するクラス。
 * 機内モード・SIM不在を優先的に検出し、それ以外はバンド・RSSI・キャリアを取得する。
 * DUAL SIM対応: 在圏セルを楽天PLMN(440-11)＋auローミング(440-50/51/53/54)で選別し、
 * 他社SIM(ドコモ等)のセルは計測対象から構造的に除外する。
 */
class NetworkInfoCollector(private val context: Context) {

    private companion object {
        /** requestCellInfoUpdate の応答待ちタイムアウト */
        const val CELL_INFO_UPDATE_TIMEOUT_MS = 2000L
    }

    private val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    /**
     * 端末の現在の通信状態を収集して NetworkSnapshot として返す。
     * 優先順: 機内モード → SIMなし → 圏外 → 通常の電波
     * API 29+ では requestCellInfoUpdate でモデムに最新セル情報を要求してから読む
     * (getAllCellInfo はOSのキャッシュを返すだけで、SIM入れ替え後などに古いバンドを
     *  返し続けることがあるため)。
     * @return NetworkSnapshot (isValidMeasurement=false なら計測データとしては無効)
     */
    @SuppressLint("MissingPermission")
    suspend fun collect(): NetworkSnapshot {
        if (isAirplaneMode()) {
            return NetworkSnapshot("AIRPLANE_MODE", null, null, null, isValidMeasurement = false)
        }
        if (isNoSim()) {
            return NetworkSnapshot("NO_SIM", null, null, null, isValidMeasurement = false)
        }
        // API 26-28: セル情報が取れないため従来のデフォルトSIM基準で判定(楽天SIM単独運用前提)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val networkType = resolveLegacyNetworkType()
            if (networkType == "NO_SERVICE") {
                return NetworkSnapshot("NO_SERVICE", null, null, null, isValidMeasurement = true)
            }
            val carrier = tm.networkOperatorName.takeIf { it.isNotBlank() }
            return NetworkSnapshot(networkType, null, null, carrier, isValidMeasurement = true)
        }

        // API 29+: 在圏セルを楽天/auローミングPLMNで選別。他社SIM(ドコモ等)のセルは採用しない
        val cell = selectServingCell(freshCellInfo())
            ?: return NetworkSnapshot("NO_SERVICE", null, null, null, isValidMeasurement = true)

        val networkType = if (cell is CellInfoNr) "5G" else "LTE"
        val (band, rssi, cellId) = resolveBandRssiAndCellId(cell)
        return NetworkSnapshot(networkType, band, rssi, "Rakuten", isValidMeasurement = true, cellId = cellId)
    }

    /**
     * セル一覧から計測対象セルを選ぶ(優先順は selectCellIndexByPlmn を参照)。
     * 5G(NR)とLTEのみ対象(楽天に3G/2Gは存在しない)。
     */
    private fun selectServingCell(cellInfo: List<CellInfo>): CellInfo? {
        val candidates = cellInfo.filter { it is CellInfoNr || it is CellInfoLte }
        val index = selectCellIndexByPlmn(
            candidates.map { PlmnCell(it.mcc(), it.mnc(), it.isRegistered) }
        ) ?: return null
        return candidates[index]
    }

    private fun CellInfo.mcc(): String? = when (this) {
        is CellInfoNr  -> (cellIdentity as CellIdentityNr).mccString
        is CellInfoLte -> (cellIdentity as CellIdentityLte).mccString
        else           -> null
    }

    private fun CellInfo.mnc(): String? = when (this) {
        is CellInfoNr  -> (cellIdentity as CellIdentityNr).mncString
        is CellInfoLte -> (cellIdentity as CellIdentityLte).mncString
        else           -> null
    }

    /**
     * API 29+ で requestCellInfoUpdate によりモデムへ最新セル情報を要求し、
     * 結果を最大2秒待って返す。タイムアウト・エラー時は従来どおり
     * getAllCellInfo (キャッシュ) にフォールバックする。API 29 未満は空リスト。
     */
    @SuppressLint("MissingPermission")
    private suspend fun freshCellInfo(): List<CellInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()

        val fresh: List<CellInfo>? = withTimeoutOrNull(CELL_INFO_UPDATE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                try {
                    tm.requestCellInfoUpdate(
                        Executor { it.run() },
                        object : TelephonyManager.CellInfoCallback() {
                            override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                                if (cont.isActive) cont.resume(cellInfo)
                            }

                            override fun onError(errorCode: Int, detail: Throwable?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        }
                    )
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        // 空リストは「更新は成功したが中身なし」を意味する機種があるため、キャッシュにフォールバックする
        return fresh?.takeIf { it.isNotEmpty() }
            ?: runCatching { tm.allCellInfo }.getOrNull()
            ?: emptyList()
    }

    /**
     * 診断用: 現在のセル一覧を人間可読の複数行テキストで返す。
     * DUAL SIM機で楽天セルが見えているか(PLMN・在圏フラグ・ARFCN)を実機で確認するためのもの。
     */
    @SuppressLint("MissingPermission")
    suspend fun debugCellDump(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return "API ${Build.VERSION.SDK_INT}: セル情報の取得に非対応"
        }
        val cells = freshCellInfo()
        val sb = StringBuilder()
        sb.appendLine("simState=${tm.simState} dataOperator=${tm.networkOperatorName}")
        sb.appendLine("cells=${cells.size}")
        cells.forEachIndexed { i, c ->
            val line = when (c) {
                is CellInfoNr -> {
                    val id = c.cellIdentity as CellIdentityNr
                    val bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) id.bands.toList().toString() else "-"
                    "NR  reg=${c.isRegistered} plmn=${id.mccString}-${id.mncString} nrarfcn=${id.nrarfcn} bands=$bands"
                }
                is CellInfoLte -> {
                    val id = c.cellIdentity as CellIdentityLte
                    val bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) id.bands.toList().toString() else "-"
                    "LTE reg=${c.isRegistered} plmn=${id.mccString}-${id.mncString} earfcn=${id.earfcn} bands=$bands"
                }
                is CellInfoWcdma -> "3G  reg=${c.isRegistered}"
                is CellInfoGsm   -> "2G  reg=${c.isRegistered}"
                else             -> c.javaClass.simpleName
            }
            sb.appendLine("[$i] $line")
        }
        return sb.toString().trimEnd()
    }

    /** Settings.Global.AIRPLANE_MODE_ON が 1 なら機内モード */
    private fun isAirplaneMode(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    /**
     * 全スロットのSIMが ABSENT / UNKNOWN なら SIM 不在とみなす。
     * 引数なしの simState はデフォルトSIMしか見ないため、DUAL SIM機で
     * 片方のスロットだけにSIMがある構成を誤ってNO_SIM判定しないよう全スロットを走査する。
     */
    private fun isNoSim(): Boolean {
        val slotCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tm.activeModemCount
        } else {
            @Suppress("DEPRECATION") tm.phoneCount
        }.coerceAtLeast(1)
        return (0 until slotCount).all { slot ->
            val state = tm.getSimState(slot)
            state == TelephonyManager.SIM_STATE_ABSENT || state == TelephonyManager.SIM_STATE_UNKNOWN
        }
    }

    /**
     * API 26-28 用のネットワーク種別判定。dataNetworkType を使用
     * （READ_PHONE_STATE が必要、maxSdkVersion=28 で宣言済み）。
     * SecurityException は念のためキャッチし NO_SERVICE を返す。
     */
    @SuppressLint("MissingPermission")
    private fun resolveLegacyNetworkType(): String = try {
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

    /**
     * 採用セルからバンド名・RSSI・セルIDを取得する。
     * バンド名は CellIdentityNr/Lte.bands (API 30+) を優先し、空・未対応の場合は
     * ARFCN(周波数チャネル番号)からの逆引き (nrArfcnToBand / earfcnToBand) にフォールバック
     * (NSA接続等で bands が空を返す機種・API 29 端末への対応)。
     * @return Triple(バンド名, RSSI dBm, セルID)
     */
    private fun resolveBandRssiAndCellId(info: CellInfo): Triple<String?, Int?, String?> {
        return when (info) {
            is CellInfoNr -> {
                val identity = info.cellIdentity as CellIdentityNr
                val signal   = info.cellSignalStrength as CellSignalStrengthNr
                val fromApi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    identity.bands.firstOrNull()?.let { bandNumberToName(it, isNr = true) }
                else null
                val bandName = fromApi
                    ?: identity.nrarfcn.takeIf { it != CellInfo.UNAVAILABLE }?.let { nrArfcnToBand(it) }
                val cellId   = identity.nci.takeIf { it != Long.MAX_VALUE }?.toString()
                Triple(bandName, signal.dbm.takeIfAvailable(), cellId)
            }
            is CellInfoLte -> {
                val identity = info.cellIdentity as CellIdentityLte
                val signal   = info.cellSignalStrength as CellSignalStrengthLte
                val fromApi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    identity.bands.firstOrNull()?.let { bandNumberToName(it, isNr = false) }
                else null
                val bandName = fromApi
                    ?: identity.earfcn.takeIf { it != CellInfo.UNAVAILABLE }?.let { earfcnToBand(it) }
                val cellId = identity.ci.takeIf { it != Int.MAX_VALUE }?.toString()
                Triple(bandName, signal.dbm.takeIfAvailable(), cellId)
            }
            else -> Triple(null, null, null)
        }
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
