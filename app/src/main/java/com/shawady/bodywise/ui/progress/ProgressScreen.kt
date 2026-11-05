package com.shawady.bodywise.ui.progress

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.shawady.bodywise.data.Measurement
import com.shawady.bodywise.data.Metric
import com.shawady.bodywise.data.ProgressPhoto
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.components.AppCard
import com.shawady.bodywise.ui.components.ConfirmDialog
import com.shawady.bodywise.ui.components.Hint
import com.shawady.bodywise.ui.components.LineChart
import com.shawady.bodywise.ui.components.NumberField
import com.shawady.bodywise.ui.components.PhotoThumb
import com.shawady.bodywise.util.formatNumber
import com.shawady.bodywise.util.parseDecimal
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

private class MeasurementEditor(val initial: Measurement?)

@Composable
fun ProgressScreen(vm: BodyWiseViewModel) {
    val context = LocalContext.current
    var metric by remember { mutableStateOf(Metric.WEIGHT) }
    var measurementEditor by remember { mutableStateOf<MeasurementEditor?>(null) }
    var measurementToDelete by remember { mutableStateOf<Measurement?>(null) }
    var photoToDelete by remember { mutableStateOf<ProgressPhoto?>(null) }
    var showCompare by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val captured = pendingCameraFile
        pendingCameraFile = null
        if (success && captured != null && captured.exists()) {
            vm.addPhoto { captured.inputStream() }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) vm.addPhoto { context.contentResolver.openInputStream(uri) }
    }

    val series = vm.measurements
        .mapNotNull { m -> m.values[metric]?.let { m.date to it } }
        .sortedBy { it.first }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Metric.entries.toList()) { m ->
                    FilterChip(selected = m == metric, onClick = { metric = m }, label = { Text(m.label) })
                }
            }
        }
        item {
            AppCard {
                Text("تطور ${metric.label}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (series.isNotEmpty()) {
                    val latest = series.last().second
                    Text("آخر قيمة: ${formatNumber(latest)} ${metric.unit}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    if (series.size >= 2) {
                        val sincePrevious = latest - series[series.size - 2].second
                        val sinceFirst = latest - series.first().second
                        Hint("عن القياس السابق: ${signed(sincePrevious)} ${metric.unit} • منذ البداية: ${signed(sinceFirst)} ${metric.unit}")
                    }
                }
                LineChart(series, metric.unit)
            }
        }
        item {
            Button(onClick = { measurementEditor = MeasurementEditor(null) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("تسجيل قياسات جديدة")
            }
        }
        item {
            Text("سجل القياسات", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (vm.measurements.isEmpty()) Hint("لسه مفيش قياسات مسجلة.")
        }
        items(vm.measurements, key = { it.id }) { m ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.date.toString(), fontWeight = FontWeight.Bold)
                        Hint(
                            Metric.entries.mapNotNull { metricItem ->
                                m.values[metricItem]?.let { "${metricItem.label}: ${formatNumber(it)} ${metricItem.unit}" }
                            }.joinToString(" • ")
                        )
                    }
                    IconButton(onClick = { measurementEditor = MeasurementEditor(m) }) { Icon(Icons.Default.Edit, "تعديل القياس") }
                    IconButton(onClick = { measurementToDelete = m }) { Icon(Icons.Default.DeleteOutline, "حذف القياس") }
                }
            }
        }
        item {
            AppCard {
                Text("صور التقدم الأسبوعية 📸", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Hint("أضف صورة كل أسبوع علشان تلاحظ التغيير مع الوقت. الصور محفوظة على جهازك فقط.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        runCatching {
                            val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                            pendingCameraFile = file
                            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                        }.onFailure { vm.toast = "تعذر فتح الكاميرا" }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CameraAlt, null)
                        Spacer(Modifier.width(6.dp))
                        Text("الكاميرا")
                    }
                    OutlinedButton(onClick = { gallery.launch("image/*") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PhotoLibrary, null)
                        Spacer(Modifier.width(6.dp))
                        Text("المعرض")
                    }
                }
                if (vm.photos.size >= 2) {
                    OutlinedButton(onClick = { showCompare = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CompareArrows, null)
                        Spacer(Modifier.width(6.dp))
                        Text("مقارنة قبل / بعد")
                    }
                }
                if (vm.busy) Hint("جاري حفظ الصورة...")
            }
        }
        if (vm.photos.isEmpty()) item { Hint("لسه مفيش صور تقدم.") }
        items(vm.photos, key = { it.file }) { photo ->
            AppCard {
                PhotoThumb(vm.photoFile(photo))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Hint(photo.date.toString(), Modifier.weight(1f))
                    IconButton(onClick = { photoToDelete = photo }) { Icon(Icons.Default.DeleteOutline, "حذف الصورة") }
                }
            }
        }
    }

    measurementEditor?.let { current ->
        MeasurementDialog(
            initial = current.initial,
            onSave = { vm.saveMeasurement(it); measurementEditor = null },
            onDismiss = { measurementEditor = null }
        )
    }
    measurementToDelete?.let { target ->
        ConfirmDialog("حذف القياس؟", "سيتم حذف قياسات ${target.date} نهائيًا.", "حذف",
            onConfirm = { vm.deleteMeasurement(target); measurementToDelete = null },
            onDismiss = { measurementToDelete = null })
    }
    photoToDelete?.let { target ->
        ConfirmDialog("حذف الصورة؟", "سيتم حذف صورة ${target.date} من جهازك نهائيًا.", "حذف",
            onConfirm = { vm.deletePhoto(target); photoToDelete = null },
            onDismiss = { photoToDelete = null })
    }
    if (showCompare) {
        CompareDialog(vm, onDismiss = { showCompare = false })
    }
}

private fun signed(value: Double): String {
    val text = formatNumber(Math.round(value * 10) / 10.0)
    return if (value > 0) "+$text" else text
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementDialog(initial: Measurement?, onSave: (Measurement) -> Unit, onDismiss: () -> Unit) {
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    val texts = remember {
        Metric.entries.associateWith { metric -> mutableStateOf(initial?.values?.get(metric)?.let { formatNumber(it) } ?: "") }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "تسجيل قياسات" else "تعديل القياسات") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("التاريخ: $date") }
                Metric.entries.forEach { metric ->
                    val state = texts.getValue(metric)
                    NumberField("${metric.label} (${metric.unit})", state.value, { state.value = it })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val values = LinkedHashMap<Metric, Double>()
                var problem: String? = null
                Metric.entries.forEach { metric ->
                    val raw = texts.getValue(metric).value
                    if (raw.isNotBlank()) {
                        val number = parseDecimal(raw)
                        if (number == null || number < metric.min || number > metric.max) {
                            problem = "قيمة ${metric.label} غير منطقية (${formatNumber(metric.min)}–${formatNumber(metric.max)} ${metric.unit})"
                        } else {
                            values[metric] = number
                        }
                    }
                }
                when {
                    problem != null -> error = problem
                    values.isEmpty() -> error = "أدخل قيمة واحدة على الأقل"
                    else -> onSave(Measurement(initial?.id ?: UUID.randomUUID().toString(), date, values))
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showPicker = false
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("إلغاء") } }
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun CompareDialog(vm: BodyWiseViewModel, onDismiss: () -> Unit) {
    val sorted = vm.photos.sortedBy { it.date }
    var before by remember { mutableStateOf(sorted.first()) }
    var after by remember { mutableStateOf(sorted.last()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مقارنة قبل / بعد") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { Hint("قبل: ${before.date}"); PhotoThumb(vm.photoFile(before), height = 220) }
                    Column(Modifier.weight(1f)) { Hint("بعد: ${after.date}"); PhotoThumb(vm.photoFile(after), height = 220) }
                }
                Hint("اختار صورة (قبل):")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sorted, key = { "b" + it.file }) { p -> FilterChip(selected = p == before, onClick = { before = p }, label = { Text(p.date.toString()) }) }
                }
                Hint("اختار صورة (بعد):")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sorted, key = { "a" + it.file }) { p -> FilterChip(selected = p == after, onClick = { after = p }, label = { Text(p.date.toString()) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}
