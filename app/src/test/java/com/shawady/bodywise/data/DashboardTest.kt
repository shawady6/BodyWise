package com.shawady.bodywise.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DashboardTest {
    private val now = LocalDateTime.of(2026, 11, 5, 19, 0)
    private val med = Medication("m", "بنادول", "1", "قرص", listOf(LocalTime.of(8, 0)), emptySet(),
        LocalDate.of(2026, 11, 1), null, "", "", null, 2.0, LocalDateTime.of(2026, 11, 1, 0, 0))

    @Test fun collectsAlertsMostSevereFirst() {
        val slot = DoseSlot(med, now.toLocalDate(), LocalTime.of(8, 0))
        val bp = VitalReading("v", VitalType.BP, now.minusHours(2), 190.0, 125.0)
        val alerts = dashboardAlerts(now, listOf(slot to SlotState.MISSED), listOf(med), listOf(bp), 3, 8, LocalDate.of(2026, 10, 20))
        assertTrue(alerts.first().severe)
        assertTrue(alerts.any { it.text.contains("فاتتك") })
        assertTrue(alerts.any { it.text.contains("تجديد") })
        assertTrue(alerts.any { it.text.contains("مية") })
        assertTrue(alerts.any { it.text.contains("آخر قياس") })
    }

    @Test fun quietWhenEverythingIsFine() {
        val ok = VitalReading("v", VitalType.PULSE, now.minusHours(1), 70.0)
        val alerts = dashboardAlerts(now, emptyList(), listOf(med.copy(remaining = 100.0)), listOf(ok), 8, 8, LocalDate.of(2026, 11, 4))
        assertEquals(emptyList<DashAlert>(), alerts)
    }
}
