package com.shawady.bodywise.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.components.AppCard
import com.shawady.bodywise.ui.components.Hint
import com.shawady.bodywise.ui.components.NumberField
import com.shawady.bodywise.ui.components.SectionTitle
import com.shawady.bodywise.util.formatNumber
import com.shawady.bodywise.util.parseDecimal

private const val STEPS = 4

/** First-run setup: welcome, about you, permissions, done. Everything can be skipped and changed later in Settings. */
@Composable
fun OnboardingScreen(vm: BodyWiseViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var height by rememberSaveable { mutableStateOf(vm.heightCm?.let { formatNumber(it) } ?: "") }
    var target by rememberSaveable { mutableStateOf(vm.targetWeight?.let { formatNumber(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val toggleReminder = rememberReminderToggler(vm)
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreBackup(uri) { vm.markOnboarded() }
    }
    LaunchedEffect(vm.toast) {
        vm.toast?.let { error = it; vm.toast = null }
    }

    fun validateProfile(): Boolean {
        val h = parseDecimal(height)
        val t = parseDecimal(target)
        return when {
            height.isNotBlank() && (h == null || h < 100 || h > 250) -> { error = "الطول لازم يكون بين 100 و250 سم"; false }
            target.isNotBlank() && (t == null || t < 20 || t > 400) -> { error = "الوزن المستهدف غير منطقي"; false }
            else -> { error = null; true }
        }
    }
    fun finish() {
        vm.setProfile(parseDecimal(height), parseDecimal(target))
        vm.markOnboarded()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                repeat(STEPS) { i ->
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(if (i == step) 24.dp else 8.dp, 8.dp)
                            .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (step) {
                    0 -> WelcomeStep(onRestore = { restore.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, busy = vm.busy, message = vm.backupMessage)
                    1 -> {
                        Text("عنك 👤", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Hint("كل ده اختياري وتقدر تغيّره بعدين من الإعدادات.")
                        AppCard {
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
                            SectionTitle("⏰", "التذكيرات")
                            SwitchRow("تذكير المياه", "كل 3 ساعات من 9 صباحًا لـ 9 مساءً", vm.settings.waterReminders) { toggleReminder("water", it) }
                            SwitchRow("تذكير قياسات الأسبوع", "الجمعة 6 مساءً", vm.settings.weeklyReminder) { toggleReminder("weekly", it) }
                        }
                    }
                    2 -> {
                        Text("الصلاحيات 🔔", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Hint("علشان تنبيهات الأدوية تشتغل بدقة حتى والتطبيق مقفول والموبايل مقفول. تقدر تتخطى وتفعّلها بعدين من الإعدادات.")
                        AppCard { AlertsPanel(vm) }
                    }
                    else -> {
                        Spacer(Modifier.height(24.dp))
                        Text("جاهز 🎉", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Hint("ابدأ بإضافة دواء من \"روشتتي\" أو سجّل وجبة أو قراءة. كل بياناتك على جهازك بس.")
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) OutlinedButton(onClick = { step-- }) { Text("السابق") }
                Spacer(Modifier.weight(1f))
                if (step in 1 until STEPS - 1) TextButton(onClick = { error = null; step++ }) { Text("تخطي") }
                Button(onClick = {
                    when (step) {
                        1 -> if (validateProfile()) step++
                        STEPS - 1 -> finish()
                        else -> step++
                    }
                }) { Text(if (step == 0) "يلا نبدأ" else if (step == STEPS - 1) "ابدأ الاستخدام" else "التالي") }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onRestore: () -> Unit, busy: Boolean, message: String) {
    Spacer(Modifier.height(12.dp))
    Text("💚", fontSize = 56.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Text("أهلًا بيك في BodyWise", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Hint("مساعدك الشخصي لمتابعة صحتك في مكان واحد، وبياناتك كلها على موبايلك بس.")
    AppCard {
        Feature("🥗", "غذائي", "وجباتك ومياهك وملاحظات يومك")
        Feature("⚖️", "قياساتي", "الوزن والمقاسات وصور التقدم")
        Feature("🩺", "حيوياتي", "الضغط والسكر والنبض مع تنبيه للقراءات غير الطبيعية")
        Feature("💊", "روشتتي", "أدويتك ومواعيدها وتنبيهات بالشاشة الكاملة وتجديد العلب")
    }
    AppCard {
        SectionTitle("📦", "بتنقل من موبايل تاني؟")
        Hint("لو عندك نسخة احتياطية من BodyWise، استعدها دلوقتي وكمّل من حيث وقفت.")
        OutlinedButton(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("استعادة نسخة احتياطية") }
        if (busy || message.isNotBlank()) Hint(message)
    }
}

@Composable
private fun Feature(emoji: String, title: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(emoji, fontSize = 24.sp)
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Hint(text)
        }
    }
}
