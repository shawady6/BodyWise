package com.shawady.bodywise.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MedicationTest {
    private fun med(
        times: List<LocalTime> = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
        days: Set<DayOfWeek> = emptySet(),
        start: LocalDate = LocalDate.of(2026, 11, 1),
        end: LocalDate? = null,
        created: LocalDateTime = LocalDateTime.of(2026, 11, 1, 0, 0)
    ) = Medication("m", "دواء", "1", "قرص", times, days, start, end, "", "", null, null, created)

    @Test fun nextOccurrenceSkipsPastTimesAndRespectsDays() {
        val m = med()
        assertEquals(LocalDateTime.of(2026, 11, 5, 20, 0), nextOccurrence(m, LocalTime.of(20, 0), LocalDateTime.of(2026, 11, 5, 9, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 6, 8, 0), nextOccurrence(m, LocalTime.of(8, 0), LocalDateTime.of(2026, 11, 5, 9, 0)))
        // Thursday 2026-11-05; only Saturdays -> 2026-11-07
        val sat = med(days = setOf(DayOfWeek.SATURDAY))
        assertEquals(LocalDateTime.of(2026, 11, 7, 8, 0), nextOccurrence(sat, LocalTime.of(8, 0), LocalDateTime.of(2026, 11, 5, 9, 0)))
    }

    @Test fun nextOccurrenceStopsAfterEndDate() {
        val m = med(end = LocalDate.of(2026, 11, 5))
        assertNull(nextOccurrence(m, LocalTime.of(8, 0), LocalDateTime.of(2026, 11, 5, 9, 0)))
    }

    @Test fun slotsIgnoreDosesBeforeCreation() {
        val m = med(created = LocalDateTime.of(2026, 11, 5, 10, 0))
        val slots = slotsFor(listOf(m), LocalDate.of(2026, 11, 5))
        assertEquals(listOf(LocalTime.of(20, 0)), slots.map { it.time })
    }

    @Test fun slotStates() {
        val m = med()
        val slot = DoseSlot(m, LocalDate.of(2026, 11, 5), LocalTime.of(8, 0))
        val logs = HashMap<String, DoseLog>()
        assertEquals(SlotState.UPCOMING, slotState(slot, logs, LocalDateTime.of(2026, 11, 5, 7, 0)))
        assertEquals(SlotState.DUE, slotState(slot, logs, LocalDateTime.of(2026, 11, 5, 8, 30)))
        assertEquals(SlotState.MISSED, slotState(slot, logs, LocalDateTime.of(2026, 11, 5, 9, 30)))
        logs[slot.key] = DoseLog("m", slot.date, slot.time, DoseStatus.SNOOZED, LocalDateTime.of(2026, 11, 5, 8, 1), 1)
        assertEquals(SlotState.SNOOZED, slotState(slot, logs, LocalDateTime.of(2026, 11, 5, 8, 30)))
        logs[slot.key] = DoseLog("m", slot.date, slot.time, DoseStatus.TAKEN, LocalDateTime.of(2026, 11, 5, 8, 40), 1)
        assertEquals(SlotState.TAKEN, slotState(slot, logs, LocalDateTime.of(2026, 11, 5, 12, 0)))
    }

    @Test fun doseAmountFallsBackToOne() {
        assertEquals(1.0, med().copy(dose = "abc").doseAmount(), 0.0)
        assertEquals(2.5, med().copy(dose = "2.5").doseAmount(), 0.0)
        assertTrue(med().isDueOn(LocalDate.of(2026, 11, 1)))
    }
}
