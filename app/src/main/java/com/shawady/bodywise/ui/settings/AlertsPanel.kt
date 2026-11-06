package com.shawady.bodywise.ui.settings

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shawady.bodywise.notify.MedAlarms
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.components.Hint

private val amber = Color(0xFFE08A00)
private val green = Color(0xFF2E7D32)

/**
 * Everything medication alarms need from the phone: status of each permission, a button that walks through
 * the missing ones one by one, the alarm tone, and a test notification. Used in Settings and in first-run setup.
 */
@Composable
fun AlertsPanel(vm: BodyWiseViewModel) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val tonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            vm.setMedTone(picked?.toString())
        }
    }
    @Suppress("UNUSED_EXPRESSION") refresh
    val manager = context.getSystemService(NotificationManager::class.java)
    val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val batteryOk = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    val fullScreenOk = Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()
    val overlayOk = Settings.canDrawOverlays(context)
    val toneUri = vm.settings.medTone?.let { Uri.parse(it) }
    val toneTitle = remember(vm.settings.medTone) {
        runCatching { toneUri?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) } }.getOrNull() ?: "نغمة المنبه الافتراضية"
    }
    val pkg = "package:${context.packageName}"

    val askNotifications = {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    val askExact = { open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse(pkg))) }
    val askFullScreen = { open(context, Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", Uri.parse(pkg))) }
    val askOverlay = { open(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse(pkg))) }
    val askBattery = { open(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }

    val missing = listOf(notificationsOn, exactOk, fullScreenOk, overlayOk, batteryOk).count { !it }
    val next: (() -> Unit)? = when {
        !notificationsOn -> askNotifications
        !exactOk -> askExact
        !fullScreenOk -> askFullScreen
        !overlayOk -> askOverlay
        !batteryOk -> askBattery
        else -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (next != null) {
            Button(onClick = next, modifier = Modifier.fillMaxWidth()) { Text("اسمح بالصلاحيات الناقصة ($missing)") }
            Hint("اضغط الزر وفعّل الصلاحية، وارجع للتطبيق، واضغطه تاني للصلاحية اللي بعدها.")
        } else {
            Text("✅ كل الصلاحيات تمام", color = green, fontSize = 14.sp)
        }
        StatusLine("الإشعارات", "علشان يوصلك تنبيه الدواء", notificationsOn, askNotifications)
        StatusLine("المواعيد الدقيقة", "علشان التنبيه يجي في الدقيقة المظبوطة", exactOk, askExact)
        StatusLine("الشاشة الكاملة وقت القفل", "علشان التنبيه يفتح والموبايل مقفول", fullScreenOk, askFullScreen)
        StatusLine("الظهور فوق التطبيقات", "علشان التنبيه يفتح شاشة كاملة والموبايل مفتوح", overlayOk, askOverlay)
        StatusLine("استثناء من توفير البطارية", "علشان التنبيه يشتغل والتطبيق مقفول (مهم في شاومي وسامسونج وأوبو)", batteryOk, askBattery)

        OutlinedButton(onClick = {
            tonePicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_RINGTONE)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "نغمة تنبيه الدواء")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, toneUri)
            })
        }, modifier = Modifier.fillMaxWidth()) { Text("🎵 نغمة التنبيه: $toneTitle") }
        Hint("لو عايز نغمتك الخاصة، حطها في فولدر Alarms أو Ringtones في الموبايل وهتظهر في القائمة.")
        OutlinedButton(onClick = {
            open(context, Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, MedAlarms.channelId(context))
            })
        }, modifier = Modifier.fillMaxWidth()) { Text("اهتزاز وإعدادات القناة") }
        Button(onClick = { MedAlarms.sendTest(context) }, modifier = Modifier.fillMaxWidth()) { Text("جرّب تنبيه دلوقتي") }
    }
}

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun StatusLine(label: String, why: String, ok: Boolean, onFix: () -> Unit) {
    Column {
        Text((if (ok) "✅ " else "⚠️ ") + label + (if (ok) " — تمام" else " — محتاج تفعيل"), fontSize = 13.sp, color = if (ok) green else amber)
        if (!ok) Hint(why)
    }
}
