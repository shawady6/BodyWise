package com.shawady.bodywise.notify

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** Reasons a dose alarm might not reach the person. Empty when everything is fine. */
fun alarmProblems(context: Context): List<String> {
    val problems = mutableListOf<String>()
    val manager = context.getSystemService(NotificationManager::class.java)
    if (!manager.areNotificationsEnabled()) {
        problems.add("الإشعارات متوقفة، تنبيه الدواء مش هيوصلك")
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = manager.getNotificationChannel(MedAlarms.channelId(context))
        if (channel != null && channel.importance < NotificationManager.IMPORTANCE_HIGH) {
            problems.add("قناة تنبيهات الأدوية اتكتمت أو اتقلّلت أهميتها من إعدادات النظام")
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
        problems.add("المنبهات الدقيقة مش مسموحة، التنبيه ممكن يتأخر")
    }
    return problems
}
