package com.shawady.bodywise.ui.vitals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawady.bodywise.data.SUGAR_TAGS
import com.shawady.bodywise.data.VitalReading
import com.shawady.bodywise.data.VitalType
import com.shawady.bodywise.data.vitalWarning
import androidx.compose.ui.graphics.Color
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.components.AppCard
import com.shawady.bodywise.ui.components.Hint
import com.shawady.bodywise.ui.components.LineChart
import com.shawady.bodywise.ui.components.NumberField
import com.shawady.bodywise.util.format12
import com.shawady.bodywise.util.formatNumber
import com.shawady.bodywise.util.parseDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID


private fun VitalReading.display(): String = when (type) {
    VitalType.BP -> "${formatNumber(value)}/${formatNumber(value2 ?: 0.0)}"
    else -> formatNumber(value)
}

@Composable
fun VitalsScreen(vm: BodyWiseViewModel) {
    var typeIndex by rememberSaveable { mutableStateOf(0) }
    val type = VitalType.entries[typeIndex]
    var editing by remember { mutableStateOf<VitalReading?>(null) }
    var adding by remember { mutableStateOf(false) }
    val readings = vm.vitals.filter { it.type == type }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VitalType.entries.forEachIndexed { i, t ->
                FilterChip(selected = i == typeIndex, onClick = { typeIndex = i }, label = { Text("${t.emoji} ${t.label}") })
            }
        }

        AppCard {
            val latest = readings.firstOrNull()
            Text("آخر قراءة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (latest == null) {
                Text("لسه مفيش قراءات", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(latest.display(), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(type.unit, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                Hint("${latest.at.toLocalDate()}  ${latest.at.toLocalTime().format12()}" + if (latest.tag.isNotBlank()) "  •  ${latest.tag}" else "")
                vitalWarning(latest)?.let { w ->
                    Text(w.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (w.severe) Color(0xFFD32F2F) else Color(0xFFE08A00))
                }
            }
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Text("  سجّل قراءة")
            }
        }

        AppCard {
            Text("التطور", fontWeight = FontWeight.Bold)
            val days = readings.groupBy { it.at.toLocalDate() }.toSortedMap().entries.toList().takeLast(30)
            val main = days.map { (d, list) -> d to list.map { it.value }.average() }
            val second = if (type == VitalType.BP) days.map { (d, list) -> d to list.mapNotNull { it.value2 }.average() } else null
            LineChart(main, type.unit, secondary = second)
            if (second != null) Hint("الخط الأزرق/الأخضر: الانقباضي (العالي) — الخط البرتقالي: الانبساطي (الواطي)")
        }

        AppCard {
            Text("السجل", fontWeight = FontWeight.Bold)
            if (readings.isEmpty()) Hint("اضغط \"سجّل قراءة\" علشان تبدأ.")
            readings.take(60).forEach { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val w = vitalWarning(r)
                        Text(
                            "${r.display()} ${type.unit}" + if (w != null) (if (w.severe) "  ⚠️ خطر" else "  ▲▼ خارج المعدل") else "",
                            fontWeight = FontWeight.SemiBold,
                            color = when { w == null -> MaterialTheme.colorScheme.onSurface; w.severe -> Color(0xFFD32F2F); else -> Color(0xFFE08A00) }
                        )
                        Hint("${r.at.toLocalDate()}  ${r.at.toLocalTime().format12()}" + (if (r.tag.isNotBlank()) "  •  ${r.tag}" else "") + (if (r.note.isNotBlank()) "  •  ${r.note}" else ""))
                    }
                    IconButton(onClick = { editing = r }) { Icon(Icons.Default.Edit, "تعديل") }
                    IconButton(onClick = { vm.deleteVital(r) }) { Icon(Icons.Default.DeleteOutline, "حذف") }
                }
            }
        }

        Hint("التحذيرات حسب المعدلات العامة للبالغين وللتنبيه بس، ومش تشخيص ولا بتغني عن استشارة الدكتور.")
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
    }

    if (adding || editing != null) {
        VitalDialog(
            type = type,
            initial = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { vm.saveVital(it); vm.toast = vitalWarning(it)?.text; adding = false; editing = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VitalDialog(type: VitalType, initial: VitalReading?, onDismiss: () -> Unit, onSave: (VitalReading) -> Unit) {
    val start = initial?.at ?: LocalDateTime.now().withSecond(0).withNano(0)
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(start.toLocalTime()) }
    var v1 by remember { mutableStateOf(initial?.value?.let { formatNumber(it) } ?: "") }
    var v2 by remember { mutableStateOf(initial?.value2?.let { formatNumber(it) } ?: "") }
    var tag by remember { mutableStateOf(initial?.tag?.ifBlank { null } ?: SUGAR_TAGS.first()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${type.label}${if (initial != null) " (تعديل)" else ""}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) { Text(date.toString()) }
                    OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) { Text(time.format12()) }
                }
                when (type) {
                    VitalType.BP -> {
                        NumberField("الانقباضي (العالي)", v1, { v1 = it })
                        NumberField("الانبساطي (الواطي)", v2, { v2 = it })
                    }
                    VitalType.SUGAR -> {
                        NumberField("السكر (mg/dL)", v1, { v1 = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SUGAR_TAGS.forEach { t -> FilterChip(selected = tag == t, onClick = { tag = t }, label = { Text(t) }) }
                        }
                    }
                    VitalType.PULSE -> NumberField("النبض (نبضة/د)", v1, { v1 = it })
                }
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة (اختياري)") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = parseDecimal(v1)
                val b = parseDecimal(v2)
                val problem = when (type) {
                    VitalType.BP -> when {
                        a == null || b == null -> "اكتب الرقمين"
                        a !in 50.0..260.0 -> "الانقباضي لازم يبقى بين 50 و260"
                        b !in 30.0..160.0 -> "الانبساطي لازم يبقى بين 30 و160"
                        b >= a -> "الانبساطي لازم يبقى أقل من الانقباضي"
                        else -> null
                    }
                    VitalType.SUGAR -> if (a == null || a !in 20.0..800.0) "السكر لازم يبقى بين 20 و800" else null
                    VitalType.PULSE -> if (a == null || a !in 30.0..220.0) "النبض لازم يبقى بين 30 و220" else null
                }
                if (problem != null) { error = problem; return@TextButton }
                onSave(
                    VitalReading(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        type = type,
                        at = LocalDateTime.of(date, time),
                        value = a!!,
                        value2 = if (type == VitalType.BP) b else null,
                        tag = if (type == VitalType.SUGAR) tag else "",
                        note = note.trim()
                    )
                )
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("إلغاء") } }
        ) { DatePicker(state = state) }
    }
    if (showTime) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = { TextButton(onClick = { time = LocalTime.of(state.hour, state.minute); showTime = false }) { Text("تم") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("إلغاء") } },
            text = { TimePicker(state = state) }
        )
    }
}
