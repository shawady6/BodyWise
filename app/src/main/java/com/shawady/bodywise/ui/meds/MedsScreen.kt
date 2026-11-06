package com.shawady.bodywise.ui.meds

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
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
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shawady.bodywise.data.DoseSlot
import com.shawady.bodywise.data.DoseStatus
import com.shawady.bodywise.data.MED_INSTRUCTIONS
import com.shawady.bodywise.data.MED_UNITS
import com.shawady.bodywise.data.Medication
import com.shawady.bodywise.data.STRIP_UNITS
import com.shawady.bodywise.data.SlotState
import com.shawady.bodywise.data.needsRefill
import com.shawady.bodywise.data.percentLeft
import com.shawady.bodywise.data.refilled
import com.shawady.bodywise.data.slotState
import com.shawady.bodywise.data.slotsFor
import com.shawady.bodywise.data.suggestTimes
import com.shawady.bodywise.notify.MedAlarms
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.components.AppCard
import com.shawady.bodywise.ui.components.Hint
import com.shawady.bodywise.ui.components.NumberField
import com.shawady.bodywise.ui.components.PhotoThumb
import com.shawady.bodywise.ui.components.SectionTitle
import com.shawady.bodywise.util.format12
import com.shawady.bodywise.util.formatNumber
import com.shawady.bodywise.util.parseDecimal
import com.shawady.bodywise.util.recognizeDrugNames
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
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
private val red = Color(0xFFD32F2F)
private val amber = Color(0xFFE08A00)
private val green = Color(0xFF2E7D32)

private fun Medication.doseText(): String = listOf(dose, unit).filter { it.isNotBlank() }.joinToString(" ")

private fun Medication.stockText(): String? {
    val left = remaining ?: return null
    val perBox = unitsPerBox
    val boxes = if (perBox != null && perBox > 0) " (${formatNumber(Math.round(left / perBox * 10) / 10.0)} علبة)" else ""
    return "${formatNumber(Math.round(left * 10) / 10.0)} $unit$boxes"
}

private fun Medication.packagingText(): String? = when {
    stripsPerBox != null && unitsPerStrip != null -> "العلبة: $stripsPerBox شريط × $unitsPerStrip $unit"
    unitsPerBox != null -> "العلبة: ${formatNumber(unitsPerBox)} $unit"
    else -> null
}

@Composable
fun MedsScreen(vm: BodyWiseViewModel, onOpenSettings: () -> Unit) {
    val now by produceState(LocalDateTime.now()) {
        while (true) { delay(30_000); value = LocalDateTime.now() }
    }
    var sub by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Medication?>(null) }
    var adding by remember { mutableStateOf(false) }
    var refilling by remember { mutableStateOf<Medication?>(null) }
    val refillList = vm.medications.filter { it.needsRefill() }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = sub, containerColor = Color.Transparent) {
            Tab(selected = sub == 0, onClick = { sub = 0 }, text = { Text("الجرعات") })
            Tab(selected = sub == 1, onClick = { sub = 1 }, text = { Text("الأدوية") })
            Tab(
                selected = sub == 2, onClick = { sub = 2 },
                text = { Text(if (refillList.isNotEmpty()) "تجديد (${refillList.size})" else "تجديد", color = if (refillList.isNotEmpty()) red else Color.Unspecified) }
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (sub) {
                0 -> DosesTab(vm, now)
                1 -> MedsTab(vm, onAdd = { adding = true }, onEdit = { editing = it }, onOpenSettings = onOpenSettings)
                else -> RefillTab(vm, refillList) { refilling = it }
            }
            Hint("التطبيق بينظّم مواعيد الدواء بس، ومش بيغني عن تعليمات الدكتور أو الصيدلي.")
            Spacer(Modifier.padding(8.dp))
        }
    }

    if (adding || editing != null) {
        MedDialog(vm, editing, onDismiss = { adding = false; editing = null }, onSave = { vm.saveMedication(it); adding = false; editing = null })
    }
    refilling?.let { med -> RefillDialog(med, onDismiss = { refilling = null }, onSave = { vm.saveMedication(it); refilling = null }) }
}

// ---------------------------------------------------------------- doses

@Composable
private fun DosesTab(vm: BodyWiseViewModel, now: LocalDateTime) {
    val todaySlots = slotsFor(vm.medications, now.toLocalDate())
    AppCard {
        SectionTitle("💊", "جرعات النهارده")
        if (todaySlots.isEmpty()) Hint(if (vm.medications.isEmpty()) "لسه ما أضفتش أدوية. من تبويب \"الأدوية\"." else "مفيش جرعات النهارده.")
        todaySlots.forEach { slot -> SlotRow(vm, slot, slotState(slot, vm.doseLogs, now)) }
    }
    HistoryCard(vm, now)
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
            Text(slot.time.format12(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
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
        SectionTitle("🗓", "سجل التناول (آخر 7 أيام)")
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
                    SlotState.TAKEN -> "✅ اتاخدت ${log?.at?.toLocalTime()?.format12() ?: ""}"
                    SlotState.SKIPPED -> "⏭ اتخطت"
                    SlotState.MISSED -> "❌ فاتت"
                    else -> "🔔 مستنية"
                } + if ((log?.snoozes ?: 0) > 0) "  (اتأجلت ${log?.snoozes} مرة)" else ""
                Hint("${slot.time.format12()}  ${slot.med.name}  —  $mark")
            }
        }
        if (!any) Hint("لسه مفيش سجل.")
    }
}

// ---------------------------------------------------------------- medications

@Composable
private fun MedsTab(vm: BodyWiseViewModel, onAdd: () -> Unit, onEdit: (Medication) -> Unit, onOpenSettings: () -> Unit) {
    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
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
                IconButton(onClick = { onEdit(med) }) { Icon(Icons.Default.Edit, "تعديل") }
                IconButton(onClick = { vm.deleteMedication(med) }) { Icon(Icons.Default.DeleteOutline, "حذف") }
            }
            Hint("المواعيد: " + med.times.joinToString(" • ") { it.format12() })
            Hint("الأيام: " + if (med.days.isEmpty()) "كل يوم" else weekOrder.filter { it in med.days }.joinToString("، ") { it.arabicName() })
            Hint("من ${med.startDate}" + (med.endDate?.let { " إلى $it" } ?: " (بدون نهاية)"))
            med.packagingText()?.let { Hint(it) }
            med.stockText()?.let { stock ->
                val low = med.needsRefill()
                Text(
                    "المتبقي: $stock" + if (low) "  — محتاج تجديد ⚠️" else "",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (low) red else MaterialTheme.colorScheme.onSurface
                )
            }
            med.price?.let { Hint("سعر العلبة: ${formatNumber(it)} ج.م") }
            if (med.notes.isNotBlank()) Hint("ملاحظات: ${med.notes}")
            med.photo?.let { PhotoThumb(vm.medPhotoFile(it), height = 110) }
        }
    }
    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("⚙️ إعدادات التنبيهات والنغمة") }
}

// ---------------------------------------------------------------- refill

@Composable
private fun RefillTab(vm: BodyWiseViewModel, refillList: List<Medication>, onRefill: (Medication) -> Unit) {
    AppCard {
        SectionTitle("🛒", "أدوية محتاجة تتجدد")
        if (refillList.isEmpty()) Hint("كل أدويتك كميتها كويسة ✅ (أي دواء يقل عن 10% من آخر كمية اشتريتها هيظهر هنا).")
        refillList.forEach { med ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(med.name, fontWeight = FontWeight.Bold)
                med.stockText()?.let { Text("المتبقي: $it", color = red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                val pct = med.percentLeft()
                if (pct != null) {
                    LinearProgressIndicator(progress = { pct / 100f }, color = red, modifier = Modifier.fillMaxWidth())
                    Hint("باقي $pct% من الكمية")
                }
                med.price?.let { Hint("آخر سعر للعلبة: ${formatNumber(it)} ج.م") }
                Button(onClick = { onRefill(med) }) { Text("جددت") }
            }
        }
    }
    AppCard {
        SectionTitle("💰", "أسعار الأدوية")
        if (vm.medications.none { it.price != null }) Hint("أضف سعر العلبة من شاشة تعديل الدواء علشان تعرف بكام في المستقبل.")
        vm.medications.filter { it.price != null }.forEach { med ->
            Row(Modifier.fillMaxWidth()) {
                Text(med.name, modifier = Modifier.weight(1f))
                Text("${formatNumber(med.price!!)} ج.م / علبة", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RefillDialog(med: Medication, onDismiss: () -> Unit, onSave: (Medication) -> Unit) {
    var boxes by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf(med.price?.let { formatNumber(it) } ?: "") }
    var perBox by remember { mutableStateOf(med.unitsPerBox?.let { formatNumber(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تجديد ${med.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("عدد العلب الجديدة", boxes, { boxes = it })
                if (med.unitsPerBox == null) NumberField("عدد ${med.unit} في العلبة", perBox, { perBox = it })
                NumberField("سعر العلبة (اختياري)", price, { price = it })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = parseDecimal(boxes)
                val per = med.unitsPerBox ?: parseDecimal(perBox)
                val p = if (price.isBlank()) null else parseDecimal(price)
                when {
                    n == null || n <= 0 -> error = "اكتب عدد العلب"
                    per == null || per <= 0 -> error = "اكتب عدد ${med.unit} في العلبة"
                    price.isNotBlank() && p == null -> error = "السعر مش رقم صحيح"
                    else -> onSave(med.copy(unitsPerBox = per).refilled(n, p))
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

// ---------------------------------------------------------------- add / edit dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedDialog(vm: BodyWiseViewModel, initial: Medication?, onDismiss: () -> Unit, onSave: (Medication) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var dose by remember { mutableStateOf(initial?.dose ?: "1") }
    var unit by remember { mutableStateOf(initial?.unit?.ifBlank { null } ?: MED_UNITS.first()) }
    var times by remember { mutableStateOf(initial?.times ?: listOf(LocalTime.of(8, 0))) }
    var days by remember { mutableStateOf(initial?.days ?: emptySet()) }
    var start by remember { mutableStateOf(initial?.startDate ?: LocalDate.now()) }
    var end by remember { mutableStateOf(initial?.endDate) }
    var instruction by remember { mutableStateOf(initial?.instruction?.ifBlank { null } ?: MED_INSTRUCTIONS.first()) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var price by remember { mutableStateOf(initial?.price?.let { formatNumber(it) } ?: "") }
    var strips by remember { mutableStateOf(initial?.stripsPerBox?.toString() ?: "") }
    var perStrip by remember { mutableStateOf(initial?.unitsPerStrip?.toString() ?: "") }
    var perBoxText by remember { mutableStateOf(if (initial?.stripsPerBox == null) initial?.unitsPerBox?.let { formatNumber(it) } ?: "" else "") }
    val initialBoxes = remember {
        val per = initial?.unitsPerBox
        val left = initial?.remaining
        if (per != null && per > 0 && left != null) formatNumber(Math.round(left / per * 100) / 100.0) else ""
    }
    var boxes by remember { mutableStateOf(initialBoxes) }
    var photo by remember { mutableStateOf(initial?.photo) }
    var newPhoto by remember { mutableStateOf<String?>(null) }
    var pendingCamera by remember { mutableStateOf<File?>(null) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var reading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var timeEditIndex by remember { mutableStateOf<Int?>(null) }
    var datePicking by remember { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCamera
        pendingCamera = null
        if (ok && file != null && file.exists()) {
            vm.importMedPhoto({ file.inputStream() }) { saved ->
                file.delete()
                if (saved != null) {
                    newPhoto?.let { vm.deleteStrayFile(it) }
                    newPhoto = saved
                    photo = saved
                    scope.launch {
                        reading = true
                        suggestions = recognizeDrugNames(vm.medPhotoFile(saved).absolutePath)
                        reading = false
                        if (suggestions.isEmpty()) vm.toast = "ما قدرتش أقرا اسم واضح، اكتبه بإيدك"
                    }
                }
            }
        }
    }
    fun dismiss() { newPhoto?.let { vm.deleteStrayFile(it) }; onDismiss() }
    val isStrip = unit in STRIP_UNITS

    AlertDialog(
        onDismissRequest = { dismiss() },
        title = { Text(if (initial == null) "دواء جديد" else "تعديل الدواء") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                photo?.let { PhotoThumb(vm.medPhotoFile(it), height = 120) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        runCatching {
                            val file = File(context.cacheDir, "med_capture_${System.currentTimeMillis()}.jpg")
                            pendingCamera = file
                            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                        }.onFailure { vm.toast = "تعذر فتح الكاميرا" }
                    }) {
                        Icon(Icons.Default.CameraAlt, null)
                        Text("  ${if (photo == null) "صوّر العلبة" else "صوّر تاني"}")
                    }
                    if (photo != null) TextButton(onClick = { newPhoto?.let { vm.deleteStrayFile(it) }; newPhoto = null; photo = null; suggestions = emptyList() }) { Text("شيل الصورة") }
                }
                if (reading) Hint("بقرا الاسم من الصورة...")
                if (suggestions.isNotEmpty()) {
                    Hint("الاسم اللي قريته من الصورة — اختار الصح:")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        suggestions.forEach { s -> FilterChip(selected = name == s, onClick = { name = s }, label = { Text(s) }) }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("اسم الدواء") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumberField("الجرعة (الكمية في المرة)", dose, { dose = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    MED_UNITS.forEach { u -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) }) }
                }

                Text("عدد المرات في اليوم", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { n ->
                        FilterChip(selected = times.size == n, onClick = { times = suggestTimes(n, times) }, label = { Text("$n") })
                    }
                }
                times.forEachIndexed { i, t ->
                    OutlinedButton(onClick = { timeEditIndex = i }, modifier = Modifier.fillMaxWidth()) { Text("المرة ${i + 1}:  ${t.format12()}") }
                }

                Text("الأيام", fontWeight = FontWeight.SemiBold)
                FilterChip(selected = days.isEmpty(), onClick = { days = emptySet() }, label = { Text("كل يوم") })
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    MED_INSTRUCTIONS.forEach { s -> FilterChip(selected = instruction == s, onClick = { instruction = s }, label = { Text(s) }) }
                }

                Text("العلب والكمية", fontWeight = FontWeight.SemiBold)
                if (isStrip) {
                    NumberField("عدد الشرايط في العلبة", strips, { strips = it })
                    NumberField("عدد ${unit}ات في الشريط", perStrip, { perStrip = it })
                } else {
                    NumberField("عدد ال$unit في العلبة/العبوة", perBoxText, { perBoxText = it })
                }
                NumberField("عدد العلب اللي معاك دلوقتي", boxes, { boxes = it })
                NumberField("سعر العلبة (اختياري)", price, { price = it })
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات (اختياري)") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = parseDecimal(dose)
                val stripsN = strips.trim().toIntOrNull()
                val perStripN = perStrip.trim().toIntOrNull()
                val perBox: Double? = if (isStrip) {
                    if (stripsN != null && perStripN != null) (stripsN * perStripN).toDouble() else null
                } else parseDecimal(perBoxText)
                val boxesN = if (boxes.isBlank()) null else parseDecimal(boxes)
                val priceN = if (price.isBlank()) null else parseDecimal(price)
                val problem = when {
                    name.isBlank() -> "اكتب اسم الدواء"
                    amount == null || amount <= 0 -> "اكتب الجرعة (رقم أكبر من صفر)"
                    end != null && end!!.isBefore(start) -> "تاريخ النهاية قبل البداية"
                    isStrip && (strips.isNotBlank() || perStrip.isNotBlank()) && perBox == null -> "اكتب عدد الشرايط وعدد ${unit}ات الشريط"
                    boxes.isNotBlank() && boxesN == null -> "عدد العلب مش رقم صحيح"
                    boxesN != null && (perBox == null || perBox <= 0) -> "اكتب حجم العلبة (شرايط × أقراص) علشان أحسب الكمية"
                    price.isNotBlank() && priceN == null -> "السعر مش رقم صحيح"
                    else -> null
                }
                if (problem != null) { error = problem; return@TextButton }

                var remaining = initial?.remaining
                var full = initial?.fullQuantity
                if (boxesN != null && perBox != null && boxes != initialBoxes) {
                    val newRemaining = boxesN * perBox
                    remaining = newRemaining
                    full = if (full == null || newRemaining > full) newRemaining else full
                }
                newPhoto = null
                onSave(
                    Medication(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(), dose = formatNumber(amount!!), unit = unit,
                        times = times.sorted(), days = days, startDate = start, endDate = end,
                        instruction = instruction, notes = notes.trim(), photo = photo, remaining = remaining,
                        createdAt = initial?.createdAt ?: LocalDateTime.now(),
                        stripsPerBox = if (isStrip) stripsN else null,
                        unitsPerStrip = if (isStrip) perStripN else null,
                        unitsPerBox = perBox,
                        fullQuantity = full,
                        price = priceN
                    )
                )
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = { dismiss() }) { Text("إلغاء") } }
    )

    timeEditIndex?.let { index ->
        val current = times.getOrNull(index) ?: LocalTime.of(8, 0)
        val state = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = false)
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
