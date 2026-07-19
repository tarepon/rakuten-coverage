package com.example.rakutencoverage.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 日本専用アプリのため、表示は端末設定に依らずJST固定 */
val JST: ZoneId = ZoneId.of("Asia/Tokyo")

/**
 * ISO8601 UTC文字列 (例: "2026-07-19T09:41:59Z") をJST表示文字列に変換する。
 * パース不能な場合は入力をそのまま返す (既存データが壊れていても画面を壊さない)。
 */
fun formatJst(isoUtc: String, pattern: String = "yyyy/MM/dd HH:mm"): String =
    runCatching {
        DateTimeFormatter.ofPattern(pattern).withZone(JST).format(Instant.parse(isoUtc))
    }.getOrElse { isoUtc }

/**
 * JSTの「今日」に対応するUTC側のISO8601境界文字列を返す (start以上・end未満)。
 * 保存タイムスタンプ (Instant.toString() — 秒以下の桁数が揺れる) との辞書順比較で
 * 正しく機能するよう、境界は分精度 ("yyyy-MM-dd'T'HH:mm") で生成する
 * (秒まで含めると "…00.123Z" < "…00Z" の辞書順逆転が境界秒で起きるため)。
 */
fun jstTodayUtcRange(now: Instant = Instant.now()): Pair<String, String> {
    val today = now.atZone(JST).toLocalDate()
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ZoneOffset.UTC)
    return fmt.format(today.atStartOfDay(JST).toInstant()) to
        fmt.format(today.plusDays(1).atStartOfDay(JST).toInstant())
}
