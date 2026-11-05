package com.shawady.bodywise.data

import android.content.Context
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Medications and dose logs live apart from the main snapshot because alarms and notification
 * buttons change them while the app is closed. Every change is read-modify-write under one lock.
 */
class MedStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bodywise_data", Context.MODE_PRIVATE)

    fun loadMeds(): List<Medication> = synchronized(LOCK) { readMeds() }
    fun loadLogs(): Map<String, DoseLog> = synchronized(LOCK) { readLogs() }

    private fun readMeds(): List<Medication> = runCatching {
        SnapshotJson.medsFromJson(JSONArray(prefs.getString("medications", "[]") ?: "[]"))
    }.getOrDefault(emptyList())

    private fun readLogs(): Map<String, DoseLog> = runCatching {
        SnapshotJson.logsFromJson(JSONArray(prefs.getString("dose_logs", "[]") ?: "[]")).associateBy { doseKey(it.medId, it.date, it.time) }
    }.getOrDefault(emptyMap())

    private fun writeMeds(list: List<Medication>) {
        prefs.edit().putString("medications", SnapshotJson.medsToJson(list).toString()).commit()
    }

    private fun writeLogs(map: Map<String, DoseLog>) {
        prefs.edit().putString("dose_logs", SnapshotJson.logsToJson(map.values.toList()).toString()).commit()
    }

    fun upsert(med: Medication) = synchronized(LOCK) {
        val list = readMeds().toMutableList()
        val i = list.indexOfFirst { it.id == med.id }
        if (i >= 0) list[i] = med else list.add(med)
        writeMeds(list)
    }

    fun delete(id: String) = synchronized(LOCK) { writeMeds(readMeds().filter { it.id != id }) }

    private fun adjustRemaining(medId: String, delta: Double) {
        val list = readMeds().map { m ->
            if (m.id == medId && m.remaining != null) m.copy(remaining = (m.remaining + delta).coerceAtLeast(0.0)) else m
        }
        writeMeds(list)
    }

    /** Records a status for one dose. Taking a dose lowers the remaining quantity; changing it back restores it. */
    fun mark(med: Medication, date: LocalDate, time: LocalTime, status: DoseStatus, now: LocalDateTime) = synchronized(LOCK) {
        val logs = readLogs().toMutableMap()
        val key = doseKey(med.id, date, time)
        val old = logs[key]
        val snoozes = (old?.snoozes ?: 0) + if (status == DoseStatus.SNOOZED) 1 else 0
        logs[key] = DoseLog(med.id, date, time, status, now, snoozes)
        val wasTaken = old?.status == DoseStatus.TAKEN
        val nowTaken = status == DoseStatus.TAKEN
        if (!wasTaken && nowTaken) adjustRemaining(med.id, -med.doseAmount())
        if (wasTaken && !nowTaken) adjustRemaining(med.id, med.doseAmount())
        writeLogs(logs)
    }

    fun clear(med: Medication, date: LocalDate, time: LocalTime) = synchronized(LOCK) {
        val logs = readLogs().toMutableMap()
        val old = logs.remove(doseKey(med.id, date, time)) ?: return@synchronized
        if (old.status == DoseStatus.TAKEN) adjustRemaining(med.id, med.doseAmount())
        writeLogs(logs)
    }

    fun replaceAll(meds: List<Medication>, logs: Collection<DoseLog>) = synchronized(LOCK) {
        writeMeds(meds)
        writeLogs(logs.associateBy { doseKey(it.medId, it.date, it.time) })
    }

    fun prune(before: LocalDate) = synchronized(LOCK) {
        val logs = readLogs()
        val kept = logs.filterValues { !it.date.isBefore(before) }
        if (kept.size != logs.size) writeLogs(kept)
    }

    private companion object { val LOCK = Any() }
}
