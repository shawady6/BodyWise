package com.shawady.bodywise.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

data class DashAlert(val text: String, val severe: Boolean)

/** Things worth telling the person on the home screen, most urgent first. */
fun dashboardAlerts(
    now: LocalDateTime,
    todaySlots: List<Pair<DoseSlot, SlotState>>,
    meds: List<Medication>,
    latestVitals: List<VitalReading>,
    waterToday: Int,
    waterGoal: Int,
    lastMeasurement: LocalDate?
): List<DashAlert> {
    val alerts = mutableListOf<DashAlert>()
    latestVitals.forEach { r ->
        val w = vitalWarning(r) ?: return@forEach
        if (Duration.between(r.at, now).toHours() < 24) {
            val value = if (r.type == VitalType.BP) "${r.value.toInt()}/${(r.value2 ?: 0.0).toInt()}" else r.value.toInt().toString()
            alerts.add(DashAlert("${r.type.label} $value: ${w.text.substringBefore(".").trim()}", w.severe))
        }
    }
    todaySlots.forEach { (slot, state) ->
        val t = slot.time.toString().take(5)
        when (state) {
            SlotState.MISSED -> alerts.add(DashAlert("فاتتك جرعة ${slot.med.name} (${t})", false))
            SlotState.DUE, SlotState.SNOOZED -> alerts.add(DashAlert("وقت ${slot.med.name} دلوقتي (${t})", false))
            else -> {}
        }
    }
    meds.forEach { med ->
        val left = med.remaining ?: return@forEach
        if (left <= med.doseAmount() * med.times.size * 3) alerts.add(DashAlert("${med.name} قرب يخلص", false))
    }
    if (now.hour >= 18 && waterToday < waterGoal) alerts.add(DashAlert("لسه ناقصك ${waterGoal - waterToday} أكواب مية النهارده", false))
    if (lastMeasurement != null && Duration.between(lastMeasurement.atStartOfDay(), now).toDays() >= 7) {
        alerts.add(DashAlert("عدّى ${Duration.between(lastMeasurement.atStartOfDay(), now).toDays()} يوم من آخر قياس للوزن والمقاسات", false))
    }
    return alerts.sortedByDescending { it.severe }
}
