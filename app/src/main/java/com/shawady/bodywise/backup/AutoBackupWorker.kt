package com.shawady.bodywise.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.shawady.bodywise.data.BodyWiseStore
import com.shawady.bodywise.data.SettingsStore
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Writes a full backup ZIP into the folder the user picked, once a week, keeping the latest five. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val settingsStore = SettingsStore(applicationContext)
        val settings = settingsStore.load()
        val uri = settings.autoBackupUri?.let { Uri.parse(it) } ?: return Result.success()
        return try {
            val folder = DocumentFile.fromTreeUri(applicationContext, uri)
            if (folder == null || !folder.canWrite()) return Result.failure()
            val store = BodyWiseStore(applicationContext)
            val file = folder.createFile("application/zip", "$PREFIX${LocalDate.now()}.zip") ?: return Result.retry()
            val output = applicationContext.contentResolver.openOutputStream(file.uri) ?: return Result.retry()
            output.use { BackupManager.writeZip(it, store.load(), store.photosDir) }
            folder.listFiles()
                .filter { it.name?.startsWith(PREFIX) == true }
                .sortedByDescending { it.name }
                .drop(KEEP)
                .forEach { it.delete() }
            settingsStore.save(settingsStore.load().copy(lastAutoBackup = LocalDate.now().toString()))
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PREFIX = "BodyWise-auto-"
        private const val KEEP = 5
        private const val WORK = "bodywise_auto_backup"

        fun schedule(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (enabled) {
                val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build()
                manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
            } else {
                manager.cancelUniqueWork(WORK)
            }
        }
    }
}
