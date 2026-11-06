package com.shawady.bodywise.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.shawady.bodywise.ui.components.SectionTitle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.shawady.bodywise.BuildConfig
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.components.AppCard
import com.shawady.bodywise.ui.components.ConfirmDialog
import com.shawady.bodywise.ui.components.Hint
import com.shawady.bodywise.ui.components.NumberField
import com.shawady.bodywise.util.formatNumber
import com.shawady.bodywise.util.parseDecimal
import com.shawady.bodywise.update.UpdateViewModel
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: BodyWiseViewModel, updateVm: UpdateViewModel, lockAvailable: Boolean, section: String? = null) {
    val context = LocalContext.current
    val settings = vm.settings
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.setAutoBackupFolder(uri)
            vm.toast = "تم تفعيل النسخ الاحتياطي الأسبوعي"
        }
    }
    val toggleReminder = rememberReminderToggler(vm)
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }
    var heightText by remember { mutableStateOf(vm.heightCm?.let { formatNumber(it) } ?: "") }
    var targetText by remember { mutableStateOf(vm.targetWeight?.let { formatNumber(it) } ?: "") }

    var openId by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(section) {
        val id = section?.substringBefore('#')?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        openId = id
        val index = listOf("look", "data", "reminders", "alerts", "security", "backup", "update").indexOf(id)
        if (index >= 0) listState.animateScrollToItem(index)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        item {
            SettingsSection("look", "🎨", "المظهر والهدف", openId, { openId = it }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("تلقائي", "فاتح", "داكن").forEachIndexed { index, label ->
                        FilterChip(selected = settings.themeMode == index, onClick = { vm.setThemeMode(index) }, label = { Text(label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("هدف المياه اليومي")
                        Hint("${vm.waterGoal} أكواب")
                    }
                    IconButton(onClick = { vm.changeWaterGoal(vm.waterGoal - 1) }) { Icon(Icons.Default.Remove, "نقص") }
                    IconButton(onClick = { vm.changeWaterGoal(vm.waterGoal + 1) }) { Icon(Icons.Default.Add, "زيادة") }
                }
            }
        }
        item {
            SettingsSection("data", "👤", "بياناتي", openId, { openId = it }) {
                NumberField("الطول (سم)", heightText, { heightText = it })
                NumberField("الوزن المستهدف (كجم)", targetText, { targetText = it })
                Hint("الطول بيُستخدم لحساب BMI تقريبي، والهدف بيظهر كخط على الرسم البياني.")
                Button(onClick = {
                    val h = parseDecimal(heightText)
                    val t = parseDecimal(targetText)
                    when {
                        heightText.isNotBlank() && (h == null || h < 100 || h > 250) -> vm.toast = "الطول لازم يكون بين 100 و250 سم"
                        targetText.isNotBlank() && (t == null || t < 20 || t > 400) -> vm.toast = "الوزن المستهدف غير منطقي"
                        else -> { vm.setProfile(h, t); vm.toast = "تم الحفظ" }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("حفظ") }
            }
        }
        item {
            SettingsSection("reminders", "⏰", "التذكيرات", openId, { openId = it }) {
                SwitchRow("تذكير المياه", "كل 3 ساعات من 9 صباحًا لـ 9 مساءً، لو لسه ما وصلتش للهدف", settings.waterReminders) { toggleReminder("water", it) }
                SwitchRow("تذكير قياسات الأسبوع", "الجمعة 6 مساءً لو ما سجلتش قياسات الأسبوع ده", settings.weeklyReminder) { toggleReminder("weekly", it) }
            }
        }
        item {
            SettingsSection("alerts", "💊", "تنبيهات الأدوية", openId, { openId = it }) {
                AlertsPanel(vm)
            }
        }
        item {
            SettingsSection("security", "🔒", "الأمان", openId, { openId = it }) {
                SwitchRow(
                    "قفل التطبيق بالبصمة", "يتطلب التحقق عند فتح التطبيق من جديد", settings.appLock
                ) { enabled ->
                    if (enabled && !lockAvailable) vm.toast = "فعّل بصمة أو قفل شاشة آمن من إعدادات الهاتف أولًا."
                    else vm.setAppLock(enabled)
                }
                Hint("كل بياناتك محفوظة محليًا على الموبايل.")
            }
        }
        item {
            SettingsSection("backup", "💾", "النسخ الاحتياطي والاستعادة", openId, { openId = it }) {
                Hint("احفظ الوجبات والمياه والقياسات وصور التقدم في ملف واحد، واستعدها عند تغيير الهاتف.")
                Button(onClick = { export.launch("BodyWise-Backup-${LocalDate.now()}.zip") }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Backup, null); Spacer(Modifier.width(8.dp)); Text("إنشاء نسخة احتياطية كاملة")
                }
                OutlinedButton(
                    onClick = { import.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    enabled = !vm.busy, modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Restore, null); Spacer(Modifier.width(8.dp)); Text("استعادة نسخة احتياطية")
                }
                OutlinedButton(onClick = { csv.launch("BodyWise-Measurements-${LocalDate.now()}.csv") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.TableChart, null); Spacer(Modifier.width(8.dp)); Text("تصدير القياسات (CSV للإكسل)")
                }
                Hint(vm.backupMessage)
                Hint("الاستعادة تستبدل البيانات الحالية بالبيانات الموجودة في النسخة المختارة.")
                if (settings.autoBackupUri == null) {
                    OutlinedButton(onClick = { folder.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Folder, null); Spacer(Modifier.width(8.dp)); Text("تفعيل النسخ الأسبوعي التلقائي (اختيار مجلد)")
                    }
                } else {
                    Text("النسخ الأسبوعي التلقائي مفعّل", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    Hint("آخر نسخة تلقائية: ${settings.lastAutoBackup ?: "لسه ما تمتش"} • بنحتفظ بآخر 5 نسخ")
                    OutlinedButton(onClick = { vm.setAutoBackupFolder(null) }, modifier = Modifier.fillMaxWidth()) { Text("إيقاف النسخ التلقائي") }
                }
            }
        }
        item {
            SettingsSection("update", "⬇️", "تحديث التطبيق", openId, { openId = it }) {
                Hint("الإصدار المثبت: ${BuildConfig.VERSION_NAME}")
                Hint(updateVm.message)
                Button(onClick = { updateVm.check() }, enabled = !updateVm.checking && !updateVm.downloading, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.SystemUpdate, null); Spacer(Modifier.width(8.dp))
                    Text(if (updateVm.checking) "جاري التحقق..." else "التحقق من التحديثات")
                }
                if (updateVm.available && updateVm.release != null) {
                    if (updateVm.downloading) {
                        LinearProgressIndicator(progress = { updateVm.progress }, modifier = Modifier.fillMaxWidth())
                        Hint("جاري تنزيل التحديث: ${(updateVm.progress * 100).toInt()}%")
                    }
                    Button(
                        onClick = { if (updateVm.readyFile != null) updateVm.install() else updateVm.download() },
                        enabled = !updateVm.downloading, modifier = Modifier.fillMaxWidth()
                    ) { Text(if (updateVm.readyFile != null) "تثبيت التحديث" else "تنزيل التحديث") }
                }
                Hint("التثبيت يحتاج موافقتك، وأندرويد بيتأكد إن التحديث موقّع بنفس مفتاح التطبيق. بياناتك لا تُحذف بالتحديث المتوافق.")
            }
        }
    }

    pendingRestore?.let { uri ->
        ConfirmDialog(
            "استبدال البيانات الحالية؟",
            "الاستعادة ستستبدل الوجبات والمياه والقياسات والصور الحالية بمحتوى النسخة المختارة، ولا يمكن التراجع. لو الملف غير صالح لن يتغير شيء.",
            "استبدال",
            onConfirm = { pendingRestore = null; vm.restoreBackup(uri) },
            onDismiss = { pendingRestore = null }
        )
    }
}

/** Tap the title to open or close the section; only one is open at a time. */
@Composable
private fun SettingsSection(id: String, emoji: String, title: String, openId: String, onOpen: (String) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val expanded = openId == id
    AppCard {
        Row(Modifier.fillMaxWidth().clickable { onOpen(if (expanded) "" else id) }, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(emoji, title, Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "إغلاق" else "فتح")
        }
        if (expanded) content()
    }
}
