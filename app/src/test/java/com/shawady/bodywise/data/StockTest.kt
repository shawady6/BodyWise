package com.shawady.bodywise.data

import com.shawady.bodywise.util.format12
import com.shawady.bodywise.util.rankNameCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class StockTest {
    private fun med(remaining: Double?, full: Double?, perBox: Double? = 30.0) = Medication(
        "m", "دواء", "1", "قرص", listOf(LocalTime.of(8, 0)), emptySet(), LocalDate.of(2026, 11, 1), null,
        "", "", null, remaining, LocalDateTime.of(2026, 11, 1, 0, 0), 3, 10, perBox, full, 40.0
    )

    @Test fun format12() {
        assertEquals("12:00 ص", LocalTime.of(0, 0).format12())
        assertEquals("8:05 ص", LocalTime.of(8, 5).format12())
        assertEquals("12:30 م", LocalTime.of(12, 30).format12())
        assertEquals("8:00 م", LocalTime.of(20, 0).format12())
    }

    @Test fun refillUsesTenPercentOfLastStock() {
        assertTrue(med(2.0, 60.0).needsRefill())
        assertFalse(med(6.0, 60.0).needsRefill())
        assertEquals(10, med(6.0, 60.0).percentLeft())
        assertFalse(med(null, 60.0).needsRefill())
    }

    @Test fun refilledAddsBoxesAndResetsReference() {
        val r = med(2.0, 60.0).refilled(2.0, 55.0)
        assertEquals(62.0, r.remaining!!, 0.0)
        assertEquals(62.0, r.fullQuantity!!, 0.0)
        assertEquals(55.0, r.price!!, 0.0)
        assertFalse(r.needsRefill())
        assertEquals(40.0, med(2.0, 60.0).refilled(1.0, null).price!!, 0.0)
    }

    @Test fun suggestTimesKeepsExisting() {
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), suggestTimes(2, listOf(LocalTime.of(8, 0))))
        assertEquals(4, suggestTimes(4, emptyList()).size)
        assertEquals(listOf(LocalTime.of(9, 30)), suggestTimes(1, listOf(LocalTime.of(9, 30), LocalTime.of(21, 0))))
        assertEquals(3, suggestTimes(3, listOf(LocalTime.of(9, 0), LocalTime.of(21, 0))).toSet().size)
    }

    @Test fun ocrPrefersBigBrandText() {
        val lines = listOf(
            "PANADOL" to 120, "Each film coated tablet contains" to 30, "500 mg" to 60,
            "Paracetamol" to 50, "Tablets" to 40, "Store below 25" to 20, "GSK" to 35
        )
        val result = rankNameCandidates(lines)
        assertEquals("Panadol", result.first())
        assertTrue("Paracetamol" in result)
        assertFalse(result.any { it.contains("Store", ignoreCase = true) || it == "Tablets" })
    }
}
