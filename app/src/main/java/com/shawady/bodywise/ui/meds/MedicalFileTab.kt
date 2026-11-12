package com.shawady.bodywise.ui.meds

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.shawady.bodywise.data.BLOOD_TYPES
import com.shawady.bodywise.data.DOC_KINDS
import com.shawady.bodywise.data.MedicalDoc
import com.shawady.bodywise.data.MedicalProfile
import com.shawady.bodywise.data.MAX_DOC_FILES
import com.shawady.bodywise.data.dialNumber
import com.shawady.bodywise.data.isPdfFile
import com.shawady.bodywise.data.pdfDisplayName
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.components.AppCard
import com.shawady.bodywise.ui.components.DatePickDialog
import com.shawady.bodywise.ui.components.Hint
import com.shawady.bodywise.ui.components.DoctorPicker
import com.shawady.bodywise.ui.components.SectionTitle
import java.io.File
import java.time.LocalDate
import java.util.UUID

@Composable
fun MedicalFileTab(vm: BodyWiseViewModel) {
    val context = LocalContext.current
    var editingProfile by remember { mutableStateOf(false) }
    var docEditor by remember { mutableStateOf<MedicalDoc?>(null) }
    var addingDoc by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Pair<MedicalDoc, Int>?>(null) }
    var doctorFilter by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf<String?>(null) }
    val p = vm.medProfile

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("🪪", "بطاقتي الطبية")
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { editingProfile = true }) { Icon(Icons.Default.Edit, "تعديل") }
            }
        }
        if (p.isEmpty()) Hint("اكتب فصيلة دمك والحساسية والأمراض المزمنة وجهة الطوارئ، علشان تلاقيهم في ثانية.")
        ProfileLine("🩸 فصيلة الدم", p.bloodType)
        ProfileLine("⚠️ الحساسية", p.allergies)
        ProfileLine("🏥 أمراض مزمنة", p.conditions)
        ProfileLine("📝 ملاحظات", p.notes)
        if (p.emergencyPhone.isNotBlank()) {
            Button(
                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${dialNumber(p.emergencyPhone)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("🚨 طوارئ: ${p.emergencyName.ifBlank { "اتصال" }} — ${p.emergencyPhone}") }
        }
    }

    AppCard {
        SectionTitle("📂", "روشتات وتحاليل وأشعة")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("الكل") })
            DOC_KINDS.forEach { k -> FilterChip(selected = filter == k, onClick = { filter = if (filter == k) null else k }, label = { Text(k) }) }
        }
        val docDoctors = vm.doctors.filter { d -> vm.medDocs.any { it.doctorId == d.id } }
        if (docDoctors.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                docDoctors.forEach { d -> FilterChip(selected = doctorFilter == d.id, onClick = { doctorFilter = if (doctorFilter == d.id) null else d.id }, label = { Text("👨‍⚕️ ${d.name}") }) }
            }
        }
        val shown = vm.medDocs.filter { (filter == null || it.kind == filter) && (doctorFilter == null || it.doctorId == doctorFilter) }
        if (shown.isEmpty()) Hint("صوّر الروشتة أو التحليل وهتتحفظ هنا بتاريخها، بدل ما تدوّر في الصور.")
        shown.forEach { d ->
            val doctorLabel = vm.doctors.firstOrNull { it.id == d.doctorId }?.name ?: d.doctorName
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${d.kind} • ${d.title.ifBlank { "بدون عنوان" }}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Hint(d.date.toString() + if (doctorLabel.isNotBlank()) " • $doctorLabel" else "")
                    }
                    IconButton(onClick = { docEditor = d }) { Icon(Icons.Default.Edit, "تعديل") }
                    IconButton(onClick = { vm.deleteDoc(d) }) { Icon(Icons.Default.DeleteOutline, "حذف") }
                }
                if (d.files.isNotEmpty()) DocFilesStrip(vm, d.files) { i -> viewing = d to i }
            }
        }
        Button(onClick = { addingDoc = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("  أضف مستند") }
    }

    if (editingProfile) ProfileDialog(p, onDismiss = { editingProfile = false }, onSave = { vm.saveProfile(it); editingProfile = false })
    if (addingDoc || docEditor != null) {
        DocDialog(vm, docEditor, onDismiss = { addingDoc = false; docEditor = null }, onSave = { vm.saveDoc(it); addingDoc = false; docEditor = null })
    }
    viewing?.let { (d, i) -> DocViewer(vm, d, i) { viewing = null } }
}

@Composable
private fun ProfileLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 14.sp)
    }
}

@Composable
private fun ProfileDialog(initial: MedicalProfile, onDismiss: () -> Unit, onSave: (MedicalProfile) -> Unit) {
    var blood by remember { mutableStateOf(initial.bloodType) }
    var allergies by remember { mutableStateOf(initial.allergies) }
    var conditions by remember { mutableStateOf(initial.conditions) }
    var emName by remember { mutableStateOf(initial.emergencyName) }
    var emPhone by remember { mutableStateOf(initial.emergencyPhone) }
    var notes by remember { mutableStateOf(initial.notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بطاقتي الطبية") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint("فصيلة الدم")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BLOOD_TYPES.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { t -> FilterChip(selected = blood == t, onClick = { blood = if (blood == t) "" else t }, label = { Text(t) }) }
                        }
                    }
                }
                OutlinedTextField(allergies, { allergies = it }, label = { Text("الحساسية (أدوية، أكل...)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(conditions, { conditions = it }, label = { Text("أمراض مزمنة") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(emName, { emName = it }, label = { Text("اسم جهة الطوارئ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(emPhone, { emPhone = it }, label = { Text("رقم الطوارئ") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(MedicalProfile(blood, allergies.trim(), conditions.trim(), emName.trim(), emPhone.trim(), notes.trim())) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun DocDialog(vm: BodyWiseViewModel, initial: MedicalDoc?, onDismiss: () -> Unit, onSave: (MedicalDoc) -> Unit) {
    val context = LocalContext.current
    var kind by remember { mutableStateOf(initial?.kind ?: DOC_KINDS.first()) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var doctorId by remember { mutableStateOf(initial?.doctorId?.takeIf { id -> vm.doctors.any { it.id == id } }) }
    var doctorName by remember { mutableStateOf(initial?.doctorName ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var files by remember { mutableStateOf(initial?.files ?: emptyList()) }
    var added by remember { mutableStateOf(listOf<String>()) }
    var pendingCamera by remember { mutableStateOf<File?>(null) }
    var showDate by remember { mutableStateOf(false) }

    fun accept(saved: String?) {
        if (saved == null) return
        if (files.size >= MAX_DOC_FILES) { vm.deleteStrayFile(saved); vm.toast = "أقصى عدد $MAX_DOC_FILES ملفات"; return }
        files = files + saved
        added = added + saved
    }
    fun remove(name: String) {
        files = files - name
        if (name in added) { vm.deleteStrayFile(name); added = added - name }
    }
    fun room(): Boolean {
        if (files.size >= MAX_DOC_FILES) { vm.toast = "أقصى عدد $MAX_DOC_FILES ملفات"; return false }
        return true
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCamera
        pendingCamera = null
        if (ok && file != null && file.exists()) vm.importMedPhoto({ file.inputStream() }) { saved -> file.delete(); accept(saved) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        uris.forEach { uri -> vm.importMedPhoto({ context.contentResolver.openInputStream(uri) }) { accept(it) } }
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val original = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: "document"
            vm.importDocPdf({ context.contentResolver.openInputStream(uri) }, original) { accept(it) }
        }
    }
    fun dismiss() { added.forEach { vm.deleteStrayFile(it) }; onDismiss() }

    AlertDialog(
        onDismissRequest = { dismiss() },
        title = { Text(if (initial == null) "مستند جديد" else "تعديل المستند") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DOC_KINDS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k) }) }
                        }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("عنوان (مثلًا: تحليل سكر تراكمي)") }, modifier = Modifier.fillMaxWidth())
                if (vm.doctors.isNotEmpty()) {
                    DoctorPicker("الدكتور (اختياري)", vm.doctors, doctorId) { d -> doctorId = d?.id; doctorName = d?.name ?: "" }
                } else {
                    OutlinedTextField(doctorName, { doctorName = it }, label = { Text("الدكتور (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) { Text("التاريخ: $date") }
                if (files.isNotEmpty()) {
                    Hint("الملفات (${files.size}/$MAX_DOC_FILES) — اضغط ✕ علشان تشيل ملف")
                    files.forEach { name ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isPdfFile(name)) Text("📄 ${pdfDisplayName(name)}", Modifier.weight(1f), fontSize = 14.sp, maxLines = 1)
                            else androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                                com.shawady.bodywise.ui.components.PhotoThumb(vm.medPhotoFile(name), height = 90)
                            }
                            IconButton(onClick = { remove(name) }) { Icon(Icons.Default.Close, "شيل") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        if (!room()) return@OutlinedButton
                        runCatching {
                            val file = File(context.cacheDir, "doc_capture_${System.currentTimeMillis()}.jpg")
                            pendingCamera = file
                            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                        }.onFailure { vm.toast = "تعذر فتح الكاميرا" }
                    }, modifier = Modifier.weight(1f)) { Text("📷 صوّر") }
                    OutlinedButton(onClick = { if (room()) gallery.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("🖼 معرض") }
                    OutlinedButton(onClick = { if (room()) pdfPicker.launch("application/pdf") }, modifier = Modifier.weight(1f)) { Text("📄 PDF") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (files.isEmpty() && title.isBlank()) { vm.toast = "اكتب عنوان أو أضف ملف"; return@TextButton }
                added = emptyList()
                onSave(MedicalDoc(initial?.id ?: UUID.randomUUID().toString(), date, kind, title.trim(), doctorName.trim(), files, doctorId))
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = { dismiss() }) { Text("إلغاء") } }
    )
    if (showDate) DatePickDialog(date, { date = it }, { showDate = false })
}
