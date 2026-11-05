package com.shawady.bodywise.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val arabic: Locale = Locale.forLanguageTag("ar")

/** Saturday that starts the Saturday–Friday week containing [date]. */
fun weekStartFor(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))

fun weekDays(date: LocalDate): List<LocalDate> {
    val start = weekStartFor(date)
    return (0L..6L).map { start.plusDays(it) }
}

fun arabicDayShort(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, arabic)
fun arabicDayFull(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, arabic)
fun arabicMonth(date: LocalDate): String = date.month.getDisplayName(TextStyle.FULL, arabic)
fun dayMonth(date: LocalDate): String = "${date.dayOfMonth} ${arabicMonth(date)}"

/** 12-hour clock with Arabic AM/PM, e.g. "8:05 ص". */
fun java.time.LocalTime.format12(): String {
    val h = if (hour % 12 == 0) 12 else hour % 12
    return "$h:${minute.toString().padStart(2, '0')} ${if (hour < 12) "ص" else "م"}"
}
