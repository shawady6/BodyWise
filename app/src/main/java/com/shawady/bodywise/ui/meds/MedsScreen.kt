package com.shawady.bodywise.ui.meds

import com.shawady.bodywise.ui.theme.Status
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
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
                text = { Text(if (refillList.isNotEmpty()) "تجديد (${refillList.size})" else "تجديد", color = if (refillList.isNotEmpty()) Status.danger() else Color.Unspecified) }
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
        MedEditor(vm, editing, onDismiss = { adding = false; editing = null }, onSave = { vm.saveMedication(it); adding = false; editing = null })
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
        SlotState.TAKEN -> "✅ اتاخدت" to Status.success()
        SlotState.SKIPPED -> "⏭ اتخطت" to MaterialTheme.colorScheme.onSurfaceVariant
        SlotState.SNOOZED -> "⏰ متأجلة" to Status.warning()
        SlotState.UPCOMING -> "قادمة" to MaterialTheme.colorScheme.onSurfaceVariant
        SlotState.DUE -> "🔔 وقتها دلوقتي" to Status.warning()
        SlotState.MISSED -> "❌ فاتت" to Status.danger()
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
    if (vm.medications.isEmpty()) {
        AppCard {
            Text("💊", fontSize = 40.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("لسه ما أضفتش أي دواء", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Hint("أضف دواءك الأول وهنفكّرك بمواعيده.")
        }
    }
    vm.medications.forEach { med -> MedCard(vm, med, onEdit) }
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
                med.stockText()?.let { Text("المتبقي: $it", color = Status.danger(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                val pct = med.percentLeft()
                if (pct != null) {
                    LinearProgressIndicator(progress = { pct / 100f }, color = Status.danger(), modifier = Modifier.fillMaxWidth())
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


@Composable
private fun MedCard(vm: BodyWiseViewModel, med: Medication, onEdit: (Medication) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(med.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    listOf(med.doseText(), med.instruction.takeIf { it != "لا يهم" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" • "),
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "خيارات") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("تعديل") }, onClick = { menu = false; onEdit(med) })
                    DropdownMenuItem(text = { Text("حذف") }, onClick = { menu = false; vm.deleteMedication(med) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            med.times.forEach { TimeChip(it.format12()) }
        }
        Hint(
            (if (med.days.isEmpty()) "كل يوم" else weekOrder.filter { it in med.days }.joinToString("، ") { it.arabicName() }) +
                " • " + (med.endDate?.let { "لحد $it" } ?: "مستمر")
        )
        val left = med.remaining
        if (left != null) {
            val low = med.needsRefill()
            val pct = med.percentLeft()
            if (pct != null) {
                LinearProgressIndicator(
                    progress = { pct / 100f },
                    color = if (low) Status.danger() else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                "باقي ${med.stockText().orEmpty()}" + if (low) "  — محتاج تجديد ⚠️" else "",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (low) Status.danger() else MaterialTheme.colorScheme.onSurface
            )
        }
        med.price?.let { Hint("سعر العلبة: ${formatNumber(it)} ج.م") }
        if (med.notes.isNotBlank()) Hint("ملاحظات: ${med.notes}")
        med.photo?.let { PhotoThumb(vm.medPhotoFile(it), height = 90) }
    }
}

@Composable
private fun TimeChip(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
