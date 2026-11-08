package com.shawady.bodywise.ui.meds

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawady.bodywise.data.Appointment
import com.shawady.bodywise.data.Doctor
import com.shawady.bodywise.data.dialNumber
import com.shawady.bodywise.data.groupByPerson
import com.shawady.bodywise.data.personName
import com.shawady.bodywise.ui.components.PersonHeader
import com.shawady.bodywise.ui.components.PersonPicker
import com.shawady.bodywise.data.overdueAppointments
import com.shawady.bodywise.data.upcomingAppointments
import com.shawady.bodywise.data.whatsappNumber
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.components.AppCard
import com.shawady.bodywise.ui.components.DatePickDialog
import com.shawady.bodywise.ui.components.Hint
import com.shawady.bodywise.ui.components.SectionTitle
import com.shawady.bodywise.ui.components.TimePickDialog
import com.shawady.bodywise.ui.theme.Status
import com.shawady.bodywise.util.format12
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private val arabicLocale = Locale("ar")

private fun apptWhen(at: LocalDateTime, now: LocalDateTime): String {
    val day = when (at.toLocalDate()) {
        now.toLocalDate() -> "النهارده"
        now.toLocalDate().plusDays(1) -> "بكرة"
        else -> "${at.dayOfWeek.getDisplayName(TextStyle.FULL, arabicLocale)} ${at.toLocalDate()}"
    }
    return "$day • ${at.toLocalTime().format12()}"
}

private fun Context_start(context: android.content.Context, intent: Intent, vm: BodyWiseViewModel, failMsg: String) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { vm.toast = failMsg }
}

@Composable
fun DoctorsTab(vm: BodyWiseViewModel, now: LocalDateTime) {
    val context = LocalContext.current
    var doctorEditor by remember { mutableStateOf<Doctor?>(null) }
    var addingDoctor by remember { mutableStateOf(false) }
    var apptEditor by remember { mutableStateOf<Appointment?>(null) }
    var addingAppt by remember { mutableStateOf(false) }
    var apptPrefillDoctor by remember { mutableStateOf<Doctor?>(null) }
    var apptPrefillPerson by remember { mutableStateOf<String?>(null) }
    var finishing by remember { mutableStateOf<Appointment?>(null) }

    val upcoming = upcomingAppointments(vm.appointments, now)
    val overdue = overdueAppointments(vm.appointments, now)
    val past = vm.appointments.filter { it.done }.sortedByDescending { it.at }

    AppCard {
        SectionTitle("📅", "مواعيدك القادمة")
        if (upcoming.isEmpty()) Hint("مفيش مواعيد. أضف موعد وهنفكّرك الليلة اللي قبله وقبله بساعتين.")
        groupByPerson(upcoming, { it.personId }, vm.people, vm.settings.careEnabled).forEach { (title, list) ->
            if (title != null) PersonHeader(title)
            list.forEach { a -> ApptRow(a, now, onEdit = { apptEditor = a }, onDone = { finishing = a }, onDelete = { vm.deleteAppointment(a) }) }
        }
        Button(onClick = { apptPrefillDoctor = null; addingAppt = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Text("  موعد جديد")
        }
    }

    if (overdue.isNotEmpty()) {
        AppCard {
            SectionTitle("❓", "عدّى ميعادها — اتزورت؟")
            groupByPerson(overdue.take(5), { it.personId }, vm.people, vm.settings.careEnabled).forEach { (title, list) ->
                if (title != null) PersonHeader(title)
                list.forEach { a ->
                    ApptRow(a, now, onEdit = { apptEditor = a }, onDone = { finishing = a }, onDelete = { vm.deleteAppointment(a) }, overdue = true)
                }
            }
        }
    }

    AppCard {
        SectionTitle("👨‍⚕️", "الدكاترة")
        if (vm.doctors.isEmpty()) Hint("سجّل اسم الدكتور ورقمه وعيادته، وبضغطة واحدة تتصل بيه.")
        vm.doctors.forEach { d ->
            DoctorCard(
                d,
                onCall = { Context_start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${dialNumber(d.phone)}")), vm, "تعذر فتح الاتصال") },
                onWhatsapp = { Context_start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${whatsappNumber(d.phone)}")), vm, "تعذر فتح واتساب") },
                onBook = { apptPrefillDoctor = d; addingAppt = true },
                onEdit = { doctorEditor = d },
                onDelete = { vm.deleteDoctor(d) }
            )
        }
        Button(onClick = { addingDoctor = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Text("  دكتور جديد")
        }
    }

    if (past.isNotEmpty()) {
        AppCard {
            SectionTitle("🗂", "زيارات سابقة")
            past.take(15).forEach { a ->
                Column(Modifier.fillMaxWidth()) {
                    personName(a.personId, vm.people)?.let { Hint("👤 $it") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.doctorName.ifBlank { "دكتور" }, fontWeight = FontWeight.SemiBold)
                            Hint("${a.at.toLocalDate()} ${a.at.toLocalTime().format12()}" + if (a.reason.isNotBlank()) " • ${a.reason}" else "")
                            if (a.outcome.isNotBlank()) Text("📝 ${a.outcome}", fontSize = 13.sp)
                        }
                        IconButton(onClick = { vm.deleteAppointment(a) }) { Icon(Icons.Default.DeleteOutline, "حذف") }
                    }
                }
            }
        }
    }

    if (addingDoctor || doctorEditor != null) {
        DoctorDialog(doctorEditor, onDismiss = { addingDoctor = false; doctorEditor = null }, onSave = { vm.saveDoctor(it); addingDoctor = false; doctorEditor = null })
    }
    if (addingAppt || apptEditor != null) {
        AppointmentDialog(
            vm, apptEditor, apptPrefillDoctor, apptPrefillPerson,
            onDismiss = { addingAppt = false; apptEditor = null; apptPrefillDoctor = null; apptPrefillPerson = null },
            onSave = { vm.saveAppointment(it); addingAppt = false; apptEditor = null; apptPrefillDoctor = null; apptPrefillPerson = null }
        )
    }
    finishing?.let { a ->
        FinishDialog(
            a,
            onDismiss = { finishing = null },
            onSave = { outcome, followUp ->
                vm.saveAppointment(a.copy(done = true, outcome = outcome))
                finishing = null
                if (followUp) {
                    apptPrefillDoctor = vm.doctors.firstOrNull { it.id == a.doctorId } ?: Doctor("", a.doctorName)
                    apptPrefillPerson = a.personId
                    addingAppt = true
                }
            }
        )
    }
}

@Composable
private fun ApptRow(a: Appointment, now: LocalDateTime, onEdit: () -> Unit, onDone: () -> Unit, onDelete: () -> Unit, overdue: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.doctorName.ifBlank { "دكتور" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(apptWhen(a.at, now), fontSize = 14.sp, color = if (overdue) Status.warning() else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                if (a.reason.isNotBlank()) Hint(a.reason)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "تعديل") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "حذف") }
        }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("✓ تمت الزيارة") }
    }
}

@Composable
private fun DoctorCard(
    d: Doctor, onCall: () -> Unit, onWhatsapp: () -> Unit, onBook: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (d.specialty.isNotBlank()) Text(d.specialty, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "تعديل") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "حذف") }
        }
        if (d.clinic.isNotBlank() || d.address.isNotBlank()) Hint("📍 " + listOf(d.clinic, d.address).filter { it.isNotBlank() }.joinToString(" - "))
        if (d.notes.isNotBlank()) Hint("📝 ${d.notes}")
        if (d.phone.isNotBlank()) {
            Button(onClick = onCall, modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📞 اتصال", fontSize = 14.sp)
                    Text(d.phone, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (d.phone.isNotBlank()) {
                OutlinedButton(onClick = onWhatsapp, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("💬 واتساب", maxLines = 1, softWrap = false, fontSize = 14.sp) }
            }
            OutlinedButton(onClick = onBook, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("📅 احجز موعد", maxLines = 1, softWrap = false, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun DoctorDialog(initial: Doctor?, onDismiss: () -> Unit, onSave: (Doctor) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var specialty by remember { mutableStateOf(initial?.specialty ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var clinic by remember { mutableStateOf(initial?.clinic ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "دكتور جديد" else "تعديل الدكتور") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; error = null }, label = { Text("الاسم") }, singleLine = true, isError = error != null, supportingText = error?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(specialty, { specialty = it }, label = { Text("التخصص (باطنة، عيون...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("رقم التليفون") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(clinic, { clinic = it }, label = { Text("العيادة / المستشفى") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات (مواعيد الكشف...)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { error = "اكتب اسم الدكتور"; return@TextButton }
                onSave(Doctor(initial?.id ?: UUID.randomUUID().toString(), name.trim(), specialty.trim(), phone.trim(), clinic.trim(), address.trim(), notes.trim()))
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AppointmentDialog(vm: BodyWiseViewModel, initial: Appointment?, prefill: Doctor?, prefillPerson: String?, onDismiss: () -> Unit, onSave: (Appointment) -> Unit) {
    val start = initial?.at ?: LocalDateTime.now().plusDays(1).withHour(17).withMinute(0).withSecond(0).withNano(0)
    var doctorId by remember { mutableStateOf(initial?.doctorId ?: prefill?.id?.ifBlank { null }) }
    var doctorName by remember { mutableStateOf(initial?.doctorName ?: prefill?.name ?: "") }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(start.toLocalTime()) }
    var reason by remember { mutableStateOf(initial?.reason ?: "") }
    var personId by remember { mutableStateOf(initial?.personId ?: prefillPerson) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "موعد جديد" else "تعديل الموعد") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.settings.careEnabled && vm.people.isNotEmpty()) PersonPicker("الموعد لـ", vm.people, personId) { personId = it }
                if (vm.doctors.isNotEmpty()) {
                    Hint("اختار دكتور من المسجلين:")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        vm.doctors.forEach { d ->
                            FilterChip(selected = doctorId == d.id, onClick = { doctorId = d.id; doctorName = d.name; error = null }, label = { Text(d.name + if (d.specialty.isNotBlank()) " • ${d.specialty}" else "") })
                        }
                    }
                }
                OutlinedTextField(
                    doctorName, { doctorName = it; doctorId = vm.doctors.firstOrNull { d -> d.name == it }?.id; error = null },
                    label = { Text("اسم الدكتور") }, singleLine = true, isError = error != null, supportingText = error?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) { Text(date.toString()) }
                    OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) { Text(time.format12()) }
                }
                OutlinedTextField(reason, { reason = it }, label = { Text("السبب (كشف، متابعة، تحاليل...)") }, modifier = Modifier.fillMaxWidth())
                Hint("هتتنبه الليلة اللي قبله الساعة 8 مساءً، وقبله بساعتين.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (doctorName.isBlank()) { error = "اكتب أو اختار الدكتور"; return@TextButton }
                onSave(Appointment(initial?.id ?: UUID.randomUUID().toString(), doctorId, doctorName.trim(), LocalDateTime.of(date, time), reason.trim(), initial?.done ?: false, initial?.outcome ?: "", personId?.takeIf { id -> vm.people.any { it.id == id } }))
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
    if (showDate) DatePickDialog(date, { date = it }, { showDate = false })
    if (showTime) TimePickDialog(time, { time = it }, { showTime = false })
}

@Composable
private fun FinishDialog(a: Appointment, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var outcome by remember { mutableStateOf(a.outcome) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("زيارة ${a.doctorName.ifBlank { "الدكتور" }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(outcome, { outcome = it }, label = { Text("الدكتور قال إيه؟ (اختياري)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(outcome.trim(), false) }) { Text("تم") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(outcome.trim(), true) }) { Text("تم + موعد متابعة") }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )
}
