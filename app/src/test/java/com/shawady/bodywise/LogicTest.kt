package com.shawady.bodywise

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test
    fun weekAlwaysStartsOnSaturdayAndContainsTheDate() {
        for (offset in 0L..6L) {
            val date = LocalDate.of(2026, 11, 2).plusDays(offset) // Monday .. Sunday
            val start = weekStartFor(date)
            assertEquals(DayOfWeek.SATURDAY, start.dayOfWeek)
            assertTrue("$date must be inside its own week", !date.isBefore(start) && date.isBefore(start.plusDays(7)))
        }
    }

    @Test
    fun thursdayBelongsToTheWeekThatStartedOnPreviousSaturday() {
        assertEquals(LocalDate.of(2026, 10, 31), weekStartFor(LocalDate.of(2026, 11, 5)))
    }

    @Test
    fun saturdayStartsItsOwnWeek() {
        assertEquals(LocalDate.of(2026, 11, 7), weekStartFor(LocalDate.of(2026, 11, 7)))
    }

    @Test
    fun versionComparison() {
        assertTrue(versionIsNewer("v0.2.6", "0.2.5"))
        assertTrue(versionIsNewer("v0.2.10", "0.2.9"))
        assertTrue(versionIsNewer("1.0", "0.9.9"))
        assertFalse(versionIsNewer("v0.2.5", "0.2.5"))
        assertFalse(versionIsNewer("v0.2.4", "0.2.5"))
    }

    @Test
    fun sampleSizeKeepsImagesAtLeastTargetSize() {
        assertEquals(1, sampleSizeFor(500, 500, 800))
        assertEquals(4, sampleSizeFor(4000, 3000, 800))
        assertEquals(1, sampleSizeFor(1000, 700, 800))
    }
}
