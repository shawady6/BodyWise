package com.shawady.bodywise.data

import android.content.Context
import com.shawady.bodywise.util.formatNumber
import com.shawady.bodywise.util.parseDecimal
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local storage. Keys match earlier versions so installed data survives the update. */
class BodyWiseStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("bodywise_data", Context.MODE_PRIVATE)

    val photosDir: File get() = context.filesDir

    fun load(): Snapshot = Snapshot(
        meals = runCatching { SnapshotJson.mealsFromJson(JSONObject(prefs.getString("meals", "{}") ?: "{}")) }.getOrDefault(emptyMap()),
        water = runCatching { SnapshotJson.waterFromJson(JSONObject(prefs.getString("water", "{}") ?: "{}")) }.getOrDefault(emptyMap()),
        measurements = runCatching { SnapshotJson.measurementsFromJson(JSONArray(prefs.getString("measurements", "[]") ?: "[]")) }.getOrDefault(emptyList()),
        photos = runCatching { SnapshotJson.photosFromJson(JSONArray(prefs.getString("photos", "[]") ?: "[]")) }.getOrDefault(emptyList()),
        waterGoal = prefs.getInt("water_goal", 8).coerceIn(1, 30),
        notes = runCatching { SnapshotJson.notesFromJson(JSONObject(prefs.getString("notes", "{}") ?: "{}")) }.getOrDefault(emptyMap()),
        heightCm = prefs.getString("height_cm", null)?.let { parseDecimal(it) },
        targetWeight = prefs.getString("target_weight", null)?.let { parseDecimal(it) },
        vitals = runCatching { SnapshotJson.vitalsFromJson(JSONArray(prefs.getString("vitals", "[]") ?: "[]")) }.getOrDefault(emptyList()),
        medications = MedStore(context).loadMeds(),
        doseLogs = MedStore(context).loadLogs().values.toList()
    )

    fun save(snapshot: Snapshot) {
        prefs.edit()
            .putString("meals", SnapshotJson.mealsToJson(snapshot.meals).toString())
            .putString("water", SnapshotJson.waterToJson(snapshot.water).toString())
            .putString("measurements", SnapshotJson.measurementsToJson(snapshot.measurements).toString())
            .putString("photos", SnapshotJson.photosToJson(snapshot.photos).toString())
            .putInt("water_goal", snapshot.waterGoal)
            .putString("notes", SnapshotJson.notesToJson(snapshot.notes).toString())
            .putString("height_cm", snapshot.heightCm?.let { formatNumber(it) })
            .putString("target_weight", snapshot.targetWeight?.let { formatNumber(it) })
            .putString("vitals", SnapshotJson.vitalsToJson(snapshot.vitals).toString())
            .apply()
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("bodywise_data", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val legacyDark = prefs.getBoolean("dark", false)
        return AppSettings(
            themeMode = if (prefs.contains("theme_mode")) prefs.getInt("theme_mode", 0) else if (legacyDark) 2 else 0,
            appLock = prefs.getBoolean("app_lock", false),
            waterReminders = prefs.getBoolean("water_reminders", false),
            weeklyReminder = prefs.getBoolean("weekly_reminder", false),
            autoBackupUri = prefs.getString("auto_backup_uri", null),
            lastAutoBackup = prefs.getString("last_auto_backup", null),
            onboarded = prefs.getBoolean("onboarded", false),
            updateNotices = prefs.getBoolean("update_notices", true),
            medTone = prefs.getString("med_tone", null)
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putInt("theme_mode", settings.themeMode)
            .putBoolean("app_lock", settings.appLock)
            .putBoolean("water_reminders", settings.waterReminders)
            .putBoolean("weekly_reminder", settings.weeklyReminder)
            .putString("auto_backup_uri", settings.autoBackupUri)
            .putString("last_auto_backup", settings.lastAutoBackup)
            .putBoolean("onboarded", settings.onboarded)
            .putBoolean("update_notices", settings.updateNotices)
            .putString("med_tone", settings.medTone)
            .apply()
    }
}
