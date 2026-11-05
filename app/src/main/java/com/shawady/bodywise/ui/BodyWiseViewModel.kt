package com.shawady.bodywise.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shawady.bodywise.backup.AutoBackupWorker
import com.shawady.bodywise.backup.BackupManager
import com.shawady.bodywise.data.AppSettings
import com.shawady.bodywise.data.BodyWiseStore
import com.shawady.bodywise.data.FoodEntry
import com.shawady.bodywise.data.MealSlot
import com.shawady.bodywise.data.Measurement
import com.shawady.bodywise.data.ProgressPhoto
import com.shawady.bodywise.data.SettingsStore
import com.shawady.bodywise.data.Snapshot
import com.shawady.bodywise.notify.Reminders
import com.shawady.bodywise.util.importPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.LocalDate

class BodyWiseViewModel(app: Application) : AndroidViewModel(app) {
    private val store = BodyWiseStore(app)
    private val settingsStore = SettingsStore(app)

    val meals = mutableStateMapOf<String, List<FoodEntry>>()
    val water = mutableStateMapOf<String, Int>()
    val measurements = mutableStateListOf<Measurement>()
    val photos = mutableStateListOf<ProgressPhoto>()
    var waterGoal by mutableIntStateOf(8)
        private set
    var settings by mutableStateOf(settingsStore.load())
        private set
    var selectedDate by mutableStateOf(LocalDate.now())
    var backupMessage by mutableStateOf("لا توجد نسخة احتياطية حديثة")
        private set
    var busy by mutableStateOf(false)
        private set
    /** One-shot message shown in a snackbar; the UI clears it after showing. */
    var toast by mutableStateOf<String?>(null)

    init {
        Reminders.ensureChannel(app)
        apply(store.load())
    }

    private fun apply(snapshot: Snapshot) {
        meals.clear(); meals.putAll(snapshot.meals)
        water.clear(); water.putAll(snapshot.water)
        measurements.clear(); measurements.addAll(snapshot.measurements.sortedByDescending { it.date })
        photos.clear(); photos.addAll(snapshot.photos.sortedByDescending { it.date })
        waterGoal = snapshot.waterGoal
    }

    private fun snapshot() = Snapshot(meals.toMap(), water.toMap(), measurements.toList(), photos.toList(), waterGoal)
    private fun persist() = store.save(snapshot())

    // ---- Food ----
    private fun key(date: LocalDate, slot: MealSlot) = "$date|${slot.key}"
    fun foods(date: LocalDate, slot: MealSlot): List<FoodEntry> = meals[key(date, slot)] ?: emptyList()
    fun mealsCount(date: LocalDate): Int = MealSlot.entries.sumOf { foods(date, it).size }

    fun addFood(date: LocalDate, slot: MealSlot, entry: FoodEntry) {
        meals[key(date, slot)] = foods(date, slot) + entry
        persist()
    }

    fun updateFood(date: LocalDate, slot: MealSlot, index: Int, entry: FoodEntry) {
        val list = foods(date, slot).toMutableList()
        if (index in list.indices) { list[index] = entry; meals[key(date, slot)] = list; persist() }
    }

    fun removeFood(date: LocalDate, slot: MealSlot, index: Int) {
        val list = foods(date, slot).toMutableList()
        if (index in list.indices) { list.removeAt(index); meals[key(date, slot)] = list; persist() }
    }

    fun canCopyPreviousDay(date: LocalDate): Boolean =
        mealsCount(date) == 0 && mealsCount(date.minusDays(1)) > 0

    fun copyPreviousDay(date: LocalDate) {
        MealSlot.entries.forEach { slot ->
            val previous = foods(date.minusDays(1), slot)
            if (foods(date, slot).isEmpty() && previous.isNotEmpty()) meals[key(date, slot)] = previous
        }
        persist()
    }

    fun recentFoodNames(limit: Int = 8): List<String> =
        meals.entries.sortedByDescending { it.key.substringBefore('|') }
            .flatMap { entry -> entry.value.map { it.name } }
            .distinct().take(limit)

    // ---- Water ----
    fun waterFor(date: LocalDate): Int = water[date.toString()] ?: 0

    fun setWater(date: LocalDate, cups: Int) {
        water[date.toString()] = cups.coerceIn(0, 60)
        persist()
    }

    fun changeWaterGoal(goal: Int) {
        waterGoal = goal.coerceIn(1, 30)
        persist()
    }

    // ---- Measurements ----
    fun saveMeasurement(measurement: Measurement) {
        val index = measurements.indexOfFirst { it.id == measurement.id }
        if (index >= 0) measurements[index] = measurement else measurements.add(measurement)
        val sorted = measurements.sortedByDescending { it.date }
        measurements.clear(); measurements.addAll(sorted)
        persist()
    }

    fun deleteMeasurement(measurement: Measurement) {
        measurements.removeAll { it.id == measurement.id }
        persist()
    }

    // ---- Photos ----
    fun addPhoto(open: () -> InputStream?) {
        viewModelScope.launch {
            busy = true
            val dest = File(store.photosDir, "progress_${System.currentTimeMillis()}.jpg")
            val ok = withContext(Dispatchers.IO) {
                runCatching { importPhoto(open, dest) }.getOrDefault(false).also { if (!it) dest.delete() }
            }
            if (ok) {
                photos.add(0, ProgressPhoto(LocalDate.now(), dest.name))
                persist()
            } else {
                toast = "تعذر حفظ الصورة"
            }
            busy = false
        }
    }

    fun deletePhoto(photo: ProgressPhoto) {
        photos.remove(photo)
        File(store.photosDir, photo.file).delete()
        persist()
    }

    fun photoFile(photo: ProgressPhoto): File = File(store.photosDir, photo.file)

    // ---- Backup ----
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            busy = true
            backupMessage = "جاري إنشاء النسخة الاحتياطية..."
            val snap = snapshot()
            val context = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("تعذر فتح ملف الحفظ")
                    output.use { BackupManager.writeZip(it, snap, store.photosDir) }
                }
            }
            backupMessage = if (result.isSuccess) "تم حفظ النسخة الاحتياطية بنجاح، بالصور والبيانات."
            else "فشل إنشاء النسخة الاحتياطية: ${result.exceptionOrNull()?.message ?: "خطأ غير معروف"}"
            busy = false
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            busy = true
            backupMessage = "جاري فحص واستعادة النسخة الاحتياطية..."
            val context = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val input = context.contentResolver.openInputStream(uri) ?: error("تعذر فتح ملف النسخة")
                    input.use { BackupManager.restore(it, store.photosDir, context.cacheDir) }
                }
            }
            result.onSuccess { restored ->
                val oldFiles = photos.map { it.file }
                apply(restored)
                persist()
                oldFiles.forEach { File(store.photosDir, it).delete() }
                backupMessage = "تمت استعادة البيانات و${restored.photos.size} صورة بنجاح."
            }.onFailure {
                backupMessage = "تعذرت الاستعادة ولم يتم تغيير بياناتك: ${it.message ?: "ملف غير صالح"}"
            }
            busy = false
        }
    }

    // ---- Settings ----
    private fun update(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
        settingsStore.save(settings)
    }

    fun setThemeMode(mode: Int) = update { it.copy(themeMode = mode) }
    fun setAppLock(enabled: Boolean) = update { it.copy(appLock = enabled) }

    fun setWaterReminders(enabled: Boolean) {
        update { it.copy(waterReminders = enabled) }
        Reminders.setWater(getApplication(), enabled)
    }

    fun setWeeklyReminder(enabled: Boolean) {
        update { it.copy(weeklyReminder = enabled) }
        Reminders.setWeekly(getApplication(), enabled)
    }

    fun setAutoBackupFolder(uri: Uri?) {
        update { it.copy(autoBackupUri = uri?.toString()) }
        AutoBackupWorker.schedule(getApplication(), uri != null)
    }
}
