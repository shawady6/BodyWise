package com.shawady.bodywise.ui.meds

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.shawady.bodywise.data.MED_INSTRUCTIONS
import com.shawady.bodywise.data.MED_UNITS
import com.shawady.bodywise.data.Medication
import com.shawady.bodywise.data.STRIP_UNITS
import com.shawady.bodywise.data.suggestTimes
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.components.Hint
import com.shawady.bodywise.ui.components.PersonPicker
import com.shawady.bodywise.ui.components.PhotoThumb
import com.shawady.bodywise.ui.theme.Status
import com.shawady.bodywise.util.format12
import com.shawady.bodywise.util.formatNumber
import com.shawady.bodywise.util.parseDecimal
import com.shawady.bodywise.util.recognizeDrugNames
import kotlinx.coroutines.launch
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

private val week = listOf(
    DayOfWeek.SATURDAY to "س", DayOfWeek.SUNDAY to "ح", DayOfWeek.MONDAY to "ن", DayOfWeek.TUESDAY to "ث",
    DayOfWeek.WEDNESDAY to "ر", DayOfWeek.THURSDAY to "خ", DayOfWeek.FRIDAY to "ج"
)
private val unitEmoji = mapOf("قرص" to "⚪", "كبسولة" to "💊", "مل" to "🧴", "نقطة" to "💧", "حقنة" to "💉", "جرعة" to "🥄")
private val instructionEmoji = mapOf("لا يهم" to "🕐", "قبل الأكل" to "⏳", "بعد الأكل" to "🍽", "مع الأكل" to "🥘")
private val durations = listOf("مستمر" to null, "أسبوع" to 6L, "أسبوعين" to 13L, "شهر" to 29L)

/** Full-screen, three-step editor: the medicine, its schedule, then boxes and price. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedEditor(vm: BodyWiseViewModel, initial: Medication?, onDismiss: () -> Unit, onSave: (Medication) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var dose by remember { mutableStateOf(initial?.dose ?: "1") }
    var unit by remember { mutableStateOf(initial?.unit?.ifBlank { null } ?: MED_UNITS.first()) }
    var times by remember { mutableStateOf(initial?.times ?: listOf(LocalTime.of(8, 0))) }
    var days by remember { mutableStateOf(initial?.days ?: emptySet()) }
    var start by remember { mutableStateOf(initial?.startDate ?: LocalDate.now()) }
    var end by remember { mutableStateOf(initial?.endDate) }
    var instruction by remember { mutableStateOf(initial?.instruction?.ifBlank { null } ?: MED_INSTRUCTIONS.first()) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var showNotes by remember { mutableStateOf(!initial?.notes.isNullOrBlank()) }
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
    var personId by remember { mutableStateOf(initial?.personId) }
    var photo by remember { mutableStateOf(initial?.photo) }
    var newPhoto by remember { mutableStateOf<String?>(null) }
    var pendingCamera by remember { mutableStateOf<File?>(null) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var reading by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var doseError by remember { mutableStateOf<String?>(null) }
    var dateError by remember { mutableStateOf<String?>(null) }
    var packError by remember { mutableStateOf<String?>(null) }
    var priceError by remember { mutableStateOf<String?>(null) }
    var timeEditIndex by remember { mutableStateOf<Int?>(null) }
    var datePicking by remember { mutableStateOf<String?>(null) }
    val isStrip = unit in STRIP_UNITS

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
    fun takePhoto() {
        runCatching {
            val file = File(context.cacheDir, "med_capture_${System.currentTimeMillis()}.jpg")
            pendingCamera = file
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        }.onFailure { vm.toast = "تعذر فتح الكاميرا" }
    }
    fun dismiss() { newPhoto?.let { vm.deleteStrayFile(it) }; onDismiss() }

    // Packaging maths, shown live as the person types.
    val stripsN = strips.trim().toIntOrNull()
    val perStripN = perStrip.trim().toIntOrNull()
    val perBox: Double? = if (isStrip) { if (stripsN != null && perStripN != null) (stripsN * perStripN).toDouble() else null } else parseDecimal(perBoxText)
    val boxesN = if (boxes.isBlank()) null else parseDecimal(boxes)
    val priceN = if (price.isBlank()) null else parseDecimal(price)
    val amount = parseDecimal(dose)

    /** Returns the first step that has a problem (and fills the error texts), or null when everything is fine. */
    fun validate(): Int? {
        nameError = null; doseError = null; dateError = null; packError = null; priceError = null
        var bad: Int? = null
        fun flag(s: Int) { if (bad == null) bad = s }
        if (name.isBlank()) { nameError = "اكتب اسم الدواء"; flag(0) }
        if (amount == null || amount <= 0) { doseError = "اكتب الجرعة (رقم أكبر من صفر)"; flag(0) }
        if (end != null && end!!.isBefore(start)) { dateError = "تاريخ النهاية قبل البداية"; flag(1) }
        if (isStrip && (strips.isNotBlank() || perStrip.isNotBlank()) && perBox == null) { packError = "اكتب عدد الشرايط وعدد ${unit}ات الشريط"; flag(2) }
        else if (boxes.isNotBlank() && boxesN == null) { packError = "عدد العلب مش رقم صحيح"; flag(2) }
        else if (boxesN != null && (perBox == null || perBox <= 0)) { packError = "اكتب حجم العلبة علشان أحسب الكمية"; flag(2) }
        if (price.isNotBlank() && priceN == null) { priceError = "السعر مش رقم صحيح"; flag(2) }
        return bad
    }

    fun save() {
        val bad = validate()
        if (bad != null) { step = bad; return }
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
                unitsPerBox = perBox, fullQuantity = full, price = priceN,
                personId = personId?.takeIf { id -> vm.people.any { it.id == id } }
            )
        )
    }

    fun goNext() {
        if (step == 0) {
            nameError = if (name.isBlank()) "اكتب اسم الدواء" else null
            doseError = if (amount == null || amount <= 0) "اكتب الجرعة (رقم أكبر من صفر)" else null
            if (nameError != null || doseError != null) return
        }
        step++
    }

    // The dialog window handles the status/navigation bars and the keyboard itself (adjustResize), so nothing is hidden at the bottom.
    Dialog(onDismissRequest = { dismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { dismiss() }) { Icon(Icons.Default.Close, "إغلاق") }
                    Text(if (initial == null) "دواء جديد" else "تعديل الدواء", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (initial != null) TextButton(onClick = { save() }) { Text("حفظ") }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("١  الدواء", "٢  المواعيد", "٣  العلب والسعر").forEachIndexed { i, label ->
                        FilterChip(selected = step == i, onClick = { step = i }, label = { Text(label, fontSize = 13.sp) })
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (step) {
                        0 -> {
                            // --- photo + name
                            if (photo != null) {
                                PhotoThumb(vm.medPhotoFile(photo!!), height = 150)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { takePhoto() }) { Text("صوّر تاني") }
                                    TextButton(onClick = { newPhoto?.let { vm.deleteStrayFile(it) }; newPhoto = null; photo = null; suggestions = emptyList() }) { Text("شيل الصورة") }
                                }
                            } else {
                                Surface(
                                    onClick = { takePhoto() }, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.CameraAlt, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text("صوّر العلبة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Hint("هقرا اسم الدواء من الصورة وتختاره بلمسة")
                                    }
                                }
                            }
                            if (reading) Hint("بقرا الاسم من الصورة...")
                            if (suggestions.isNotEmpty()) {
                                Hint("الاسم اللي قريته — اختار الصح:")
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    suggestions.forEach { s -> FilterChip(selected = name == s, onClick = { name = s; nameError = null }, label = { Text(s) }) }
                                }
                            }
                            if (vm.settings.careEnabled && vm.people.isNotEmpty()) {
                                PersonPicker("الدواء لـ", vm.people, personId) { personId = it }
                            }
                            OutlinedTextField(
                                name, { name = it; nameError = null }, label = { Text("اسم الدواء") }, singleLine = true,
                                isError = nameError != null, supportingText = { nameError?.let { Text(it) } }, modifier = Modifier.fillMaxWidth()
                            )
                            // --- form
                            Text("نوع الدواء", fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                MED_UNITS.forEach { u -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text("${unitEmoji[u] ?: ""} $u") }) }
                            }
                            // --- dose
                            Text("الجرعة في المرة الواحدة", fontWeight = FontWeight.SemiBold)
                            val stepSize = if (isStrip) 0.5 else 1.0
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = {
                                    val v = (amount ?: 1.0) - stepSize
                                    if (v >= 0.25) { dose = formatNumber(v); doseError = null }
                                }) { Icon(Icons.Default.Remove, "نقص") }
                                OutlinedTextField(
                                    dose, { dose = it; doseError = null }, singleLine = true, isError = doseError != null,
                                    textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(110.dp)
                                )
                                IconButton(onClick = { dose = formatNumber((amount ?: 0.0) + stepSize); doseError = null }) { Icon(Icons.Default.Add, "زيادة") }
                                Text(unit, fontSize = 16.sp)
                            }
                            doseError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                            if (isStrip) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("¼" to 0.25, "½" to 0.5, "1" to 1.0, "2" to 2.0).forEach { (label, v) ->
                                        FilterChip(selected = amount == v, onClick = { dose = formatNumber(v); doseError = null }, label = { Text(label) })
                                    }
                                }
                            }
                        }
                        1 -> {
                            Text("كام مرة في اليوم؟", fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("مرة", "مرتين", "٣ مرات", "٤ مرات").forEachIndexed { i, label ->
                                    FilterChip(selected = times.size == i + 1, onClick = { times = suggestTimes(i + 1, times) }, label = { Text(label) })
                                }
                            }
                            times.forEachIndexed { i, t ->
                                OutlinedButton(onClick = { timeEditIndex = i }, modifier = Modifier.fillMaxWidth()) {
                                    Text("المرة ${i + 1}", modifier = Modifier.weight(1f))
                                    Text(t.format12(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text("الأيام", fontWeight = FontWeight.SemiBold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                week.forEach { (d, label) ->
                                    DayCircle(label, days.isEmpty() || d in days) {
                                        val base = if (days.isEmpty()) week.map { it.first }.toSet() else days
                                        val toggled = if (d in base) base - d else base + d
                                        days = if (toggled.size == 7 || toggled.isEmpty()) emptySet() else toggled
                                    }
                                }
                            }
                            Hint(if (days.isEmpty()) "كل يوم" else "أيام محددة فقط")
                            Text("المدة", fontWeight = FontWeight.SemiBold)
                            val length = end?.let { ChronoUnit.DAYS.between(start, it) }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                durations.forEach { (label, len) ->
                                    FilterChip(
                                        selected = if (len == null) end == null else length == len,
                                        onClick = { end = len?.let { start.plusDays(it) }; dateError = null },
                                        label = { Text(label) }
                                    )
                                }
                                FilterChip(
                                    selected = end != null && durations.none { it.second == length },
                                    onClick = { datePicking = "end" }, label = { Text("تاريخ محدد") }
                                )
                            }
                            Hint(end?.let { "لحد $it" } ?: "من غير تاريخ نهاية")
                            OutlinedButton(onClick = { datePicking = "start" }, modifier = Modifier.fillMaxWidth()) {
                                Text("يبدأ: " + if (start == LocalDate.now()) "النهارده" else start.toString())
                            }
                            dateError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                            Text("التعليمات", fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                MED_INSTRUCTIONS.forEach { s -> FilterChip(selected = instruction == s, onClick = { instruction = s }, label = { Text("${instructionEmoji[s] ?: ""} $s") }) }
                            }
                        }
                        else -> {
                            Text("العلبة", fontWeight = FontWeight.SemiBold)
                            if (isStrip) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SmallNumber("شرايط في العلبة", strips, Modifier.weight(1f)) { strips = it; packError = null }
                                    SmallNumber("${unit}ات في الشريط", perStrip, Modifier.weight(1f)) { perStrip = it; packError = null }
                                }
                            } else {
                                SmallNumber("عدد ال$unit في العلبة/العبوة", perBoxText, Modifier.fillMaxWidth()) { perBoxText = it; packError = null }
                            }
                            SmallNumber("عدد العلب اللي معاك دلوقتي", boxes, Modifier.fillMaxWidth()) { boxes = it; packError = null }
                            packError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                            if (boxesN != null && perBox != null) {
                                val total = formatNumber(Math.round(boxesN * perBox * 10) / 10.0)
                                val formula = if (isStrip && stripsN != null) "${formatNumber(boxesN)} علبة × $stripsN شريط × $perStripN $unit" else "${formatNumber(boxesN)} علبة × ${formatNumber(perBox)} $unit"
                                Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp)) {
                                        Hint(formula)
                                        Text("= $total $unit", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                }
                            }
                            SmallNumber("سعر العلبة (اختياري)", price, Modifier.fillMaxWidth()) { price = it; priceError = null }
                            priceError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                            if (showNotes) {
                                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
                            } else {
                                TextButton(onClick = { showNotes = true }) { Text("＋ إضافة ملاحظة") }
                            }
                            // --- summary
                            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("الملخص", fontWeight = FontWeight.Bold)
                                    Text(
                                        (name.ifBlank { "—" }) + " — ${formatNumber(amount ?: 0.0)} $unit" +
                                            (instruction.takeIf { it != "لا يهم" }?.let { " ($it)" } ?: ""),
                                        fontSize = 14.sp
                                    )
                                    Hint(times.joinToString("، ") { it.format12() } + " • " + (if (days.isEmpty()) "كل يوم" else "أيام محددة") + " • " + (end?.let { "لحد $it" } ?: "مستمر"))
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (step > 0) OutlinedButton(onClick = { step-- }) { Text("السابق") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { if (step < 2) goNext() else save() }) { Text(if (step < 2) "التالي" else "حفظ") }
                }
            }
        }
    }

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
                        if (which == "start") {
                            val len = end?.let { e -> ChronoUnit.DAYS.between(start, e) }
                            start = picked
                            end = len?.let { l -> picked.plusDays(l) }
                        } else end = picked
                    }
                    datePicking = null
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { datePicking = null }) { Text("إلغاء") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun SmallNumber(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier
    )
}

@Composable
private fun DayCircle(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
