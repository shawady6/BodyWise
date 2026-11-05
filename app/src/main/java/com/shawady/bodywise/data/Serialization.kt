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
    const val VERSION = 2

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

    fun toBackup(snapshot: Snapshot, exportedAt: LocalDate): JSONObject = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("exportedAt", exportedAt.toString())
        .put("meals", mealsToJson(snapshot.meals))
        .put("water", waterToJson(snapshot.water))
        .put("measurements", measurementsToJson(snapshot.measurements))
        .put("photos", photosToJson(snapshot.photos))
        .put("settings", JSONObject().put("waterGoal", snapshot.waterGoal))

    /** Strict: throws on anything that is not a valid BodyWise backup (v1 or v2). */
    fun fromBackup(json: JSONObject): Snapshot {
        check(json.optString("format") == FORMAT && json.optInt("version") in 1..VERSION) { "صيغة النسخة غير مدعومة" }
        return Snapshot(
            meals = mealsFromJson(json.getJSONObject("meals")),
            water = waterFromJson(json.getJSONObject("water")),
            measurements = measurementsFromJson(json.getJSONArray("measurements")),
            photos = photosFromJson(json.optJSONArray("photos") ?: JSONArray()),
            waterGoal = (json.optJSONObject("settings")?.optInt("waterGoal", 8) ?: 8).coerceIn(1, 30)
        )
    }
}
