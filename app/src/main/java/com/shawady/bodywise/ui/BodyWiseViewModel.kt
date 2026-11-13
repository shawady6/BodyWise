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
import com.shawady.bodywise.data.DayNote
import com.shawady.bodywise.data.FoodEntry
import com.shawady.bodywise.data.MealSlot
import com.shawady.bodywise.data.Measurement
import com.shawady.bodywise.data.ProgressPhoto
import com.shawady.bodywise.data.SettingsStore
import com.shawady.bodywise.data.Snapshot
import com.shawady.bodywise.data.measurementsToCsv
import com.shawady.bodywise.notify.Reminders
import com.shawady.bodywise.util.importPhoto
import com.shawady.bodywise.widget.WaterWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.LocalDate

/** A delete the user can still take back. [commit] runs when the undo window closes without undo. */
class UndoRequest(val message: String, val undo: () -> Unit, val commit: () -> Unit = {})

class BodyWiseViewModel(app: Application) : AndroidViewModel(app) {
    private val store = BodyWiseStore(app)
    private val settingsStore = SettingsStore(app)

    val meals = mutableStateMapOf<String, List<FoodEntry>>()
    val water = mutableStateMapOf<String, Int>()
    val notes = mutableStateMapOf<String, DayNote>()
    val measurements = mutableStateListOf<Measurement>()
    val photos = mutableStateListOf<ProgressPhoto>()
    private val medStore = com.shawady.bodywise.data.MedStore(app)
    val medications = mutableStateListOf<com.shawady.bodywise.data.Medication>()
    val doseLogs = mutableStateMapOf<String, com.shawady.bodywise.data.DoseLog>()
    val vitals = mutableStateListOf<com.shawady.bodywise.data.VitalReading>()
    private val healthStore = com.shawady.bodywise.data.HealthStore(app)
    val doctors = mutableStateListOf<com.shawady.bodywise.data.Doctor>()
    /** Set by the home screen to open a given sub-tab of "روشتتي" (2 = doctors). */
    var medsSubRequest by mutableStateOf<Int?>(null)
    val appointments = mutableStateListOf<com.shawady.bodywise.data.Appointment>()
    val medDocs = mutableStateListOf<com.shawady.bodywise.data.MedicalDoc>()
    val people = mutableStateListOf<com.shawady.bodywise.data.Person>()
    var medProfile by mutableStateOf(com.shawady.bodywise.data.MedicalProfile())
        private set
    var waterGoal by mutableIntStateOf(8)
        private set
    var heightCm by mutableStateOf<Double?>(null)
        private set
    var targetWeight by mutableStateOf<Double?>(null)
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
    var undoRequest by mutableStateOf<UndoRequest?>(null)
        private set

    init {
        Reminders.ensureChannel(app)
        com.shawady.bodywise.notify.MedAlarms.ensureChannel(app)
        com.shawady.bodywise.update.UpdateChecks.ensureChannel(app)
        com.shawady.bodywise.update.UpdateChecks.schedule(app)
        apply(store.load())
        medStore.prune(LocalDate.now().minusDays(120))
        reloadMeds()
        reloadHealth()
        com.shawady.bodywise.notify.MedAlarms.rescheduleAll(app)
        com.shawady.bodywise.notify.ApptAlarms.ensureChannel(app)
        com.shawady.bodywise.notify.MedAlarms.ensureChannel(app)
        com.shawady.bodywise.notify.ApptAlarms.rescheduleAll(app)
        Reminders.setDaily(app, settings.dailyChecks)
        cleanOrphanPhotos()
    }

    private fun apply(snapshot: Snapshot) {
        meals.clear(); meals.putAll(snapshot.meals)
        water.clear(); water.putAll(snapshot.water)
        notes.clear(); notes.putAll(snapshot.notes)
        measurements.clear(); measurements.addAll(snapshot.measurements.sortedByDescending { it.date })
        photos.clear(); photos.addAll(snapshot.photos.sortedByDescending { it.date })
        vitals.clear(); vitals.addAll(snapshot.vitals.sortedByDescending { it.at })
        waterGoal = snapshot.waterGoal
        heightCm = snapshot.heightCm
        targetWeight = snapshot.targetWeight
    }

    private fun snapshot() = Snapshot(
        meals.toMap(), water.toMap(), measurements.toList(), photos.toList(), waterGoal,
        notes.toMap(), heightCm, targetWeight, vitals.toList(),
        medStore.loadMeds(), medStore.loadLogs().values.toList(),
        healthStore.loadDoctors(), healthStore.loadAppointments(), healthStore.loadProfile(), healthStore.loadDocs(), healthStore.loadPeople()
    )

    private fun persist() = store.save(snapshot())

    /** Photos whose delete was never confirmed (app closed during the undo window) are cleaned up. */
    private fun cleanOrphanPhotos() {
        val used = photos.map { it.file }.toSet()
        viewModelScope.launch(Dispatchers.IO) {
            store.photosDir.listFiles { file ->
                file.name.startsWith("progress_") && file.name.endsWith(".jpg") && file.name !in used
            }?.forEach { it.delete() }
        }
    }

    // ---- Undo ----
    private fun offerUndo(request: UndoRequest) {
        undoRequest?.commit?.invoke()
        undoRequest = request
    }

    fun resolveUndo(request: UndoRequest, undone: Boolean) {
        if (undoRequest !== request) return
        undoRequest = null
        if (undone) request.undo() else request.commit()
    }

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
        if (index !in list.indices) return
        val removed = list.removeAt(index)
        meals[key(date, slot)] = list
        persist()
        offerUndo(UndoRequest("اتحذف \"${removed.name}\"", undo = {
            val current = foods(date, slot).toMutableList()
            current.add(index.coerceAtMost(current.size), removed)
            meals[key(date, slot)] = current
            persist()
        }))
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
        WaterWidgetProvider.refresh(getApplication())
    }

    fun changeWaterGoal(goal: Int) {
        waterGoal = goal.coerceIn(1, 30)
        persist()
        WaterWidgetProvider.refresh(getApplication())
    }

    /** The widget can add cups while the app is in the background; pick them up when the app returns. */
    fun reloadWater() {
        val fresh = store.load().water
        water.clear(); water.putAll(fresh)
    }

    // ---- Daily notes ----
    fun noteFor(date: LocalDate): DayNote? = notes[date.toString()]

    fun saveNote(date: LocalDate, note: DayNote) {
        if (note.isEmpty) notes.remove(date.toString()) else notes[date.toString()] = note
        persist()
    }

    // ---- Profile ----
    fun setProfile(height: Double?, target: Double?) {
        heightCm = height
        targetWeight = target
        persist()
    }

    fun markOnboarded() = update { it.copy(onboarded = true) }

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
        offerUndo(UndoRequest("اتحذف قياس ${measurement.date}", undo = { saveMeasurement(measurement) }))
    }

    // ---- Medications ----
    /** Alarms and notification buttons change these while the app is closed, so the screen re-reads them. */
    fun reloadMeds() {
        val meds = medStore.loadMeds().sortedBy { it.name }
        medications.clear(); medications.addAll(meds)
        val logs = medStore.loadLogs()
        doseLogs.clear(); doseLogs.putAll(logs)
        com.shawady.bodywise.widget.MedWidgetProvider.refresh(getApplication())
    }

    private fun medsChanged() {
        reloadMeds()
        com.shawady.bodywise.notify.MedAlarms.rescheduleAll(getApplication())
    }

    fun saveMedication(med: com.shawady.bodywise.data.Medication) {
        val old = medications.firstOrNull { it.id == med.id }
        medStore.upsert(med)
        if (old?.photo != null && old.photo != med.photo) File(store.photosDir, old.photo).delete()
        medsChanged()
    }

    fun deleteMedication(med: com.shawady.bodywise.data.Medication) {
        medStore.delete(med.id)
        medsChanged()
        offerUndo(UndoRequest("اتحذف دواء ${med.name}",
            undo = { medStore.upsert(med); medsChanged() },
            commit = { med.photo?.let { File(store.photosDir, it).delete() } }))
    }

    fun markDose(slot: com.shawady.bodywise.data.DoseSlot, status: com.shawady.bodywise.data.DoseStatus) {
        medStore.mark(slot.med, slot.date, slot.time, status, java.time.LocalDateTime.now())
        reloadMeds()
        getApplication<Application>().getSystemService(android.app.NotificationManager::class.java)
            .cancel(("false|${slot.med.id}|${slot.time}").hashCode())
    }

    fun clearDose(slot: com.shawady.bodywise.data.DoseSlot) {
        medStore.clear(slot.med, slot.date, slot.time)
        reloadMeds()
    }

    fun importMedPhoto(open: () -> InputStream?, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val dest = File(store.photosDir, "med_${System.currentTimeMillis()}.jpg")
            val ok = withContext(Dispatchers.IO) {
                runCatching { importPhoto(open, dest, 2048) }.getOrDefault(false).also { if (!it) dest.delete() }
            }
            if (!ok) toast = "تعذر حفظ الصورة"
            onDone(if (ok) dest.name else null)
        }
    }

    /** Copies a PDF into app storage (max [com.shawady.bodywise.data.MAX_PDF_BYTES]); [onDone] gets the stored name or null. */
    fun importDocPdf(open: () -> InputStream?, originalName: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val stem = com.shawady.bodywise.data.safeFileStem(originalName).ifBlank { "ملف" }
            val dest = File(store.photosDir, "med_pdf_${System.currentTimeMillis()}_$stem.pdf")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val limit = com.shawady.bodywise.data.MAX_PDF_BYTES
                    var copied = 0L
                    val input = open() ?: return@runCatching "fail"
                    input.use { source ->
                        dest.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = source.read(buffer)
                                if (read < 0) break
                                copied += read
                                if (copied > limit) return@runCatching "big"
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                    // A real PDF starts with "%PDF".
                    val header = dest.inputStream().use { ins -> ByteArray(4).also { ins.read(it) } }
                    if (String(header, Charsets.ISO_8859_1) != "%PDF") "notpdf" else "ok"
                }.getOrDefault("fail").also { if (it != "ok") dest.delete() }
            }
            when (result) {
                "big" -> toast = "الملف أكبر من 25 ميجا"
                "notpdf" -> toast = "الملف ده مش PDF"
                "fail" -> toast = "تعذر حفظ الملف"
            }
            onDone(if (result == "ok") dest.name else null)
        }
    }

    fun medPhotoFile(name: String): File = File(store.photosDir, name)
    fun deleteStrayFile(name: String) { File(store.photosDir, name).delete() }

    // ---- Doctors, appointments, medical file ----
    fun reloadHealth() {
        doctors.clear(); doctors.addAll(healthStore.loadDoctors().sortedBy { it.name })
        appointments.clear(); appointments.addAll(healthStore.loadAppointments().sortedBy { it.at })
        medDocs.clear(); medDocs.addAll(healthStore.loadDocs().sortedByDescending { it.date })
        people.clear(); people.addAll(healthStore.loadPeople())
        medProfile = healthStore.loadProfile()
    }

    private fun apptsChanged() {
        reloadHealth()
        com.shawady.bodywise.notify.ApptAlarms.rescheduleAll(getApplication())
    }

    fun saveDoctor(d: com.shawady.bodywise.data.Doctor) {
        healthStore.upsertDoctor(d)
        reloadHealth()
    }

    fun deleteDoctor(d: com.shawady.bodywise.data.Doctor) {
        healthStore.deleteDoctor(d.id)
        reloadHealth()
        offerUndo(UndoRequest("اتحذف الدكتور ${d.name}", undo = { healthStore.upsertDoctor(d); reloadHealth() }))
    }

    fun saveAppointment(a: com.shawady.bodywise.data.Appointment) {
        healthStore.upsertAppointment(a)
        apptsChanged()
    }

    fun deleteAppointment(a: com.shawady.bodywise.data.Appointment) {
        healthStore.deleteAppointment(a.id)
        apptsChanged()
        offerUndo(UndoRequest("اتحذف الموعد", undo = { healthStore.upsertAppointment(a); apptsChanged() }))
    }

    fun saveProfile(p: com.shawady.bodywise.data.MedicalProfile) {
        healthStore.saveProfile(p)
        medProfile = p
    }

    fun saveDoc(d: com.shawady.bodywise.data.MedicalDoc) {
        val old = medDocs.firstOrNull { it.id == d.id }
        healthStore.upsertDoc(d)
        old?.files?.filter { it !in d.files }?.forEach { File(store.photosDir, it).delete() }
        reloadHealth()
    }

    fun deleteDoc(d: com.shawady.bodywise.data.MedicalDoc) {
        healthStore.deleteDoc(d.id)
        reloadHealth()
        offerUndo(UndoRequest("اتحذف المستند ${d.title}",
            undo = { healthStore.upsertDoc(d); reloadHealth() },
            commit = { d.files.forEach { File(store.photosDir, it).delete() } }))
    }

    // ---- People I look after ----
    fun setRepeatAlerts(enabled: Boolean) = update { it.copy(repeatAlerts = enabled) }

    /** Days without a backup once it passes the reminder threshold (since install if never backed up). */
    fun backupOverdueDays(): Long? = com.shawady.bodywise.notify.Reminders.backupOverdueDays(getApplication())

    fun setCareEnabled(enabled: Boolean) = update { it.copy(careEnabled = enabled) }

    /** Returns an error message, or null when added. */
    fun addPerson(name: String): String? {
        val clean = name.trim()
        if (clean.isBlank()) return "اكتب الاسم"
        if (people.size >= com.shawady.bodywise.data.MAX_PEOPLE) return "الحد الأقصى ${com.shawady.bodywise.data.MAX_PEOPLE} أشخاص"
        if (people.any { it.name == clean } || clean == com.shawady.bodywise.data.ME_LABEL) return "الاسم ده موجود"
        healthStore.savePeople(people.toList() + com.shawady.bodywise.data.Person(java.util.UUID.randomUUID().toString(), clean))
        reloadHealth()
        return null
    }

    /** [deleteData] removes the person's medications and appointments; otherwise they move to the owner. */
    fun deletePerson(person: com.shawady.bodywise.data.Person, deleteData: Boolean) {
        val meds = medStore.loadMeds().filter { it.personId == person.id }
        meds.forEach { if (deleteData) { medStore.delete(it.id); it.photo?.let { p -> File(store.photosDir, p).delete() } } else medStore.upsert(it.copy(personId = null)) }
        healthStore.loadAppointments().filter { it.personId == person.id }.forEach {
            if (deleteData) healthStore.deleteAppointment(it.id) else healthStore.upsertAppointment(it.copy(personId = null))
        }
        healthStore.savePeople(healthStore.loadPeople().filter { it.id != person.id })
        medsChanged()
        apptsChanged()
    }

    // ---- Vitals ----
    fun saveVital(reading: com.shawady.bodywise.data.VitalReading) {
        val index = vitals.indexOfFirst { it.id == reading.id }
        if (index >= 0) vitals[index] = reading else vitals.add(reading)
        val sorted = vitals.sortedByDescending { it.at }
        vitals.clear(); vitals.addAll(sorted)
        persist()
    }

    fun deleteVital(reading: com.shawady.bodywise.data.VitalReading) {
        vitals.removeAll { it.id == reading.id }
        persist()
        offerUndo(UndoRequest("اتحذفت قراءة ${reading.type.label}", undo = { saveVital(reading) }))
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            val csv = measurementsToCsv(measurements.toList())
            val context = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("تعذر فتح ملف الحفظ")
                    output.use {
                        it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                        it.write(csv.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            toast = if (result.isSuccess) "تم تصدير القياسات (CSV)" else "فشل التصدير: ${result.exceptionOrNull()?.message ?: "خطأ غير معروف"}"
        }
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
        val index = photos.indexOf(photo)
        if (index < 0) return
        photos.removeAt(index)
        persist()
        offerUndo(UndoRequest(
            "اتحذفت صورة ${photo.date}",
            undo = {
                photos.add(index.coerceAtMost(photos.size), photo)
                val sorted = photos.sortedByDescending { it.date }
                photos.clear(); photos.addAll(sorted)
                persist()
            },
            commit = { File(store.photosDir, photo.file).delete() }
        ))
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
            if (result.isSuccess) context.getSharedPreferences("bodywise_data", android.content.Context.MODE_PRIVATE).edit().putLong("last_backup", System.currentTimeMillis()).apply()
            backupMessage = if (result.isSuccess) "تم حفظ النسخة الاحتياطية بنجاح، بالصور والبيانات."
            else "فشل إنشاء النسخة الاحتياطية: ${result.exceptionOrNull()?.message ?: "خطأ غير معروف"}"
            busy = false
        }
    }

    fun restoreBackup(uri: Uri, onDone: () -> Unit = {}) {
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
                undoRequest?.commit?.invoke()
                undoRequest = null
                val oldFiles = photos.map { it.file } + medications.mapNotNull { it.photo } + medDocs.flatMap { it.files }
                apply(restored)
                persist()
                medStore.replaceAll(restored.medications, restored.doseLogs)
                medsChanged()
                healthStore.replaceAll(restored.doctors, restored.appointments, restored.medProfile, restored.medDocs, restored.people)
                if (restored.people.isNotEmpty()) update { it.copy(careEnabled = true) }
                apptsChanged()
                oldFiles.forEach { File(store.photosDir, it).delete() }
                WaterWidgetProvider.refresh(context)
                backupMessage = "تمت استعادة البيانات و${restored.photos.size} صورة بنجاح."
                onDone()
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

    fun setUpdateNotices(enabled: Boolean) = update { it.copy(updateNotices = enabled) }

    fun setDailyChecks(enabled: Boolean) {
        update { it.copy(dailyChecks = enabled) }
        Reminders.setDaily(getApplication(), enabled)
    }

    fun setMedTone(uri: String?) {
        update { it.copy(medTone = uri) }
        com.shawady.bodywise.notify.MedAlarms.ensureChannel(getApplication())
    }

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
