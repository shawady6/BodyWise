package com.shawady.bodywise.ui.settings

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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.components.AppCard
import com.shawady.bodywise.ui.components.Hint
import com.shawady.bodywise.ui.components.NumberField
import com.shawady.bodywise.util.formatNumber
import com.shawady.bodywise.util.parseDecimal

/** Shown once on first launch. Everything is optional and can be changed later in Settings. */
@Composable
fun OnboardingScreen(vm: BodyWiseViewModel) {
    var height by remember { mutableStateOf(vm.heightCm?.let { formatNumber(it) } ?: "") }
    var target by remember { mutableStateOf(vm.targetWeight?.let { formatNumber(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val toggleReminder = rememberReminderToggler(vm)
    LaunchedEffect(vm.toast) {
        vm.toast?.let { error = it; vm.toast = null }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("أهلًا بيك في BodyWise 💚", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
            Hint("خطوة صغيرة كل يوم. كل البيانات هنا اختيارية وتقدر تغيّرها بعدين من الإعدادات، وكلها على جهازك بس.")
            AppCard {
                Text("عنك", fontWeight = FontWeight.Bold)
                NumberField("الطول (سم) — علشان نحسب BMI تقريبي", height, { height = it })
                NumberField("الوزن المستهدف (كجم)", target, { target = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("هدف المياه اليومي")
                        Hint("${vm.waterGoal} أكواب")
                    }
                    IconButton(onClick = { vm.changeWaterGoal(vm.waterGoal - 1) }) { Icon(Icons.Default.Remove, "نقص") }
                    IconButton(onClick = { vm.changeWaterGoal(vm.waterGoal + 1) }) { Icon(Icons.Default.Add, "زيادة") }
                }
            }
            AppCard {
                Text("التذكيرات", fontWeight = FontWeight.Bold)
                SwitchRow("تذكير المياه", "كل 3 ساعات من 9 صباحًا لـ 9 مساءً", vm.settings.waterReminders) { toggleReminder("water", it) }
                SwitchRow("تذكير قياسات الأسبوع", "الجمعة 6 مساءً", vm.settings.weeklyReminder) { toggleReminder("weekly", it) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            Button(onClick = {
                val h = parseDecimal(height)
                val t = parseDecimal(target)
                when {
                    height.isNotBlank() && (h == null || h < 100 || h > 250) -> error = "الطول لازم يكون بين 100 و250 سم"
                    target.isNotBlank() && (t == null || t < 20 || t > 400) -> error = "الوزن المستهدف غير منطقي"
                    else -> { vm.setProfile(h, t); vm.markOnboarded() }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("ابدأ") }
            TextButton(onClick = { vm.markOnboarded() }, modifier = Modifier.fillMaxWidth()) { Text("تخطي دلوقتي") }
        }
    }
}
