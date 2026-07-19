package com.example.rakutencoverage.util

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JST表示変換・JST日界のUTC境界生成の単体テスト */
class TimeUtilsTest {

    @Test
    fun `UTC文字列をJST表示に変換する`() {
        assertEquals("2026/07/19 18:41", formatJst("2026-07-19T09:41:59Z"))
    }

    @Test
    fun `秒以下の桁があっても変換できる`() {
        assertEquals("2026/07/19 18:41", formatJst("2026-07-19T09:41:59.123456Z"))
    }

    @Test
    fun `日付をまたぐ変換 - UTC15時以降はJSTの翌日`() {
        assertEquals("2026/07/20 00:30", formatJst("2026-07-19T15:30:00Z"))
    }

    @Test
    fun `パターン指定 - 日付のみ`() {
        assertEquals("2026-07-19", formatJst("2026-07-19T09:41:59Z", "yyyy-MM-dd"))
    }

    @Test
    fun `パース不能な文字列はそのまま返す`() {
        assertEquals("broken", formatJst("broken"))
    }

    @Test
    fun `JSTの今日に対応するUTC境界 - 通常時刻`() {
        // JST 2026-07-19 18:41 → JSTの7/19はUTCの 7/18 15:00 〜 7/19 15:00
        val (start, end) = jstTodayUtcRange(Instant.parse("2026-07-19T09:41:59Z"))
        assertEquals("2026-07-18T15:00", start)
        assertEquals("2026-07-19T15:00", end)
    }

    @Test
    fun `JSTの今日に対応するUTC境界 - 深夜0時前後で日付が切り替わる`() {
        // UTC 14:59 = JST 23:59 (まだ7/18)
        val (s1, _) = jstTodayUtcRange(Instant.parse("2026-07-18T14:59:59Z"))
        assertEquals("2026-07-17T15:00", s1)
        // UTC 15:00 = JST 0:00 (7/19になる)
        val (s2, _) = jstTodayUtcRange(Instant.parse("2026-07-18T15:00:00Z"))
        assertEquals("2026-07-18T15:00", s2)
    }

    @Test
    fun `境界文字列は保存形式との辞書順比較で正しく機能する`() {
        val (start, end) = jstTodayUtcRange(Instant.parse("2026-07-19T09:41:59Z"))
        // 境界秒ちょうど+ミリ秒付き(Instant.toString()の揺れ)も範囲内に入る
        assertTrue("2026-07-18T15:00:00.123456Z" >= start)
        assertTrue("2026-07-18T15:00:00Z" >= start)
        assertTrue("2026-07-19T09:41:59Z" < end)
        // 前日末尾は範囲外
        assertTrue("2026-07-18T14:59:59.999Z" < start)
    }
}
