package com.shawady.bodywise.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.shawady.bodywise.MainActivity
import com.shawady.bodywise.R
import com.shawady.bodywise.data.ApptReminder
import com.shawady.bodywise.data.HealthStore
import com.shawady.bodywise.data.appointmentReminders
import com.shawady.bodywise.util.format12
import java.time.LocalDateTime
import java.time.ZoneId

/** Two reminders per doctor appointment: the evening before and two hours before. */
object ApptAlarms {
    const val ACTION = "com.shawady.bodywise.APPT_FIRE"
    private const val EXTRA_ID = "appt"
    private const val EXTRA_KIND = "kind"
    private const val REGISTRY = "appt_alarm_codes"
    const val CHANNEL = "bodywise_appointments"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) != null) return
            val channel = NotificationChannel(CHANNEL, "مواعيد الدكاترة", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تذكير قبل موعد الدكتور"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun code(id: String, kind: ApptReminder) = "appt|$id|${kind.name}".hashCode()

    private fun pending(context: Context, id: String, kind: ApptReminder): PendingIntent {
        val intent = Intent(context, ApptReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_ID, id); putExtra(EXTRA_KIND, kind.name)
        }
        return PendingIntent.getBroadcast(context, code(id, kind), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java)
        val prefs = app.getSharedPreferences("bodywise_data", Context.MODE_PRIVATE)
        val dummy = Intent(app, ApptReceiver::class.java).apply { action = ACTION }
        prefs.getStringSet(REGISTRY, emptySet())?.forEach { c ->
            c.toIntOrNull()?.let { alarms.cancel(PendingIntent.getBroadcast(app, it, dummy, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)) }
        }
        val codes = mutableSetOf<String>()
        val now = LocalDateTime.now()
        HealthStore(app).loadAppointments().forEach { appt ->
            appointmentReminders(appt, now).forEach { (kind, at) ->
                val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val pi = pending(app, appt.id, kind)
                val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
                try {
                    if (canExact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
                    else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
                } catch (_: SecurityException) {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
                }
                codes.add(code(appt.id, kind).toString())
            }
        }
        prefs.edit().putStringSet(REGISTRY, codes).apply()
    }

    fun show(context: Context, id: String, kind: ApptReminder) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        val appt = HealthStore(context).loadAppointments().firstOrNull { it.id == id } ?: return
        if (appt.done) return
        ensureChannel(context)
        val who = appt.doctorName.ifBlank { "الدكتور" }
        val forPerson = com.shawady.bodywise.data.personName(appt.personId, HealthStore(context).loadPeople())
        val whenText = if (kind == ApptReminder.EVENING_BEFORE) "بكرة الساعة ${appt.at.toLocalTime().format12()}" else "الساعة ${appt.at.toLocalTime().format12()} (بعد ساعتين)"
        val text = whenText + if (appt.reason.isNotBlank()) " • ${appt.reason}" else ""
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (forPerson != null) "📅 موعد $forPerson مع $who" else "📅 موعدك مع $who")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(code(id, kind), n)
    }

    internal fun readExtras(intent: Intent): Pair<String, ApptReminder>? {
        val id = intent.getStringExtra(EXTRA_ID) ?: return null
        val kind = runCatching { ApptReminder.valueOf(intent.getStringExtra(EXTRA_KIND).orEmpty()) }.getOrNull() ?: return null
        return id to kind
    }
}

class ApptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ApptAlarms.ACTION) return
        val (id, kind) = ApptAlarms.readExtras(intent) ?: return
        ApptAlarms.show(context.applicationContext, id, kind)
        ApptAlarms.rescheduleAll(context.applicationContext)
    }
}
