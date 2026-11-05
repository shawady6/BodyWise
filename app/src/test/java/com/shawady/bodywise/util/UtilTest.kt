package com.shawady.bodywise.util

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilTest {
    @Test
    fun weekAlwaysStartsOnSaturdayAndContainsTheDate() {
        for (offset in 0L..13L) {
            val date = LocalDate.of(2026, 11, 2).plusDays(offset)
            val start = weekStartFor(date)
            assertEquals(DayOfWeek.SATURDAY, start.dayOfWeek)
            assertTrue("$date must be inside its own week", !date.isBefore(start) && date.isBefore(start.plusDays(7)))
            assertEquals(7, weekDays(date).size)
            assertEquals(start, weekDays(date).first())
        }
    }

    @Test
    fun thursdayBelongsToTheWeekThatStartedOnPreviousSaturday() {
        assertEquals(LocalDate.of(2026, 10, 31), weekStartFor(LocalDate.of(2026, 11, 5)))
        assertEquals(LocalDate.of(2026, 11, 7), weekStartFor(LocalDate.of(2026, 11, 7)))
    }

    @Test
    fun parsesDecimalsInDifferentFormats() {
        assertEquals(70.5, parseDecimal("70.5")!!, 0.0)
        assertEquals(70.5, parseDecimal("70,5")!!, 0.0)
        assertEquals(72.5, parseDecimal("٧٢٫٥")!!, 0.0)
        assertEquals(85.0, parseDecimal(" 85 ")!!, 0.0)
        assertNull(parseDecimal(""))
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal("NaN"))
    }

    @Test
    fun formatsNumbersWithoutTrailingZeros() {
        assertEquals("70", formatNumber(70.0))
        assertEquals("70.5", formatNumber(70.5))
        assertEquals("0", formatNumber(0.0))
    }

    @Test
    fun sampleSizes() {
        assertEquals(1, sampleSizeAtMost(1500, 1000, 2048))
        assertEquals(2, sampleSizeAtMost(4000, 3000, 2048))
        assertEquals(4, sampleSizeAtMost(8000, 6000, 2048))
        assertEquals(1, sampleSizeAtLeast(500, 500, 800))
        assertEquals(4, sampleSizeAtLeast(4000, 3000, 800))
    }
}
