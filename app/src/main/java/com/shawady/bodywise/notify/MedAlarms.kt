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
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.core.app.NotificationCompat
import com.shawady.bodywise.MainActivity
import com.shawady.bodywise.R
import com.shawady.bodywise.data.DoseStatus
import com.shawady.bodywise.data.MedStore
import com.shawady.bodywise.data.Medication
import com.shawady.bodywise.data.SNOOZE_MINUTES
import com.shawady.bodywise.data.nextOccurrence
import com.shawady.bodywise.util.format12
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object MedAlarms {
        const val ACTION_FIRE = "com.shawady.bodywise.MED_FIRE"
    const val ACTION_TAKEN = "com.shawady.bodywise.MED_TAKEN"
    const val ACTION_SNOOZE = "com.shawady.bodywise.MED_SNOOZE"
    const val ACTION_REPEAT = "com.shawady.bodywise.MED_REPEAT"
    const val EXTRA_FIRED_AT = "fired_at"
    const val EXTRA_MED = "med"
    const val EXTRA_DATE = "date"
    const val EXTRA_TIME = "time"
    private const val REGISTRY = "med_alarm_codes"

    private const val CHANNEL_PREFIX = "bodywise_meds"

    private fun tone(context: Context): Uri? =
        context.getSharedPreferences("bodywise_data", Context.MODE_PRIVATE).getString("med_tone", null)?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    /** Channel sounds cannot be changed after creation, so each tone gets its own channel id. */
    fun channelId(context: Context): String = "${CHANNEL_PREFIX}_${(tone(context)?.toString() ?: "none").hashCode().toUInt().toString(16)}"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val id = channelId(context)
            manager.notificationChannels.filter { it.id.startsWith(CHANNEL_PREFIX) && it.id != id }.forEach { manager.deleteNotificationChannel(it.id) }
            if (manager.getNotificationChannel(id) != null) return
            val channel = NotificationChannel(id, "تنبيهات الأدوية", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تنبيه في موعد كل جرعة"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(false)
                setSound(tone(context), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
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

    /**
     * Dose alarms use the alarm-clock type: Android treats it as the most important kind of alarm (it survives Doze and
     * battery savers better), at the price of an alarm icon in the status bar and the next dose as "next alarm".
     */
    private fun setAlarm(context: Context, at: LocalDateTime, pi: PendingIntent) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val show = PendingIntent.getActivity(context, 7, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), pi)
        } catch (_: SecurityException) {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
            try {
                if (canExact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } catch (_: SecurityException) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        }
    }

    private fun setExactSimple(context: Context, at: LocalDateTime, pi: PendingIntent) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun repeatPending(context: Context, medId: String, date: LocalDate, time: LocalTime, n: Int, firedAt: LocalDateTime): PendingIntent {
        val intent = Intent(context, MedReceiver::class.java).apply {
            action = ACTION_REPEAT
            putExtra(EXTRA_MED, medId); putExtra(EXTRA_DATE, date.toString()); putExtra(EXTRA_TIME, time.toString())
            putExtra(EXTRA_FIRED_AT, firedAt.toString())
        }
        return PendingIntent.getBroadcast(context, ("rep$n|$medId|$time").hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun repeatsEnabled(context: Context): Boolean =
        context.getSharedPreferences("bodywise_data", Context.MODE_PRIVATE).getBoolean("repeat_alerts", true)

    /** "Still not taken" reminders 15 and 30 minutes after the alarm, if the person did nothing in between. */
    fun scheduleRepeats(context: Context, med: Medication, date: LocalDate, time: LocalTime, firedAt: LocalDateTime) {
        if (!repeatsEnabled(context)) return
        com.shawady.bodywise.data.REPEAT_REMINDER_MINUTES.forEachIndexed { i, minutes ->
            setExactSimple(context, firedAt.plusMinutes(minutes), repeatPending(context, med.id, date, time, i + 1, firedAt))
        }
    }

    /**
     * After a restart: a dose that came due while the phone was off rings now, and a snooze that was still running
     * is set again.
     */
    fun afterBoot(context: Context) {
        val app = context.applicationContext
        val store = MedStore(app)
        val meds = store.loadMeds()
        val logs = store.loadLogs()
        val now = LocalDateTime.now()
        com.shawady.bodywise.data.slotsFor(meds, now.toLocalDate()).forEach { slot ->
            when (com.shawady.bodywise.data.slotState(slot, logs, now)) {
                com.shawady.bodywise.data.SlotState.DUE -> {
                    showDose(app, slot.med, slot.date, slot.time, prefix = "⏰ فاتتك جرعة: ")
                    scheduleRepeats(app, slot.med, slot.date, slot.time, now)
                }
                com.shawady.bodywise.data.SlotState.SNOOZED -> {
                    val until = logs[slot.key]?.at?.plusMinutes(SNOOZE_MINUTES)
                    if (until != null && until.isAfter(now)) setAlarm(app, until, pending(app, slot.med.id, slot.date, slot.time, snooze = true))
                    else showDose(app, slot.med, slot.date, slot.time, prefix = "⏰ تذكير: ")
                }
                else -> {}
            }
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
                setAlarm(app, next, pending(app, med.id, next.toLocalDate(), time))
                codes.add(code(med.id, time).toString())
            }
        }
        prefs.edit().putStringSet(REGISTRY, codes).apply()
        com.shawady.bodywise.widget.MedWidgetProvider.refresh(app)
    }

    fun scheduleSnooze(context: Context, med: Medication, date: LocalDate, time: LocalTime) {
        val at = LocalDateTime.now().plusMinutes(SNOOZE_MINUTES)
        setAlarm(context, at, pending(context, med.id, date, time, snooze = true))
    }

    fun showDose(context: Context, med: Medication, date: LocalDate, time: LocalTime, prefix: String = "") {
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
        val alarmIntent = Intent(context, DoseAlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_MED, med.id); putExtra(EXTRA_DATE, date.toString()); putExtra(EXTRA_TIME, time.toString())
        }
        val fullScreen = PendingIntent.getActivity(context, id + 3, alarmIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val dose = listOf(med.dose, med.unit).filter { it.isNotBlank() }.joinToString(" ")
        val extra = med.instruction.takeIf { it.isNotBlank() && it != "لا يهم" }
        val text = listOfNotNull(dose.ifBlank { null }, extra).joinToString(" • ").ifBlank { "حان موعد الدواء" }
        val builder = NotificationCompat.Builder(context, channelId(context))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${if (prefix.isBlank()) "💊 " else prefix}${com.shawady.bodywise.data.medLabel(med, com.shawady.bodywise.data.HealthStore(context).loadPeople())} — ${time.format12()}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text + if (med.notes.isNotBlank()) "\n${med.notes}" else ""))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreen, true)
            .addAction(action(ACTION_TAKEN, "أخدتها ✓", 1))
            .addAction(action(ACTION_SNOOZE, "أجّل $SNOOZE_MINUTES دقايق", 2))
        val notification = builder.build()
        notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT
        manager.notify(id, notification)
        // With "display over other apps" allowed, the alarm screen can open even while the phone is in use.
        if (Settings.canDrawOverlays(context)) runCatching { context.startActivity(alarmIntent) }
    }

    /** Real end-to-end check: an alarm that goes through the same AlarmManager path as a dose, [minutes] from now. */
    fun scheduleTestIn(context: Context, minutes: Long) {
        val intent = Intent(context, TestReceiver::class.java).setAction("com.shawady.bodywise.TEST_ALARM")
        val pi = PendingIntent.getBroadcast(context, 9003, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        setAlarm(context, LocalDateTime.now().plusMinutes(minutes), pi)
        android.widget.Toast.makeText(context, "اتظبط تنبيه بعد $minutes دقيقة. اقفل الشاشة وانتظر.", android.widget.Toast.LENGTH_LONG).show()
    }

    fun sendTest(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, channelId(context))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("💊 تجربة تنبيه الدواء")
            .setContentText("لو شفت الرسالة دي وسمعت الصوت يبقى التنبيهات شغالة.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setFullScreenIntent(
                PendingIntent.getActivity(context, 9002, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT), true
            )
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
                    if (!done) {
                        MedAlarms.showDose(app, med, date, time)
                        MedAlarms.scheduleRepeats(app, med, date, time, LocalDateTime.now())
                    }
                }
                MedAlarms.rescheduleAll(app)
            }
            MedAlarms.ACTION_REPEAT -> {
                val firedAt = runCatching { LocalDateTime.parse(intent.getStringExtra(MedAlarms.EXTRA_FIRED_AT)) }.getOrNull() ?: return
                if (med != null && com.shawady.bodywise.data.shouldRepeatReminder(store.loadLogs()[com.shawady.bodywise.data.doseKey(med.id, date, time)], firedAt)) {
                    MedAlarms.showDose(app, med, date, time, prefix = "⏰ لسه ما اتاخدتش: ")
                }
            }
            MedAlarms.ACTION_TAKEN -> {
                if (med != null) store.mark(med, date, time, DoseStatus.TAKEN, LocalDateTime.now())
                app.getSystemService(NotificationManager::class.java).cancel(("false|$medId|$time").hashCode())
                com.shawady.bodywise.widget.MedWidgetProvider.refresh(app)
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
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) runCatching { MedAlarms.afterBoot(context) }
        ApptAlarms.rescheduleAll(context)
        Reminders.setDaily(context, context.getSharedPreferences("bodywise_data", Context.MODE_PRIVATE).getBoolean("daily_checks", true))
    }
}

class TestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MedAlarms.sendTest(context.applicationContext)
    }
}
