package com.shawady.bodywise.backup

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.shawady.bodywise.MainActivity
import com.shawady.bodywise.R
import com.shawady.bodywise.data.BodyWiseStore
import com.shawady.bodywise.data.SettingsStore
import com.shawady.bodywise.notify.Reminders
import java.io.IOException
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Writes a full backup ZIP into the folder the user picked, every N days (their choice), keeping the latest seven,
 * and tells them when it finishes (or why it failed).
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val settingsStore = SettingsStore(applicationContext)
        val settings = settingsStore.load()
        val uri = settings.autoBackupUri?.let { Uri.parse(it) } ?: return Result.success()
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        var created: DocumentFile? = null
        return try {
            val folder = DocumentFile.fromTreeUri(applicationContext, uri)
            if (folder == null || !folder.exists() || !folder.canWrite()) {
                failed(settingsStore, "الفولدر مش متاح أو مفيش صلاحية كتابة. اختار الفولدر تاني من الإعدادات.")
                return Result.failure()
            }
            val store = BodyWiseStore(applicationContext)
            val name = "$PREFIX${LocalDateTime.now().format(STAMP)}.zip"
            val file = folder.createFile("application/zip", name) ?: throw IOException("تعذر إنشاء الملف")
            created = file
            val output = applicationContext.contentResolver.openOutputStream(file.uri) ?: throw IOException("تعذر فتح الملف للكتابة")
            output.use { BackupManager.writeZip(it, store.load(), store.photosDir) }
            val size = file.length()
            if (size <= 0L) throw IOException("الملف طلع فاضي")
            folder.listFiles()
                .filter { it.name?.startsWith(PREFIX) == true && it.uri != file.uri }
                .sortedByDescending { it.name }
                .drop(KEEP - 1)
                .forEach { it.delete() }
            val mb = String.format(Locale.US, "%.1f", size / 1_048_576.0)
            val info = "${LocalDateTime.now().format(SHOWN)} • $mb ميجا"
            settingsStore.recordAutoBackup(info, null)
            if (manual || settings.autoBackupNotify) {
                notify("تم النسخ الاحتياطي ✅", "اتحفظ في \"${folder.name ?: "الفولدر"}\" • $mb ميجا")
            }
            Result.success()
        } catch (e: Exception) {
            runCatching { created?.delete() }
            if (runAttemptCount < 2) Result.retry()
            else { failed(settingsStore, "فشل النسخ الاحتياطي: ${e.message ?: "خطأ غير معروف"}"); Result.failure() }
        }
    }

    private fun failed(settingsStore: SettingsStore, message: String) {
        settingsStore.recordAutoBackup(null, message)
        notify("فشل النسخ الاحتياطي ⚠️", message)
    }

    private fun notify(title: String, text: String) {
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val PREFIX = "BodyWise-auto-"
        private const val KEEP = 7
        private const val WORK = "bodywise_auto_backup"
        private const val WORK_NOW = "bodywise_auto_backup_now"
        private const val KEY_MANUAL = "manual"
        private const val NOTIFICATION_ID = 4101
        private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
        private val SHOWN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        /** Runs every [days] days at about 3 AM, when the phone is usually idle; [days] changes take effect right away. */
        fun schedule(context: Context, enabled: Boolean, days: Int) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) { manager.cancelUniqueWork(WORK); return }
            val now = LocalDateTime.now()
            var first = now.toLocalDate().atTime(3, 0)
            if (!first.isAfter(now)) first = first.plusDays(1)
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(days.coerceAtLeast(1).toLong(), TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, first).toMinutes(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setBackoffRequest(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                .build()
            manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** One backup right now, with a notification when it finishes. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setInputData(Data.Builder().putBoolean(KEY_MANUAL, true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
