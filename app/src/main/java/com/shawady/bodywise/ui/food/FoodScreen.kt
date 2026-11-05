package com.shawady.bodywise.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawady.bodywise.data.FoodEntry
import com.shawady.bodywise.data.MealSlot
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.components.AppCard
import com.shawady.bodywise.ui.components.Hint
import com.shawady.bodywise.ui.components.SmallStat
import com.shawady.bodywise.util.arabicDayFull
import com.shawady.bodywise.util.arabicDayShort
import com.shawady.bodywise.util.dayMonth
import com.shawady.bodywise.util.weekDays
import java.time.LocalDate

private class FoodEditor(val slot: MealSlot, val index: Int?, val initial: FoodEntry?)

@Composable
fun FoodScreen(vm: BodyWiseViewModel) {
    val today = LocalDate.now()
    val selected = vm.selectedDate
    val week = weekDays(selected)
    var editor by remember { mutableStateOf<FoodEditor?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.selectedDate = selected.minusDays(7) }) { Icon(Icons.Default.ChevronRight, "الأسبوع السابق") }
                Text(
                    "${dayMonth(week.first())} – ${dayMonth(week.last())}",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(onClick = { vm.selectedDate = selected.plusDays(7) }) { Icon(Icons.Default.ChevronLeft, "الأسبوع التالي") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val active = day == selected
                    Surface(
                        onClick = { vm.selectedDate = day },
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    ) {
                        val textColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(arabicDayShort(day), fontSize = 10.sp, color = textColor)
                            Text(day.dayOfMonth.toString(), fontWeight = FontWeight.Bold, color = textColor)
                            Text(if (day == today) "●" else " ", fontSize = 8.sp, color = textColor)
                        }
                    }
                }
            }
        }
        if (selected != today) {
            item { TextButton(onClick = { vm.selectedDate = today }) { Text("الرجوع لليوم") } }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallStat("وجبات اليوم", "${vm.mealsCount(selected)} / 3", Modifier.weight(1f))
                SmallStat("المياه", "${vm.waterFor(selected)} / ${vm.waterGoal} كوب", Modifier.weight(1f))
            }
        }
        item {
            Text("وجبات ${arabicDayFull(selected)} ${dayMonth(selected)}", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        if (vm.canCopyPreviousDay(selected)) {
            item {
                OutlinedButton(onClick = { vm.copyPreviousDay(selected) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer8()
                    Text("نسخ وجبات اليوم السابق")
                }
            }
        }
        items(MealSlot.entries.toList()) { slot ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(slot.emoji, fontSize = 22.sp)
                    Text(slot.key, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                    TextButton(onClick = { editor = FoodEditor(slot, null, null) }) {
                        Icon(Icons.Default.Add, null)
                        Text("إضافة")
                    }
                }
                val list = vm.foods(selected, slot)
                if (list.isEmpty()) Hint("لسه مفيش وجبات مضافة")
                list.forEachIndexed { index, food ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(food.name, fontWeight = FontWeight.Medium)
                            if (food.amount.isNotBlank()) Hint(food.amount)
                        }
                        IconButton(onClick = { editor = FoodEditor(slot, index, food) }) { Icon(Icons.Default.Edit, "تعديل") }
                        IconButton(onClick = { vm.removeFood(selected, slot, index) }) { Icon(Icons.Default.DeleteOutline, "حذف") }
                    }
                }
            }
        }
        item { WaterCard(vm, selected) }
        item { WeeklySummary(vm, week, today) }
    }

    editor?.let { current ->
        FoodDialog(
            title = if (current.index == null) "إضافة ${current.slot.key}" else "تعديل ${current.slot.key}",
            initial = current.initial,
            suggestions = vm.recentFoodNames(),
            onSave = { entry ->
                if (current.index == null) vm.addFood(selected, current.slot, entry)
                else vm.updateFood(selected, current.slot, current.index, entry)
                editor = null
            },
            onDismiss = { editor = null }
        )
    }
}

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))

@Composable
private fun WaterCard(vm: BodyWiseViewModel, date: LocalDate) {
    val cups = vm.waterFor(date)
    AppCard {
        Text("شرب المياه 💧", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$cups / ${vm.waterGoal} كوب", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.setWater(date, cups - 1) }) { Icon(Icons.Default.Remove, "نقص كوب") }
            OutlinedButton(onClick = { vm.setWater(date, cups + 1) }) { Text("+ كوب") }
        }
        LinearProgressIndicator(progress = { (cups / vm.waterGoal.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Hint("تقدر تغيّر الهدف من الإعدادات. التسجيل بيتحفظ لليوم المختار.")
    }
}

@Composable
private fun WeeklySummary(vm: BodyWiseViewModel, week: List<LocalDate>, today: LocalDate) {
    val loggedDays = week.count { vm.mealsCount(it) > 0 }
    val elapsed = week.filter { !it.isAfter(today) }
    val avgWater = if (elapsed.isEmpty()) 0.0 else elapsed.sumOf { vm.waterFor(it) }.toDouble() / elapsed.size
    val goalDays = elapsed.count { vm.waterFor(it) >= vm.waterGoal }
    AppCard {
        Text("ملخص الأسبوع 📊", fontWeight = FontWeight.Bold)
        Text("سجّلت وجبات في $loggedDays من 7 أيام")
        Text("متوسط المياه: ${"%.1f".format(avgWater)} كوب في اليوم")
        Text("أيام وصلت فيها لهدف المياه: $goalDays")
    }
}

@Composable
private fun FoodDialog(
    title: String,
    initial: FoodEntry?,
    suggestions: List<String>,
    onSave: (FoodEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amount by remember { mutableStateOf(initial?.amount ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم الأكل") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("الكمية أو ملاحظات") }, singleLine = true)
                if (initial == null && suggestions.isNotEmpty()) {
                    Hint("اخترت قبل كده:")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(suggestions) { suggestion ->
                            AssistChip(onClick = { name = suggestion }, label = { Text(suggestion) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(FoodEntry(name.trim(), amount.trim())) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
