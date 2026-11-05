package com.shawady.bodywise.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.shawady.bodywise.ui.BodyWiseViewModel

/** Returns a function (kind, enabled) that turns reminders on/off and asks for notification permission when needed. */
@Composable
fun rememberReminderToggler(vm: BodyWiseViewModel): (String, Boolean) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val kind = pending
        pending = null
        if (granted) {
            if (kind == "water") vm.setWaterReminders(true)
            if (kind == "weekly") vm.setWeeklyReminder(true)
        } else {
            vm.toast = "لازم تسمح بالإشعارات من إعدادات الهاتف علشان التذكيرات تشتغل"
        }
    }
    return { kind, enabled ->
        if (!enabled) {
            if (kind == "water") vm.setWaterReminders(false) else vm.setWeeklyReminder(false)
        } else {
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                pending = kind
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (kind == "water") vm.setWaterReminders(true) else vm.setWeeklyReminder(true)
        }
    }
}

@Composable
fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f)) {
            androidx.compose.material3.Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            com.shawady.bodywise.ui.components.Hint(description)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}
