package com.shawady.bodywise.data

import com.shawady.bodywise.util.bmi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FeaturesTest {
    @Test
    fun csvEscapesAndSortsOldestFirst() {
        assertEquals("\"a,b\"", csvEscape("a,b"))
        assertEquals("\"a\"\"b\"", csvEscape("a\"b"))
        val csv = measurementsToCsv(listOf(
            Measurement("2", LocalDate.of(2026, 11, 8), mapOf(Metric.WEIGHT to 81.0)),
            Measurement("1", LocalDate.of(2026, 11, 1), mapOf(Metric.WEIGHT to 82.5))
        ))
        val lines = csv.trim().split("\r\n")
        assertEquals(3, lines.size)
        assertTrue(lines[1].startsWith("2026-11-01,"))
        assertTrue(lines[2].startsWith("2026-11-08,"))
    }

    @Test
    fun monthStatsCountOnlyUntilToday() {
        val stats = computeMonthStats(
            month = YearMonth.of(2026, 11),
            today = LocalDate.of(2026, 11, 10),
            mealsCount = { if (it.dayOfMonth <= 4) 1 else 0 },
            water = { 8 },
            note = { if (it.dayOfMonth == 2) DayNote(8.0, 3, 20, "") else null },
            measurements = listOf(
                Measurement("a", LocalDate.of(2026, 11, 1), mapOf(Metric.WEIGHT to 80.0)),
                Measurement("b", LocalDate.of(2026, 11, 9), mapOf(Metric.WEIGHT to 78.0)),
                Measurement("c", LocalDate.of(2026, 10, 9), mapOf(Metric.WEIGHT to 90.0))
            ),
            waterGoal = 8
        )
        assertEquals(10, stats.daysInRange)
        assertEquals(4, stats.daysWithMeals)
        assertEquals(10, stats.goalDays)
        assertEquals(8.0, stats.avgSleep!!, 0.0)
        assertEquals(1, stats.exerciseDays)
        assertEquals(80.0, stats.weightFirst!!, 0.0)
        assertEquals(78.0, stats.weightLast!!, 0.0)
        assertEquals(79.0, stats.weightAvg!!, 0.0)
    }

    @Test
    fun bmiHandlesBadInput() {
        assertEquals(22.86, bmi(70.0, 175.0)!!, 0.01)
        assertNull(bmi(70.0, 10.0))
        assertNull(bmi(0.0, 175.0))
    }
}
