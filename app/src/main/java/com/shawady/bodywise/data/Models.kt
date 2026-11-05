package com.shawady.bodywise.data

import java.time.LocalDate

enum class MealSlot(val key: String, val emoji: String) {
    BREAKFAST("الفطار", "🍳"),
    LUNCH("الغداء", "🍲"),
    DINNER("العشاء", "🥗")
}

data class FoodEntry(val name: String, val amount: String)

enum class Metric(val key: String, val label: String, val unit: String, val min: Double, val max: Double) {
    WEIGHT("weight", "الوزن", "كجم", 20.0, 400.0),
    BELLY("belly", "البطن", "سم", 20.0, 300.0),
    WAIST("waist", "الخصر", "سم", 20.0, 300.0),
    CHEST("chest", "الصدر", "سم", 20.0, 300.0),
    ARM("arm", "الذراع", "سم", 5.0, 100.0),
    THIGH("thigh", "الفخذ", "سم", 10.0, 200.0)
}

data class Measurement(val id: String, val date: LocalDate, val values: Map<Metric, Double>)

data class ProgressPhoto(val date: LocalDate, val file: String)

/** Daily extras: sleep, mood (1–5), exercise minutes and a short text. */
data class DayNote(val sleepHours: Double?, val mood: Int?, val exerciseMinutes: Int?, val text: String) {
    val isEmpty: Boolean get() = sleepHours == null && mood == null && exerciseMinutes == null && text.isBlank()
}

/** Everything the user has recorded. Meal keys are "yyyy-MM-dd|<slot key>", water keys are "yyyy-MM-dd". */
data class Snapshot(
    val meals: Map<String, List<FoodEntry>> = emptyMap(),
    val water: Map<String, Int> = emptyMap(),
    val measurements: List<Measurement> = emptyList(),
    val photos: List<ProgressPhoto> = emptyList(),
    val waterGoal: Int = 8,
    val notes: Map<String, DayNote> = emptyMap(),
    val heightCm: Double? = null,
    val targetWeight: Double? = null
)

/** themeMode: 0 = follow system, 1 = light, 2 = dark. */
data class AppSettings(
    val themeMode: Int = 0,
    val appLock: Boolean = false,
    val waterReminders: Boolean = false,
    val weeklyReminder: Boolean = false,
    val autoBackupUri: String? = null,
    val lastAutoBackup: String? = null,
    val onboarded: Boolean = false
)
