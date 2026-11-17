package com.closecircle.app

import com.closecircle.app.data.DurId
import com.closecircle.app.domain.Freshness
import com.closecircle.app.domain.durationOf
import com.closecircle.app.domain.freshnessLabel
import com.closecircle.app.domain.freshnessOf
import com.closecircle.app.domain.initialsOf
import com.closecircle.app.domain.isDelayed
import com.closecircle.app.domain.leftText
import com.closecircle.app.domain.lengthText
import com.closecircle.app.domain.whenText
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN = 60_000L

class DomainTest {
    @Test
    fun freshnessThresholds() {
        assertEquals(Freshness.RECENT, freshnessOf(0))
        assertEquals(Freshness.RECENT, freshnessOf(5))
        assertEquals(Freshness.LAST_KNOWN, freshnessOf(6))
        assertEquals(Freshness.LAST_KNOWN, freshnessOf(24 * 60))
        assertEquals(Freshness.UNKNOWN, freshnessOf(24 * 60 + 1))
        assertEquals(Freshness.UNKNOWN, freshnessOf(null))
    }

    @Test
    fun freshnessLabels() {
        assertEquals("Recentă · 1 min", freshnessLabel(1))
        assertEquals("Ultima cunoscută · 47 min", freshnessLabel(47))
        assertEquals("Necunoscută", freshnessLabel(null))
    }

    @Test
    fun delayedOnlyAfterFifteenMinutes() {
        assertFalse(isDelayed(15))
        assertTrue(isDelayed(16))
        assertTrue(isDelayed(30))
    }

    @Test
    fun leftTextRoundsUp() {
        assertEquals("1 min", leftText(1))
        assertEquals("14 min", leftText(14 * MIN - 10_000))
        assertEquals("2 h 14 min", leftText(134 * MIN))
        assertEquals("2 h", leftText(120 * MIN))
        assertEquals("1 zi 3 h", leftText((24 + 3) * 60 * MIN))
        assertEquals("2 zile", leftText(48 * 60 * MIN))
        assertEquals("0 min", leftText(-5))
    }

    @Test
    fun lengthTextForDurations() {
        assertEquals("30 min", lengthText(30))
        assertEquals("1 h 30 min", lengthText(90))
        assertEquals("1 zi", lengthText(1440))
        assertEquals("3 zile", lengthText(4320))
    }

    @Test
    fun whenTextToday() {
        val zone = ZoneId.of("Europe/Bucharest")
        val now = ZonedDateTime.of(2026, 11, 9, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val sameDay = ZonedDateTime.of(2026, 11, 9, 16, 0, 0, 0, zone).toInstant().toEpochMilli()
        val nextDay = ZonedDateTime.of(2026, 11, 10, 8, 5, 0, 0, zone).toInstant().toEpochMilli()
        val later = ZonedDateTime.of(2026, 11, 14, 8, 5, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("la 16:00", whenText(sameDay, now, zone))
        assertEquals("mâine la 08:05", whenText(nextDay, now, zone))
        assertTrue(whenText(later, now, zone).startsWith("pe 14 "))
    }

    @Test
    fun durationIsMatchedToAChipOrShownAsCustom() {
        assertEquals(DurId.ALWAYS to "", durationOf(null))
        assertEquals(DurId.H1 to "", durationOf(60 * MIN))
        assertEquals(DurId.H4 to "", durationOf(240 * MIN + 400)) // microsecond-level drift is rounded away
        assertEquals(DurId.H8 to "", durationOf(480 * MIN))
        assertEquals(DurId.CUSTOM to "3 h 30 min", durationOf(210 * MIN))
    }

    @Test
    fun initialsFromNames() {
        assertEquals("RP", initialsOf("Radu Popescu"))
        assertEquals("A", initialsOf("ana"))
        assertEquals("AM", initialsOf("  ana   maria  bianca ")) // first two words only
        assertEquals("?", initialsOf("   "))
    }
}
