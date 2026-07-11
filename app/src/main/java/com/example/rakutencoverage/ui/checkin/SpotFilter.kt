package com.example.rakutencoverage.ui.checkin

import com.example.rakutencoverage.data.Spot

/**
 * スポットピッカー(CheckInInputScreen)向けの検索・絞り込み・ソートを担う純粋関数群。
 * Composeに依存しないため、ローカルJVM単体テストで検証できる。
 */
object SpotFilter {

    /** JIS X 0401 都道府県コード順(01北海道〜47沖縄県) */
    val PREF_ORDER: List<String> = listOf(
        "北海道", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県",
        "茨城県", "栃木県", "群馬県", "埼玉県", "千葉県", "東京都", "神奈川県",
        "新潟県", "富山県", "石川県", "福井県", "山梨県", "長野県", "岐阜県",
        "静岡県", "愛知県", "三重県", "滋賀県", "京都府", "大阪府", "兵庫県",
        "奈良県", "和歌山県", "鳥取県", "島根県", "岡山県", "広島県", "山口県",
        "徳島県", "香川県", "愛媛県", "高知県", "福岡県", "佐賀県", "長崎県",
        "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県"
    )

    private val prefIndex: Map<String, Int> = PREF_ORDER.withIndex().associate { (i, pref) -> pref to i }

    /**
     * @param query 部分一致対象(name/pref/city/club)。空白のみ・空文字はフィルタなし扱い
     * @param pref null または "すべて" 相当の値はフィルタなし
     * @param maxDistanceM null=無制限。指定時、distancesに距離が無いスポットは除外する
     * @param division null はフィルタなし
     * @param distances spotId→メートル。空の場合は距離条件・距離ソートを無効化する
     */
    fun filter(
        spots: List<Spot>,
        query: String,
        pref: String?,
        maxDistanceM: Float?,
        division: String?,
        distances: Map<String, Float>
    ): List<Spot> {
        val trimmedQuery = query.trim()
        val prefFilter = pref?.takeIf { it.isNotBlank() && it != "すべて" }
        val divisionFilter = division?.takeIf { it.isNotBlank() && it != "すべて" }
        val distanceEnabled = distances.isNotEmpty()

        val filtered = spots.filter { spot ->
            val matchesQuery = trimmedQuery.isEmpty() ||
                listOf(spot.name, spot.pref, spot.city, spot.club).any { it.contains(trimmedQuery) }
            val matchesPref = prefFilter == null || spot.pref == prefFilter
            val matchesDivision = divisionFilter == null || spot.division == divisionFilter
            val matchesDistance = when {
                !distanceEnabled -> true
                maxDistanceM == null -> true
                else -> {
                    val d = distances[spot.id]
                    d != null && d <= maxDistanceM
                }
            }
            matchesQuery && matchesPref && matchesDivision && matchesDistance
        }

        return if (distanceEnabled) {
            filtered.sortedBy { distances[it.id] ?: Float.MAX_VALUE }
        } else {
            filtered.sortedWith(
                compareBy(
                    { prefIndex[it.pref] ?: Int.MAX_VALUE },
                    { it.name }
                )
            )
        }
    }
}
