package com.example.rakutencoverage.ui.checkin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CheckInGeofence.judge / formatDistance の境界値テスト。 */
class CheckInGeofenceTest {

    @Test
    fun nullDistanceIsNoLocation() {
        assertEquals(CheckInGeofence.Verdict.NoLocation, CheckInGeofence.judge(null))
    }

    @Test
    fun withinRadiusIsOk() {
        assertEquals(CheckInGeofence.Verdict.Ok, CheckInGeofence.judge(0f))
        assertEquals(CheckInGeofence.Verdict.Ok, CheckInGeofence.judge(999f))
        // 境界ちょうど(1000m)は圏内扱い
        assertEquals(CheckInGeofence.Verdict.Ok, CheckInGeofence.judge(1000f))
    }

    @Test
    fun beyondRadiusIsTooFarWithDistance() {
        val v = CheckInGeofence.judge(1001f)
        assertTrue(v is CheckInGeofence.Verdict.TooFar)
        assertEquals(1001f, (v as CheckInGeofence.Verdict.TooFar).distanceM, 0.001f)

        val far = CheckInGeofence.judge(2500f)
        assertTrue(far is CheckInGeofence.Verdict.TooFar)
    }

    @Test
    fun formatDistanceUsesMetersBelow1kmAndKmAbove() {
        assertEquals("850m", CheckInGeofence.formatDistance(850f))
        assertEquals("1.2km", CheckInGeofence.formatDistance(1234f))
        assertEquals("999m", CheckInGeofence.formatDistance(999.4f))
        assertEquals("12.0km", CheckInGeofence.formatDistance(12000f))
    }
}
