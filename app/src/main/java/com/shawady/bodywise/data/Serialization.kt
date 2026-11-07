package com.shawady.bodywise.data

import com.shawady.bodywise.util.formatNumber
import com.shawady.bodywise.util.parseDecimal
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** JSON shapes are kept compatible with earlier app versions (v0.1 – v0.2). */
object SnapshotJson {
    const val FORMAT = "bodywise-backup"
    const val VERSION = 6

    fun mealsToJson(meals: Map<String, List<FoodEntry>>): JSONObject {
        val json = JSONObject()
        meals.forEach { (key, list) ->
            if (list.isNotEmpty()) {
                val array = JSONArray()
                list.forEach { array.put(JSONObject().put("name", it.name).put("amount", it.amount)) }
                json.put(key, array)
            }
        }
        return json
    }

    fun mealsFromJson(json: JSONObject): Map<String, List<FoodEntry>> {
        val result = LinkedHashMap<String, List<FoodEntry>>()
        json.keys().forEach { key ->
            val array = json.getJSONArray(key)
            result[key] = List(array.length()) { i ->
                val item = array.getJSONObject(i)
                FoodEntry(item.getString("name"), item.optString("amount"))
            }
        }
        return result
    }

    fun waterToJson(water: Map<String, Int>): JSONObject {
        val json = JSONObject()
        water.forEach { (day, cups) -> json.put(day, cups) }
        return json
    }

    fun waterFromJson(json: JSONObject): Map<String, Int> {
        val result = LinkedHashMap<String, Int>()
        json.keys().forEach { result[it] = json.getInt(it) }
        return result
    }

    fun measurementsToJson(list: List<Measurement>): JSONArray {
        val array = JSONArray()
        list.forEach { m ->
            val item = JSONObject().put("id", m.id).put("date", m.date.toString())
            Metric.entries.forEach { metric -> item.put(metric.key, m.values[metric]?.let { formatNumber(it) } ?: "") }
            array.put(item)
        }
        return array
    }

    fun measurementsFromJson(array: JSONArray): List<Measurement> = List(array.length()) { i ->
        val item = array.getJSONObject(i)
        val values = LinkedHashMap<Metric, Double>()
        Metric.entries.forEach { metric -> parseDecimal(item.optString(metric.key))?.let { values[metric] = it } }
        Measurement(
            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
            date = LocalDate.parse(item.getString("date")),
            values = values
        )
    }

    fun photosToJson(list: List<ProgressPhoto>): JSONArray {
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("date", it.date.toString()).put("file", it.file)) }
        return array
    }

    fun photosFromJson(array: JSONArray): List<ProgressPhoto> = List(array.length()) { i ->
        val item = array.getJSONObject(i)
        ProgressPhoto(LocalDate.parse(item.getString("date")), File(item.getString("file")).name)
    }

    fun notesToJson(notes: Map<String, DayNote>): JSONObject {
        val json = JSONObject()
        notes.forEach { (day, note) ->
            if (!note.isEmpty) {
                val item = JSONObject().put("text", note.text)
                note.sleepHours?.let { item.put("sleep", formatNumber(it)) }
                note.mood?.let { item.put("mood", it) }
                note.exerciseMinutes?.let { item.put("exercise", it) }
                json.put(day, item)
            }
        }
        return json
    }

    fun notesFromJson(json: JSONObject): Map<String, DayNote> {
        val result = LinkedHashMap<String, DayNote>()
        json.keys().forEach { day ->
            LocalDate.parse(day)
            val item = json.getJSONObject(day)
            result[day] = DayNote(
                sleepHours = parseDecimal(item.optString("sleep")),
                mood = if (item.has("mood")) item.getInt("mood").coerceIn(1, 5) else null,
                exerciseMinutes = if (item.has("exercise")) item.getInt("exercise").coerceIn(0, 1000) else null,
                text = item.optString("text")
            )
        }
        return result
    }

    fun vitalsToJson(list: List<VitalReading>): JSONArray {
        val array = JSONArray()
        list.forEach { v ->
            val item = JSONObject().put("id", v.id).put("type", v.type.key).put("at", v.at.toString())
                .put("value", formatNumber(v.value)).put("tag", v.tag).put("note", v.note)
            v.value2?.let { item.put("value2", formatNumber(it)) }
            array.put(item)
        }
        return array
    }

    fun vitalsFromJson(array: JSONArray): List<VitalReading> = List(array.length()) { i ->
        val item = array.getJSONObject(i)
        VitalReading(
            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
            type = VitalType.entries.first { it.key == item.getString("type") },
            at = java.time.LocalDateTime.parse(item.getString("at")),
            value = parseDecimal(item.getString("value")) ?: error("قيمة غير صالحة"),
            value2 = parseDecimal(item.optString("value2")),
            tag = item.optString("tag"),
            note = item.optString("note")
        )
    }

    fun medsToJson(list: List<Medication>): JSONArray {
        val array = JSONArray()
        list.forEach { m ->
            array.put(JSONObject()
                .put("id", m.id).put("name", m.name).put("dose", m.dose).put("unit", m.unit)
                .put("times", JSONArray(m.times.map { it.toString() }))
                .put("days", JSONArray(m.days.map { it.value }))
                .put("start", m.startDate.toString()).put("end", m.endDate?.toString() ?: "")
                .put("instruction", m.instruction).put("notes", m.notes)
                .put("photo", m.photo?.let { java.io.File(it).name } ?: "")
                .put("remaining", m.remaining?.let { formatNumber(it) } ?: "")
                .put("createdAt", m.createdAt.toString())
                .put("stripsPerBox", m.stripsPerBox?.toString() ?: "")
                .put("unitsPerStrip", m.unitsPerStrip?.toString() ?: "")
                .put("unitsPerBox", m.unitsPerBox?.let { formatNumber(it) } ?: "")
                .put("fullQuantity", m.fullQuantity?.let { formatNumber(it) } ?: "")
                .put("price", m.price?.let { formatNumber(it) } ?: ""))
        }
        return array
    }

    fun medsFromJson(array: JSONArray): List<Medication> = List(array.length()) { i ->
        val item = array.getJSONObject(i)
        val times = item.getJSONArray("times")
        val days = item.optJSONArray("days") ?: JSONArray()
        Medication(
            id = item.getString("id"),
            name = item.getString("name"),
            dose = item.optString("dose"),
            unit = item.optString("unit"),
            times = List(times.length()) { java.time.LocalTime.parse(times.getString(it)) }.sorted(),
            days = List(days.length()) { java.time.DayOfWeek.of(days.getInt(it)) }.toSet(),
            startDate = LocalDate.parse(item.getString("start")),
            endDate = item.optString("end").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
            instruction = item.optString("instruction"),
            notes = item.optString("notes"),
            photo = item.optString("photo").takeIf { it.isNotBlank() }?.let { java.io.File(it).name },
            remaining = parseDecimal(item.optString("remaining")),
            createdAt = java.time.LocalDateTime.parse(item.getString("createdAt")),
            stripsPerBox = item.optString("stripsPerBox").toIntOrNull(),
            unitsPerStrip = item.optString("unitsPerStrip").toIntOrNull(),
            unitsPerBox = parseDecimal(item.optString("unitsPerBox")),
            fullQuantity = parseDecimal(item.optString("fullQuantity")),
            price = parseDecimal(item.optString("price"))
        )
    }

    fun logsToJson(list: List<DoseLog>): JSONArray {
        val array = JSONArray()
        list.forEach { l ->
            array.put(JSONObject().put("med", l.medId).put("date", l.date.toString()).put("time", l.time.toString())
                .put("status", l.status.name).put("at", l.at.toString()).put("snoozes", l.snoozes))
        }
        return array
    }

    fun logsFromJson(array: JSONArray): List<DoseLog> = List(array.length()) { i ->
        val item = array.getJSONObject(i)
        DoseLog(
            medId = item.getString("med"),
            date = LocalDate.parse(item.getString("date")),
            time = java.time.LocalTime.parse(item.getString("time")),
            status = DoseStatus.valueOf(item.getString("status")),
            at = java.time.LocalDateTime.parse(item.getString("at")),
            snoozes = item.optInt("snoozes", 0)
        )
    }

    fun doctorsToJson(list: List<Doctor>): JSONArray {
        val array = JSONArray()
        list.forEach { d ->
            array.put(JSONObject().put("id", d.id).put("name", d.name).put("specialty", d.specialty).put("phone", d.phone)
                .put("clinic", d.clinic).put("address", d.address).put("notes", d.notes))
        }
        return array
    }

    fun doctorsFromJson(array: JSONArray): List<Doctor> = List(array.length()) { i ->
        val o = array.getJSONObject(i)
        Doctor(o.getString("id"), o.getString("name"), o.optString("specialty"), o.optString("phone"),
            o.optString("clinic"), o.optString("address"), o.optString("notes"))
    }

    fun appointmentsToJson(list: List<Appointment>): JSONArray {
        val array = JSONArray()
        list.forEach { a ->
            array.put(JSONObject().put("id", a.id).put("doctorId", a.doctorId ?: "").put("doctorName", a.doctorName)
                .put("at", a.at.toString()).put("reason", a.reason).put("done", a.done).put("outcome", a.outcome))
        }
        return array
    }

    fun appointmentsFromJson(array: JSONArray): List<Appointment> = List(array.length()) { i ->
        val o = array.getJSONObject(i)
        Appointment(o.getString("id"), o.optString("doctorId").ifBlank { null }, o.optString("doctorName"),
            java.time.LocalDateTime.parse(o.getString("at")), o.optString("reason"), o.optBoolean("done", false), o.optString("outcome"))
    }

    fun profileToJson(p: MedicalProfile): JSONObject = JSONObject().put("bloodType", p.bloodType).put("allergies", p.allergies)
        .put("conditions", p.conditions).put("emergencyName", p.emergencyName).put("emergencyPhone", p.emergencyPhone).put("notes", p.notes)

    fun profileFromJson(o: JSONObject): MedicalProfile = MedicalProfile(
        o.optString("bloodType"), o.optString("allergies"), o.optString("conditions"),
        o.optString("emergencyName"), o.optString("emergencyPhone"), o.optString("notes")
    )

    fun docsToJson(list: List<MedicalDoc>): JSONArray {
        val array = JSONArray()
        list.forEach { d ->
            array.put(JSONObject().put("id", d.id).put("date", d.date.toString()).put("kind", d.kind).put("title", d.title)
                .put("doctorName", d.doctorName).put("photo", d.photo ?: ""))
        }
        return array
    }

    fun docsFromJson(array: JSONArray): List<MedicalDoc> = List(array.length()) { i ->
        val o = array.getJSONObject(i)
        MedicalDoc(o.getString("id"), LocalDate.parse(o.getString("date")), o.optString("kind", "أخرى"), o.optString("title"),
            o.optString("doctorName"), o.optString("photo").ifBlank { null })
    }

    fun toBackup(snapshot: Snapshot, exportedAt: LocalDate): JSONObject = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("exportedAt", exportedAt.toString())
        .put("meals", mealsToJson(snapshot.meals))
        .put("water", waterToJson(snapshot.water))
        .put("measurements", measurementsToJson(snapshot.measurements))
        .put("photos", photosToJson(snapshot.photos))
        .put("notes", notesToJson(snapshot.notes))
        .put("vitals", vitalsToJson(snapshot.vitals))
        .put("medications", medsToJson(snapshot.medications))
        .put("doseLogs", logsToJson(snapshot.doseLogs))
        .put("doctors", doctorsToJson(snapshot.doctors))
        .put("appointments", appointmentsToJson(snapshot.appointments))
        .put("medProfile", profileToJson(snapshot.medProfile))
        .put("medDocs", docsToJson(snapshot.medDocs))
        .put("settings", JSONObject()
            .put("waterGoal", snapshot.waterGoal)
            .put("heightCm", snapshot.heightCm?.let { formatNumber(it) } ?: "")
            .put("targetWeight", snapshot.targetWeight?.let { formatNumber(it) } ?: ""))

    /** Strict: throws on anything that is not a valid BodyWise backup (v1 or v2). */
    fun fromBackup(json: JSONObject): Snapshot {
        check(json.optString("format") == FORMAT && json.optInt("version") in 1..VERSION) { "صيغة النسخة غير مدعومة" }
        return Snapshot(
            meals = mealsFromJson(json.getJSONObject("meals")),
            water = waterFromJson(json.getJSONObject("water")),
            measurements = measurementsFromJson(json.getJSONArray("measurements")),
            photos = photosFromJson(json.optJSONArray("photos") ?: JSONArray()),
            waterGoal = (json.optJSONObject("settings")?.optInt("waterGoal", 8) ?: 8).coerceIn(1, 30),
            notes = json.optJSONObject("notes")?.let { notesFromJson(it) } ?: emptyMap(),
            heightCm = parseDecimal(json.optJSONObject("settings")?.optString("heightCm").orEmpty()),
            targetWeight = parseDecimal(json.optJSONObject("settings")?.optString("targetWeight").orEmpty()),
            vitals = json.optJSONArray("vitals")?.let { vitalsFromJson(it) } ?: emptyList(),
            medications = json.optJSONArray("medications")?.let { medsFromJson(it) } ?: emptyList(),
            doseLogs = json.optJSONArray("doseLogs")?.let { logsFromJson(it) } ?: emptyList(),
            doctors = json.optJSONArray("doctors")?.let { doctorsFromJson(it) } ?: emptyList(),
            appointments = json.optJSONArray("appointments")?.let { appointmentsFromJson(it) } ?: emptyList(),
            medProfile = json.optJSONObject("medProfile")?.let { profileFromJson(it) } ?: MedicalProfile(),
            medDocs = json.optJSONArray("medDocs")?.let { docsFromJson(it) } ?: emptyList()
        )
    }
}
