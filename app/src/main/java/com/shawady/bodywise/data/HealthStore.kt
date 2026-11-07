package com.shawady.bodywise.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Doctors, appointments, medical card and documents. Appointment alarms read them while the app is closed, so changes go through one lock. */
class HealthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bodywise_data", Context.MODE_PRIVATE)

    fun loadDoctors(): List<Doctor> = synchronized(LOCK) { runCatching { SnapshotJson.doctorsFromJson(JSONArray(prefs.getString("doctors", "[]") ?: "[]")) }.getOrDefault(emptyList()) }
    fun loadAppointments(): List<Appointment> = synchronized(LOCK) { runCatching { SnapshotJson.appointmentsFromJson(JSONArray(prefs.getString("appointments", "[]") ?: "[]")) }.getOrDefault(emptyList()) }
    fun loadProfile(): MedicalProfile = synchronized(LOCK) { runCatching { SnapshotJson.profileFromJson(JSONObject(prefs.getString("med_profile", "{}") ?: "{}")) }.getOrDefault(MedicalProfile()) }
    fun loadDocs(): List<MedicalDoc> = synchronized(LOCK) { runCatching { SnapshotJson.docsFromJson(JSONArray(prefs.getString("med_docs", "[]") ?: "[]")) }.getOrDefault(emptyList()) }

    fun loadPeople(): List<Person> = synchronized(LOCK) { runCatching { SnapshotJson.peopleFromJson(JSONArray(prefs.getString("people", "[]") ?: "[]")) }.getOrDefault(emptyList()) }

    fun savePeople(list: List<Person>) = synchronized(LOCK) {
        prefs.edit().putString("people", SnapshotJson.peopleToJson(list).toString()).commit()
    }

    fun upsertDoctor(d: Doctor) = synchronized(LOCK) {
        val list = loadDoctorsRaw().toMutableList()
        val i = list.indexOfFirst { it.id == d.id }
        if (i >= 0) list[i] = d else list.add(d)
        prefs.edit().putString("doctors", SnapshotJson.doctorsToJson(list).toString()).commit()
    }

    fun deleteDoctor(id: String) = synchronized(LOCK) {
        prefs.edit().putString("doctors", SnapshotJson.doctorsToJson(loadDoctorsRaw().filter { it.id != id }).toString()).commit()
    }

    fun upsertAppointment(a: Appointment) = synchronized(LOCK) {
        val list = loadApptsRaw().toMutableList()
        val i = list.indexOfFirst { it.id == a.id }
        if (i >= 0) list[i] = a else list.add(a)
        prefs.edit().putString("appointments", SnapshotJson.appointmentsToJson(list).toString()).commit()
    }

    fun deleteAppointment(id: String) = synchronized(LOCK) {
        prefs.edit().putString("appointments", SnapshotJson.appointmentsToJson(loadApptsRaw().filter { it.id != id }).toString()).commit()
    }

    fun saveProfile(p: MedicalProfile) = synchronized(LOCK) {
        prefs.edit().putString("med_profile", SnapshotJson.profileToJson(p).toString()).commit()
    }

    fun upsertDoc(d: MedicalDoc) = synchronized(LOCK) {
        val list = loadDocsRaw().toMutableList()
        val i = list.indexOfFirst { it.id == d.id }
        if (i >= 0) list[i] = d else list.add(d)
        prefs.edit().putString("med_docs", SnapshotJson.docsToJson(list).toString()).commit()
    }

    fun deleteDoc(id: String) = synchronized(LOCK) {
        prefs.edit().putString("med_docs", SnapshotJson.docsToJson(loadDocsRaw().filter { it.id != id }).toString()).commit()
    }

    fun replaceAll(doctors: List<Doctor>, appts: List<Appointment>, profile: MedicalProfile, docs: List<MedicalDoc>, people: List<Person>) = synchronized(LOCK) {
        prefs.edit()
            .putString("people", SnapshotJson.peopleToJson(people).toString())
            .putString("doctors", SnapshotJson.doctorsToJson(doctors).toString())
            .putString("appointments", SnapshotJson.appointmentsToJson(appts).toString())
            .putString("med_profile", SnapshotJson.profileToJson(profile).toString())
            .putString("med_docs", SnapshotJson.docsToJson(docs).toString())
            .commit()
    }

    // Raw readers are called while the lock is already held (the lock is re-entrant).
    private fun loadDoctorsRaw() = loadDoctors()
    private fun loadApptsRaw() = loadAppointments()
    private fun loadDocsRaw() = loadDocs()

    private companion object { val LOCK = Any() }
}
