package com.shawady.bodywise.data

import com.shawady.bodywise.util.parseDecimal
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class DoseStatus { TAKEN, SKIPPED, SNOOZED }

val MED_UNITS = listOf("قرص", "كبسولة", "مل", "نقطة", "حقنة", "جرعة")
val MED_INSTRUCTIONS = listOf("لا يهم", "قبل الأكل", "بعد الأكل", "مع الأكل")

/** [days] empty means every day. [remaining] is in dose units (tablets, ml...). */
data class Medication(
    val id: String,
    val name: String,
    val dose: String,
    val unit: String,
    val times: List<LocalTime>,
    val days: Set<DayOfWeek>,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val instruction: String,
    val notes: String,
    val photo: String?,
    val remaining: Double?,
    val createdAt: LocalDateTime
)

data class DoseLog(
    val medId: String,
    val date: LocalDate,
    val time: LocalTime,
    val status: DoseStatus,
    val at: LocalDateTime,
    val snoozes: Int = 0
)

/** One scheduled dose of one medication on one day. */
data class DoseSlot(val med: Medication, val date: LocalDate, val time: LocalTime) {
    val dateTime: LocalDateTime get() = LocalDateTime.of(date, time)
    val key: String get() = doseKey(med.id, date, time)
}

enum class SlotState { TAKEN, SKIPPED, SNOOZED, UPCOMING, DUE, MISSED }

fun doseKey(medId: String, date: LocalDate, time: LocalTime): String = "$medId|$date|$time"

/** How long after its time a dose still counts as "due" before it is "missed". */
const val DOSE_GRACE_MINUTES = 60L
const val SNOOZE_MINUTES = 10L

fun Medication.doseAmount(): Double = parseDecimal(dose)?.takeIf { it > 0 } ?: 1.0

fun Medication.isDueOn(date: LocalDate): Boolean =
    !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate)) && (days.isEmpty() || date.dayOfWeek in days)

/** Doses of [meds] scheduled on [date], sorted by time. Doses earlier than the medication's creation are ignored. */
fun slotsFor(meds: List<Medication>, date: LocalDate): List<DoseSlot> =
    meds.filter { it.isDueOn(date) }
        .flatMap { med -> med.times.map { DoseSlot(med, date, it) } }
        .filter { !it.dateTime.isBefore(it.med.createdAt.withSecond(0).withNano(0)) }
        .sortedWith(compareBy({ it.time }, { it.med.name }))

fun slotState(slot: DoseSlot, logs: Map<String, DoseLog>, now: LocalDateTime): SlotState {
    val log = logs[slot.key]
    return when {
        log?.status == DoseStatus.TAKEN -> SlotState.TAKEN
        log?.status == DoseStatus.SKIPPED -> SlotState.SKIPPED
        slot.dateTime.isAfter(now) -> SlotState.UPCOMING
        Duration.between(slot.dateTime, now).toMinutes() <= DOSE_GRACE_MINUTES ->
            if (log?.status == DoseStatus.SNOOZED) SlotState.SNOOZED else SlotState.DUE
        else -> SlotState.MISSED
    }
}

/** The next moment (strictly after [from]) this medication should be taken at [time], or null if never again. */
fun nextOccurrence(med: Medication, time: LocalTime, from: LocalDateTime): LocalDateTime? {
    var day = from.toLocalDate()
    repeat(15) {
        if (med.isDueOn(day)) {
            val candidate = LocalDateTime.of(day, time)
            if (candidate.isAfter(from)) return candidate
        }
        day = day.plusDays(1)
    }
    return null
}
