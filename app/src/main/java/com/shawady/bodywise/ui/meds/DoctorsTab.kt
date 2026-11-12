package com.shawady.bodywise.ui.meds

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shawady.bodywise.data.matchesAppointment
import com.shawady.bodywise.data.matchesDoctor
import com.shawady.bodywise.data.nextAppointmentFor
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
import com.shawady.bodywise.data.docsOfDoctor
import com.shawady.bodywise.data.medsOfDoctor
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
    var apptEditor by remember { mutableStateOf<Appointment?>(null) }
    var addingAppt by remember { mutableStateOf(false) }
    var apptPrefillDoctor by remember { mutableStateOf<Doctor?>(null) }
    var apptPrefillPerson by remember { mutableStateOf<String?>(null) }
    var finishing by remember { mutableStateOf<Appointment?>(null) }

    val upcoming = upcomingAppointments(vm.appointments, now)
    val overdue = overdueAppointments(vm.appointments, now)
    val past = vm.appointments.filter { it.done }.sortedByDescending { it.at }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showCatalog by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } } }

    // Catalog button always stays on top; the search icon turns the row into a search bar.
    // The row keeps a fixed layout (a slot that fills the width + one icon), so nothing is resized while animating;
    // only the slot's content fades between the catalog button and the search field.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(56.dp), contentAlignment = Alignment.Center) {
            Crossfade(targetState = searching, animationSpec = tween(150), label = "search") { isSearching ->
                if (isSearching) {
                    OutlinedTextField(
                        query, { query = it }, singleLine = true, placeholder = { Text("ابحث في المواعيد...") },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                } else {
                    Button(onClick = { showCatalog = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("📒 كاتلوج الدكاترة (${vm.doctors.size})") }
                }
            }
        }
        IconButton(onClick = { if (searching) { searching = false; query = "" } else searching = true }) {
            Icon(if (searching) Icons.Default.Close else Icons.Default.Search, if (searching) "إغلاق البحث" else "بحث في المواعيد")
        }
    }

    val results = if (searching && query.isNotBlank()) vm.appointments.filter { matchesAppointment(it, query, vm.people) }.sortedByDescending { it.at } else null
    if (results != null) {
        AppCard {
            SectionTitle("🔍", "نتايج البحث (${results.size})")
            if (results.isEmpty()) Hint("مفيش مواعيد مطابقة.")
            results.forEach { a ->
                when {
                    a.done -> PastRow(a, vm.people) { vm.deleteAppointment(a) }
                    overdue.any { it.id == a.id } -> ApptRow(a, now, onEdit = { apptEditor = a }, onDone = { finishing = a }, onDelete = { vm.deleteAppointment(a) }, overdue = true)
                    else -> ApptRow(a, now, onEdit = { apptEditor = a }, onDone = { finishing = a }, onDelete = { vm.deleteAppointment(a) })
                }
            }
        }
    } else {
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

        if (past.isNotEmpty()) {
            AppCard {
                SectionTitle("🗂", "زيارات سابقة")
                past.take(15).forEach { a -> PastRow(a, vm.people) { vm.deleteAppointment(a) } }
                if (past.size > 15) Hint("ظاهر آخر 15 زيارة. ابحث (🔍) علشان تلاقي الباقي.")
            }
        }
    }

    if (showCatalog) DoctorCatalog(vm, now) { showCatalog = false }

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
private fun PastRow(a: Appointment, people: List<com.shawady.bodywise.data.Person>, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        personName(a.personId, people)?.let { Hint("👤 $it") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.doctorName.ifBlank { "دكتور" }, fontWeight = FontWeight.SemiBold)
                Hint("${a.at.toLocalDate()} ${a.at.toLocalTime().format12()}" + if (a.reason.isNotBlank()) " • ${a.reason}" else "")
                if (a.outcome.isNotBlank()) Text("📝 ${a.outcome}", fontSize = 13.sp)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "حذف") }
        }
    }
}

/** Full-screen list of every saved doctor, with search and a centered add button. */
@Composable
private fun DoctorCatalog(vm: BodyWiseViewModel, now: LocalDateTime, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var doctorEditor by remember { mutableStateOf<Doctor?>(null) }
    var addingDoctor by remember { mutableStateOf(false) }
    var booking by remember { mutableStateOf(false) }
    var bookDoctor by remember { mutableStateOf<Doctor?>(null) }
    val shown = vm.doctors.filter { matchesDoctor(it, query) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "إغلاق") }
                        Text("📒 كاتلوج الدكاترة", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(
                        query, { query = it }, singleLine = true, placeholder = { Text("ابحث بالاسم أو التخصص أو العيادة") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (vm.doctors.isEmpty()) {
                            Hint("لسه ما سجلتش أي دكتور. اضغط \"دكتور جديد\" وسجّل اسمه ورقمه، وبضغطة واحدة تتصل بيه.")
                        } else if (shown.isEmpty()) {
                            Hint("مفيش دكتور مطابق للبحث.")
                        }
                        shown.forEach { d ->
                            AppCard {
                                DoctorCard(
                                    d, nextAppointmentFor(d, vm.appointments, now), now,
                                    medCount = medsOfDoctor(d, vm.medications).size, docCount = docsOfDoctor(d, vm.medDocs).size,
                                    onCall = { Context_start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${dialNumber(d.phone)}")), vm, "تعذر فتح الاتصال") },
                                    onWhatsapp = { Context_start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${whatsappNumber(d.phone)}")), vm, "تعذر فتح واتساب") },
                                    onBook = { bookDoctor = d; booking = true },
                                    onEdit = { doctorEditor = d },
                                    onDelete = { vm.deleteDoctor(d) }
                                )
                            }
                        }
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = { addingDoctor = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("دكتور جديد") },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
                )
            }
        }
    }
    if (addingDoctor || doctorEditor != null) {
        DoctorDialog(doctorEditor, onDismiss = { addingDoctor = false; doctorEditor = null }, onSave = { vm.saveDoctor(it); addingDoctor = false; doctorEditor = null })
    }
    if (booking) {
        AppointmentDialog(vm, null, bookDoctor, null, onDismiss = { booking = false; bookDoctor = null }, onSave = { vm.saveAppointment(it); booking = false; bookDoctor = null })
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
    d: Doctor, next: Appointment?, now: LocalDateTime, medCount: Int, docCount: Int,
    onCall: () -> Unit, onWhatsapp: () -> Unit, onBook: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
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
        if (medCount > 0 || docCount > 0) Hint("💊 $medCount أدوية • 📂 $docCount مستندات")
        if (next != null) Text("📅 موعدك الجاي: ${apptWhen(next.at, now)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
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
