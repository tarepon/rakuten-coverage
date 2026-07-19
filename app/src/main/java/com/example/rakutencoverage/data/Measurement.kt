package com.example.rakutencoverage.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 1回の計測結果を表す Room エンティティ。
 * 通常計測とアリーナチェックイン計測を兼用する。
 * アリーナフィールド (arenaId 以降) は通常計測時はすべて null。
 *
 * @property networkType 通信種別文字列 ("5G" / "LTE" / "NO_SERVICE" / "AIRPLANE_MODE" / "NO_SIM" 等)
 * @property band        バンド名 ("Band 28" / "n77" 等)。取得不可なら null
 * @property rssi        電波強度 dBm。取得不可なら null
 * @property rttMs       RTT ms。圏外・失敗時は -1
 * @property carrier     キャリア名。取得不可なら null
 */
@Entity(tableName = "measurements")
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,      // ISO8601 形式 (例: "2025-06-01T12:34:56Z")
    val networkType: String,
    val band: String?,
    val rssi: Int?,
    val rttMs: Int,
    val carrier: String?,
    val arenaId: String?    = null,   // スポットマスタの GeoJSON feature id
    val arenaName: String?  = null,   // 表示用スポット名
    val seatLabel: String?  = null,   // 例: "A-12列-5番"
    val gamePhase: String?  = null,   // GamePhase.name
    val cellId: String?     = null    // 基地局ID (LTE: ci, NR: nci)。将来の図鑑用に保存
) {
    /**
     * networkType と band の組み合わせから信号レベルを導出する。
     * 楽天プラチナバンド判定: LTE Band 28 / 5G n28。
     * auローミング判定: Band 18 / Band 26 を含む場合は WEAK 扱い。
     */
    val signalLevel: SignalLevel get() = resolveSignalLevel(networkType, band)

    /** チェックインモードかどうか (arenaId が設定されていれば true) */
    val isArenaMode: Boolean get() = arenaId != null
}

/** networkType と band から SignalLevel を導出する純粋関数。Measurement.signalLevel はこれに委譲する。 */
fun resolveSignalLevel(networkType: String, band: String?): SignalLevel = when (networkType) {
    "AIRPLANE_MODE"          -> SignalLevel.AIRPLANE_MODE
    "NO_SIM"                 -> SignalLevel.NO_SIM
    "NO_SERVICE", "UNKNOWN"  -> SignalLevel.NO_SIGNAL
    "5G" -> when {
        band == "Band 28" || band == "n28" -> SignalLevel.PLATINUM_5G
        else                               -> SignalLevel.FIVE_G
    }
    "LTE" -> when {
        band == "Band 28" || band == "n28"                           -> SignalLevel.PLATINUM
        band != null && !band.contains("18") && !band.contains("26") -> SignalLevel.LTE
        else                                                          -> SignalLevel.WEAK
    }
    else -> SignalLevel.WEAK
}

/**
 * 信号レベルの分類。強い順に並んでいる。
 * WEAK 以下は楽天回線として有効な通信ができていない状態。
 * MILLIMETER_WAVE(n257)は対応端末が存在せずエリアも極小のため削除済み(n257計測時はFIVE_G扱い)。
 */
enum class SignalLevel {
    PLATINUM_5G,     // 5G + プラチナバンド (n28)
    FIVE_G,          // 5G Sub6 (非プラチナ)
    PLATINUM,        // LTE Band 28 (プラチナバンド)
    LTE,             // LTE (楽天回線) ← コモン
    WEAK,            // 弱電界 / au パートナー回線 ← 闇
    NO_SIGNAL,       // 圏外 (SIM あり・機内モードなし) ← 闇
    AIRPLANE_MODE,   // 機内モード中 → スタンプ無効
    NO_SIM           // SIM 未挿入 → スタンプ無効
}

/**
 * 試合の進行フェーズ。アリーナチェックイン時に選択する。
 * BREAK_Q1_Q2 / BREAK_Q3_Q4 (クォーター間の休憩) を追加。
 * 既存データは GamePhase.name (文字列) で保存されているため、列挙子の追加は安全 (順序変更・削除は不可)。
 */
enum class GamePhase(val label: String) {
    PRE_GAME("試合前"),
    Q1("第1Q"),
    BREAK_Q1_Q2("1-2Q間"),
    Q2("第2Q"),
    HALFTIME("ハーフタイム"),
    Q3("第3Q"),
    BREAK_Q3_Q4("3-4Q間"),
    Q4("第4Q"),
    OVERTIME("延長"),
    POST_GAME("試合後")
}

/**
 * チェックイン入力画面のフェーズ自動送りに使う純粋関数。
 * enum順でordinal+1の次フェーズを返す。末尾(POST_GAME)は据え置き。
 */
fun nextPhase(current: GamePhase): GamePhase {
    val entries = GamePhase.entries
    val nextIndex = current.ordinal + 1
    return if (nextIndex < entries.size) entries[nextIndex] else current
}
