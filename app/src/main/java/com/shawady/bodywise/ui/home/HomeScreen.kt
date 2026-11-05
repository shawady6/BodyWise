package com.shawady.bodywise.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.shawady.bodywise.data.slotState
import com.shawady.bodywise.data.slotsFor
import com.shawady.bodywise.data.vitalWarning
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.components.AppCard
import com.shawady.bodywise.ui.components.Hint
import com.shawady.bodywise.util.bmi
import com.shawady.bodywise.util.formatNumber
import kotlinx.coroutines.delay
import java.time.LocalDateTime

private val red = Color(0xFFD32F2F)
private val amber = Color(0xFFE08A00)

/** Tab numbers: 1 غذائي، 2 قياساتي، 3 حيوياتي، 4 روشتتي. */
@Composable
fun HomeScreen(vm: BodyWiseViewModel, onNavigate: (Int) -> Unit) {
    val now by produceState(LocalDateTime.now()) {
        while (true) { delay(30_000); value = LocalDateTime.now() }
    }
    val today = now.toLocalDate()
    val slots = slotsFor(vm.medications, today).map { it to slotState(it, vm.doseLogs, now) }
    val latestVitals = VitalType.entries.mapNotNull { t -> vm.vitals.firstOrNull { it.type == t } }
    val weights = vm.measurements.filter { it.values[Metric.WEIGHT] != null }
    val lastWeight = weights.firstOrNull()
    val alerts = dashboardAlerts(
        now, slots, vm.medications, latestVitals, vm.waterFor(today), vm.waterGoal, vm.measurements.firstOrNull()?.date
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (alerts.isNotEmpty()) {
            AppCard {
                Text("تنبيهات مهمة", fontWeight = FontWeight.Bold)
                alerts.forEach { a ->
                    Text((if (a.severe) "⚠️ " else "• ") + a.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (a.severe) red else amber)
                }
            }
        }

        AppCard {
            Text("الأدوية القادمة", fontWeight = FontWeight.Bold)
            val pending = slots.filter { it.second != SlotState.TAKEN && it.second != SlotState.SKIPPED }
            if (vm.medications.isEmpty()) Hint("لسه ما أضفتش أدوية.")
            else if (pending.isEmpty()) Hint("خلّصت جرعات النهارده ✅")
            pending.take(4).forEach { (slot, state) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(slot.time.toString().take(5), fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(slot.med.name, fontWeight = FontWeight.SemiBold)
                        Hint(listOf(slot.med.dose, slot.med.unit).filter { it.isNotBlank() }.joinToString(" ") + if (state == SlotState.MISSED) " • فاتت" else "")
                    }
                    Button(onClick = { vm.markDose(slot, DoseStatus.TAKEN) }) { Text("أخدتها") }
                }
            }
            OutlinedButton(onClick = { onNavigate(4) }, modifier = Modifier.fillMaxWidth()) { Text("روشتتي") }
        }

        AppCard {
            Text("التغذية والمياه", fontWeight = FontWeight.Bold)
            Text("الأكل النهارده: ${vm.mealsCount(today)} صنف", fontSize = 14.sp)
            val cups = vm.waterFor(today)
            Text("المياه: $cups من ${vm.waterGoal} أكواب 💧", fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.setWater(today, cups + 1) }) { Text("+ كوب") }
                OutlinedButton(onClick = { onNavigate(1) }) { Text("غذائي") }
            }
        }

        AppCard {
            Text("الوزن", fontWeight = FontWeight.Bold)
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
            Text("آخر القياسات الحيوية", fontWeight = FontWeight.Bold)
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
            color = when { w == null -> MaterialTheme.colorScheme.onSurface; w.severe -> red; else -> amber }
        )
    }
    Hint("${r.at.toLocalDate()} ${r.at.toLocalTime().toString().take(5)}")
}
