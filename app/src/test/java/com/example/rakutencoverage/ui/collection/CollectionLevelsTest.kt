package com.example.rakutencoverage.ui.collection

import com.example.rakutencoverage.data.SignalLevel
import com.example.rakutencoverage.data.isCollectable
import com.example.rakutencoverage.data.monster.category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 図鑑のセクション一覧の網羅性テスト。幻(PLATINUM_5G)の表示漏れ再発防止。 */
class CollectionLevelsTest {

    @Test
    fun phantomIsShownInLightCollection() {
        assertTrue(SignalLevel.PLATINUM_5G in collectionLightLevels)
    }

    @Test
    fun lightLevelsAreOrderedByStarCountDescending() {
        assertEquals(
            collectionLightLevels.sortedByDescending { it.category!!.starCount },
            collectionLightLevels
        )
    }

    @Test
    fun everyCollectableLevelAppearsExactlyOnceAcrossLightAndDark() {
        val all = collectionLightLevels + collectionDarkLevels
        assertEquals(all.distinct(), all)
        assertEquals(
            SignalLevel.entries.filter { it.isCollectable }.toSet(),
            all.toSet()
        )
    }
}
