package com.shawady.bodywise.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class Doctor(
    val id: String,
    val name: String,
    val specialty: String = "",
    val phone: String = "",
    val clinic: String = "",
    val address: String = "",
    val notes: String = ""
)

/** [doctorName] is copied so the appointment still reads well if the doctor card is deleted. */
data class Appointment(
    val id: String,
    val doctorId: String?,
    val doctorName: String,
    val at: LocalDateTime,
    val reason: String = "",
    val done: Boolean = false,
    val outcome: String = ""
)

data class MedicalProfile(
    val bloodType: String = "",
    val allergies: String = "",
    val conditions: String = "",
    val emergencyName: String = "",
    val emergencyPhone: String = "",
    val notes: String = ""
) {
    fun isEmpty() = listOf(bloodType, allergies, conditions, emergencyName, emergencyPhone, notes).all { it.isBlank() }
}

val BLOOD_TYPES = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
val DOC_KINDS = listOf("روشتة", "تحليل", "أشعة", "تقرير", "أخرى")

/** A photographed prescription, lab result, scan or report. */
data class MedicalDoc(
    val id: String,
    val date: LocalDate,
    val kind: String,
    val title: String,
    val doctorName: String = "",
    val photo: String? = null
)

enum class ApptReminder(val label: String) { EVENING_BEFORE("الليلة اللي قبله"), TWO_HOURS("قبله بساعتين") }

val EVENING_BEFORE_TIME: LocalTime = LocalTime.of(20, 0)

/** Reminder moments for one appointment that are still in the future. Done appointments get none. */
fun appointmentReminders(appt: Appointment, now: LocalDateTime): List<Pair<ApptReminder, LocalDateTime>> {
    if (appt.done) return emptyList()
    val list = listOf(
        ApptReminder.EVENING_BEFORE to appt.at.toLocalDate().minusDays(1).atTime(EVENING_BEFORE_TIME),
        ApptReminder.TWO_HOURS to appt.at.minusHours(2)
    )
    return list.filter { it.second.isAfter(now) && it.second.isBefore(appt.at) }
}

fun upcomingAppointments(list: List<Appointment>, now: LocalDateTime): List<Appointment> =
    list.filter { !it.done && it.at.isAfter(now.minusHours(1)) }.sortedBy { it.at }

/** Not marked done and the time passed more than an hour ago. */
fun overdueAppointments(list: List<Appointment>, now: LocalDateTime): List<Appointment> =
    list.filter { !it.done && !it.at.isAfter(now.minusHours(1)) }.sortedByDescending { it.at }

/** "20" + number without the leading zero for Egyptian local numbers; keeps international numbers. */
fun whatsappNumber(phone: String): String {
    val digits = phone.filter { it.isDigit() || it == '+' }
    return when {
        digits.startsWith("+") -> digits.drop(1)
        digits.startsWith("00") -> digits.drop(2)
        digits.startsWith("0") -> "20" + digits.drop(1)
        else -> digits
    }
}

fun dialNumber(phone: String): String = phone.filter { it.isDigit() || it == '+' }

/** Days the remaining stock lasts at the current schedule; null when not tracked or no schedule. */
fun daysOfStockLeft(med: Medication): Int? {
    val left = med.remaining ?: return null
    if (med.times.isEmpty()) return null
    val activeDays = if (med.days.isEmpty()) 7 else med.days.size
    val perDay = med.times.size * med.doseAmount() * activeDays / 7.0
    if (perDay <= 0.0) return null
    return Math.floor(left / perDay + 1e-9).toInt()
}

const val RUNOUT_WARN_DAYS = 5

/**
 * Vital types the person usually measures but has not lately. Needs at least 3 readings of a type; the expected gap is
 * the average gap of the latest readings (1..7 days) and the reminder starts one day after that gap passes.
 */
fun overdueVitals(vitals: List<VitalReading>, now: LocalDateTime): Map<VitalType, Long> {
    val result = mutableMapOf<VitalType, Long>()
    VitalType.entries.forEach { type ->
        val days = vitals.filter { it.type == type }.map { it.at.toLocalDate() }.distinct().sortedDescending().take(6)
        if (days.size < 3) return@forEach
        val gaps = days.zipWithNext { a, b -> Duration.between(b.atStartOfDay(), a.atStartOfDay()).toDays() }
        val expected = gaps.average().coerceIn(1.0, 7.0)
        val since = Duration.between(days.first().atStartOfDay(), now.toLocalDate().atStartOfDay()).toDays()
        if (since > expected + 1) result[type] = since
    }
    return result
}
