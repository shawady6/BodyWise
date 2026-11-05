package com.shawady.bodywise

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

private const val INSTALL_ACTION = "com.shawady.bodywise.INSTALL_RESULT"

/** Last install result message for the UI (null when there is nothing new). */
internal object InstallEvents {
    val message = MutableStateFlow<String?>(null)
}

/** Receives the system installer's progress and surfaces the real failure reason. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                        .onFailure { InstallEvents.message.value = "تعذر فتح شاشة تأكيد التثبيت: ${it.message ?: ""}" }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> InstallEvents.message.value = "تم تثبيت التحديث."
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallEvents.message.value = "تم إلغاء التثبيت."
            PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallEvents.message.value =
                "فشل التثبيت: تعارض مع النسخة المثبتة (غالبًا توقيع مختلف). خد نسخة احتياطية، امسح BodyWise مرة واحدة، ثم ثبّت النسخة الجديدة. ($detail)"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallEvents.message.value = "فشل التثبيت: النسخة غير متوافقة مع هذا الجهاز. ($detail)"
            PackageInstaller.STATUS_FAILURE_INVALID -> InstallEvents.message.value = "فشل التثبيت: ملف التحديث غير صالح. ($detail)"
            PackageInstaller.STATUS_FAILURE_STORAGE -> InstallEvents.message.value = "فشل التثبيت: مساحة التخزين غير كافية. ($detail)"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallEvents.message.value = "تم حظر التثبيت من إعدادات الجهاز أو الحماية. ($detail)"
            else -> InstallEvents.message.value = "فشل التثبيت (كود $status): $detail"
        }
    }
}

/**
 * Installs through the official PackageInstaller session API. Android still asks the user to
 * approve; nothing is installed silently, and failures come back with a reason.
 */
internal fun installApk(context: Context, apk: File) {
    val installer = context.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
    params.setSize(apk.length())
    val sessionId = installer.createSession(params)
    installer.openSession(sessionId).use { session ->
        apk.inputStream().use { input ->
            session.openWrite("bodywise.apk", 0, apk.length()).use { output ->
                input.copyTo(output)
                session.fsync(output)
            }
        }
        val intent = Intent(context, InstallResultReceiver::class.java)
            .setAction(INSTALL_ACTION)
            .setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        session.commit(PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender)
    }
}
