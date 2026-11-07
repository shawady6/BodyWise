package com.shawady.bodywise.ui.meds

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import com.shawady.bodywise.data.dialNumber
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.components.AppCard
import com.shawady.bodywise.ui.components.DatePickDialog
import com.shawady.bodywise.ui.components.Hint
import com.shawady.bodywise.ui.components.PhotoThumb
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
    var viewing by remember { mutableStateOf<MedicalDoc?>(null) }
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
        val shown = vm.medDocs.filter { filter == null || it.kind == filter }
        if (shown.isEmpty()) Hint("صوّر الروشتة أو التحليل وهتتحفظ هنا بتاريخها، بدل ما تدوّر في الصور.")
        shown.forEach { d ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${d.kind} • ${d.title.ifBlank { "بدون عنوان" }}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Hint(d.date.toString() + if (d.doctorName.isNotBlank()) " • ${d.doctorName}" else "")
                    }
                    IconButton(onClick = { docEditor = d }) { Icon(Icons.Default.Edit, "تعديل") }
                    IconButton(onClick = { vm.deleteDoc(d) }) { Icon(Icons.Default.DeleteOutline, "حذف") }
                }
                d.photo?.let { name ->
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().clickableNoRipple { viewing = d }) { PhotoThumb(vm.medPhotoFile(name), height = 110) }
                }
            }
        }
        Button(onClick = { addingDoc = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("  أضف مستند") }
    }

    if (editingProfile) ProfileDialog(p, onDismiss = { editingProfile = false }, onSave = { vm.saveProfile(it); editingProfile = false })
    if (addingDoc || docEditor != null) {
        DocDialog(vm, docEditor, onDismiss = { addingDoc = false; docEditor = null }, onSave = { vm.saveDoc(it); addingDoc = false; docEditor = null })
    }
    viewing?.let { d ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("إغلاق") } },
            title = { Text(d.title.ifBlank { d.kind }) },
            text = { d.photo?.let { PhotoThumb(vm.medPhotoFile(it), height = 420) } }
        )
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

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
    var doctor by remember { mutableStateOf(initial?.doctorName ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var photo by remember { mutableStateOf(initial?.photo) }
    var newPhoto by remember { mutableStateOf<String?>(null) }
    var pendingCamera by remember { mutableStateOf<File?>(null) }
    var showDate by remember { mutableStateOf(false) }

    fun accept(saved: String?) {
        if (saved != null) { newPhoto?.let { vm.deleteStrayFile(it) }; newPhoto = saved; photo = saved }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCamera
        pendingCamera = null
        if (ok && file != null && file.exists()) vm.importMedPhoto({ file.inputStream() }) { saved -> file.delete(); accept(saved) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) vm.importMedPhoto({ context.contentResolver.openInputStream(uri) }) { accept(it) }
    }
    fun dismiss() { newPhoto?.let { vm.deleteStrayFile(it) }; onDismiss() }

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
                OutlinedTextField(doctor, { doctor = it }, label = { Text("الدكتور (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) { Text("التاريخ: $date") }
                photo?.let { PhotoThumb(vm.medPhotoFile(it), height = 140) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        runCatching {
                            val file = File(context.cacheDir, "doc_capture_${System.currentTimeMillis()}.jpg")
                            pendingCamera = file
                            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                        }.onFailure { vm.toast = "تعذر فتح الكاميرا" }
                    }, modifier = Modifier.weight(1f)) { Text("📷 صوّر") }
                    OutlinedButton(onClick = { gallery.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("🖼 من المعرض") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (photo == null && title.isBlank()) { vm.toast = "اكتب عنوان أو أضف صورة"; return@TextButton }
                newPhoto = null
                onSave(MedicalDoc(initial?.id ?: UUID.randomUUID().toString(), date, kind, title.trim(), doctor.trim(), photo))
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = { dismiss() }) { Text("إلغاء") } }
    )
    if (showDate) DatePickDialog(date, { date = it }, { showDate = false })
}
