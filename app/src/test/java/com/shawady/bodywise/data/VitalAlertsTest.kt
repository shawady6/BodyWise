package com.shawady.bodywise.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class VitalAlertsTest {
    private val now = LocalDateTime.of(2026, 11, 5, 8, 0)
    private fun r(t: VitalType, a: Double, b: Double? = null, tag: String = "") = VitalReading("x", t, now, a, b, tag)

    @Test fun normalReadingsHaveNoWarning() {
        assertNull(vitalWarning(r(VitalType.BP, 120.0, 80.0)))
        assertNull(vitalWarning(r(VitalType.SUGAR, 90.0, tag = "صائم")))
        assertNull(vitalWarning(r(VitalType.PULSE, 72.0)))
    }

    @Test fun bloodPressure() {
        assertFalse(vitalWarning(r(VitalType.BP, 145.0, 85.0))!!.severe)
        assertTrue(vitalWarning(r(VitalType.BP, 185.0, 100.0))!!.severe)
        assertFalse(vitalWarning(r(VitalType.BP, 85.0, 58.0))!!.severe)
        assertTrue(vitalWarning(r(VitalType.BP, 75.0, 45.0))!!.severe)
    }

    @Test fun sugarDependsOnContext() {
        assertNotNull(vitalWarning(r(VitalType.SUGAR, 130.0, tag = "صائم")))
        assertNull(vitalWarning(r(VitalType.SUGAR, 130.0, tag = "بعد الأكل")))
        assertTrue(vitalWarning(r(VitalType.SUGAR, 50.0, tag = "عشوائي"))!!.severe)
        assertTrue(vitalWarning(r(VitalType.SUGAR, 320.0, tag = "عشوائي"))!!.severe)
    }

    @Test fun pulse() {
        assertEquals(false, vitalWarning(r(VitalType.PULSE, 110.0))!!.severe)
        assertTrue(vitalWarning(r(VitalType.PULSE, 35.0))!!.severe)
    }
}
