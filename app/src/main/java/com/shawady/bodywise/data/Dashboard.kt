package com.shawady.bodywise.data

import com.shawady.bodywise.util.format12
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
    lastMeasurement: LocalDate?,
    appointments: List<Appointment> = emptyList(),
    allVitals: List<VitalReading> = emptyList(),
    people: List<Person> = emptyList(),
    /** Alarm reliability problems (muted notifications...) shown first, and the days since the last backup. */
    alarmProblems: List<String> = emptyList(),
    backupOverdueDays: Long? = null
): List<DashAlert> {
    val alerts = mutableListOf<DashAlert>()
    if (meds.isNotEmpty()) alarmProblems.forEach { alerts.add(DashAlert("⚠️ $it", true)) }
    latestVitals.forEach { r ->
        val w = vitalWarning(r) ?: return@forEach
        if (Duration.between(r.at, now).toHours() < 24) {
            val value = if (r.type == VitalType.BP) "${r.value.toInt()}/${(r.value2 ?: 0.0).toInt()}" else r.value.toInt().toString()
            alerts.add(DashAlert("${r.type.label} $value: ${w.text.substringBefore(".").trim()}", w.severe))
        }
    }
    todaySlots.forEach { (slot, state) ->
        val t = slot.time.format12()
        when (state) {
            SlotState.MISSED -> alerts.add(DashAlert("فاتتك جرعة ${medLabel(slot.med, people)} (${t})", false))
            SlotState.DUE, SlotState.SNOOZED -> alerts.add(DashAlert("وقت ${medLabel(slot.med, people)} دلوقتي (${t})", false))
            else -> {}
        }
    }
    upcomingAppointments(appointments, now).filter { it.at.toLocalDate() <= now.toLocalDate().plusDays(1) }.forEach { a ->
        val day = if (a.at.toLocalDate() == now.toLocalDate()) "النهارده" else "بكرة"
        alerts.add(DashAlert("📅 موعد ${personName(a.personId, people) ?: ""} ${a.doctorName.ifBlank { "الدكتور" }} $day ${a.at.toLocalTime().format12()}".replace("  ", " "), false))
    }
    overdueAppointments(appointments, now).filter { Duration.between(it.at, now).toDays() < 3 }.forEach { a ->
        alerts.add(DashAlert("📅 موعد ${a.doctorName.ifBlank { "الدكتور" }} عدّى، علّم عليه \"تمت\" لو رحت", false))
    }
    meds.forEach { med ->
        val days = daysOfStockLeft(med)
        if (med.needsRefill()) alerts.add(DashAlert("${medLabel(med, people)} محتاج تجديد" + (days?.let { " (يكفي $it يوم)" } ?: ""), false))
        else if (days != null && days <= RUNOUT_WARN_DAYS) alerts.add(DashAlert("${medLabel(med, people)} هيخلص بعد $days يوم", false))
    }
    overdueVitals(allVitals, now).forEach { (type, days) ->
        alerts.add(DashAlert("${type.label}: آخر قياس من $days يوم", false))
    }
    if (now.hour >= 18 && waterToday < waterGoal) alerts.add(DashAlert("لسه ناقصك ${waterGoal - waterToday} أكواب مية النهارده", false))
    if (lastMeasurement != null && Duration.between(lastMeasurement.atStartOfDay(), now).toDays() >= 7) {
        alerts.add(DashAlert("عدّى ${Duration.between(lastMeasurement.atStartOfDay(), now).toDays()} يوم من آخر قياس للوزن والمقاسات", false))
    }
    if (backupOverdueDays != null) alerts.add(DashAlert("💾 عدّى $backupOverdueDays يوم من غير نسخة احتياطية (الإعدادات ← النسخ الاحتياطي)", false))
    return alerts.sortedByDescending { it.severe }
}
