package com.example.rakutencoverage.measurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLMN判定・ARFCN→バンド逆引きの純関数テスト(DUAL SIM対応の中核ロジック) */
class RakutenCellFilterTest {

    // ---------- PLMN判定 ----------

    @Test
    fun rakutenPlmnIsAccepted() {
        assertTrue(isRakutenPlmn("440", "11"))
    }

    @Test
    fun docomoPlmnIsRejected() {
        assertFalse(isRakutenPlmn("440", "10"))
    }

    @Test
    fun partnerRoamingPlmnsAreAccepted() {
        assertTrue(isPartnerRoamingPlmn("440", "50"))
        assertTrue(isPartnerRoamingPlmn("440", "51"))
        assertTrue(isPartnerRoamingPlmn("440", "53"))
        assertTrue(isPartnerRoamingPlmn("440", "54"))
    }

    @Test
    fun docomoAndSoftbankAreNotPartnerRoaming() {
        assertFalse(isPartnerRoamingPlmn("440", "10"))
        assertFalse(isPartnerRoamingPlmn("440", "20"))
    }

    @Test
    fun nullPlmnIsRejected() {
        assertFalse(isRakutenPlmn(null, null))
        assertFalse(isPartnerRoamingPlmn(null, null))
    }

    @Test
    fun foreignMccIsRejected() {
        assertFalse(isRakutenPlmn("310", "11"))
        assertFalse(isPartnerRoamingPlmn("310", "50"))
    }

    // ---------- NR-ARFCN → バンド ----------

    @Test
    fun nrArfcnMapsToN77() {
        assertEquals("n77", nrArfcnToBand(620000))
        assertEquals("n77", nrArfcnToBand(650000))
        assertEquals("n77", nrArfcnToBand(680000))
    }

    @Test
    fun nrArfcnMapsToN28() {
        assertEquals("n28", nrArfcnToBand(151600))
        assertEquals("n28", nrArfcnToBand(156100))
        assertEquals("n28", nrArfcnToBand(160600))
    }

    @Test
    fun unknownNrArfcnReturnsNull() {
        assertNull(nrArfcnToBand(0))
        assertNull(nrArfcnToBand(400000))
        assertNull(nrArfcnToBand(2100000)) // ミリ波帯(削除済み区分)は対象外
    }

    // ---------- EARFCN → バンド ----------

    @Test
    fun earfcnMapsToBand3() {
        assertEquals("Band 3", earfcnToBand(1200))
        assertEquals("Band 3", earfcnToBand(1575))
        assertEquals("Band 3", earfcnToBand(1949))
    }

    @Test
    fun earfcnMapsToRoamingBands() {
        assertEquals("Band 18", earfcnToBand(5850))
        assertEquals("Band 18", earfcnToBand(5999))
        assertEquals("Band 26", earfcnToBand(8690))
        assertEquals("Band 26", earfcnToBand(9039))
    }

    @Test
    fun earfcnMapsToBand28() {
        assertEquals("Band 28", earfcnToBand(9210))
        assertEquals("Band 28", earfcnToBand(9659))
    }

    @Test
    fun unknownEarfcnReturnsNull() {
        assertNull(earfcnToBand(0))       // Band 1(ドコモ)
        assertNull(earfcnToBand(6050))    // Band 19(ドコモ)
        assertNull(earfcnToBand(6500))    // Band 21(ドコモ)
    }
}
