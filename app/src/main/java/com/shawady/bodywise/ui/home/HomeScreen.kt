package com.shawady.bodywise.ui.home

import com.shawady.bodywise.ui.theme.Status
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawady.bodywise.data.DoseStatus
import com.shawady.bodywise.data.Metric
import com.shawady.bodywise.data.SlotState
import com.shawady.bodywise.data.VitalReading
import com.shawady.bodywise.data.VitalType
import com.shawady.bodywise.data.dashboardAlerts
import com.shawady.bodywise.data.upcomingAppointments
import com.shawady.bodywise.data.slotState
import com.shawady.bodywise.data.slotsFor
import com.shawady.bodywise.data.vitalWarning
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.components.AppCard
import com.shawady.bodywise.ui.components.Hint
import com.shawady.bodywise.util.arabicDayFull
import com.shawady.bodywise.util.bmi
import com.shawady.bodywise.util.dayMonth
import com.shawady.bodywise.util.format12
import com.shawady.bodywise.ui.components.SectionTitle
import com.shawady.bodywise.ui.components.SmallStat
import com.shawady.bodywise.util.formatNumber
import kotlinx.coroutines.delay
import java.time.LocalDateTime


/** Tab numbers: 1 غذائي، 2 قياساتي، 3 حيوياتي، 4 روشتتي. */
@Composable
fun HomeScreen(vm: BodyWiseViewModel, updateVm: com.shawady.bodywise.update.UpdateViewModel, onNavigate: (Int) -> Unit) {
    val now by produceState(LocalDateTime.now()) {
        while (true) { delay(30_000); value = LocalDateTime.now() }
    }
    val today = now.toLocalDate()
    val slots = slotsFor(vm.medications, today).map { it to slotState(it, vm.doseLogs, now) }
    val latestVitals = VitalType.entries.mapNotNull { t -> vm.vitals.firstOrNull { it.type == t } }
    val weights = vm.measurements.filter { it.values[Metric.WEIGHT] != null }
    val lastWeight = weights.firstOrNull()
    val alerts = dashboardAlerts(
        now, slots, vm.medications, latestVitals, vm.waterFor(today), vm.waterGoal, vm.measurements.firstOrNull()?.date,
        vm.appointments.toList(), vm.vitals.toList(), vm.people.toList(),
        alarmProblems = com.shawady.bodywise.notify.alarmProblems(androidx.compose.ui.platform.LocalContext.current),
        backupOverdueDays = vm.backupOverdueDays()
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (updateVm.available && !updateVm.prompt) {
            AppCard {
                SectionTitle("🎉", "فيه تحديث جديد ${updateVm.release?.tag.orEmpty()}")
                Hint("اختياري، وبياناتك مش بتتمسح.")
                Button(onClick = { updateVm.showPrompt() }, modifier = Modifier.fillMaxWidth()) { Text("عرض التحديث") }
            }
        }
        val takenToday = slots.count { it.second == SlotState.TAKEN }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(
                (if (now.hour < 12) "صباح الخير ☀️" else if (now.hour < 18) "مساء الخير 🌤" else "مساء الخير 🌙"),
                fontSize = 24.sp, fontWeight = FontWeight.Bold
            )
            Hint("${arabicDayFull(today)} ${dayMonth(today)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallStat("💧 المياه", "${vm.waterFor(today)}/${vm.waterGoal}", Modifier.weight(1f))
                SmallStat("💊 الجرعات", if (slots.isEmpty()) "—" else "$takenToday/${slots.size}", Modifier.weight(1f))
                SmallStat("🍽 الأكل", "${vm.mealsCount(today)}", Modifier.weight(1f))
            }
        }

        if (alerts.isNotEmpty()) {
            AppCard {
                SectionTitle("🔔", "تنبيهات مهمة")
                alerts.forEach { a ->
                    Text((if (a.severe) "⚠️ " else "• ") + a.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (a.severe) Status.danger() else Status.warning())
                }
            }
        }

        AppCard {
            SectionTitle("💊", "الأدوية القادمة")
            val pending = slots.filter { it.second != SlotState.TAKEN && it.second != SlotState.SKIPPED }
            if (vm.medications.isEmpty()) Hint("لسه ما أضفتش أدوية.")
            else if (pending.isEmpty()) Hint("خلّصت جرعات النهارده ✅")
            pending.take(4).forEach { (slot, state) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(slot.time.format12(), fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(com.shawady.bodywise.data.medLabel(slot.med, vm.people), fontWeight = FontWeight.SemiBold)
                        Hint(listOf(slot.med.dose, slot.med.unit).filter { it.isNotBlank() }.joinToString(" ") + if (state == SlotState.MISSED) " • فاتت" else "")
                    }
                    Button(onClick = { vm.markDose(slot, DoseStatus.TAKEN) }) { Text("أخدتها") }
                }
            }
            OutlinedButton(onClick = { onNavigate(4) }, modifier = Modifier.fillMaxWidth()) { Text("روشتتي") }
        }

        val nextAppts = upcomingAppointments(vm.appointments, now).take(3)
        if (vm.doctors.isNotEmpty() || nextAppts.isNotEmpty()) {
            val context = androidx.compose.ui.platform.LocalContext.current
            fun call(d: com.shawady.bodywise.data.Doctor) {
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${com.shawady.bodywise.data.dialNumber(d.phone)}")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { vm.toast = "تعذر فتح الاتصال" }
            }
            AppCard {
                SectionTitle("👨‍⚕️", "دكاترتي")
                if (nextAppts.isEmpty()) Hint("مفيش مواعيد جاية.")
                nextAppts.forEach { a ->
                    val day = when (a.at.toLocalDate()) { today -> "النهارده"; today.plusDays(1) -> "بكرة"; else -> a.at.toLocalDate().toString() }
                    val doctor = com.shawady.bodywise.data.doctorOf(a, vm.doctors)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text((com.shawady.bodywise.data.personName(a.personId, vm.people)?.let { "$it • " } ?: "") + a.doctorName.ifBlank { "دكتور" }, fontWeight = FontWeight.SemiBold)
                            if (a.reason.isNotBlank()) Hint(a.reason)
                        }
                        Text("$day • ${a.at.toLocalTime().format12()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (doctor != null && doctor.phone.isNotBlank()) {
                            androidx.compose.material3.IconButton(onClick = { call(doctor) }) {
                                Text("📞", fontSize = 20.sp)
                            }
                        }
                    }
                }
                val quick = com.shawady.bodywise.data.topDoctors(vm.doctors, vm.appointments)
                if (quick.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        quick.forEach { d -> OutlinedButton(onClick = { call(d) }) { Text("📞 ${d.name}", maxLines = 1) } }
                    }
                }
                OutlinedButton(onClick = { vm.medsSubRequest = 2; onNavigate(4) }, modifier = Modifier.fillMaxWidth()) { Text("المواعيد والكاتلوج") }
            }
        }

        AppCard {
            SectionTitle("🥗", "التغذية والمياه")
            Text("الأكل النهارده: ${vm.mealsCount(today)} صنف", fontSize = 14.sp)
            val cups = vm.waterFor(today)
            Text("المياه: $cups من ${vm.waterGoal} أكواب 💧", fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.setWater(today, cups + 1) }) { Text("+ كوب") }
                OutlinedButton(onClick = { onNavigate(1) }) { Text("غذائي") }
            }
        }

        AppCard {
            SectionTitle("⚖️", "الوزن")
            if (lastWeight == null) Hint("لسه ما سجلتش وزن.") else {
                val w = lastWeight.values.getValue(Metric.WEIGHT)
                Text("${formatNumber(w)} كجم", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Hint("بتاريخ ${lastWeight.date}")
                weights.getOrNull(1)?.let { prev ->
                    val d = w - prev.values.getValue(Metric.WEIGHT)
                    Hint("الفرق عن القياس اللي قبله: ${if (d > 0) "+" else ""}${formatNumber(Math.round(d * 10) / 10.0)} كجم")
                }
                vm.targetWeight?.let { Hint("الهدف: ${formatNumber(it)} كجم — فاضل ${formatNumber(Math.round(Math.abs(w - it) * 10) / 10.0)} كجم") }
                vm.heightCm?.let { h -> bmi(w, h)?.let { Hint("BMI تقديري: ${formatNumber(Math.round(it * 10) / 10.0)}") } }
            }
            OutlinedButton(onClick = { onNavigate(2) }, modifier = Modifier.fillMaxWidth()) { Text("قياساتي") }
        }

        AppCard {
            SectionTitle("🩺", "آخر القياسات الحيوية")
            if (latestVitals.isEmpty()) Hint("لسه ما سجلتش قراءات.")
            latestVitals.forEach { r -> VitalLine(r) }
            OutlinedButton(onClick = { onNavigate(3) }, modifier = Modifier.fillMaxWidth()) { Text("حيوياتي") }
        }
        Spacer(Modifier.padding(8.dp))
    }
}

@Composable
private fun VitalLine(r: VitalReading) {
    val w = vitalWarning(r)
    val value = if (r.type == VitalType.BP) "${formatNumber(r.value)}/${formatNumber(r.value2 ?: 0.0)}" else formatNumber(r.value)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${r.type.emoji} ${r.type.label}", modifier = Modifier.weight(1f), fontSize = 14.sp)
        Text(
            "$value ${r.type.unit}" + if (w != null) " ⚠️" else "",
            fontWeight = FontWeight.Bold,
            color = when { w == null -> MaterialTheme.colorScheme.onSurface; w.severe -> Status.danger(); else -> Status.warning() }
        )
    }
    Hint("${r.at.toLocalDate()} ${r.at.toLocalTime().format12()}")
}
