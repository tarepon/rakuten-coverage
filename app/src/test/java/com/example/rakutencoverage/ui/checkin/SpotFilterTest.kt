package com.example.rakutencoverage.ui.checkin

import com.example.rakutencoverage.data.Spot
import com.example.rakutencoverage.data.SpotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SpotFilter.filter / PREF_ORDER の単体テスト。
 * Spot は純粋なデータクラスなのでローカルJVM単体テスト(Robolectric不使用)で直接生成できる。
 */
class SpotFilterTest {

    private fun arena(id: String, name: String, pref: String, city: String = "", club: String, division: String) =
        Spot(id = id, name = name, latitude = 0.0, longitude = 0.0, type = SpotType.ARENA, pref = pref, city = city, club = club, division = division)

    private fun michinoeki(id: String, name: String, pref: String, city: String) =
        Spot(id = id, name = name, latitude = 0.0, longitude = 0.0, type = SpotType.MICHINOEKI, pref = pref, city = city)

    private val arenas = listOf(
        arena("arena_004", "北海きたえーる", pref = "北海道", club = "レバンガ北海道", division = "PREMIER"),
        arena("arena_006", "ゼビオアリーナ仙台", pref = "宮城県", club = "仙台89ERS", division = "PREMIER"),
        arena("arena_099", "サンロータス弘前", pref = "青森県", club = "青森ワッツ", division = "ONE")
    )

    private val michinoekis = listOf(
        michinoeki("michi_v1", "道の駅 三笠", pref = "北海道", city = "三笠市"),
        michinoeki("michi_v2", "道の駅 スタープラザ 芦別", pref = "北海道", city = "芦別市"),
        michinoeki("michi_v3", "道の駅 南ふらの", pref = "北海道", city = "空知郡南富良野町")
    )

    @Test
    fun queryMatchesNamePrefCityOrClub() {
        // name部分一致
        assertEquals(listOf("arena_004"), SpotFilter.filter(arenas, "きたえーる", null, null, null, emptyMap()).map { it.id })
        // pref部分一致
        assertEquals(listOf("arena_006"), SpotFilter.filter(arenas, "宮城", null, null, null, emptyMap()).map { it.id })
        // club部分一致
        assertEquals(listOf("arena_099"), SpotFilter.filter(arenas, "青森ワッツ", null, null, null, emptyMap()).map { it.id })
        // city部分一致(道の駅)
        assertEquals(listOf("michi_v3"), SpotFilter.filter(michinoekis, "南富良野", null, null, null, emptyMap()).map { it.id })
        // 空クエリは全件通す
        assertEquals(3, SpotFilter.filter(arenas, "", null, null, null, emptyMap()).size)
        // 空白のみも全件通す(トリムされる)
        assertEquals(3, SpotFilter.filter(arenas, "   ", null, null, null, emptyMap()).size)
    }

    @Test
    fun prefFilterNarrowsToMatchingPrefOnly() {
        val result = SpotFilter.filter(arenas, "", pref = "北海道", maxDistanceM = null, division = null, distances = emptyMap())
        assertEquals(listOf("arena_004"), result.map { it.id })
    }

    @Test
    fun prefNullOrSubeteMeansNoFilter() {
        assertEquals(3, SpotFilter.filter(arenas, "", pref = null, maxDistanceM = null, division = null, distances = emptyMap()).size)
        assertEquals(3, SpotFilter.filter(arenas, "", pref = "すべて", maxDistanceM = null, division = null, distances = emptyMap()).size)
    }

    @Test
    fun distanceFilterExcludesBeyondMaxAndSpotsMissingFromDistanceMap() {
        // arena_004:1000m, arena_006:20000m。arena_099はdistances未収録(位置情報未取得扱い)
        val distances = mapOf("arena_004" to 1000f, "arena_006" to 20000f)
        val result = SpotFilter.filter(arenas, "", pref = null, maxDistanceM = 10000f, division = null, distances = distances)
        assertEquals(listOf("arena_004"), result.map { it.id })
    }

    @Test
    fun distanceMapEmptyDisablesDistanceFilterEvenIfMaxSpecified() {
        // distances空の場合は距離条件・距離ソートを無効化する(仕様どおり)
        val result = SpotFilter.filter(arenas, "", pref = null, maxDistanceM = 1f, division = null, distances = emptyMap())
        assertEquals(3, result.size)
    }

    @Test
    fun divisionFilterNarrowsToMatchingDivisionOnly() {
        val result = SpotFilter.filter(arenas, "", pref = null, maxDistanceM = null, division = "ONE", distances = emptyMap())
        assertEquals(listOf("arena_099"), result.map { it.id })
    }

    @Test
    fun combinedPrefAndDivisionFilterAppliesBothConditions() {
        val moreArenas = arenas + arena("arena_100", "青森アリーナB", pref = "青森県", club = "別クラブ", division = "PREMIER")
        val result = SpotFilter.filter(moreArenas, "", pref = "青森県", maxDistanceM = null, division = "ONE", distances = emptyMap())
        assertEquals(listOf("arena_099"), result.map { it.id })
    }

    @Test
    fun sortsByDistanceAscendingWhenDistancesProvided() {
        val distances = mapOf("arena_004" to 5000f, "arena_006" to 100f, "arena_099" to 2000f)
        val result = SpotFilter.filter(arenas, "", pref = null, maxDistanceM = null, division = null, distances = distances)
        assertEquals(listOf("arena_006", "arena_099", "arena_004"), result.map { it.id })
    }

    @Test
    fun sortsByPrefJisOrderThenNameWhenNoDistances() {
        // 北海道(全件同一pref) -> 名前の五十音(文字列比較)順、宮城県は北海道より後、青森県は北海道の直後
        val result = SpotFilter.filter(arenas, "", pref = null, maxDistanceM = null, division = null, distances = emptyMap())
        // PREF_ORDER: 北海道(0) < 青森県(1) < 宮城県(3)
        assertEquals(listOf("arena_004", "arena_099", "arena_006"), result.map { it.id })

        val michiResult = SpotFilter.filter(michinoekis, "", pref = null, maxDistanceM = null, division = null, distances = emptyMap())
        // 同一pref(北海道)内は名前順: "道の駅 スタープラザ 芦別" < "道の駅 三笠" < "道の駅 南ふらの"
        assertEquals(listOf("michi_v2", "michi_v1", "michi_v3"), michiResult.map { it.id })
    }

    @Test
    fun prefOrderHas47UniquePrefectures() {
        assertEquals(47, SpotFilter.PREF_ORDER.size)
        assertEquals(47, SpotFilter.PREF_ORDER.toSet().size)
        assertEquals("北海道", SpotFilter.PREF_ORDER.first())
        assertEquals("沖縄県", SpotFilter.PREF_ORDER.last())
        // JIS順の一部区間チェック(ユーザー指摘の間違いやすいポイント: 高知県の次は福岡県)
        assertEquals(
            listOf("高知県", "福岡県", "佐賀県", "長崎県", "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県"),
            SpotFilter.PREF_ORDER.takeLast(9)
        )
        assertTrue(SpotFilter.PREF_ORDER.all { it.isNotBlank() })
    }
}
