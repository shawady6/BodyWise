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
    val createdAt: LocalDateTime,
    /** Packaging: strips per box and units per strip (tablets/capsules), or units per box (injections, ml...). */
    val stripsPerBox: Int? = null,
    val unitsPerStrip: Int? = null,
    val unitsPerBox: Double? = null,
    /** Quantity (in units) at the last purchase or refill; "low" is measured against it. */
    val fullQuantity: Double? = null,
    /** Price of one box. */
    val price: Double? = null,
    /** null = the app owner. */
    val personId: String? = null,
    /** The doctor who prescribed it (a card in the catalog), optional. */
    val doctorId: String? = null
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

/** Tablets and capsules come in strips; everything else is counted directly per box. */
val STRIP_UNITS = setOf("قرص", "كبسولة")

const val REFILL_FRACTION = 0.10

/** Less than 10% of the last stocked quantity is left. Without a stocked quantity, fewer than 3 days of doses remain. */
fun Medication.needsRefill(): Boolean {
    val left = remaining ?: return false
    val full = fullQuantity
    return if (full != null && full > 0) left < full * REFILL_FRACTION
    else left <= doseAmount() * times.size.coerceAtLeast(1) * 3
}

fun Medication.percentLeft(): Int? {
    val left = remaining ?: return null
    val full = fullQuantity?.takeIf { it > 0 } ?: return null
    return Math.round(left / full * 100).toInt().coerceIn(0, 100)
}

/** Adds [boxes] new boxes to the stock and makes the new total the reference for the 10% rule. */
fun Medication.refilled(boxes: Double, newPrice: Double?): Medication {
    val perBox = unitsPerBox ?: return this
    val total = (remaining ?: 0.0) + boxes * perBox
    return copy(remaining = total, fullQuantity = total, price = newPrice ?: price)
}

/** Evenly spread default times; keeps times the user already set when the count changes. */
fun suggestTimes(count: Int, existing: List<LocalTime>): List<LocalTime> {
    val defaults = when (count) {
        1 -> listOf(8, 0)
        2 -> listOf(8, 20)
        3 -> listOf(8, 14, 20)
        else -> listOf(8, 12, 16, 20)
    }.filter { it != 0 }.map { LocalTime.of(it, 0) }
    val result = existing.take(count).toMutableList()
    for (t in defaults) { if (result.size >= count) break; if (t !in result) result.add(t) }
    var hour = 9
    while (result.size < count) { val t = LocalTime.of(hour++ % 24, 0); if (t !in result) result.add(t) }
    return result.sorted()
}

/** Minutes after the first alarm when a "still not taken" reminder repeats. */
val REPEAT_REMINDER_MINUTES = listOf(15L, 30L)

/**
 * A repeat reminder rings only if the person did nothing about the dose since the alarm fired:
 * not taken, not skipped and not snoozed (a snooze brings its own alarm).
 */
fun shouldRepeatReminder(log: DoseLog?, firedAt: LocalDateTime): Boolean = when {
    log == null -> true
    log.status == DoseStatus.TAKEN || log.status == DoseStatus.SKIPPED -> false
    else -> log.at.isBefore(firedAt)
}

/** Days without a backup after which the app reminds the person; null while recent enough. */
const val BACKUP_REMINDER_DAYS = 14L

fun backupOverdueDays(lastBackupMillis: Long, firstInstallMillis: Long, nowMillis: Long): Long? {
    val reference = maxOf(lastBackupMillis, firstInstallMillis)
    val days = (nowMillis - reference) / (24L * 60 * 60 * 1000)
    return days.takeIf { it >= BACKUP_REMINDER_DAYS }
}
