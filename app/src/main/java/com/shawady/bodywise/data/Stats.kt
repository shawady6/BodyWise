package com.shawady.bodywise.data

import java.time.LocalDate
import java.time.YearMonth

data class MonthStats(
    val daysInRange: Int,
    val daysWithMeals: Int,
    val avgWater: Double,
    val goalDays: Int,
    val avgSleep: Double?,
    val exerciseDays: Int,
    val exerciseMinutes: Int,
    val weightFirst: Double?,
    val weightLast: Double?,
    val weightAvg: Double?
)

/** Counts only days up to [today], so the current month is not diluted by future days. */
fun computeMonthStats(
    month: YearMonth,
    today: LocalDate,
    mealsCount: (LocalDate) -> Int,
    water: (LocalDate) -> Int,
    note: (LocalDate) -> DayNote?,
    measurements: List<Measurement>,
    waterGoal: Int
): MonthStats {
    val end = if (month.atEndOfMonth().isAfter(today)) today else month.atEndOfMonth()
    val days = generateSequence(month.atDay(1)) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    val sleeps = days.mapNotNull { note(it)?.sleepHours }
    val exercise = days.mapNotNull { note(it)?.exerciseMinutes }.filter { it > 0 }
    val weights = measurements
        .filter { YearMonth.from(it.date) == month && it.values[Metric.WEIGHT] != null }
        .sortedBy { it.date }
        .map { it.values.getValue(Metric.WEIGHT) }
    return MonthStats(
        daysInRange = days.size,
        daysWithMeals = days.count { mealsCount(it) > 0 },
        avgWater = if (days.isEmpty()) 0.0 else days.sumOf { water(it) }.toDouble() / days.size,
        goalDays = days.count { water(it) >= waterGoal },
        avgSleep = if (sleeps.isEmpty()) null else sleeps.average(),
        exerciseDays = exercise.size,
        exerciseMinutes = exercise.sum(),
        weightFirst = weights.firstOrNull(),
        weightLast = weights.lastOrNull(),
        weightAvg = if (weights.isEmpty()) null else weights.average()
    )
}
