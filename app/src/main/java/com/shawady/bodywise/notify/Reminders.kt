package com.shawady.bodywise.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.shawady.bodywise.MainActivity
import com.shawady.bodywise.R
import com.shawady.bodywise.data.BodyWiseStore
import com.shawady.bodywise.data.RUNOUT_WARN_DAYS
import com.shawady.bodywise.data.daysOfStockLeft
import com.shawady.bodywise.data.overdueVitals
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object Reminders {
    const val CHANNEL = "bodywise_reminders"
    private const val WATER = "water_reminder"
    private const val WEEKLY = "weekly_reminder"
    private const val DAILY = "daily_checks"

    /** Every day at 10:00: medicines about to run out and vitals not recorded lately. */
    fun setDaily(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (enabled) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(LocalTime.of(10, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next))
                .setInputData(Data.Builder().putString("kind", "daily").build())
                .build()
            manager.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            manager.cancelUniqueWork(DAILY)
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "التذكيرات", NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun setWater(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(3, TimeUnit.HOURS)
                .setInputData(Data.Builder().putString("kind", "water").build())
                .build()
            manager.enqueueUniquePeriodicWork(WATER, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            manager.cancelUniqueWork(WATER)
        }
    }

    /** Checks every day at 18:00; the worker only notifies on Fridays when no measurement was logged this week. */
    fun setWeekly(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (enabled) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(LocalTime.of(18, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next))
                .setInputData(Data.Builder().putString("kind", "weekly").build())
                .build()
            manager.enqueueUniquePeriodicWork(WEEKLY, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            manager.cancelUniqueWork(WEEKLY)
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val snapshot = BodyWiseStore(applicationContext).load()
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        when (inputData.getString("kind")) {
            "water" -> {
                val cups = snapshot.water[today.toString()] ?: 0
                if (now.hour in 9..21 && cups < snapshot.waterGoal) {
                    show(1, "وقت المياه 💧", "شربت $cups من ${snapshot.waterGoal} أكواب النهارده.")
                }
            }
            "daily" -> {
                val runningOut = snapshot.medications.mapNotNull { m -> daysOfStockLeft(m)?.takeIf { it <= RUNOUT_WARN_DAYS }?.let { com.shawady.bodywise.data.medLabel(m, snapshot.people) to it } }
                if (runningOut.isNotEmpty()) {
                    show(3, "💊 دواء قرّب يخلص", runningOut.joinToString("، ") { (n, d) -> if (d <= 0) "$n خلص" else "$n هيخلص بعد $d يوم" })
                }
                val overdue = overdueVitals(snapshot.vitals, now)
                if (overdue.isNotEmpty()) {
                    show(4, "🩺 قياسات ما اتسجلتش", overdue.entries.joinToString("، ") { (t, d) -> "${t.label} من $d يوم" })
                }
            }
            "weekly" -> {
                val recent = snapshot.measurements.any { !it.date.isBefore(today.minusDays(6)) }
                if (now.dayOfWeek == DayOfWeek.FRIDAY && !recent) {
                    show(2, "قياسات الأسبوع 📏", "سجّل وزنك وقياساتك وصورة التقدم الأسبوعية.")
                }
            }
        }
        return Result.success()
    }

    private fun show(id: Int, title: String, text: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        Reminders.ensureChannel(applicationContext)
        val open = PendingIntent.getActivity(
            applicationContext, 0, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }
}
