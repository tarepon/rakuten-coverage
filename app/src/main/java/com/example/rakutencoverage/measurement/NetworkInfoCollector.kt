package com.example.rakutencoverage.measurement

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.telephony.*
import android.util.Log
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

/**
 * セル選定用のPLMN情報(純関数テスト用の抽象)。
 * isNr / hasSignal は 5G NSA 対応で追加 (デフォルト値により既存の呼び出しと互換)。
 */
data class PlmnCell(
    val mcc: String?,
    val mnc: String?,
    val registered: Boolean,
    val isNr: Boolean = false,
    val hasSignal: Boolean = true
)

/**
 * 計測対象セルのインデックスを選ぶ。優先順:
 * 在圏中の楽天NR → 有効な信号強度を持つ楽天NR → 在圏中の楽天 → 楽天(非在圏含む)
 * → 在圏中のauローミング → auローミング(非在圏含む)。該当なしは null(楽天として圏外)。
 *
 * NR優先の背景: 5G NSA構成 (LTEアンカー + NRセカンダリセル) では在圏フラグは
 * LTEアンカー側に立ち、NRセルは isRegistered=false で報告される端末が多い。
 * 在圏優先だけだとステータスバーが5GでもLTEアンカーを選んでしまうため、
 * 楽天PLMNのNRセルは在圏フラグより優先する (信号強度が取れない孤立NRは除く)。
 *
 * DSDS端末はデータ通信SIM以外のセルを isRegistered=false で報告する機種があるため、
 * PLMNが楽天/auなら在圏フラグが立っていなくても採用する。
 */
fun selectCellIndexByPlmn(cells: List<PlmnCell>): Int? {
    val rakuten = cells.withIndex().filter { isRakutenPlmn(it.value.mcc, it.value.mnc) }
    val partner = cells.withIndex().filter { isPartnerRoamingPlmn(it.value.mcc, it.value.mnc) }
    return (rakuten.firstOrNull { it.value.isNr && it.value.registered }
        ?: rakuten.firstOrNull { it.value.isNr && it.value.hasSignal }
        ?: rakuten.firstOrNull { it.value.registered }
        // 最終フォールバックは非NRを優先: 信号強度の取れない孤立NR(優先順2で漏れたもの)を
        // 選ぶと RSSI=null の 5G レコードが生まれるため、LTE があればそちらを使う
        ?: rakuten.firstOrNull { !it.value.isNr }
        ?: rakuten.firstOrNull()
        ?: partner.firstOrNull { it.value.registered }
        ?: partner.firstOrNull())?.index
}

/**
 * TelephonyDisplayInfo 由来の NSA 5G 接続情報でセル由来の判定を補正する純粋関数。
 * セル一覧に NR セルが一切現れない端末では、在圏 LTE アンカーのまま NSA 5G 通信
 * していることがあるため、LTE + NR 接続中 → "5G" に昇格する。
 * その際バンドは LTE アンカーのもの (NR バンドではない) のため null に落とす
 * ("Band 28" アンカーが 5G n28 プラチナと誤判定されるのを防ぐ。resolveSignalLevel 参照)。
 * LTE 以外 (すでに 5G 判定済み・圏外等) はそのまま返す。
 *
 * isRakutenCell ゲート: 昇格は選択セルが楽天自社網 (440-11) のときに限る。
 * auローミングセルしか見えない場所で DisplayInfo の NR 状態 (直前の楽天5G圏の残存や
 * コールバック遅延で古い値が残りうる) を適用すると、楽天5Gが無い場所を
 * 5G として記録してしまうため。
 */
fun applyNrOverride(
    networkType: String,
    band: String?,
    nrConnected: Boolean,
    isRakutenCell: Boolean = true
): Pair<String, String?> =
    if (nrConnected && isRakutenCell && networkType == "LTE") "5G" to null else networkType to band

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
 *
 * 5G 判定は 2 系統の併用:
 *  - セル一覧に現れる楽天NRセルの検出 (SA / NRセルを報告するNSA端末)。selectCellIndexByPlmn がNR優先
 *  - DisplayInfoMonitor (TelephonyDisplayInfo) による NSA 補正 (API 31+。
 *    NRセルを報告しないNSA端末でもステータスバーの5G表示と一致させる)
 */
class NetworkInfoCollector(private val context: Context) {

    private companion object {
        /** requestCellInfoUpdate の応答待ちタイムアウト */
        const val CELL_INFO_UPDATE_TIMEOUT_MS = 2000L

        /** 楽天モバイルSIMのPLMN (simOperatorの返り値) */
        const val RAKUTEN_SIM_OPERATOR = "44011"

        /** subId総当たりの上限(subIdはSIM挿入ごとに1から連番で払い出される) */
        const val MAX_PROBE_SUB_ID = 20

        /** 楽天SIM探索ミスのネガティブキャッシュ有効期間 */
        const val PROBE_MISS_TTL_MS = 60_000L
    }

    private val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    // 注意: ここで DisplayInfoMonitor.ensureStarted は呼ばない。
    // デフォルトTMで先に購読すると、DSDS機では初回 collect() で楽天SIM側への
    // 張り替えが必ず発生し、また設定画面の診断用に生成される一時的なコレクタが
    // プロセス共有の購読先を書き換えてしまうため、購読は collect() 内で
    // 楽天SIM特定後の targetTm に対してのみ行う。

    /** 楽天SIMサブスクリプション専用のTelephonyManager(見つかればキャッシュ) */
    private var cachedRakutenTm: TelephonyManager? = null

    /** 直近の探索で楽天SIMが見つからなかった時刻 (SystemClock.elapsedRealtime)。0=未探索 */
    private var lastProbeMissAt: Long = 0L

    /**
     * 楽天SIMのサブスクリプションに紐づくTelephonyManagerを探す。
     * DUAL SIM機ではデフォルトのTelephonyManagerがデータ通信SIM側のセルしか
     * 返さない機種があるため、楽天SIM専用インスタンスでセル情報を取る必要がある。
     * simOperator(SIMのPLMN)は権限不要で読めるため、デフォルト系subId＋総当たりで特定する。
     * 見つからなければ null(楽天SIM未挿入、またはシングルSIMでデフォルトが楽天でない)。
     *
     * ネガティブキャッシュ: 総当たりは subId ごとに simOperator の binder IPC を伴い、
     * 計測ループ(数秒間隔)で毎回やり直すと楽天SIM非搭載時に恒常的な負荷になるため、
     * ミス結果を PROBE_MISS_TTL_MS の間キャッシュする (SIM挿入はTTL経過後に検出される)。
     */
    private fun rakutenTelephonyManager(): TelephonyManager? {
        cachedRakutenTm?.let { cached ->
            if (runCatching { cached.simOperator }.getOrNull() == RAKUTEN_SIM_OPERATOR) return cached
            cachedRakutenTm = null  // SIM入れ替え等で無効化された場合は再探索
        }
        if (lastProbeMissAt != 0L &&
            SystemClock.elapsedRealtime() - lastProbeMissAt < PROBE_MISS_TTL_MS) {
            return null
        }
        val candidates = buildList {
            add(SubscriptionManager.getDefaultDataSubscriptionId())
            add(SubscriptionManager.getDefaultVoiceSubscriptionId())
            add(SubscriptionManager.getDefaultSmsSubscriptionId())
            add(SubscriptionManager.getDefaultSubscriptionId())
            addAll(0..MAX_PROBE_SUB_ID)
        }.distinct().filter { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        for (subId in candidates) {
            val candidate = runCatching { tm.createForSubscriptionId(subId) }.getOrNull() ?: continue
            if (runCatching { candidate.simOperator }.getOrNull() == RAKUTEN_SIM_OPERATOR) {
                cachedRakutenTm = candidate
                lastProbeMissAt = 0L
                return candidate
            }
        }
        lastProbeMissAt = SystemClock.elapsedRealtime()
        return null
    }

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

        // DUAL SIM機ではデフォルトTMがデータSIM側のセルしか返さない機種があるため、
        // 楽天SIM専用のTelephonyManagerが見つかればセル情報・DisplayInfoともそちらで取る
        val targetTm = rakutenTelephonyManager() ?: tm
        DisplayInfoMonitor.ensureStarted(context, targetTm)

        // API 29+: 在圏セルを楽天/auローミングPLMNで選別。他社SIM(ドコモ等)のセルは採用しない
        val candidates = freshCellInfo(targetTm).filter { it is CellInfoNr || it is CellInfoLte }
        val plmnCells = candidates.map {
            PlmnCell(it.mcc(), it.mnc(), it.isRegistered, it.isNrCell(), it.hasValidSignal())
        }
        val index = selectCellIndexByPlmn(plmnCells)
        if (index == null) {
            Log.d(DIAG_TAG, "楽天/auセルなし cells=[${plmnCells.summary()}] " +
                "nrOverride=${DisplayInfoMonitor.isNrConnected} → NO_SERVICE")
            return NetworkSnapshot("NO_SERVICE", null, null, null, isValidMeasurement = true)
        }
        val cell = candidates[index]
        val rawType = if (cell is CellInfoNr) "5G" else "LTE"
        val (rawBand, rssi, cellId) = resolveBandRssiAndCellId(cell)
        // NRセルがリストに現れない端末のNSA 5GはTelephonyDisplayInfoで補正
        // (RSSI・セルIDは実測可能なLTEアンカーのものをそのまま記録する)。
        // auローミングセル選択時は昇格しない(applyNrOverrideのisRakutenCellゲート参照)
        val selected = plmnCells[index]
        val (networkType, band) = applyNrOverride(
            rawType, rawBand,
            nrConnected = DisplayInfoMonitor.isNrConnected,
            isRakutenCell = isRakutenPlmn(selected.mcc, selected.mnc)
        )
        Log.d(DIAG_TAG, "cells=[${plmnCells.summary()}] 選択=$index " +
            "nrOverride=${DisplayInfoMonitor.isNrConnected} → $networkType・${band ?: "?"}・${rssi ?: "?"}dBm")
        return NetworkSnapshot(networkType, band, rssi, "Rakuten", isValidMeasurement = true, cellId = cellId)
    }

    /** 診断ログ用のセル一覧サマリ (例: "LTE(440-11)*, NR(440-11)") */
    private fun List<PlmnCell>.summary(): String = joinToString { c ->
        "${if (c.isNr) "NR" else "LTE"}(${c.mcc}-${c.mnc})" +
            (if (c.registered) "*" else "") + (if (!c.hasSignal) "(信号なし)" else "")
    }

    private fun CellInfo.isNrCell(): Boolean = this is CellInfoNr

    /** NR/LTEセルの信号強度が取得可能か (それ以外のセル種別は選定対象外のため false で足りる) */
    private fun CellInfo.hasValidSignal(): Boolean = when (this) {
        is CellInfoNr  -> cellSignalStrength.dbm.takeIfAvailable() != null
        is CellInfoLte -> cellSignalStrength.dbm.takeIfAvailable() != null
        else           -> false
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
    private suspend fun freshCellInfo(targetTm: TelephonyManager): List<CellInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()

        val fresh: List<CellInfo>? = withTimeoutOrNull(CELL_INFO_UPDATE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                try {
                    targetTm.requestCellInfoUpdate(
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
            ?: runCatching { targetTm.allCellInfo }.getOrNull()
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
        val rakutenTm = rakutenTelephonyManager()
        val cells = freshCellInfo(rakutenTm ?: tm)
        val sb = StringBuilder()
        sb.appendLine("simState=${tm.simState} dataOperator=${tm.networkOperatorName}")
        sb.appendLine("rakutenSim=${if (rakutenTm != null) "検出(セル取得は楽天SIM経由)" else "未検出(デフォルトSIM経由)"}")
        sb.appendLine("displayInfoNr=${DisplayInfoMonitor.isNrConnected} (ステータスバー相当のNSA 5G判定)")
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
