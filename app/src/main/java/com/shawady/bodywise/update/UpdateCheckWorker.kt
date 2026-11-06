package com.shawady.bodywise.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.shawady.bodywise.BuildConfig
import com.shawady.bodywise.MainActivity
import com.shawady.bodywise.R
import java.util.concurrent.TimeUnit

object UpdateChecks {
    const val CHANNEL = "bodywise_updates"
    private const val WORK = "update_check"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "تحديثات التطبيق", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    /** Checks for a new release about twice a day while online. The notice itself is optional (see settings). */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** Tells the person a new version exists. Never installs anything: that always needs their approval. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("bodywise_data", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("update_notices", true)) return Result.success()
        val info = runCatching { UpdateManager.fetchLatest() }.getOrNull() ?: return Result.success()
        if (!versionIsNewer(info.tag, BuildConfig.VERSION_NAME)) return Result.success()
        if (prefs.getString("update_notified_tag", null) == info.tag) return Result.success()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return Result.success()
        UpdateChecks.ensureChannel(applicationContext)
        val open = PendingIntent.getActivity(
            applicationContext, 7001, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, UpdateChecks.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🎉 تحديث جديد لـ BodyWise (${info.tag})")
            .setContentText("افتح التطبيق وحدّث وقت ما تحب.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(7001, notification)
        prefs.edit().putString("update_notified_tag", info.tag).apply()
        return Result.success()
    }
}
