package com.shawady.bodywise.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SerializationTest {
    private val sample = Snapshot(
        meals = mapOf("2026-11-05|الفطار" to listOf(FoodEntry("فول", "2 رغيف"), FoodEntry("بيض", ""))),
        water = mapOf("2026-11-05" to 5),
        measurements = listOf(
            Measurement("a1", LocalDate.of(2026, 11, 1), mapOf(Metric.WEIGHT to 82.5, Metric.WAIST to 95.0)),
            Measurement("a2", LocalDate.of(2026, 11, 8), mapOf(Metric.WEIGHT to 81.0))
        ),
        photos = listOf(ProgressPhoto(LocalDate.of(2026, 11, 1), "progress_1.jpg")),
        waterGoal = 10,
        notes = mapOf("2026-11-05" to DayNote(7.5, 4, 30, "نمت كويس")),
        heightCm = 175.0,
        targetWeight = 78.0,
        vitals = listOf(
            VitalReading("v1", VitalType.BP, LocalDateTime.of(2026, 11, 5, 8, 30), 120.0, 80.0, "", "الصبح"),
            VitalReading("v2", VitalType.SUGAR, LocalDateTime.of(2026, 11, 5, 9, 0), 95.0, null, "صائم", "")
        )
    )

    @Test
    fun backupRoundTrip() {
        val json = SnapshotJson.toBackup(sample, LocalDate.of(2026, 11, 9))
        val restored = SnapshotJson.fromBackup(JSONObject(json.toString()))
        assertEquals(sample, restored)
    }

    @Test
    fun readsVersionOneBackupsFromOldApp() {
        val old = JSONObject()
            .put("format", "bodywise-backup").put("version", 1)
            .put("meals", JSONObject().put("2026-10-01|الغداء", JSONArray().put(JSONObject().put("name", "رز").put("amount", ""))))
            .put("water", JSONObject().put("2026-10-01", 3))
            .put("measurements", JSONArray().put(JSONObject().put("date", "2026-10-01").put("weight", "90").put("belly", "").put("waist", "100.5").put("chest", "").put("arm", "").put("thigh", "")))
            .put("photos", JSONArray().put(JSONObject().put("date", "2026-10-01").put("file", "progress_9.jpg")))
        val restored = SnapshotJson.fromBackup(old)
        assertEquals(8, restored.waterGoal)
        assertEquals(1, restored.measurements.size)
        assertEquals(90.0, restored.measurements[0].values[Metric.WEIGHT]!!, 0.0)
        assertEquals(100.5, restored.measurements[0].values[Metric.WAIST]!!, 0.0)
        assertTrue(restored.measurements[0].id.isNotBlank())
        assertEquals("progress_9.jpg", restored.photos[0].file)
    }

    @Test
    fun oldBackupsWithoutVitalsStillLoad() {
        val v3 = SnapshotJson.toBackup(sample, LocalDate.of(2026, 11, 9)).put("version", 3)
        v3.remove("vitals")
        assertTrue(SnapshotJson.fromBackup(v3).vitals.isEmpty())
    }

    @Test
    fun rejectsInvalidBackups() {
        val wrongFormat = JSONObject().put("format", "other").put("version", 1)
        try { SnapshotJson.fromBackup(wrongFormat); fail("expected failure") } catch (_: IllegalStateException) {}
        val futureVersion = SnapshotJson.toBackup(sample, LocalDate.of(2026, 11, 9)).put("version", 99)
        try { SnapshotJson.fromBackup(futureVersion); fail("expected failure") } catch (_: IllegalStateException) {}
        val badDate = SnapshotJson.toBackup(sample, LocalDate.of(2026, 11, 9))
        badDate.getJSONArray("measurements").getJSONObject(0).put("date", "not-a-date")
        try { SnapshotJson.fromBackup(badDate); fail("expected failure") } catch (_: Exception) {}
    }

    @Test
    fun photoFileNamesCannotEscapeTheDirectory() {
        val json = SnapshotJson.photosToJson(listOf(ProgressPhoto(LocalDate.of(2026, 1, 1), "x.jpg")))
        json.getJSONObject(0).put("file", "../../evil.jpg")
        assertEquals("evil.jpg", SnapshotJson.photosFromJson(json)[0].file)
    }
}
