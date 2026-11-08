package com.shawady.bodywise.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class HealthTest {
    private val appt = Appointment("a", "d", "د. أحمد", LocalDateTime.of(2026, 11, 10, 17, 0), "متابعة")

    @Test fun remindersEveningBeforeAndTwoHours() {
        val r = appointmentReminders(appt, LocalDateTime.of(2026, 11, 8, 9, 0))
        assertEquals(2, r.size)
        assertEquals(LocalDateTime.of(2026, 11, 9, 20, 0), r[0].second)
        assertEquals(LocalDateTime.of(2026, 11, 10, 15, 0), r[1].second)
    }

    @Test fun remindersSkipPastAndDone() {
        assertEquals(1, appointmentReminders(appt, LocalDateTime.of(2026, 11, 9, 21, 0)).size)
        // booked on short notice: a single reminder 15 minutes before
        val late = appointmentReminders(appt, LocalDateTime.of(2026, 11, 10, 16, 0))
        assertEquals(listOf(ApptReminder.QUARTER), late.map { it.first })
        assertEquals(LocalDateTime.of(2026, 11, 10, 16, 45), late[0].second)
        assertTrue(appointmentReminders(appt, LocalDateTime.of(2026, 11, 10, 16, 50)).isEmpty())
        assertTrue(appointmentReminders(appt.copy(done = true), LocalDateTime.of(2026, 11, 8, 9, 0)).isEmpty())
    }

    @Test fun upcomingAndOverdue() {
        val now = LocalDateTime.of(2026, 11, 10, 17, 30)
        assertTrue(upcomingAppointments(listOf(appt), now).isNotEmpty()) // within the last hour still counts as upcoming
        assertTrue(overdueAppointments(listOf(appt), now.plusHours(2)).isNotEmpty())
        assertTrue(overdueAppointments(listOf(appt.copy(done = true)), now.plusHours(2)).isEmpty())
    }

    @Test fun phoneNumbers() {
        assertEquals("201012345678", whatsappNumber("0101 234 5678"))
        assertEquals("201012345678", whatsappNumber("+20 101 234 5678"))
        assertEquals("201012345678", whatsappNumber("00201012345678"))
        assertEquals("+201012345678", dialNumber("+20 101-234-5678"))
    }

    private fun med(remaining: Double?, times: Int, days: Set<java.time.DayOfWeek> = emptySet(), dose: String = "1") = Medication(
        "m", "دواء", dose, "قرص", List(times) { LocalTime.of(8 + it, 0) }, days, LocalDate.of(2026, 11, 1), null,
        "", "", null, remaining, LocalDateTime.of(2026, 11, 1, 0, 0)
    )

    @Test fun daysOfStock() {
        assertEquals(10, daysOfStockLeft(med(20.0, 2)))
        assertEquals(5, daysOfStockLeft(med(10.0, 2)))
        assertNull(daysOfStockLeft(med(null, 2)))
        assertNull(daysOfStockLeft(med(10.0, 0)))
        // 3 days a week: 3 doses per week -> 6 units last 14 days
        assertEquals(14, daysOfStockLeft(med(6.0, 1, setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.FRIDAY))))
    }

    private fun bp(day: Int) = VitalReading("v$day", VitalType.BP, LocalDateTime.of(2026, 11, day, 9, 0), 120.0, 80.0)

    @Test fun overdueVitalsUsesPersonalHabit() {
        val daily = listOf(bp(1), bp(2), bp(3), bp(4))
        assertTrue(overdueVitals(daily, LocalDateTime.of(2026, 11, 5, 10, 0)).isEmpty())
        assertEquals(4L, overdueVitals(daily, LocalDateTime.of(2026, 11, 8, 10, 0))[VitalType.BP])
        // weekly habit is not nagged after three days
        assertTrue(overdueVitals(listOf(bp(1), bp(8), bp(15)), LocalDateTime.of(2026, 11, 18, 10, 0)).isEmpty())
        // fewer than three readings: no reminder
        assertTrue(overdueVitals(listOf(bp(1), bp(2)), LocalDateTime.of(2026, 11, 30, 10, 0)).isEmpty())
    }

    @Test fun serializationRoundTrip() {
        val doc = Doctor("d", "د. أحمد", "باطنة", "0101", "عيادة", "شارع", "السبت")
        assertEquals(listOf(doc), SnapshotJson.doctorsFromJson(JSONArray(SnapshotJson.doctorsToJson(listOf(doc)).toString())))
        val a = appt.copy(done = true, outcome = "تحاليل")
        assertEquals(listOf(a), SnapshotJson.appointmentsFromJson(JSONArray(SnapshotJson.appointmentsToJson(listOf(a)).toString())))
        val noDoctor = appt.copy(doctorId = null)
        assertNull(SnapshotJson.appointmentsFromJson(SnapshotJson.appointmentsToJson(listOf(noDoctor))).first().doctorId)
        val p = MedicalProfile("O+", "بنسلين", "ضغط", "ماما", "0102", "")
        assertEquals(p, SnapshotJson.profileFromJson(JSONObject(SnapshotJson.profileToJson(p).toString())))
        assertTrue(MedicalProfile().isEmpty())
        val md = MedicalDoc("x", LocalDate.of(2026, 11, 1), "تحليل", "سكر", "د. أحمد", "med_1.jpg")
        assertEquals(listOf(md), SnapshotJson.docsFromJson(JSONArray(SnapshotJson.docsToJson(listOf(md)).toString())))
    }

    @Test fun backupKeepsHealthData() {
        val snap = Snapshot(doctors = listOf(Doctor("d", "x")), appointments = listOf(appt), medProfile = MedicalProfile("A+"), medDocs = emptyList())
        val back = SnapshotJson.fromBackup(JSONObject(SnapshotJson.toBackup(snap, LocalDate.of(2026, 11, 8)).toString()))
        assertEquals(snap.doctors, back.doctors)
        assertEquals(snap.appointments, back.appointments)
        assertEquals("A+", back.medProfile.bloodType)
        assertFalse(back.appointments.first().done)
    }

    @Test fun oldBackupsStillLoad() {
        val snap = Snapshot()
        val json = SnapshotJson.toBackup(snap, LocalDate.of(2026, 11, 8)).put("version", 5)
        json.remove("doctors"); json.remove("appointments"); json.remove("medProfile"); json.remove("medDocs")
        val back = SnapshotJson.fromBackup(json)
        assertTrue(back.doctors.isEmpty() && back.medProfile.isEmpty())
    }
}

class CareTest {
    private val people = listOf(Person("p1", "أحمد"), Person("p2", "ماما"))

    @Test fun groupsMeFirstThenPeople() {
        val items = listOf("a" to "p2", "b" to null, "c" to "p1", "d" to "gone", "e" to "p1")
        val groups = groupByPerson(items, { it.second }, people, careEnabled = true)
        assertEquals(listOf("أنا", "أحمد", "ماما"), groups.map { it.first })
        assertEquals(listOf("b", "d"), groups[0].second.map { it.first })
        assertEquals(listOf("c", "e"), groups[1].second.map { it.first })
    }

    @Test fun noGroupingWhenCareOffOrNoPeople() {
        val items = listOf("a" to "p1", "b" to null)
        assertEquals(1, groupByPerson(items, { it.second }, people, careEnabled = false).size)
        assertEquals(null, groupByPerson(items, { it.second }, emptyList(), careEnabled = true).single().first)
    }

    @Test fun emptyGroupsAreSkipped() {
        val groups = groupByPerson(listOf("a" to "p2"), { it.second }, people, careEnabled = true)
        assertEquals(listOf("ماما"), groups.map { it.first })
    }

    @Test fun labelsAndRoundTrip() {
        val med = Medication(
            "m", "بنادول", "1", "قرص", listOf(LocalTime.of(8, 0)), emptySet(), LocalDate.of(2026, 11, 1), null,
            "", "", null, null, LocalDateTime.of(2026, 11, 1, 0, 0), personId = "p1"
        )
        assertEquals("بنادول (أحمد)", medLabel(med, people))
        assertEquals("بنادول", medLabel(med.copy(personId = null), people))
        assertEquals("بنادول", medLabel(med.copy(personId = "gone"), people))
        assertEquals("p1", SnapshotJson.medsFromJson(SnapshotJson.medsToJson(listOf(med))).first().personId)
        assertNull(SnapshotJson.medsFromJson(SnapshotJson.medsToJson(listOf(med.copy(personId = null)))).first().personId)
        val appt = Appointment("a", null, "د. س", LocalDateTime.of(2026, 11, 10, 17, 0), personId = "p2")
        assertEquals("p2", SnapshotJson.appointmentsFromJson(SnapshotJson.appointmentsToJson(listOf(appt))).first().personId)
        assertEquals(people, SnapshotJson.peopleFromJson(SnapshotJson.peopleToJson(people)))
    }

    @Test fun backupKeepsPeople() {
        val back = SnapshotJson.fromBackup(SnapshotJson.toBackup(Snapshot(people = people), LocalDate.of(2026, 11, 8)))
        assertEquals(people, back.people)
        assertEquals(MAX_PEOPLE, 3)
    }
}


class ReliabilityTest {
    private val fired = LocalDateTime.of(2026, 11, 8, 8, 0)
    private fun log(status: DoseStatus, at: LocalDateTime) = DoseLog("m", LocalDate.of(2026, 11, 8), LocalTime.of(8, 0), status, at)

    @Test fun repeatOnlyWhenNothingWasDone() {
        assertTrue(shouldRepeatReminder(null, fired))
        assertFalse(shouldRepeatReminder(log(DoseStatus.TAKEN, fired.plusMinutes(3)), fired))
        assertFalse(shouldRepeatReminder(log(DoseStatus.SKIPPED, fired.plusMinutes(3)), fired))
        // snoozed after the alarm fired: the snooze brings its own alarm
        assertFalse(shouldRepeatReminder(log(DoseStatus.SNOOZED, fired.plusMinutes(2)), fired))
        // a snooze from an earlier ring, alarm fired again since: remind
        assertTrue(shouldRepeatReminder(log(DoseStatus.SNOOZED, fired.minusMinutes(8)), fired))
        assertEquals(listOf(15L, 30L), REPEAT_REMINDER_MINUTES)
    }

    @Test fun backupReminder() {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        assertEquals(null, backupOverdueDays(now - 3 * day, now - 50 * day, now))
        assertEquals(20L, backupOverdueDays(now - 20 * day, now - 50 * day, now))
        // never backed up: counted from install
        assertEquals(null, backupOverdueDays(0, now - 5 * day, now))
        assertEquals(30L, backupOverdueDays(0, now - 30 * day, now))
        assertEquals(BACKUP_REMINDER_DAYS, 14L)
    }
}
