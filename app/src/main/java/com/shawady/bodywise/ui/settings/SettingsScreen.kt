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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SystemUpdate
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
import com.shawady.bodywise.update.UpdateViewModel
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: BodyWiseViewModel, updateVm: UpdateViewModel, lockAvailable: Boolean) {
    val context = LocalContext.current
    val settings = vm.settings
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var pendingReminder by remember { mutableStateOf<String?>(null) }

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
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val kind = pendingReminder
        pendingReminder = null
        if (granted) {
            if (kind == "water") vm.setWaterReminders(true)
            if (kind == "weekly") vm.setWeeklyReminder(true)
        } else {
            vm.toast = "لازم تسمح بالإشعارات من إعدادات الهاتف علشان التذكيرات تشتغل"
        }
    }

    fun toggleReminder(kind: String, enabled: Boolean) {
        if (!enabled) {
            if (kind == "water") vm.setWaterReminders(false) else vm.setWeeklyReminder(false)
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingReminder = kind
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (kind == "water") vm.setWaterReminders(true) else vm.setWeeklyReminder(true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        item {
            AppCard {
                Text("المظهر والهدف", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            AppCard {
                Text("التذكيرات", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                SwitchRow("تذكير المياه", "كل 3 ساعات من 9 صباحًا لـ 9 مساءً، لو لسه ما وصلتش للهدف", settings.waterReminders) { toggleReminder("water", it) }
                SwitchRow("تذكير قياسات الأسبوع", "الجمعة 6 مساءً لو ما سجلتش قياسات الأسبوع ده", settings.weeklyReminder) { toggleReminder("weekly", it) }
            }
        }
        item {
            AppCard {
                Text("الأمان", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            AppCard {
                Text("النسخ الاحتياطي والاستعادة", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            AppCard {
                Text("تحديث التطبيق", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                Hint("يتم فحص SHA-256 قبل التثبيت. التثبيت يحتاج موافقتك، وبياناتك لا تُحذف بالتحديث المتوافق.")
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

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Hint(description)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
