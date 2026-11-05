package com.shawady.bodywise.ui.meds

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.shawady.bodywise.data.DoseSlot
import com.shawady.bodywise.data.DoseStatus
import com.shawady.bodywise.data.MED_INSTRUCTIONS
import com.shawady.bodywise.data.MED_UNITS
import com.shawady.bodywise.data.Medication
import com.shawady.bodywise.data.SlotState
import com.shawady.bodywise.data.doseAmount
import com.shawady.bodywise.data.slotState
import com.shawady.bodywise.data.slotsFor
import com.shawady.bodywise.notify.MedAlarms
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.components.AppCard
import com.shawady.bodywise.ui.components.Hint
import com.shawady.bodywise.ui.components.NumberField
import com.shawady.bodywise.ui.components.PhotoThumb
import com.shawady.bodywise.util.formatNumber
import com.shawady.bodywise.util.parseDecimal
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private val weekOrder = listOf(
    DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
)
private val arabic = Locale("ar")
private fun DayOfWeek.arabicName(): String = getDisplayName(TextStyle.FULL, arabic)
private fun LocalTime.hm(): String = toString().take(5)
private val red = Color(0xFFD32F2F)
private val amber = Color(0xFFE08A00)
private val green = Color(0xFF2E7D32)

private fun Medication.doseText(): String = listOf(dose, unit).filter { it.isNotBlank() }.joinToString(" ")

@Composable
fun MedsScreen(vm: BodyWiseViewModel) {
    val now by produceState(LocalDateTime.now()) {
        while (true) { delay(30_000); value = LocalDateTime.now() }
    }
    var editing by remember { mutableStateOf<Medication?>(null) }
    var adding by remember { mutableStateOf(false) }
    val today = now.toLocalDate()
    val todaySlots = slotsFor(vm.medications, today)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppCard {
            Text("جرعات النهارده", fontWeight = FontWeight.Bold)
            if (todaySlots.isEmpty()) Hint(if (vm.medications.isEmpty()) "لسه ما أضفتش أدوية." else "مفيش جرعات النهارده.")
            todaySlots.forEach { slot -> SlotRow(vm, slot, slotState(slot, vm.doseLogs, now)) }
        }

        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Text("  إضافة دواء")
        }

        vm.medications.forEach { med ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("💊 ${med.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Hint(listOf(med.doseText(), med.instruction.takeIf { it != "لا يهم" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" • "))
                    }
                    IconButton(onClick = { editing = med }) { Icon(Icons.Default.Edit, "تعديل") }
                    IconButton(onClick = { vm.deleteMedication(med) }) { Icon(Icons.Default.DeleteOutline, "حذف") }
                }
                Hint("المواعيد: " + med.times.joinToString(" • ") { it.hm() })
                Hint("الأيام: " + if (med.days.isEmpty()) "كل يوم" else weekOrder.filter { it in med.days }.joinToString("، ") { it.arabicName() })
                Hint("من ${med.startDate}" + (med.endDate?.let { " إلى $it" } ?: " (بدون نهاية)"))
                med.remaining?.let { left ->
                    val perDay = med.doseAmount() * med.times.size
                    val low = left <= perDay * 3
                    Text(
                        "المتبقي: ${formatNumber(left)} ${med.unit}" + if (low) "  — قرب يخلص ⚠️" else "",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (low) red else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (med.notes.isNotBlank()) Hint("ملاحظات: ${med.notes}")
                med.photo?.let { PhotoThumb(vm.medPhotoFile(it), height = 110) }
            }
        }

        HistoryCard(vm, now)
        AlertsCard()
        Hint("التطبيق بينظّم مواعيد الدواء بس، ومش بيغني عن تعليمات الدكتور أو الصيدلي.")
        Spacer(Modifier.padding(8.dp))
    }

    if (adding || editing != null) {
        MedDialog(vm, editing, onDismiss = { adding = false; editing = null }, onSave = { vm.saveMedication(it); adding = false; editing = null })
    }
}

@Composable
private fun SlotRow(vm: BodyWiseViewModel, slot: DoseSlot, state: SlotState) {
    val (label, color) = when (state) {
        SlotState.TAKEN -> "✅ اتاخدت" to green
        SlotState.SKIPPED -> "⏭ اتخطت" to MaterialTheme.colorScheme.onSurfaceVariant
        SlotState.SNOOZED -> "⏰ متأجلة" to amber
        SlotState.UPCOMING -> "قادمة" to MaterialTheme.colorScheme.onSurfaceVariant
        SlotState.DUE -> "🔔 وقتها دلوقتي" to amber
        SlotState.MISSED -> "❌ فاتت" to red
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(slot.time.hm(), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(slot.med.name, fontWeight = FontWeight.SemiBold)
                Hint(slot.med.doseText())
            }
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state == SlotState.TAKEN || state == SlotState.SKIPPED) {
                TextButton(onClick = { vm.clearDose(slot) }) { Text("تراجع") }
            } else {
                Button(onClick = { vm.markDose(slot, DoseStatus.TAKEN) }) { Text("أخدتها") }
                TextButton(onClick = { vm.markDose(slot, DoseStatus.SKIPPED) }) { Text("تخطي") }
            }
        }
    }
}

@Composable
private fun HistoryCard(vm: BodyWiseViewModel, now: LocalDateTime) {
    AppCard {
        Text("سجل التناول (آخر 7 أيام)", fontWeight = FontWeight.Bold)
        var any = false
        for (back in 0..6) {
            val day = now.toLocalDate().minusDays(back.toLong())
            val slots = slotsFor(vm.medications, day).filter { slotState(it, vm.doseLogs, now) != SlotState.UPCOMING }
            if (slots.isEmpty()) continue
            any = true
            val states = slots.map { it to slotState(it, vm.doseLogs, now) }
            val taken = states.count { it.second == SlotState.TAKEN }
            val missed = states.count { it.second == SlotState.MISSED }
            Text(
                "$day — أخدت $taken من ${slots.size}" + if (missed > 0) "، فاتت $missed" else "",
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
            )
            states.forEach { (slot, state) ->
                val log = vm.doseLogs[slot.key]
                val mark = when (state) {
                    SlotState.TAKEN -> "✅ اتاخدت ${log?.at?.toLocalTime()?.hm() ?: ""}"
                    SlotState.SKIPPED -> "⏭ اتخطت"
                    SlotState.MISSED -> "❌ فاتت"
                    else -> "🔔 مستنية"
                } + if ((log?.snoozes ?: 0) > 0) "  (اتأجلت ${log?.snoozes} مرة)" else ""
                Hint("${slot.time.hm()}  ${slot.med.name}  —  $mark")
            }
        }
        if (!any) Hint("لسه مفيش سجل.")
    }
}

@Composable
private fun AlertsCard() {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    @Suppress("UNUSED_EXPRESSION") refresh
    val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val batteryOk = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    AppCard {
        Text("إعدادات التنبيهات", fontWeight = FontWeight.Bold)
        StatusLine("الإشعارات", notificationsOn)
        StatusLine("المواعيد الدقيقة", exactOk)
        StatusLine("استثناء من توفير البطارية", batteryOk)
        if (!notificationsOn) {
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else openAppNotificationSettings(context)
            }, modifier = Modifier.fillMaxWidth()) { Text("تفعيل الإشعارات") }
            OutlinedButton(onClick = { openAppNotificationSettings(context) }, modifier = Modifier.fillMaxWidth()) { Text("لو مش بتظهر: افتح إعدادات الإشعارات") }
        }
        if (!exactOk) {
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("السماح بالمواعيد الدقيقة") }
        }
        if (!batteryOk) {
            OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, modifier = Modifier.fillMaxWidth()) { Text("استثناء BodyWise من توفير البطارية") }
            Hint("في بعض الموبايلات (شاومي، سامسونج، أوبو...) لازم ده علشان التنبيه يشتغل والتطبيق مقفول.")
        }
        OutlinedButton(onClick = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, MedAlarms.CHANNEL)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("صوت واهتزاز التنبيه") }
        Button(onClick = { MedAlarms.sendTest(context) }, modifier = Modifier.fillMaxWidth()) { Text("جرّب تنبيه دلوقتي") }
    }
}

private fun openAppNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Text((if (ok) "✅ " else "⚠️ ") + label + (if (ok) " — تمام" else " — محتاج تفعيل"), fontSize = 13.sp, color = if (ok) green else amber)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedDialog(vm: BodyWiseViewModel, initial: Medication?, onDismiss: () -> Unit, onSave: (Medication) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var dose by remember { mutableStateOf(initial?.dose ?: "1") }
    var unit by remember { mutableStateOf(initial?.unit?.ifBlank { null } ?: MED_UNITS.first()) }
    var times by remember { mutableStateOf(initial?.times ?: listOf(LocalTime.of(8, 0))) }
    var days by remember { mutableStateOf(initial?.days ?: emptySet()) }
    var start by remember { mutableStateOf(initial?.startDate ?: LocalDate.now()) }
    var end by remember { mutableStateOf(initial?.endDate) }
    var instruction by remember { mutableStateOf(initial?.instruction?.ifBlank { null } ?: MED_INSTRUCTIONS.first()) }
    var remaining by remember { mutableStateOf(initial?.remaining?.let { formatNumber(it) } ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var photo by remember { mutableStateOf(initial?.photo) }
    var newPhoto by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var timeEditIndex by remember { mutableStateOf<Int?>(null) }
    var datePicking by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            vm.importMedPhoto({ context.contentResolver.openInputStream(uri) }) { saved ->
                if (saved != null) {
                    newPhoto?.let { vm.deleteStrayFile(it) }
                    newPhoto = saved
                    photo = saved
                }
            }
        }
    }
    fun dismiss() { newPhoto?.let { vm.deleteStrayFile(it) }; onDismiss() }

    AlertDialog(
        onDismissRequest = { dismiss() },
        title = { Text(if (initial == null) "دواء جديد" else "تعديل الدواء") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم الدواء") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumberField("الجرعة (الكمية في المرة)", dose, { dose = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScrollCompat()) {
                    MED_UNITS.forEach { u -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) }) }
                }
                Text("مواعيد اليوم (${times.size} مرة)", fontWeight = FontWeight.SemiBold)
                times.forEachIndexed { i, t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { timeEditIndex = i }, modifier = Modifier.weight(1f)) { Text(t.hm()) }
                        if (times.size > 1) IconButton(onClick = { times = times.filterIndexed { j, _ -> j != i } }) { Icon(Icons.Default.DeleteOutline, "حذف الموعد") }
                    }
                }
                OutlinedButton(onClick = { times = times + LocalTime.of(20, 0); timeEditIndex = times.size - 1 }, modifier = Modifier.fillMaxWidth()) { Text("+ موعد تاني") }
                Text("الأيام", fontWeight = FontWeight.SemiBold)
                FilterChip(selected = days.isEmpty(), onClick = { days = emptySet() }, label = { Text("كل يوم") })
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScrollCompat()) {
                    weekOrder.forEach { d ->
                        FilterChip(selected = d in days, onClick = { days = if (d in days) days - d else days + d }, label = { Text(d.getDisplayName(TextStyle.SHORT, arabic)) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { datePicking = "start" }, modifier = Modifier.weight(1f)) { Text("من $start") }
                    OutlinedButton(onClick = { datePicking = "end" }, modifier = Modifier.weight(1f)) { Text(end?.let { "إلى $it" } ?: "بدون نهاية") }
                }
                if (end != null) TextButton(onClick = { end = null }) { Text("إلغاء تاريخ النهاية") }
                Text("التعليمات", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScrollCompat()) {
                    MED_INSTRUCTIONS.forEach { s -> FilterChip(selected = instruction == s, onClick = { instruction = s }, label = { Text(s) }) }
                }
                NumberField("الكمية المتبقية (اختياري)", remaining, { remaining = it })
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات (اختياري)") }, modifier = Modifier.fillMaxWidth())
                photo?.let { PhotoThumb(vm.medPhotoFile(it), height = 110) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pick.launch("image/*") }) { Text(if (photo == null) "إضافة صورة" else "تغيير الصورة") }
                    if (photo != null) TextButton(onClick = { newPhoto?.let { vm.deleteStrayFile(it) }; newPhoto = null; photo = null }) { Text("شيل الصورة") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = parseDecimal(dose)
                val left = if (remaining.isBlank()) null else parseDecimal(remaining)
                val problem = when {
                    name.isBlank() -> "اكتب اسم الدواء"
                    amount == null || amount <= 0 -> "اكتب الجرعة (رقم أكبر من صفر)"
                    times.map { it.hm() }.toSet().size != times.size -> "فيه موعدين متكررين"
                    end != null && end!!.isBefore(start) -> "تاريخ النهاية قبل البداية"
                    remaining.isNotBlank() && left == null -> "الكمية المتبقية مش رقم صحيح"
                    else -> null
                }
                if (problem != null) { error = problem; return@TextButton }
                newPhoto = null
                onSave(
                    Medication(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(), dose = formatNumber(amount!!), unit = unit,
                        times = times.sorted(), days = days, startDate = start, endDate = end,
                        instruction = instruction, notes = notes.trim(), photo = photo, remaining = left,
                        createdAt = initial?.createdAt ?: LocalDateTime.now()
                    )
                )
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = { dismiss() }) { Text("إلغاء") } }
    )

    timeEditIndex?.let { index ->
        val current = times.getOrNull(index) ?: LocalTime.of(8, 0)
        val state = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timeEditIndex = null },
            confirmButton = {
                TextButton(onClick = {
                    times = times.mapIndexed { j, t -> if (j == index) LocalTime.of(state.hour, state.minute) else t }
                    timeEditIndex = null
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { timeEditIndex = null }) { Text("إلغاء") } },
            text = { TimePicker(state = state) }
        )
    }
    datePicking?.let { which ->
        val base = if (which == "start") start else (end ?: start)
        val state = rememberDatePickerState(initialSelectedDateMillis = base.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { datePicking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val picked = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == "start") start = picked else end = picked
                    }
                    datePicking = null
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { datePicking = null }) { Text("إلغاء") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))
