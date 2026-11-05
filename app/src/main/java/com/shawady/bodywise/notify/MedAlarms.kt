package com.shawady.bodywise.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.shawady.bodywise.MainActivity
import com.shawady.bodywise.R
import com.shawady.bodywise.data.DoseStatus
import com.shawady.bodywise.data.MedStore
import com.shawady.bodywise.data.Medication
import com.shawady.bodywise.data.SNOOZE_MINUTES
import com.shawady.bodywise.data.nextOccurrence
import com.shawady.bodywise.util.formatNumber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object MedAlarms {
    const val CHANNEL = "bodywise_meds"
    const val ACTION_FIRE = "com.shawady.bodywise.MED_FIRE"
    const val ACTION_TAKEN = "com.shawady.bodywise.MED_TAKEN"
    const val ACTION_SNOOZE = "com.shawady.bodywise.MED_SNOOZE"
    const val EXTRA_MED = "med"
    const val EXTRA_DATE = "date"
    const val EXTRA_TIME = "time"
    private const val REGISTRY = "med_alarm_codes"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) != null) return
            val channel = NotificationChannel(CHANNEL, "تنبيهات الأدوية", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تنبيه في موعد كل جرعة"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun code(medId: String, time: LocalTime, snooze: Boolean = false): Int = ("$snooze|$medId|$time").hashCode()

    private fun pending(context: Context, medId: String, date: LocalDate, time: LocalTime, snooze: Boolean = false): PendingIntent {
        val intent = Intent(context, MedReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_MED, medId)
            putExtra(EXTRA_DATE, date.toString())
            putExtra(EXTRA_TIME, time.toString())
        }
        return PendingIntent.getBroadcast(context, code(medId, time, snooze), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun setExact(context: Context, at: LocalDateTime, pi: PendingIntent) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (canExact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    /** Cancels everything scheduled before and sets the next alarm for each medication time. Safe to call any time. */
    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java)
        val prefs = app.getSharedPreferences("bodywise_data", Context.MODE_PRIVATE)
        val meds = MedStore(app).loadMeds()
        // Alarms are matched by request code, so cancelling needs the same intent identity.
        val dummy = Intent(app, MedReceiver::class.java).apply { action = ACTION_FIRE }
        prefs.getStringSet(REGISTRY, emptySet())?.forEach { c ->
            c.toIntOrNull()?.let { alarms.cancel(PendingIntent.getBroadcast(app, it, dummy, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)) }
        }
        val codes = mutableSetOf<String>()
        val now = LocalDateTime.now()
        meds.forEach { med ->
            med.times.forEach { time ->
                val next = nextOccurrence(med, time, now) ?: return@forEach
                setExact(app, next, pending(app, med.id, next.toLocalDate(), time))
                codes.add(code(med.id, time).toString())
            }
        }
        prefs.edit().putStringSet(REGISTRY, codes).apply()
    }

    fun scheduleSnooze(context: Context, med: Medication, date: LocalDate, time: LocalTime) {
        val at = LocalDateTime.now().plusMinutes(SNOOZE_MINUTES)
        setExact(context, at, pending(context, med.id, date, time, snooze = true))
    }

    fun showDose(context: Context, med: Medication, date: LocalDate, time: LocalTime) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val id = code(med.id, time)
        fun action(act: String, label: String, req: Int): NotificationCompat.Action {
            val intent = Intent(context, MedReceiver::class.java).apply {
                action = act
                putExtra(EXTRA_MED, med.id); putExtra(EXTRA_DATE, date.toString()); putExtra(EXTRA_TIME, time.toString())
            }
            val pi = PendingIntent.getBroadcast(context, id + req, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return NotificationCompat.Action.Builder(0, label, pi).build()
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val dose = listOf(med.dose, med.unit).filter { it.isNotBlank() }.joinToString(" ")
        val extra = med.instruction.takeIf { it.isNotBlank() && it != "لا يهم" }
        val text = listOfNotNull(dose.ifBlank { null }, extra).joinToString(" • ").ifBlank { "حان موعد الدواء" }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("💊 ${med.name} — ${time.toString().take(5)}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text + if (med.notes.isNotBlank()) "\n${med.notes}" else ""))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(action(ACTION_TAKEN, "أخدتها ✓", 1))
            .addAction(action(ACTION_SNOOZE, "أجّل $SNOOZE_MINUTES دقايق", 2))
        manager.notify(id, builder.build())
    }

    fun sendTest(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("💊 تجربة تنبيه الدواء")
            .setContentText("لو شفت الرسالة دي وسمعت الصوت يبقى التنبيهات شغالة.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()
        manager.notify(9001, notification)
    }

    fun nowMillis(): Long = Instant.now().toEpochMilli()
}

/** Handles the alarm itself and the two notification buttons. */
class MedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val store = MedStore(app)
        val medId = intent.getStringExtra(MedAlarms.EXTRA_MED) ?: return
        val date = runCatching { LocalDate.parse(intent.getStringExtra(MedAlarms.EXTRA_DATE)) }.getOrNull() ?: return
        val time = runCatching { LocalTime.parse(intent.getStringExtra(MedAlarms.EXTRA_TIME)) }.getOrNull() ?: return
        val med = store.loadMeds().firstOrNull { it.id == medId }
        when (intent.action) {
            MedAlarms.ACTION_FIRE -> {
                if (med != null) {
                    val key = com.shawady.bodywise.data.doseKey(med.id, date, time)
                    val done = store.loadLogs()[key]?.status.let { it == DoseStatus.TAKEN || it == DoseStatus.SKIPPED }
                    if (!done) MedAlarms.showDose(app, med, date, time)
                }
                MedAlarms.rescheduleAll(app)
            }
            MedAlarms.ACTION_TAKEN -> {
                if (med != null) store.mark(med, date, time, DoseStatus.TAKEN, LocalDateTime.now())
                app.getSystemService(NotificationManager::class.java).cancel(("false|$medId|$time").hashCode())
            }
            MedAlarms.ACTION_SNOOZE -> {
                if (med != null) {
                    store.mark(med, date, time, DoseStatus.SNOOZED, LocalDateTime.now())
                    MedAlarms.scheduleSnooze(app, med, date, time)
                }
                app.getSystemService(NotificationManager::class.java).cancel(("false|$medId|$time").hashCode())
            }
        }
    }
}

/** Alarms are lost on reboot, app update, clock or time zone change, so they are set up again. */
class MedBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MedAlarms.rescheduleAll(context)
    }
}
